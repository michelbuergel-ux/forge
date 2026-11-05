package forge.gamemodes.net.server;

import forge.gamemodes.net.CompatibleObjectDecoder;
import forge.gamemodes.net.CompatibleObjectEncoder;
import forge.gamemodes.net.ReplyPool;
import forge.trackable.Tracker;
import forge.gamemodes.net.event.IdentifiableNetEvent;
import forge.gamemodes.net.event.NetEvent;
import forge.util.IHasForgeLog;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import io.netty.util.IllegalReferenceCountException;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RemoteClient implements IToClient, IHasForgeLog {

    /** Special value indicating the client hasn't been assigned a slot yet. */
    public static final int UNASSIGNED_SLOT = -1;

    private volatile Channel channel;
    private String username;
    private int index = UNASSIGNED_SLOT;
    private boolean libgdx;
    private volatile ReplyPool replies = new ReplyPool();
    private volatile Tracker codecTracker;
    private volatile int codecConsumerId = -1;
    private final AtomicInteger sendErrors = new AtomicInteger(0);
    private RemoteClientGuiGame gui;
    /**
     * The questions {@link #sendAndWait} sent and still waits for an answer to, by id, as they went out (a retained
     * copy, released when the answer comes): {@link #resendWaiting} sends them again.
     */
    private final Map<Integer, ByteBuf> waiting = new ConcurrentHashMap<>();

    // Package-private: SaturationLoggingHandler reads/resets these on writability transitions
    volatile long saturationStartMs = 0L;
    final AtomicInteger sendsDuringSaturation = new AtomicInteger(0);

    private void recordSendIfSaturated(final Channel ch) {
        if (!ch.isWritable()) {
            sendsDuringSaturation.incrementAndGet();
        }
    }

    public RemoteClient(final Channel channel) {
        this.channel = channel;
    }

    /**
     * Swap the underlying channel for a reconnecting client.
     * Updates the channel, creates a fresh ReplyPool, and re-applies the codec
     * tracker to the new channel's pipeline so IdRef resolution keeps working.
     */
    public void swapChannel(final Channel newChannel) {
        this.channel = newChannel;
        this.replies = new ReplyPool();
        applyCodecTracker(newChannel);
    }

    /**
     * Check if this client has been assigned a valid lobby slot.
     * @return true if the client has a valid slot (index >= 0)
     */
    public boolean hasValidSlot() {
        return index >= 0;
    }

    /** Remote peer address, for admission limits and logging. */
    public SocketAddress getRemoteAddress() {
        final Channel ch = channel;
        return ch == null ? null : ch.remoteAddress();
    }

    /** Encodes synchronously on the caller's thread. Returns null on failure (logged). */
    private ByteBuf encodeOnCallingThread(final NetEvent event) {
        final Channel ch = channel;
        final CompatibleObjectEncoder encoder = ch.pipeline().get(CompatibleObjectEncoder.class);
        if (encoder == null) {
            netLog.error("No encoder in pipeline for {} (event: {})", username, event);
            sendErrors.incrementAndGet();
            return null;
        }
        try {
            return encoder.encodeToBuf(event, ch.alloc());
        } catch (Exception e) {
            sendErrors.incrementAndGet();
            netLog.error(e, "Network encode error for {} (event: {})", username, event);
            return null;
        }
    }

    @Override
    public void send(final NetEvent event) {
        final Channel ch = channel;
        recordSendIfSaturated(ch);
        final ByteBuf encoded = encodeOnCallingThread(event);
        if (encoded == null) return;
        ch.writeAndFlush(encoded).addListener(f -> {
            if (!f.isSuccess()) {
                sendErrors.incrementAndGet();
                Throwable c = f.cause();
                if (c != null) {
                    netLog.error(c, "Network send error for {} (event: {})", username, event);
                } else {
                    netLog.error("Network send error for {} (event: {}, cause: {})",
                            username, event, f.isCancelled() ? "cancelled" : "no cause");
                }
            }
        });
    }

    @Override
    public void write(final NetEvent event) {
        final Channel ch = channel;
        recordSendIfSaturated(ch);
        final ByteBuf encoded = encodeOnCallingThread(event);
        if (encoded == null) return;
        ch.write(encoded).addListener(f -> {
            if (!f.isSuccess()) {
                sendErrors.incrementAndGet();
                Throwable c = f.cause();
                if (c != null) {
                    netLog.error(c, "Network write error for {} (event: {})", username, event);
                } else {
                    netLog.error("Network write error for {} (event: {}, cause: {})",
                            username, event, f.isCancelled() ? "cancelled" : "no cause");
                }
            }
        });
    }

    @Override
    public Object sendAndWait(final IdentifiableNetEvent event) {
        replies.initialize(event.getId());
        final Channel ch = channel;
        recordSendIfSaturated(ch);
        final ByteBuf encoded = encodeOnCallingThread(event);
        if (encoded == null) {
            replies.complete(event.getId(), null);
            return replies.get(event.getId());
        }
        waiting.put(event.getId(), encoded.retainedDuplicate()); // kept until the answer, to be sent again if need be
        try {
            return writeAndWait(ch, encoded, event);
        } finally {
            final ByteBuf kept = waiting.remove(event.getId());
            if (kept != null) {
                kept.release();
            }
        }
    }

    /**
     * Sends again every question this client has not answered yet: the same bytes with the same id, so its answer
     * reaches the game that waits for it. For a fight that hangs because a question or its answer got lost on the
     * way (else the game thread waits for ever); a question that only waits for a slow player is shown to them a
     * second time, and of two answers the first counts. Returns how many questions were sent.
     */
    public int resendWaiting() {
        final Channel ch = channel;
        int sent = 0;
        for (final Map.Entry<Integer, ByteBuf> e : waiting.entrySet()) {
            final ByteBuf copy;
            try {
                copy = e.getValue().retainedDuplicate();
            } catch (final IllegalReferenceCountException answered) {
                continue; // answered (and released) meanwhile
            }
            if (ch == null) {
                copy.release();
                continue;
            }
            netLog.info("Sending question {} to {} again", e.getKey(), username);
            ch.writeAndFlush(copy).addListener(f -> {
                if (!f.isSuccess()) {
                    sendErrors.incrementAndGet();
                    netLog.error("Sending question {} to {} again failed", e.getKey(), username);
                }
            });
            sent++;
        }
        return sent;
    }

    private Object writeAndWait(final Channel ch, final ByteBuf encoded, final IdentifiableNetEvent event) {
        ch.writeAndFlush(encoded).addListener(f -> {
            if (!f.isSuccess()) {
                sendErrors.incrementAndGet();
                Throwable c = f.cause();
                if (c != null) {
                    netLog.error(c, "sendAndWait write failed for {} (event: {})", username, event);
                } else {
                    netLog.error("sendAndWait write failed for {} (event: {}, cause: {})",
                            username, event, f.isCancelled() ? "cancelled" : "no cause");
                }
                replies.complete(event.getId(), null);
            }
        });
        return replies.get(event.getId());
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(final String username) {
        this.username = username;
    }

    public int getIndex() {
        return index;
    }
    public void setIndex(final int index) {
        this.index = index;
    }

    public boolean isLibgdx() {
        return libgdx;
    }
    public void setLibgdx(final boolean libgdx) {
        this.libgdx = libgdx;
    }

    public RemoteClientGuiGame getGui() {
        return gui;
    }
    void setGui(final RemoteClientGuiGame gui) {
        this.gui = gui;
    }

    /**
     * Set the tracker and per-client consumerId on the channel's encoder and
     * decoder. Called when the game starts (before any client protocol
     * messages arrive). Cached so that {@link #swapChannel} can re-apply
     * after a reconnect. The consumerId is the {@code DeltaSyncManager} id
     * for this client; the encoder uses it to gate IdRef substitution to
     * objects this client has actually been told about.
     */
    public void setCodecTracker(Tracker tracker, int consumerId) {
        // Skip no-op rebinds: setGameView fires on every view push, the tracker changes per game.
        if (tracker == codecTracker && consumerId == codecConsumerId) {
            return;
        }
        this.codecTracker = tracker;
        this.codecConsumerId = consumerId;
        applyCodecTracker(channel);
    }

    private void applyCodecTracker(Channel ch) {
        if (codecTracker == null || ch == null) {
            return;
        }
        // Swap on the event loop so it lands between decoded frames: a message the
        // client sent against the previous game must not resolve against the new one.
        if (!ch.eventLoop().inEventLoop()) {
            ch.eventLoop().execute(() -> applyCodecTracker(ch));
            return;
        }
        CompatibleObjectEncoder encoder = ch.pipeline().get(CompatibleObjectEncoder.class);
        if (encoder != null) {
            encoder.setTracker(codecTracker);
            encoder.setConsumerId(codecConsumerId);
        }
        CompatibleObjectDecoder decoder = ch.pipeline().get(CompatibleObjectDecoder.class);
        if (decoder != null) {
            decoder.setTracker(codecTracker);
        }
    }

    public int getSendErrorCount() {
        return sendErrors.get();
    }

    ReplyPool getReplyPool() {
        return replies;
    }
}
