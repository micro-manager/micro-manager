package org.micromanager.internal.menus;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import mmcorej.CMMCore;
import org.micromanager.alerts.internal.DefaultAlertManager;
import org.micromanager.events.internal.MouseMovesStageStateChangeEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.dialogs.OptionsDlg;
import org.micromanager.internal.dialogs.StageControlFrame;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.HotKeysDialog;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.quickaccess.internal.DefaultQuickAccessManager;
import org.micromanager.quickaccess.internal.QuickAccessPanelEvent;

/**
 * This class is responsible for Micro-Manager's Tools Menu.
 */
public final class ToolsMenu {

   private static final String MOUSE_MOVES_STAGE =
         "whether or not the hand tool can be used to move the stage";

   private final JMenu quickAccessMenu_;
   private JCheckBoxMenuItem centerAndDragMenuItem_;

   private final MMStudio mmStudio_;
   private final CMMCore core_;

   /**
    * Create Micro-Manager's Tools Menu.
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public ToolsMenu(MMStudio studio, JMenuBar menuBar) {
      mmStudio_ = studio;
      core_ = mmStudio_.core();
      quickAccessMenu_ = new JMenu("Quick Access Panels");

      JMenu toolsMenu = GUIUtils.createMenuInMenuBar(menuBar, "Tools");

      GUIUtils.addMenuItem(toolsMenu, "Refresh GUI",
            "Refresh all GUI controls directly from the hardware", () -> {
               // let updateGUI also update the core cache
               mmStudio_.uiManager().updateGUI(true, false);
            },
            "arrow_refresh.png");

      toolsMenu.addSeparator();

      GUIUtils.addMenuItem(toolsMenu, "Script Panel...",
            "Open Micro-Manager script editor window",
            mmStudio_.uiManager()::showScriptPanel);

      populateQuickAccessMenu();
      toolsMenu.add(quickAccessMenu_);

      GUIUtils.addMenuItem(toolsMenu, "Shortcuts...",
            "Create keyboard shortcuts to activate image acquisition, "
                  + "mark positions, or run custom scripts",
            HotKeysDialog::new);

      GUIUtils.addMenuItem(toolsMenu, "Messages...",
            "Show the Messages window", () -> ((DefaultAlertManager)
                  mmStudio_.alerts()).alertsWindow().showWithoutFocus(),
            "bell.png");

      toolsMenu.addSeparator();

      GUIUtils.addMenuItem(toolsMenu, "Stage Control...",
            "Control the stage position with a virtual joystick",
            () -> StageControlFrame.showStageControl(mmStudio_),
            "move.png");

      centerAndDragMenuItem_ = GUIUtils.addCheckBoxMenuItem(toolsMenu,
            "Mouse Moves Stage (Use Hand Tool)",
            "When enabled, double clicking or dragging in the snap/live\n"
                  + "window moves the XY-stage. Requires the hand tool.", () -> {
               boolean state = centerAndDragMenuItem_.isSelected();
               mmStudio_.updateCenterAndDragListener(state);
               String icon = state ? "move_hand_on.png" : "move_hand.png";
               centerAndDragMenuItem_.setIcon(IconLoader.getIcon(
                     "/org/micromanager/icons/" + icon));
            },
            getMouseMovesStage());
      String icon = getMouseMovesStage() ? "move_hand_on.png" : "move_hand.png";
      centerAndDragMenuItem_.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/" + icon));

      GUIUtils.addMenuItem(toolsMenu, "Stage Position List...",
            "Open the stage position list window",
            () -> mmStudio_.app().showPositionList(),
            "application_view_list.png");

      toolsMenu.addSeparator();

      GUIUtils.addMenuItem(toolsMenu, "Multi-Dimensional Acquisition...",
            "Open multi-dimensional acquisition setup window",
            mmStudio_.uiManager()::openAcqControlDialog,
            "film.png");

      toolsMenu.addSeparator();

      GUIUtils.addMenuItem(toolsMenu, "Options...",
            "Set a variety of Micro-Manager configuration options", () -> {
               final int oldBufsize = mmStudio_.settings().getCircularBufferSize();

               OptionsDlg dlg = new OptionsDlg(core_, mmStudio_);
               dlg.setVisible(true);
               // adjust memory footprint if necessary
               if (oldBufsize != mmStudio_.settings().getCircularBufferSize()) {
                  try {
                     core_.setCircularBufferMemoryFootprint(
                           mmStudio_.settings().getCircularBufferSize());
                  } catch (Exception exc) {
                     ReportingUtils.showError(exc);
                  }
               }
            });

      mmStudio_.events().registerForEvents(this);
   }

   private void populateQuickAccessMenu() {
      quickAccessMenu_.removeAll();
      GUIUtils.addMenuItem(quickAccessMenu_, "Create New Panel",
            "Create a new Quick Access Panel, for easy access to commonly-used controls.",
            () -> ((DefaultQuickAccessManager) mmStudio_.quickAccess()).createNewPanel());

      final Map<String, JFrame> titleToFrame = mmStudio_.quickAccess().getPanels();
      ArrayList<String> titles = new ArrayList<>(titleToFrame.keySet());
      Collections.sort(titles);

      JMenu deleteMenu = new JMenu("Delete...");
      deleteMenu.setEnabled(titles.size() > 0);
      for (final String title : titles) {
         GUIUtils.addMenuItem(deleteMenu, title, "Delete this panel",
               () -> ((DefaultQuickAccessManager)
                     mmStudio_.quickAccess()).promptToDelete(titleToFrame.get(title)));
      }
      quickAccessMenu_.add(deleteMenu);
      quickAccessMenu_.addSeparator();
      JMenuItem show = GUIUtils.addMenuItem(quickAccessMenu_, "Show all",
            "Show all Quick Access Panels; create a new one if necessary",
            () -> mmStudio_.quickAccess().showPanels());
      show.setEnabled(titles.size() > 0);

      for (final String title : titles) {
         GUIUtils.addMenuItem(quickAccessMenu_, title, "",
               () -> titleToFrame.get(title).setVisible(true));
      }

      quickAccessMenu_.addSeparator();

      GUIUtils.addMenuItem(quickAccessMenu_, "Save Settings...",
            "Save the Quick Access Panel settings to a file for use elsewhere", () -> {
               JFileChooser chooser = new JFileChooser();
               chooser.showSaveDialog(null);
               File file = chooser.getSelectedFile();
               if (file != null) {
                  mmStudio_.quickAccess().saveSettingsToFile(file);
               }
            });
      GUIUtils.addMenuItem(quickAccessMenu_, "Load Settings...",
            "Load saved settings from a file", () -> {
               JFileChooser chooser = new JFileChooser();
               chooser.showOpenDialog(null);
               File file = chooser.getSelectedFile();
               if (file != null) {
                  mmStudio_.quickAccess().loadSettingsFromFile(file);
               }
            });
   }

   @Subscribe
   public void onQuickAccessPanel(QuickAccessPanelEvent event) {
      populateQuickAccessMenu();
   }

   /**
    * Handles event signalling that a change in control of the stage by the mouse occurred.
    *
    * @param event Signals whether the user does or does not want to control the stage
    *              with the mouse.
    */
   @Subscribe
   public void onMouseMovesStage(MouseMovesStageStateChangeEvent event) {
      String icon = event.isEnabled() ? "move_hand_on.png" : "move_hand.png";
      centerAndDragMenuItem_.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/" + icon));
      centerAndDragMenuItem_.setSelected(event.isEnabled());
   }

   public boolean getMouseMovesStage() {
      return mmStudio_.profile().getSettings(ToolsMenu.class)
            .getBoolean(MOUSE_MOVES_STAGE, false);
   }

   public void setMouseMovesStage(boolean doesMove) {
      mmStudio_.profile().getSettings(ToolsMenu.class)
            .putBoolean(MOUSE_MOVES_STAGE, doesMove);
   }

}
