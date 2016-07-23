package org.micromanager.internal.menus;

import com.google.common.eventbus.Subscribe;

import java.awt.Cursor;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import mmcorej.CMMCore;

import org.micromanager.events.internal.MouseMovesStageEvent;
import org.micromanager.internal.dialogs.IntroDlg;
import org.micromanager.internal.dialogs.OptionsDlg;
import org.micromanager.internal.dialogs.StageControlFrame;
import org.micromanager.internal.MainFrame;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.HotKeysDialog;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.quickaccess.internal.DefaultQuickAccessManager;
import org.micromanager.quickaccess.internal.QuickAccessPanelEvent;

public class ToolsMenu {
   private static final String MOUSE_MOVES_STAGE = "whether or not the hand tool can be used to move the stage";

   private JMenu toolsMenu_;
   private final JMenu quickAccessMenu_;
   private JCheckBoxMenuItem centerAndDragMenuItem_;

   private final MMStudio studio_;
   private final CMMCore core_;

   @SuppressWarnings("LeakingThisInConstructor")
   public ToolsMenu(MMStudio studio, CMMCore core, JMenuBar menuBar) {
      studio_ = studio;
      core_ = core;
      quickAccessMenu_ = new JMenu("Quick Access Panels");

      toolsMenu_ = GUIUtils.createMenuInMenuBar(menuBar, "Tools");

      GUIUtils.addMenuItem(toolsMenu_, "Refresh GUI",
              "Refresh all GUI controls directly from the hardware",
              new Runnable() {
                 @Override
                 public void run() {
                    core_.updateSystemStateCache();
                    studio_.updateGUI(true);
                 }
              },
              "arrow_refresh.png");

      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Script Panel...",
              "Open Micro-Manager script editor window",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.showScriptPanel();
                 }
              });

      populateQuickAccessMenu();
      toolsMenu_.add(quickAccessMenu_);

      GUIUtils.addMenuItem(toolsMenu_, "Shortcuts...",
              "Create keyboard shortcuts to activate image acquisition, mark positions, or run custom scripts",
              new Runnable() {
                 @Override
                 public void run() {
                    HotKeysDialog hk = new HotKeysDialog();
                 }
              });

      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Stage Control...",
            "Control the stage position with a virtual joystick",
            new Runnable() {
               @Override
               public void run() {
                  StageControlFrame.showStageControl();
               }
            });

      GUIUtils.addMenuItem(toolsMenu_, "Stage Position List...",
              "Open the stage position list window",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.app().showPositionList();
                 }
              },
              "application_view_list.png");

      centerAndDragMenuItem_ = GUIUtils.addCheckBoxMenuItem(toolsMenu_,
              "Mouse Moves Stage (Use Hand Tool)",
              "When enabled, double clicking or dragging in the snap/live\n"
              + "window moves the XY-stage. Requires the hand tool.",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.updateCenterAndDragListener(
                       centerAndDragMenuItem_.isSelected());
                 }
              },
              getMouseMovesStage());

      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Multi-Dimensional Acquisition...",
              "Open multi-dimensional acquisition setup window",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.openAcqControlDialog();
                 }
              },
              "film.png");
      
      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Pixel Size Calibration...",
              "Define size calibrations specific to each objective lens.  "
              + "When the objective in use has a calibration defined, "
              + "micromanager will automatically use it when "
              + "calculating metadata",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.createCalibrationListDlg();
                 }
              });

      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Options...",
              "Set a variety of Micro-Manager configuration options",
              new Runnable() {
         @Override
         public void run() {
            final int oldBufsize = MMStudio.getCircularBufferSize();

            OptionsDlg dlg = new OptionsDlg(core_, studio_);
            dlg.setVisible(true);
            // adjust memory footprint if necessary
            if (oldBufsize != MMStudio.getCircularBufferSize()) {
               try {
                  core_.setCircularBufferMemoryFootprint(
                     MMStudio.getCircularBufferSize());
               } catch (Exception exc) {
                  ReportingUtils.showError(exc);
               }
            }
         }
      });

      studio_.events().registerForEvents(this);
   }

   private void populateQuickAccessMenu() {
      quickAccessMenu_.removeAll();
      GUIUtils.addMenuItem(quickAccessMenu_, "Create New Panel",
            "Create a new Quick Access Panel, for easy access to commonly-used controls.",
            new Runnable() {
               @Override
               public void run() {
                  DefaultQuickAccessManager.createNewPanel();
               }
            });

      final Map<String, JFrame> titleToFrame = studio_.quickAccess().getPanels();
      ArrayList<String> titles = new ArrayList<String>(titleToFrame.keySet());
      Collections.sort(titles);

      JMenu deleteMenu = new JMenu("Delete...");
      deleteMenu.setEnabled(titles.size() > 0);
      for (final String title : titles) {
         GUIUtils.addMenuItem(deleteMenu, title, "Delete this panel",
               new Runnable() {
                  @Override
                  public void run() {
                     DefaultQuickAccessManager.promptToDelete(
                        titleToFrame.get(title));
                  }
               });
      }
      quickAccessMenu_.add(deleteMenu);
      quickAccessMenu_.addSeparator();
      JMenuItem show = GUIUtils.addMenuItem(quickAccessMenu_, "Show all",
            "Show all Quick Access Panels; create a new one if necessary",
            new Runnable() {
               @Override
               public void run() {
                  studio_.quickAccess().showPanels();
               }
            });
      show.setEnabled(titles.size() > 0);

      for (final String title : titles) {
         GUIUtils.addMenuItem(quickAccessMenu_, title, "",
               new Runnable() {
               @Override
               public void run() {
                  titleToFrame.get(title).setVisible(true);
               }
         });
      }

      quickAccessMenu_.addSeparator();

      GUIUtils.addMenuItem(quickAccessMenu_, "Save Settings...",
            "Save the Quick Access Panel settings to a file for use elsewhere",
            new Runnable() {
               @Override
               public void run() {
                  JFileChooser chooser = new JFileChooser();
                  chooser.showSaveDialog(null);
                  File file = chooser.getSelectedFile();
                  if (file != null) {
                     studio_.quickAccess().saveSettingsToFile(file);
                  }
               }
            });
      GUIUtils.addMenuItem(quickAccessMenu_, "Load Settings...",
            "Load saved settings from a file",
            new Runnable() {
               @Override
               public void run() {
                  JFileChooser chooser = new JFileChooser();
                  chooser.showOpenDialog(null);
                  File file = chooser.getSelectedFile();
                  if (file != null) {
                     studio_.quickAccess().loadSettingsFromFile(file);
                  }
               }
            });
   }

   @Subscribe
   public void onQuickAccessPanel(QuickAccessPanelEvent event) {
      populateQuickAccessMenu();
   }

   @Subscribe
   public void onMouseMovesStage(MouseMovesStageEvent event) {
      centerAndDragMenuItem_.setSelected(event.getIsEnabled());
   }

   public static boolean getMouseMovesStage() {
      return DefaultUserProfile.getInstance().getBoolean(
            ToolsMenu.class, MOUSE_MOVES_STAGE, false);
   }

   public static void setMouseMovesStage(boolean doesMove) {
      DefaultUserProfile.getInstance().setBoolean(
            ToolsMenu.class, MOUSE_MOVES_STAGE, doesMove);
   }
}
