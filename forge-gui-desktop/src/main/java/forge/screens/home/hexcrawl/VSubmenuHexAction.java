package forge.screens.home.hexcrawl;

import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.screens.home.CHomeUI;
import forge.screens.home.EMenuGroup;
import forge.screens.home.IVSubmenu;
import forge.util.Localizer;

/**
 * The Hexkampagne group of the home menu, besides Start: a new campaign, loading one, and joining a friend's.
 * Each opens the campaign screen ("Start", which stays the entry selected, so that Forge does not repeat the
 * action at its next start) and then does what the campaign menu does.
 */
public enum VSubmenuHexAction implements IVSubmenu<CSubmenuHexCrawl> {
    NEW("lblHexMenuNew", HexCrawlPanel::startNewCampaign),
    LOAD("lblHexMenuLoad", HexCrawlPanel::chooseCampaignToLoad),
    JOIN("lblHexMenuJoin", HexCrawlPanel::joinFriend);

    private final String titleKey;
    private final Consumer<HexCrawlPanel> action;
    private DragCell parentCell;
    private DragTab tab;

    VSubmenuHexAction(final String titleKey0, final Consumer<HexCrawlPanel> action0) {
        titleKey = titleKey0;
        action = action0;
    }

    @Override
    public EMenuGroup getGroupEnum() {
        return EMenuGroup.HEX;
    }

    @Override
    public String getMenuTitle() {
        return Localizer.getInstance().getMessage(titleKey);
    }

    @Override
    public EDocID getItemEnum() {
        switch (this) {
            case NEW: return EDocID.HOME_HEXCRAWL_NEW;
            case LOAD: return EDocID.HOME_HEXCRAWL_LOAD;
            default: return EDocID.HOME_HEXCRAWL_JOIN;
        }
    }

    @Override
    public EDocID getDocumentID() {
        return getItemEnum();
    }

    @Override
    public DragTab getTabLabel() {
        if (tab == null) {
            tab = new DragTab(getMenuTitle());
        }
        return tab;
    }

    @Override
    public CSubmenuHexCrawl getLayoutControl() {
        return CSubmenuHexCrawl.SINGLETON_INSTANCE;
    }

    @Override
    public void setParentCell(final DragCell cell0) {
        parentCell = cell0;
    }

    @Override
    public DragCell getParentCell() {
        return parentCell;
    }

    /** Clicked: the campaign screen comes up (as Start), then the action runs on it. */
    @Override
    public void populate() {
        SwingUtilities.invokeLater(() -> {
            CHomeUI.SINGLETON_INSTANCE.itemClick(EDocID.HOME_HEXCRAWL);
            action.accept(VSubmenuHexCrawl.SINGLETON_INSTANCE.getPanel());
        });
    }
}
