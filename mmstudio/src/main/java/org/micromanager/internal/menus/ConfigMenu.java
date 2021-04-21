package org.micromanager.internal.menus;

import com.google.common.eventbus.Subscribe;
import mmcorej.CMMCore;
import org.micromanager.events.SystemConfigurationLoadedEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.MainFrame;
import org.micromanager.internal.hcwizard.ConfigWizard;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.profile.internal.gui.HardwareConfigurationManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashSet;

public final class ConfigMenu {

  private final JMenu configMenu_;
  private final JMenu switchConfigurationMenu_;

  private final MMStudio mmStudio_;
  private final CMMCore core_;

  @SuppressWarnings("LeakingThisInConstructor")
  public ConfigMenu(MMStudio studio, JMenuBar menuBar) {
    mmStudio_ = studio;
    core_ = mmStudio_.core();
    switchConfigurationMenu_ = new JMenu();

    configMenu_ = GUIUtils.createMenuInMenuBar(menuBar, "Devices");

    GUIUtils.addMenuItem(
        configMenu_,
        "Device Property Browser...",
        "View and set device states and features",
        new Runnable() {
          @Override
          public void run() {
            mmStudio_.uiManager().createPropertyEditor();
          }
        });

    configMenu_.addSeparator();

    GUIUtils.addMenuItem(
        configMenu_,
        "Hardware Configuration Wizard...",
        "Set up hardware devices or modify an existing hardware configuration",
        new Runnable() {
          @Override
          public void run() {
            runHardwareWizard();
          }
        });

    GUIUtils.addMenuItem(
        configMenu_,
        "Load Hardware Configuration...",
        "Load hardware setup from a file",
        new Runnable() {
          @Override
          public void run() {
            loadConfiguration();
            // TODO: this may be redundant.
            mmStudio_.uiManager().initializeGUI();
          }
        });

    GUIUtils.addMenuItem(
        configMenu_,
        "Reload Hardware Configuration",
        "Reload the current hardware configuration and initialize device control",
        new Runnable() {
          @Override
          public void run() {
            mmStudio_.loadSystemConfiguration();
            // TODO: this is redundant.
            mmStudio_.uiManager().initializeGUI();
          }
        });

    for (int i = 0; i < 5; i++) {
      JMenuItem configItem = new JMenuItem();
      configItem.setText(Integer.toString(i));
      switchConfigurationMenu_.add(configItem);
    }

    switchConfigurationMenu_.setText("Switch Hardware Configuration");
    switchConfigurationMenu_.setToolTipText("Switch to a recently used hardware configuration");
    configMenu_.add(switchConfigurationMenu_);

    GUIUtils.addMenuItem(
        configMenu_,
        "Save Hardware Configuration As...",
        "Save the current hardware configuration",
        new Runnable() {
          @Override
          public void run() {
            mmStudio_.uiManager().promptToSaveConfigPresets();
            mmStudio_.uiManager().updateChannelCombos();
          }
        });

    configMenu_.addSeparator();

    GUIUtils.addMenuItem(
        configMenu_,
        "Pixel Size Calibration...",
        "Define pixel sizes and how they depend on hardware state",
        new Runnable() {
          @Override
          public void run() {
            mmStudio_.uiManager().createCalibrationListDlg();
          }
        });

    mmStudio_.events().registerForEvents(this);
  }

  private void loadConfiguration() {
    File configFile =
        FileDialogs.openFile(
            mmStudio_.uiManager().frame(), "Load a Configuration File", FileDialogs.MM_CONFIG_FILE);
    if (configFile != null) {
      mmStudio_.setSysConfigFile(configFile.getAbsolutePath());
    }
  }

  private void runHardwareWizard() {
    if (SwingUtilities.isEventDispatchThread()) {
      new Thread(
              new Runnable() {
                @Override
                public void run() {
                  runHardwareWizard();
                }
              })
          .start();
      return;
    }

    if (mmStudio_.hasConfigChanged()) {
      Object[] options = {"Yes", "No"};
      int n =
          JOptionPane.showOptionDialog(
              null,
              "Save Hardware Configuration?",
              "Micro-Manager",
              JOptionPane.YES_NO_OPTION,
              JOptionPane.QUESTION_MESSAGE,
              null,
              options,
              options[0]);
      if (n == JOptionPane.YES_OPTION) {
        mmStudio_.uiManager().promptToSaveConfigPresets();
      }
      mmStudio_.setConfigChanged(false);
    }

    try {
      mmStudio_.live().setSuspended(true);

      // Show a "please wait" dialog.
      JDialog waiter =
          GUIUtils.createBareMessageDialog(
              mmStudio_.app().getMainWindow(), "Loading wizard; please wait...");
      waiter.setVisible(true);

      // unload all devices before starting configurator
      core_.reset();
      GUIUtils.preventDisplayAdapterChangeExceptions();

      // run Configurator
      ConfigWizard cfg = null;
      MainFrame frame = mmStudio_.uiManager().frame();
      try {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        cfg = new ConfigWizard(mmStudio_, mmStudio_.getSysConfigFile());
      } finally {
        frame.setCursor(Cursor.getDefaultCursor());
        waiter.setVisible(false);
        waiter.dispose();
      }

      if (cfg == null) {
        ReportingUtils.showError("Failed to launch Hardware Configuration Wizard");
        return;
      }
      cfg.setVisible(true);
      GUIUtils.preventDisplayAdapterChangeExceptions();

      // re-initialize the system with the new configuration file
      mmStudio_.setSysConfigFile(cfg.getFileName());

      GUIUtils.preventDisplayAdapterChangeExceptions();
    } catch (Exception e) {
      ReportingUtils.showError(e);
    } finally {
      mmStudio_.live().setSuspended(false);
    }
  }

  @Subscribe
  public void onSystemConfigurationLoaded(SystemConfigurationLoadedEvent event) {
    switchConfigurationMenu_.removeAll();
    HashSet<String> seenConfigs = new HashSet<String>();

    for (final String configFile :
        HardwareConfigurationManager.getRecentlyUsedConfigFilesFromProfile(mmStudio_.profile())) {
      if (configFile.equals(mmStudio_.getSysConfigFile()) || seenConfigs.contains(configFile)) {
        continue;
      }
      String label = configFile;
      if (configFile.equals("")) {
        label = "(none)";
      }
      GUIUtils.addMenuItem(
          switchConfigurationMenu_,
          label,
          null,
          new Runnable() {
            @Override
            public void run() {
              mmStudio_.setSysConfigFile(configFile);
            }
          });
      seenConfigs.add(configFile);
    }
  }
}
