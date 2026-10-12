package forge.screens.home.hexcrawl;

import forge.gui.framework.ICDoc;

/** Controller of the hex campaign home screen entry. */
public enum CSubmenuHexCrawl implements ICDoc {
    SINGLETON_INSTANCE;

    @Override
    public void register() {
    }

    @Override
    public void initialize() {
        // create the screen at start-up: as a guest it has to be there when the host's campaign arrives
        VSubmenuHexCrawl.SINGLETON_INSTANCE.getPanel();
    }

    @Override
    public void update() {
        final HexCrawlPanel panel = VSubmenuHexCrawl.SINGLETON_INSTANCE.getPanel();
        panel.loadLastCampaign();
        panel.refresh();
    }
}
