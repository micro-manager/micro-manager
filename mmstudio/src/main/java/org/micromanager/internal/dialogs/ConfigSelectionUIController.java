/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.dialogs;

import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.UserProfile;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.profile.internal.UserProfileAdmin;
import org.micromanager.profile.internal.gui.HardwareConfigurationManager;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.io.IOException;

/** @author Mark A. Tsuchida */
public class ConfigSelectionUIController {
  private final UserProfileAdmin admin_;
  private ChangeListener currentProfileListener_;

  private final JPanel panel_;
  private final JComboBox configComboBox_ = new JComboBox();
  private final JButton browseButton_ = new JButton();

  private static final String NO_HARDWARE_CONFIG_ITEM = "(none)";

  public static ConfigSelectionUIController create(UserProfileAdmin admin) {
    final ConfigSelectionUIController ret = new ConfigSelectionUIController(admin);

    ret.configComboBox_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            ret.handleConfigSelection();
          }
        });
    ret.browseButton_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            ret.handleBrowse();
          }
        });

    // Add listener for user profile change, while we are shown
    ret.panel_.addHierarchyListener(
        new HierarchyListener() {
          @Override
          public void hierarchyChanged(HierarchyEvent e) {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
              if (ret.panel_.isShowing()) {
                ret.uiWasShown();
              } else {
                ret.uiWasHidden();
              }
            }
          }
        });

    return ret;
  }

  private ConfigSelectionUIController(UserProfileAdmin admin) {
    admin_ = admin;

    configComboBox_.setPrototypeDisplayValue("");
    configComboBox_.setMaximumRowCount(12);

    browseButton_.setText("...");
    browseButton_.setPreferredSize(new Dimension(24, 22));

    panel_ = new JPanel(new MigLayout(new LC().fillX().insets("0").gridGap("0", "0")));
    panel_.add(configComboBox_, new CC().growX().pushX());
    panel_.add(browseButton_, new CC().width("pref!").height("pref!"));

    configComboBox_.setToolTipText(
        "<html>Select the hardware configuration to load.<br />"
            + "Use the \"...\" button to browse for the configuration file.</html>");
    browseButton_.setToolTipText("Select a hardware configuration file");
  }

  public JComponent getUI() {
    return panel_;
  }

  public String getSelectedConfigFilePath() {
    String item = (String) configComboBox_.getSelectedItem();
    if (NO_HARDWARE_CONFIG_ITEM.equals(item)) {
      return null;
    }
    return item;
  }

  public void setSelectedConfigFilePath(String path) {
    if (path == null) {
      path = NO_HARDWARE_CONFIG_ITEM;
    }
    // The path may not be in the combo box if it is from a browse, but
    // this is okay.
    configComboBox_.getModel().setSelectedItem(path);
  }

  private void handleConfigSelection() {
    // Nothing to do
  }

  private void handleBrowse() {
    File file =
        FileDialogs.openFile(
            (Window) panel_.getTopLevelAncestor(),
            "Choose a hardware configuration",
            FileDialogs.MM_CONFIG_FILE);
    if (file == null) {
      return;
    }
    setSelectedConfigFilePath(file.getAbsolutePath());
  }

  private void uiWasShown() {
    currentProfileListener_ =
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            handleProfileSwitch();
          }
        };
    admin_.addCurrentProfileChangeListener(currentProfileListener_);

    handleProfileSwitch();
  }

  private void uiWasHidden() {
    admin_.removeCurrentProfileChangeListener(currentProfileListener_);
    currentProfileListener_ = null;
  }

  private void handleProfileSwitch() {
    configComboBox_.removeAllItems();
    UserProfile profile;
    try {
      profile = admin_.getNonSavingProfile(admin_.getUUIDOfCurrentProfile());
    } catch (IOException ex) {
      ReportingUtils.showError(ex, "There was an error reading the user profile");
      // Leave combo box unpopulated; browse button should still work.
      return;
    }

    for (String path :
        HardwareConfigurationManager.getRecentlyUsedConfigFilesFromProfile(profile)) {
      configComboBox_.addItem(path);
    }
    configComboBox_.addItem(NO_HARDWARE_CONFIG_ITEM);
    configComboBox_.setSelectedIndex(0);
  }

  public static void main(String[] args) {
    ConfigSelectionUIController c = ConfigSelectionUIController.create(UserProfileAdmin.create());
    JFrame f = new JFrame();
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    f.add(c.getUI());
    f.pack();
    f.setVisible(true);
  }
}
