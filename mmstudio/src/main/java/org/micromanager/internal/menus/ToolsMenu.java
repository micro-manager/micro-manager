package org.micromanager.internal.menus;

import com.google.common.eventbus.Subscribe;

import java.awt.Cursor;
import java.io.File;
import java.util.HashSet;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import mmcorej.CMMCore;

import org.micromanager.events.internal.MouseMovesStageEvent;
import org.micromanager.internal.conf2.ConfiguratorDlg2;
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

public class ToolsMenu {
   private static final String MOUSE_MOVES_STAGE = "whether or not the hand tool can be used to move the stage";

   private JMenu toolsMenu_;
   private final JMenu switchConfigurationMenu_;
   private JCheckBoxMenuItem centerAndDragMenuItem_;

   private final MMStudio studio_;
   private final CMMCore core_;

   @SuppressWarnings("LeakingThisInConstructor")
   public ToolsMenu(MMStudio studio, CMMCore core) {
      studio_ = studio;
      core_ = core;
      switchConfigurationMenu_ = new JMenu();
      studio_.events().registerForEvents(this);
   }
   
   public void initializeToolsMenu(JMenuBar menuBar) {
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

      GUIUtils.addMenuItem(toolsMenu_, "Rebuild GUI",
              "Regenerate Micro-Manager user interface",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.initializeGUI();
                    core_.updateSystemStateCache();
                 }
              });
      
      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Script Panel...",
              "Open Micro-Manager script editor window",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.showScriptPanel();
                 }
              });

      GUIUtils.addMenuItem(toolsMenu_, "Shortcuts...",
              "Create keyboard shortcuts to activate image acquisition, mark positions, or run custom scripts",
              new Runnable() {
                 @Override
                 public void run() {
                    HotKeysDialog hk = new HotKeysDialog();
                 }
              });

      GUIUtils.addMenuItem(toolsMenu_, "Device Property Browser...",
              "Open new window to view and edit device property values",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.createPropertyEditor();
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
                    studio_.showXYPositionList();
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

      GUIUtils.addMenuItem(toolsMenu_, "Hardware Configuration Wizard...",
              "Open wizard to create new hardware configuration",
              new Runnable() {
                 @Override
                 public void run() {
                    runHardwareWizard();
                 }
              });

      GUIUtils.addMenuItem(toolsMenu_, "Load Hardware Configuration...",
              "Un-initialize current configuration and initialize new one",
              new Runnable() {
                 @Override
                 public void run() {
                    loadConfiguration();
                    studio_.initializeGUI();
                 }
              });

      GUIUtils.addMenuItem(toolsMenu_, "Reload Hardware Configuration",
              "Shutdown current configuration and initialize most recently loaded configuration",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.loadSystemConfiguration();
                    studio_.initializeGUI();
                 }
              });

      for (int i=0; i<5; i++)
      {
         JMenuItem configItem = new JMenuItem();
         configItem.setText(Integer.toString(i));
         switchConfigurationMenu_.add(configItem);
      }
      
      switchConfigurationMenu_.setText("Switch Hardware Configuration");
      toolsMenu_.add(switchConfigurationMenu_);
      switchConfigurationMenu_.setToolTipText("Switch between recently used configurations");

      GUIUtils.addMenuItem(toolsMenu_, "Save Configuration Settings As...",
              "Save current configuration settings as new configuration file",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.saveConfigPresets();
                    studio_.updateChannelCombos();
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
   }

   private void loadConfiguration() {
      File configFile = FileDialogs.openFile(MMStudio.getFrame(), 
            "Load a config file", MMStudio.MM_CONFIG_FILE);
      if (configFile != null) {
         studio_.setSysConfigFile(configFile.getAbsolutePath());
      }
   }

   private void runHardwareWizard() {
      try {
         if (studio_.getIsConfigChanged()) {
            Object[] options = {"Yes", "No"};
            int n = JOptionPane.showOptionDialog(null,
                  "Save Changed Configuration?", "Micro-Manager",
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE, null, options,
                  options[0]);
            if (n == JOptionPane.YES_OPTION) {
               studio_.saveConfigPresets();
            }
            studio_.setConfigChanged(false);
         }

         studio_.live().setSuspended(true);

         // unload all devices before starting configurator
         core_.reset();
         GUIUtils.preventDisplayAdapterChangeExceptions();

         // run Configurator
         ConfiguratorDlg2 cfg2 = null;
         MainFrame frame = MMStudio.getFrame();
         try {
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            cfg2 = new ConfiguratorDlg2(core_, studio_.getSysConfigFile());
         } finally {
            frame.setCursor(Cursor.getDefaultCursor());
         }

         if (cfg2 == null)
         {
            ReportingUtils.showError("Failed to launch Hardware Configuration Wizard");
            return;
         }
         cfg2.setVisible(true);
         GUIUtils.preventDisplayAdapterChangeExceptions();

         // re-initialize the system with the new configuration file
         studio_.setSysConfigFile(cfg2.getFileName());

         GUIUtils.preventDisplayAdapterChangeExceptions();
         studio_.live().setSuspended(false);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   public void updateSwitchConfigurationMenu() {
      switchConfigurationMenu_.removeAll();
      HashSet<String> seenConfigs = new HashSet<String>();
      for (final String configFile : IntroDlg.getRecentlyUsedConfigs()) {
         if (configFile.equals(studio_.getSysConfigFile()) ||
               seenConfigs.contains(configFile)) {
            continue;
         }
         GUIUtils.addMenuItem(switchConfigurationMenu_,
                 configFile, null,
                 new Runnable() {
            @Override
            public void run() {
               studio_.setSysConfigFile(configFile);
            }
         });
         seenConfigs.add(configFile);
      }
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
