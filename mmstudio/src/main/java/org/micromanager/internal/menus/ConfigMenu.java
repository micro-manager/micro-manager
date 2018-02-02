package org.micromanager.internal.menus;

import com.google.common.eventbus.Subscribe;
import java.awt.Cursor;
import java.io.File;
import java.util.HashSet;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import org.micromanager.events.SystemConfigurationLoadedEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.MainFrame;
import org.micromanager.internal.hcwizard.ConfigWizard;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.UserProfileStaticInterface;
import org.micromanager.profile.internal.gui.HardwareConfigurationManager;

public final class ConfigMenu {

   private final JMenu configMenu_;
   private final JMenu switchConfigurationMenu_;

   private final MMStudio studio_;
   private final CMMCore core_;

   @SuppressWarnings("LeakingThisInConstructor")
   public ConfigMenu(MMStudio studio, JMenuBar menuBar) {
      studio_ = studio;
      core_ = studio_.core();
      switchConfigurationMenu_ = new JMenu();

      configMenu_ = GUIUtils.createMenuInMenuBar(menuBar, "Devices");

      GUIUtils.addMenuItem(configMenu_, "Device Property Browser...",
            "View and set device states and features",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.createPropertyEditor();
                 }
              });

      configMenu_.addSeparator();

      GUIUtils.addMenuItem(configMenu_, "Hardware Configuration Wizard...",
            "Set up hardware devices or modify an existing hardware configuration",
              new Runnable() {
                 @Override
                 public void run() {
                    runHardwareWizard();
                 }
              });

      GUIUtils.addMenuItem(configMenu_, "Load Hardware Configuration...",
            "Load hardware setup from a file",
              new Runnable() {
                 @Override
                 public void run() {
                    loadConfiguration();
                    // TODO: this may be redundant.
                    studio_.initializeGUI();
                 }
              });

      GUIUtils.addMenuItem(configMenu_, "Reload Hardware Configuration",
            "Reload the current hardware configuration and initialize device control",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.loadSystemConfiguration();
                    // TODO: this is redundant.
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
      switchConfigurationMenu_.setToolTipText("Switch to a recently used hardware configuration");
      configMenu_.add(switchConfigurationMenu_);

      GUIUtils.addMenuItem(configMenu_, "Save Hardware Configuration As...",
              "Save the current hardware configuration",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.promptToSaveConfigPresets();
                    studio_.updateChannelCombos();
                 }
              });

      configMenu_.addSeparator();

      GUIUtils.addMenuItem(configMenu_, "Pixel Size Calibration...",
            "Define pixel sizes and how they depend on hardware state",
            new Runnable() {
               @Override
               public void run() {
                  studio_.createCalibrationListDlg();
               }
            });

      studio_.events().registerForEvents(this);
   }

   private void loadConfiguration() {
      File configFile = FileDialogs.openFile(MMStudio.getFrame(), 
            "Load a Configuration File", FileDialogs.MM_CONFIG_FILE);
      if (configFile != null) {
         studio_.setSysConfigFile(configFile.getAbsolutePath());
      }
   }

   private void runHardwareWizard() {
      if (SwingUtilities.isEventDispatchThread()) {
         new Thread(new Runnable() {
            @Override
            public void run() {
               runHardwareWizard();
            }
         }).start();
         return;
      }
      try {
         if (studio_.getIsConfigChanged()) {
            Object[] options = {"Yes", "No"};
            int n = JOptionPane.showOptionDialog(null,
                  "Save Hardware Configuration?", "Micro-Manager",
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE, null, options,
                  options[0]);
            if (n == JOptionPane.YES_OPTION) {
               studio_.promptToSaveConfigPresets();
            }
            studio_.setConfigChanged(false);
         }

         studio_.live().setSuspended(true);

         // Show a "please wait" dialog.
         JDialog waiter = GUIUtils.createBareMessageDialog(
               studio_.app().getMainWindow(),
               "Loading wizard; please wait...");
         waiter.setVisible(true);

         // unload all devices before starting configurator
         core_.reset();
         GUIUtils.preventDisplayAdapterChangeExceptions();

         // run Configurator
         ConfigWizard cfg = null;
         MainFrame frame = MMStudio.getFrame();
         try {
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            cfg = new ConfigWizard(core_, studio_.getSysConfigFile());
         } finally {
            frame.setCursor(Cursor.getDefaultCursor());
            waiter.setVisible(false);
            waiter.dispose();
         }

         if (cfg == null)
         {
            ReportingUtils.showError("Failed to launch Hardware Configuration Wizard");
            return;
         }
         cfg.setVisible(true);
         GUIUtils.preventDisplayAdapterChangeExceptions();

         // re-initialize the system with the new configuration file
         studio_.setSysConfigFile(cfg.getFileName());

         GUIUtils.preventDisplayAdapterChangeExceptions();
         studio_.live().setSuspended(false);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   @Subscribe
   public void onSystemConfigurationLoaded(SystemConfigurationLoadedEvent event) {
      switchConfigurationMenu_.removeAll();
      HashSet<String> seenConfigs = new HashSet<String>();

      for (final String configFile : HardwareConfigurationManager.
            getRecentlyUsedConfigFilesFromProfile(
                  UserProfileStaticInterface.getInstance())) {
         if (configFile.equals(studio_.getSysConfigFile()) ||
               seenConfigs.contains(configFile)) {
            continue;
         }
         String label = configFile;
         if (configFile.equals("")) {
            label = "(none)";
         }
         GUIUtils.addMenuItem(switchConfigurationMenu_,
                 label, null,
                 new Runnable() {
            @Override
            public void run() {
               studio_.setSysConfigFile(configFile);
            }
         });
         seenConfigs.add(configFile);
      }
   }
}
