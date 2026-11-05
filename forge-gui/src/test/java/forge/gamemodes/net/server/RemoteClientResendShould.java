package forge.gamemodes.net.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.Test;

import forge.gamemodes.net.CompatibleObjectEncoder;
import forge.gamemodes.net.ProtocolMethod;
import forge.gamemodes.net.event.GuiGameEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.embedded.EmbeddedChannel;

/** A question the host waits on can be sent to the guest again (a hanging fight: it, or its answer, got lost). */
public class RemoteClientResendShould {

    private static ByteBuf next(final EmbeddedChannel ch) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            final ByteBuf out = ch.readOutbound();
            if (out != null) {
                return out;
            }
            Thread.sleep(10);
        }
        return null;
    }

    @Test
    public void sendAQuestionAgainWithTheSameBytesUntilItIsAnswered() throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel(new CompatibleObjectEncoder(null));
        final RemoteClient client = new RemoteClient(ch);
        final GuiGameEvent question = new GuiGameEvent(ProtocolMethod.showConfirmDialog, "Go on?", "Fight", "Yes", "No", true);
        final AtomicReference<Object> answer = new AtomicReference<>();
        final Thread game = new Thread(() -> answer.set(client.sendAndWait(question)), "Game-test");
        game.start();

        final ByteBuf first = next(ch);
        assertThat(first).as("sent once").isNotNull();
        assertThat(client.resendWaiting()).isEqualTo(1);
        final ByteBuf again = next(ch);
        assertThat(again).as("sent again").isNotNull();
        assertThat(ByteBufUtil.equals(first, again)).as("the same question, the same id").isTrue();

        // the answer to it: the game goes on with it, and nothing waits any more
        client.getReplyPool().complete(question.getId(), Boolean.TRUE);
        game.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(game.isAlive()).isFalse();
        assertThat(answer.get()).isEqualTo(Boolean.TRUE);
        assertThat(client.resendWaiting()).isZero();
        // a second answer (to the question shown twice) changes nothing
        client.getReplyPool().complete(question.getId(), Boolean.FALSE);
        assertThat(answer.get()).isEqualTo(Boolean.TRUE);

        first.release();
        again.release();
        ch.finishAndReleaseAll();
    }
}
