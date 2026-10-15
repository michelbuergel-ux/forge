package forge.gamemodes.hexcrawl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.Gson;

import forge.gamemodes.net.event.HexActionEvent;

/**
 * A running campaign with the people playing it. On the host (or when playing alone) the
 * session owns the campaign and applies every action; on a guest it mirrors the host's state
 * and sends the guest's actions to the host.
 *
 * Every human head has the same rights: each votes where to go, each confirms a fight, each
 * picks its own reward and trades for itself. Heads without a person at the table (companions,
 * or players who are not connected) are played by the AI and never hold the others up.
 */
public class HexSession {
    public interface Listener {
        /** Something changed (may be called from any thread). */
        void sessionChanged();
        /** Host only: everybody is ready, start the pending fight now. */
        void startFight();
    }

    /** What the host sends to the guests. */
    public static final class State {
        public String campaignJson;
        public Map<String, String> votes = new LinkedHashMap<>();
        public List<String> fightReady = new ArrayList<>();
        public List<String> present = new ArrayList<>();
        public boolean fightRunning;
        public String hostHead;
        /** The build of the host (see {@link HexNet#BUILD_ID}). */
        public String buildId;
    }

    private static final Gson GSON = new Gson();

    private HexCampaign campaign;
    private final boolean guest;
    private String myHead;
    private String hostHead;
    private final Map<String, String> votes = new LinkedHashMap<>();
    private final Set<String> fightReady = new LinkedHashSet<>();
    private final Set<String> present = new LinkedHashSet<>();
    private boolean fightRunning;
    private String hostBuild;
    private Listener listener;

    /** A session on the computer that owns the campaign. */
    public HexSession(final HexCampaign campaign0) {
        campaign = campaign0;
        guest = false;
        for (final HexPlayer p : campaign.players) {
            if (!p.ai) {
                myHead = p.name;
                break;
            }
        }
        if (myHead == null) {
            myHead = campaign.players.get(0).name;
        }
        hostHead = myHead;
        present.add(myHead);
    }

    private HexSession() {
        guest = true;
    }

    /** A session on a guest's computer; it is empty until the first state arrives. */
    public static HexSession forGuest() {
        return new HexSession();
    }

    public void setListener(final Listener listener0) { listener = listener0; }
    public HexCampaign getCampaign() { return campaign; }
    public boolean isGuest() { return guest; }
    public String getMyHead() { return myHead; }
    public String getHostHead() { return hostHead; }
    public synchronized Map<String, String> getVotes() { return new LinkedHashMap<>(votes); }
    public synchronized Set<String> getFightReady() { return new LinkedHashSet<>(fightReady); }
    public synchronized Set<String> getPresent() { return new LinkedHashSet<>(present); }
    public boolean isFightRunning() { return fightRunning; }
    /** Guest: does the host run the same build as this program? */
    public boolean sameBuildAsHost() { return !guest || HexNet.buildId().equals(hostBuild); }

    /** Host: the guests whose program is not the same build as the host's (they cannot play a fight together). */
    public synchronized Set<String> guestsWithOtherBuild() {
        final Set<String> result = new java.util.TreeSet<>();
        for (final Map.Entry<String, String> e : guestBuilds.entrySet()) {
            if (present.contains(e.getKey()) && !HexNet.buildId().equals(e.getValue())) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /** Host: the build of each guest, as their last action told it. */
    private final Map<String, String> guestBuilds = new LinkedHashMap<>();

    /** True for heads played by the AI in a fight: companions and people who are not connected. */
    public synchronized boolean isAiHead(final String head) {
        final HexPlayer p = campaign == null ? null : campaign.player(head);
        return p == null || p.ai || !present.contains(head);
    }

    /** May the given person act for the given head? Everyone for themselves; the host also for AI heads. */
    public synchronized boolean mayActFor(final String actor, final String head) {
        if (actor == null || head == null || campaign == null || campaign.player(head) == null) {
            return false;
        }
        return actor.equals(head) || (actor.equals(hostHead) && isAiHead(head));
    }

    // ------------------------------------------------------------------ what a player can do

    public void vote(final int q, final int r) {
        act(new HexActionEvent(HexActionEvent.VOTE, q, r, null, false, null));
    }

    public void readyToFight() {
        act(new HexActionEvent(HexActionEvent.FIGHT, 0, 0, null, true, null));
    }

    public void chooseReward(final boolean takeCard) {
        act(new HexActionEvent(HexActionEvent.REWARD, 0, 0, null, takeCard, null));
    }

    /** Buys a card or a relic; the shop may be null if it does not matter which shop sells it. */
    public void buy(final String head, final HexShopType shop, final String card) {
        act(new HexActionEvent(HexActionEvent.BUY, shop == null ? -1 : shop.ordinal(), 0, card, false, head));
    }

    public void buy(final String head, final String card) {
        buy(head, null, card);
    }

    public void sellRelic(final String head, final String relic) {
        act(new HexActionEvent(HexActionEvent.SELL_RELIC, 0, 0, relic, false, head));
    }

    /** The party takes on a task of the town; every person at the table may do that. */
    public void acceptQuest(final int questId) {
        act(new HexActionEvent(HexActionEvent.QUEST, questId, 0, null, false, null));
    }

    public void sell(final String head, final String card) {
        act(new HexActionEvent(HexActionEvent.SELL, 0, 0, card, false, head));
    }

    public void buyLand(final String head, final HexColor color) {
        act(new HexActionEvent(HexActionEvent.LAND, 0, 0, color.name(), false, head));
    }

    private void act(final HexActionEvent action) {
        if (guest) {
            HexNet.sendToHost(action);
        } else {
            handle(myHead, action);
        }
    }

    // ------------------------------------------------------------------ host side

    /** Host: applies an action of the given person. Wrong or late actions are ignored. */
    public void handle(final String actor, final HexActionEvent action) {
        if (guest || campaign == null || actor == null) {
            return;
        }
        boolean start = false;
        synchronized (this) {
            if (!actor.equals(myHead)) {
                guestBuilds.put(actor, action.getBuild());
            }
            final String head = action.getHead() == null ? actor : action.getHead();
            if (!mayActFor(actor, head) || (!present.contains(actor))) {
                return;
            }
            final String type = action.getType();
            if (HexActionEvent.VOTE.equals(type)) {
                final HexTile target = campaign.map.get(action.getQ(), action.getR());
                if (!fightRunning && campaign.canEnter(target)) {
                    votes.put(actor, target.key());
                }
            } else if (HexActionEvent.FIGHT.equals(type)) {
                if (campaign.pendingEncounter != null && !fightRunning) {
                    fightReady.add(actor);
                }
            } else if (HexActionEvent.REWARD.equals(type)) {
                if (campaign.pendingEncounter == null) {
                    campaign.chooseReward(head, action.getFlag());
                }
            } else if (HexActionEvent.BUY.equals(type)) {
                final int shop = action.getQ();
                campaign.buy(head, shop >= 0 && shop < HexShopType.values().length ? HexShopType.values()[shop] : null, action.getText());
            } else if (HexActionEvent.SELL_RELIC.equals(type)) {
                campaign.sellRelic(head, action.getText());
            } else if (HexActionEvent.QUEST.equals(type)) {
                campaign.acceptQuest(action.getQ());
            } else if (HexActionEvent.SELL.equals(type)) {
                campaign.sell(head, action.getText());
            } else if (HexActionEvent.LAND.equals(type)) {
                try {
                    campaign.buyBasicLand(head, HexColor.valueOf(action.getText()));
                } catch (final IllegalArgumentException ignored) {
                    // not a colour
                }
            }
            start = settle();
        }
        changed();
        if (start && listener != null) {
            listener.startFight();
        }
    }

    /** The human heads that are at the table. */
    private List<String> voters() {
        final List<String> result = new ArrayList<>();
        for (final HexPlayer p : campaign.players) {
            if (!p.ai && present.contains(p.name)) {
                result.add(p.name);
            }
        }
        return result;
    }

    /** Carries out whatever is decided now. Returns true if the fight has to start. */
    private boolean settle() {
        final List<String> voters = voters();
        votes.keySet().retainAll(voters);
        fightReady.retainAll(voters);

        // AI heads never keep the others waiting: they take the card
        for (final HexReward reward : new ArrayList<>(campaign.pendingRewards)) {
            if (campaign.pendingEncounter == null && isAiHead(reward.playerName)) {
                campaign.chooseReward(reward.playerName, true);
            }
        }

        // travelling: once everybody has voted, the field with the most votes wins
        if (!voters.isEmpty() && votes.keySet().containsAll(voters) && !campaign.isBusy() && !fightRunning) {
            final Map<String, Integer> count = new LinkedHashMap<>();
            for (final String key : votes.values()) {
                count.merge(key, 1, Integer::sum);
            }
            int best = 0;
            for (final int n : count.values()) {
                best = Math.max(best, n);
            }
            final List<String> top = new ArrayList<>();
            for (final Map.Entry<String, Integer> e : count.entrySet()) {
                if (e.getValue() == best) {
                    top.add(e.getKey());
                }
            }
            final Random rnd = campaign.random();
            final String winner = top.get(top.size() == 1 ? 0 : rnd.nextInt(top.size())); // a tie is decided by lot
            votes.clear();
            fightReady.clear();
            final String[] qr = winner.split(",");
            campaign.moveTo(Integer.parseInt(qr[0]), Integer.parseInt(qr[1]));
        } else if (campaign.isBusy()) {
            votes.clear();
        }

        if (campaign.pendingEncounter != null && !fightRunning && !voters.isEmpty() && fightReady.containsAll(voters)) {
            fightRunning = true;
            return true;
        }
        return false;
    }

    /** Host: the fight is over. */
    public void fightFinished(final boolean partyWon) {
        synchronized (this) {
            campaign.resolveBattle(partyWon);
            fightRunning = false;
            fightReady.clear();
            votes.clear();
            settle();
        }
        changed();
    }

    /** Host: the fight could not be started. */
    public void fightAborted() {
        synchronized (this) {
            fightRunning = false;
            fightReady.clear();
        }
        changed();
    }

    /**
     * Host: tells the session which guests are connected right now. A guest whose name is not in
     * the party yet joins it as a new head (up to four heads).
     * @return true if something changed
     */
    public boolean setGuests(final Collection<String> guestNames) {
        boolean start = false;
        boolean changed = false;
        synchronized (this) {
            final Set<String> now = new LinkedHashSet<>();
            now.add(hostHead);
            for (final String name : guestNames) {
                if (name == null || name.trim().isEmpty() || name.equals(hostHead)) {
                    continue;
                }
                HexPlayer p = campaign.player(name);
                if (p == null && campaign.players.size() < HexCampaign.MAX_HEADS) {
                    p = campaign.addPlayer(name);
                    changed = true;
                }
                if (p != null && !p.ai) {
                    now.add(name);
                }
            }
            if (!now.equals(present)) {
                present.clear();
                present.addAll(now);
                changed = true;
            }
            if (changed) {
                start = settle();
            }
        }
        if (changed) {
            changed();
        }
        if (start && listener != null) {
            listener.startFight();
        }
        return changed;
    }

    public synchronized String toStateJson() {
        final State s = new State();
        if (campaign.tile().poi == HexPoi.TOWN) {
            campaign.shop(); // guests can only see offers that exist
        }
        s.campaignJson = HexCampaignIO.toJson(campaign);
        s.votes.putAll(votes);
        s.fightReady.addAll(fightReady);
        s.present.addAll(present);
        s.fightRunning = fightRunning;
        s.hostHead = hostHead;
        s.buildId = HexNet.buildId();
        return GSON.toJson(s);
    }

    // ------------------------------------------------------------------ guest side

    /** Guest: takes over the state the host sent. */
    public void applyState(final String stateJson, final String yourHead) {
        if (!guest) {
            return;
        }
        final State s = GSON.fromJson(stateJson, State.class);
        if (hostBuild == null) {
            // the first state: tell the host which build this program is, before anything else is done
            HexNet.sendToHost(new HexActionEvent(HexActionEvent.HELLO, 0, 0, null, false, null));
        }
        synchronized (this) {
            campaign = HexCampaignIO.fromJson(s.campaignJson);
            myHead = yourHead;
            hostHead = s.hostHead;
            votes.clear();
            votes.putAll(s.votes);
            fightReady.clear();
            fightReady.addAll(s.fightReady);
            present.clear();
            present.addAll(s.present);
            fightRunning = s.fightRunning;
            hostBuild = s.buildId;
        }
        changed();
    }

    private void changed() {
        if (listener != null) {
            listener.sessionChanged();
        }
    }
}
