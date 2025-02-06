package org.micromanager.internal.dialogs.introdialogparts;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.UserProfile;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.profile.internal.UserProfileAdmin;
import org.micromanager.profile.internal.gui.HardwareConfigurationManager;

/**
 * Controller for a UI that allows the user to select a hardware configuration.
 * The UI consists of a combo box with a "..." button to browse for a file.
 *
 * @author Mark A. Tsuchida
 */
public class ConfigSelectionUIController {
   private final UserProfileAdmin admin_;
   private ChangeListener currentProfileListener_;

   private final JPanel panel_;
   private final JComboBox<String> configComboBox_ = new JComboBox<>();
   private final JButton browseButton_ = new JButton();

   private static final String NO_HARDWARE_CONFIG_ITEM = "(none)";

   /**
    * Create a new controller.
    *
    * @param admin   User profile admin to use
    * @return a new controller
    */
   public static ConfigSelectionUIController create(UserProfileAdmin admin) {
      final ConfigSelectionUIController ret =
            new ConfigSelectionUIController(admin);

      ret.configComboBox_.addActionListener(e -> ret.handleConfigSelection());
      ret.browseButton_.addActionListener(e -> ret.handleBrowse());

      // Add listener for user profile change, while we are shown
      ret.panel_.addHierarchyListener(e -> {
         if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
            if (ret.panel_.isShowing()) {
               ret.uiWasShown();
            } else {
               ret.uiWasHidden();
            }
         }
      });

      return ret;
   }

   /**
    * private constructor.
    *
    * @param admin admin UserProfile
    */
   private ConfigSelectionUIController(UserProfileAdmin admin) {
      admin_ = admin;

      configComboBox_.setPrototypeDisplayValue("");
      configComboBox_.setMaximumRowCount(12);
      // On Windows, right align using custom renderer
      if (UIManager.getLookAndFeel().getClass().getName().equals(
             "com.sun.java.swing.plaf.windows.WindowsLookAndFeel")) {
         configComboBox_.setRenderer(new ComboBoxCellRenderer(340));
         configComboBox_.setBackground(Color.WHITE);
      }

      browseButton_.setText("...");
      browseButton_.setPreferredSize(new Dimension(24, 22));

      panel_ = new JPanel(new MigLayout(
            new LC().fillX().insets("0").gridGap("0", "0")));
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

   /**
    * Get the path of the selected hardware configuration file.
    *
    * @return path of the selected configuration file, or null if none selected
    */
   public String getSelectedConfigFilePath() {
      String item = (String) configComboBox_.getSelectedItem();
      if (NO_HARDWARE_CONFIG_ITEM.equals(item)) {
         return null;
      }
      return item;
   }

   /**
    * Set the selected hardware configuration file path.
    *
    * @param path path of the configuration file to select, or null to select none
    */
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
      File file = FileDialogs.openFile((Window) panel_.getTopLevelAncestor(),
            "Choose a hardware configuration",
            FileDialogs.MM_CONFIG_FILE);
      if (file == null) {
         return;
      }
      setSelectedConfigFilePath(file.getAbsolutePath());
   }

   private void uiWasShown() {
      currentProfileListener_ = e -> handleProfileSwitch();
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

      for (String path : HardwareConfigurationManager
            .getRecentlyUsedConfigFilesFromProfile(profile)) {
         configComboBox_.addItem(path);
      }
      configComboBox_.addItem(NO_HARDWARE_CONFIG_ITEM);
      configComboBox_.setSelectedIndex(0);
   }

   /**
    * Test main.
    *
    * @param args ignored
    */
   public static void main(String[] args) {
      ConfigSelectionUIController c = ConfigSelectionUIController.create(
            UserProfileAdmin.create());
      JFrame f = new JFrame();
      f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      f.add(c.getUI());
      f.pack();
      f.setVisible(true);
   }


}