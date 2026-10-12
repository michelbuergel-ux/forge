package forge.screens.home.hexcrawl;

import javax.swing.JPanel;

import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.screens.home.EMenuGroup;
import forge.screens.home.IVSubmenu;
import forge.screens.home.VHomeUI;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/** Home screen entry for the hex campaign. */
public enum VSubmenuHexCrawl implements IVSubmenu<CSubmenuHexCrawl> {
    SINGLETON_INSTANCE;

    private final Localizer localizer = Localizer.getInstance();
    private DragCell parentCell;
    private final DragTab tab = new DragTab(localizer.getMessage("lblHexCampaign"));
    private HexCrawlPanel panel;

    public HexCrawlPanel getPanel() {
        if (panel == null) {
            panel = new HexCrawlPanel();
        }
        return panel;
    }

    @Override
    public EMenuGroup getGroupEnum() {
        return EMenuGroup.SANCTIONED;
    }

    @Override
    public String getMenuTitle() {
        return localizer.getMessage("lblHexCampaign");
    }

    @Override
    public EDocID getItemEnum() {
        return EDocID.HOME_HEXCRAWL;
    }

    @Override
    public EDocID getDocumentID() {
        return EDocID.HOME_HEXCRAWL;
    }

    @Override
    public DragTab getTabLabel() {
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

    @Override
    public void populate() {
        final JPanel container = VHomeUI.SINGLETON_INSTANCE.getPnlDisplay();
        container.removeAll();
        container.setLayout(new MigLayout("insets 0, gap 0, fill"));
        container.add(getPanel(), "grow");
        getPanel().loadLastCampaign();
        getPanel().refresh();
        if (container.isShowing()) {
            container.validate();
            container.repaint();
        }
    }
}
