package org.micromanager.internal.menus;

import java.awt.Color;
import java.awt.Cursor;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import mmcorej.CMMCore;

import org.micromanager.events.internal.MouseMovesStageEvent;
import org.micromanager.internal.hcwizard.ConfiguratorDlg2;
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

public class ConfigMenu {

   private JMenu configMenu_;
   private final JMenu switchConfigurationMenu_;

   private final MMStudio studio_;
   private final CMMCore core_;

   @SuppressWarnings("LeakingThisInConstructor")
   public ConfigMenu(MMStudio studio, CMMCore core, JMenuBar menuBar) {
      studio_ = studio;
      core_ = core;
      switchConfigurationMenu_ = new JMenu();

      configMenu_ = GUIUtils.createMenuInMenuBar(menuBar, "Config");

      GUIUtils.addMenuItem(configMenu_, "Device Property Browser...",
              "Open new window to view and edit device property values",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.createPropertyEditor();
                 }
              });

      configMenu_.addSeparator();

      GUIUtils.addMenuItem(configMenu_, "Hardware Configuration Wizard...",
              "Open wizard to create new hardware configuration",
              new Runnable() {
                 @Override
                 public void run() {
                    runHardwareWizard();
                 }
              });

      GUIUtils.addMenuItem(configMenu_, "Load Hardware Configuration...",
              "Un-initialize current configuration and initialize new one",
              new Runnable() {
                 @Override
                 public void run() {
                    loadConfiguration();
                    // TODO: this may be redundant.
                    studio_.initializeGUI();
                 }
              });

      GUIUtils.addMenuItem(configMenu_, "Reload Hardware Configuration",
              "Shutdown current configuration and initialize most recently loaded configuration",
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
      configMenu_.add(switchConfigurationMenu_);
      switchConfigurationMenu_.setToolTipText("Switch between recently used configurations");

      GUIUtils.addMenuItem(configMenu_, "Save Configuration Settings As...",
              "Save current configuration settings as new configuration file",
              new Runnable() {
                 @Override
                 public void run() {
                    studio_.saveConfigPresets();
                    studio_.updateChannelCombos();
                 }
              });
   }

   private void loadConfiguration() {
      File configFile = FileDialogs.openFile(MMStudio.getFrame(), 
            "Load a config file", FileDialogs.MM_CONFIG_FILE);
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

         // Show a "please wait" dialog.
         JFrame waiter = new JFrame();
         waiter.setUndecorated(true);
         JPanel contents = new JPanel();
         contents.add(new JLabel("Loading Wizard; please wait..."));
         contents.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
         waiter.add(contents);
         waiter.pack();
         GUIUtils.centerFrameWithFrame(waiter,
               studio_.compat().getMainWindow());
         waiter.setVisible(true);

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
            waiter.setVisible(false);
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
