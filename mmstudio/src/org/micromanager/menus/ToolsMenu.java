package org.micromanager.menus;

import ij.gui.Toolbar;
import ij.IJ;

import java.awt.Cursor;
import java.io.File;
import java.util.prefs.Preferences;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import mmcorej.CMMCore;

import org.micromanager.conf2.ConfiguratorDlg2;
import org.micromanager.dialogs.OptionsDlg;
import org.micromanager.MainFrame;
import org.micromanager.MMOptions;
import org.micromanager.MMStudio;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.HotKeysDialog;
import org.micromanager.utils.ReportingUtils;

public class ToolsMenu {
   private static final String MOUSE_MOVES_STAGE = "mouse_moves_stage";

   private JMenu toolsMenu_;
   private JMenu switchConfigurationMenu_;
   private JCheckBoxMenuItem centerAndDragMenuItem_;

   private MMStudio studio_;
   private CMMCore core_;
   private MMOptions options_;
   
   public ToolsMenu(MMStudio studio, CMMCore core, MMOptions options) {
      studio_ = studio;
      core_ = core;
      options_ = options;
      switchConfigurationMenu_ = new JMenu();
   }
   
   public void initializeToolsMenu(JMenuBar menuBar, 
         final Preferences prefs) {
      toolsMenu_ = GUIUtils.createMenuInMenuBar(menuBar, "Tools");

      GUIUtils.addMenuItem(toolsMenu_, "Refresh GUI",
              "Refresh all GUI controls directly from the hardware",
              new Runnable() {
                 public void run() {
                    core_.updateSystemStateCache();
                    studio_.updateGUI(true);
                 }
              },
              "arrow_refresh.png");

      GUIUtils.addMenuItem(toolsMenu_, "Rebuild GUI",
              "Regenerate Micro-Manager user interface",
              new Runnable() {
                 public void run() {
                    studio_.initializeGUI();
                    core_.updateSystemStateCache();
                 }
              });
      
      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Script Panel...",
              "Open Micro-Manager script editor window",
              new Runnable() {
                 public void run() {
                    studio_.showScriptPanel();
                 }
              });

      GUIUtils.addMenuItem(toolsMenu_, "Shortcuts...",
              "Create keyboard shortcuts to activate image acquisition, mark positions, or run custom scripts",
              new Runnable() {
                 public void run() {
                    new HotKeysDialog(studio_.getBackgroundColor());
                 }
              });

      GUIUtils.addMenuItem(toolsMenu_, "Device Property Browser...",
              "Open new window to view and edit device property values",
              new Runnable() {
                 public void run() {
                    studio_.createPropertyEditor();
                 }
              });
      
      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Stage Position List...",
              "Open the stage position list window",
              new Runnable() {
                 public void run() {
                    studio_.showXYPositionList();
                 }
              },
              "application_view_list.png");

      GUIUtils.addMenuItem(toolsMenu_, "Multi-Dimensional Acquisition...",
              "Open multi-dimensional acquisition setup window",
              new Runnable() {
                 public void run() {
                    studio_.openAcqControlDialog();
                 }
              },
              "film.png");

      centerAndDragMenuItem_ = GUIUtils.addCheckBoxMenuItem(toolsMenu_,
              "Mouse Moves Stage (Use Hand Tool)",
              "When enabled, double clicking or dragging in the snap/live\n"
              + "window moves the XY-stage. Requires the hand tool.",
              new Runnable() {
                 public void run() {
                    studio_.updateCenterAndDragListener();
                    IJ.setTool(Toolbar.HAND);
                    prefs.putBoolean(MOUSE_MOVES_STAGE, centerAndDragMenuItem_.isSelected());
                 }
              },
              prefs.getBoolean(MOUSE_MOVES_STAGE, false));
      
      GUIUtils.addMenuItem(toolsMenu_, "Pixel Size Calibration...",
              "Define size calibrations specific to each objective lens.  "
              + "When the objective in use has a calibration defined, "
              + "micromanager will automatically use it when "
              + "calculating metadata",
              new Runnable() {
                 public void run() {
                    studio_.createCalibrationListDlg();
                 }
              });
      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Hardware Configuration Wizard...",
              "Open wizard to create new hardware configuration",
              new Runnable() {
                 public void run() {
                    runHardwareWizard(prefs);
                 }
              });

      GUIUtils.addMenuItem(toolsMenu_, "Load Hardware Configuration...",
              "Un-initialize current configuration and initialize new one",
              new Runnable() {
                 public void run() {
                    loadConfiguration();
                    studio_.initializeGUI();
                 }
              });

      GUIUtils.addMenuItem(toolsMenu_, "Reload Hardware Configuration",
              "Shutdown current configuration and initialize most recently loaded configuration",
              new Runnable() {
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
                 public void run() {
                    studio_.saveConfigPresets();
                    studio_.updateChannelCombos();
                 }
              });

      toolsMenu_.addSeparator();

      GUIUtils.addMenuItem(toolsMenu_, "Options...",
              "Set a variety of Micro-Manager configuration options",
              new Runnable() {
         public void run() {
            final int oldBufsize = options_.circularBufferSizeMB_;

            OptionsDlg dlg = new OptionsDlg(options_, core_, prefs,
                    studio_);
            dlg.setVisible(true);
            // adjust memory footprint if necessary
            if (oldBufsize != options_.circularBufferSizeMB_) {
               try {
                  core_.setCircularBufferMemoryFootprint(options_.circularBufferSizeMB_);
               } catch (Exception exc) {
                  ReportingUtils.showError(exc);
               }
            }
         }
      });
   }

   private void loadConfiguration() {
      File configFile = FileDialogs.openFile(studio_.getFrame(), 
            "Load a config file", MMStudio.MM_CONFIG_FILE);
      if (configFile != null) {
         studio_.setSysConfigFile(configFile.getAbsolutePath());
      }
   }

   private void runHardwareWizard(Preferences prefs) {
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

         // Track whether we should turn live mode back on after reloading.
         boolean liveRunning = false;
         if (studio_.isLiveModeOn()) {
            liveRunning = true;
            studio_.enableLiveMode(false);
         }

         // unload all devices before starting configurator
         core_.reset();
         GUIUtils.preventDisplayAdapterChangeExceptions();

         // run Configurator
         ConfiguratorDlg2 cfg2 = null;
         MainFrame frame = studio_.getFrame();
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
         if (liveRunning) {
            studio_.enableLiveMode(liveRunning);
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   public boolean getIsCenterAndDragChecked() {
      return centerAndDragMenuItem_.isSelected();
   }

   public void updateSwitchConfigurationMenu() {
      switchConfigurationMenu_.removeAll();
      for (final String configFile : studio_.getMRUConfigFiles()) {
         if (!configFile.equals(studio_.getSysConfigFile())) {
            GUIUtils.addMenuItem(switchConfigurationMenu_,
                    configFile, null,
                    new Runnable() {
               public void run() {
                  studio_.setSysConfigFile(configFile);
               }
            });
         }
      }
   }
}
