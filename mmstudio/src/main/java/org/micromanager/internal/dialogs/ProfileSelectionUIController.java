/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.dialogs;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.collect.ImmutableList;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.utils.PopupButton;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.profile.internal.UserProfileAdmin;

/**
 * The user profile selection/management UI shown at startup.
 *
 * @author Mark A. Tsuchida
 */
public final class ProfileSelectionUIController
      implements PopupButton.Listener, ActionListener {
   private final UserProfileAdmin admin_;

   private ChangeListener currentProfileListener_;
   private ChangeListener profileIndexListener_;

   private final JPanel panel_;
   private final JCheckBox readOnlyCheckBox_ = new JCheckBox();
   private final JComboBox profileComboBox_ = new JComboBox();
   private final PopupButton gearButton_;

   private final JPopupMenu gearMenu_ = new JPopupMenu();
   private final JMenuItem gearMenuNewProfileItem_ =
         new JMenuItem("New User Profile...");
   private final JMenuItem gearMenuDuplicateProfileItem_ =
         new JMenuItem("Duplicate Profile...");
   private final JMenuItem gearMenuRenameProfileItem_ =
         new JMenuItem("Rename Profile...");
   private final JMenuItem gearMenuDeleteProfileItem_ =
         new JMenuItem("Delete User Profile...");

   private static class ProfileComboItem {
      final UUID uuid_;
      final String name_;
      ProfileComboItem(UUID uuid, String name) {
         uuid_ = uuid;
         name_ = name;
      }
      @Override
      public String toString() {
         return name_;
      }
      UUID getUUID() {
         return uuid_;
      }
   }

   public static ProfileSelectionUIController create(UserProfileAdmin admin)
         throws IOException {
      final ProfileSelectionUIController ret =
            new ProfileSelectionUIController(admin);

      ret.profileComboBox_.addActionListener(ret);
      ret.gearButton_.addPopupButtonListener(ret);
      ret.readOnlyCheckBox_.addActionListener(ret);
      for (JMenuItem item : ImmutableList.of(ret.gearMenuNewProfileItem_,
            ret.gearMenuDuplicateProfileItem_, ret.gearMenuRenameProfileItem_,
            ret.gearMenuDeleteProfileItem_)) {
         item.addActionListener(ret);
      }

      ret.panel_.addHierarchyListener(new HierarchyListener() {
         @Override
         public void hierarchyChanged(HierarchyEvent e) {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
               if (ret.panel_.isShowing()) {
                  ret.uiWasShown();
               }
               else {
                  ret.uiWasHidden();
               }
            }
         }
      });

      return ret;
   }

   private ProfileSelectionUIController(UserProfileAdmin admin) throws IOException {
      admin_ = admin;

      profileComboBox_.setPrototypeDisplayValue("");
      profileComboBox_.setMaximumRowCount(32);

      gearMenu_.add(gearMenuNewProfileItem_);
      gearMenu_.add(gearMenuDuplicateProfileItem_);
      gearMenu_.add(gearMenuRenameProfileItem_);
      gearMenu_.add(gearMenuDeleteProfileItem_);

      gearButton_ = PopupButton.create(
            IconLoader.getIcon("/org/micromanager/icons/gear.png"),
            gearMenu_);
      gearButton_.setPreferredSize(new Dimension(24, 22));

      panel_ = new JPanel(new MigLayout(
            new LC().fillX().insets("0").gridGap("0", "0")));
      panel_.add(profileComboBox_, new CC().growX().pushX());
      panel_.add(gearButton_, new CC().width("pref!").height("pref!"));
      panel_.add(readOnlyCheckBox_);

      profileComboBox_.setToolTipText(
            "<html>Select the user profile for this session.<br />" +
                  "Settings and preferences are saved in each user profile.</html>");
      readOnlyCheckBox_.setToolTipText(
            "Prevent changes from being saved.");
      gearButton_.setToolTipText(
            "Manage user profiles");
   }

   private void populateComboBox() {
      profileComboBox_.removeAllItems();
      Map<UUID, String> profiles;
      try {
         profiles = admin_.getProfileUUIDsAndNames();
      }
      catch (IOException e) {
         // If the profiles are not accessible, we should have gotten an error
         // before reaching here. Just leave combo box empty.
         ReportingUtils.logError(e, "Error getting profile index");
         return;
      }

      // Default profile first
      UUID defaultUUID = admin_.getUUIDOfDefaultProfile();
      profileComboBox_.addItem(new ProfileComboItem(defaultUUID, profiles.get(
            defaultUUID)));

      // Rest in given order
      for (Map.Entry<UUID, String> e : profiles.entrySet()) {
         if (defaultUUID.equals(e.getKey())) {
            continue;
         }
         profileComboBox_.addItem(new ProfileComboItem(e.getKey(), e.getValue()));
      }

      setSelectedProfileUUID(admin_.getUUIDOfCurrentProfile());
   }

   public JComponent getUI() {
      return panel_;
   }

   public UUID getSelectedProfileUUID() {
      ProfileComboItem item = (ProfileComboItem) profileComboBox_.getSelectedItem();
      if (item == null) {
         return null;
      }
      return item.getUUID();
   }

   private void setSelectedProfileUUID(UUID uuid) {
      for (int i = 0; i < profileComboBox_.getItemCount(); ++i) {
         ProfileComboItem item = (ProfileComboItem) profileComboBox_.getItemAt(i);
         if (item.getUUID().equals(uuid)) {
            profileComboBox_.setSelectedIndex(i);
            break;
         }
      }
   }

   @Override
   public void popupButtonWillShowPopup(PopupButton button) {
      boolean defaultSelected = admin_.getUUIDOfDefaultProfile().equals(
            getSelectedProfileUUID());
      gearMenuRenameProfileItem_.setEnabled(!defaultSelected);
      gearMenuDeleteProfileItem_.setEnabled(!defaultSelected);
   }

   @Override
   public void actionPerformed(ActionEvent event) {
      if (event.getSource() == profileComboBox_) {
         handleProfileSelection();
      }
      else if (event.getSource() == gearMenuNewProfileItem_) {
         handleNewProfile();
      }
      else if (event.getSource() == gearMenuDuplicateProfileItem_) {
         handleDuplicateProfile();
      }
      else if (event.getSource() == gearMenuRenameProfileItem_) {
         handleRenameProfile();
      }
      else if (event.getSource() == gearMenuDeleteProfileItem_) {
         handleDeleteProfile();
      }
      else if (event.getSource() == readOnlyCheckBox_) {
         handleReadOnlyAction();
      }
   }

   private void handleReadOnlyAction(){
      UUID uuid = admin_.getUUIDOfCurrentProfile();
      if (uuid != null) {
          try {
            admin_.setProfileReadOnly(readOnlyCheckBox_.isSelected());
          } catch (IOException e) {}
      }
   }
   
   private void handleProfileSelection() {
      try {
         UUID uuid = getSelectedProfileUUID();
         if (uuid != null) {
            admin_.setCurrentUserProfile(uuid);
            readOnlyCheckBox_.setSelected(admin_.isProfileReadOnly());
         }
      }
      catch (IOException e) {
         ReportingUtils.showError(e,
               "There was an error accessing the selected profile.");
      }
   }

   private void handleNewProfile() {
      Component parent = panel_.getTopLevelAncestor();
      try {
         String name = null;
         do {
            if (name != null) {
               JOptionPane.showMessageDialog(parent,
                     String.format("The name \"%s\" is already in use.", name),
                     "Error Creating User Profile", JOptionPane.ERROR_MESSAGE);
            }
            name = JOptionPane.showInputDialog(parent,
                  "Please name the new user profile:", "New User Profile",
                  JOptionPane.QUESTION_MESSAGE);
            if (name == null) {
               return;
            }
         } while (!isProfileNameAvailable(name));
         UUID uuid = admin_.createProfile(name);
         admin_.setCurrentUserProfile(uuid);
      }
      catch (IOException e) {
         ReportingUtils.showError(e,
               "There was an error creating the User Profile",
               parent);
      }
   }

   private void handleDuplicateProfile() {
      Component parent = panel_.getTopLevelAncestor();
      try {
         UUID originalUUID = getSelectedProfileUUID();
         if (originalUUID == null) { // Just in case
            return;
         }
         String originalName = admin_.getProfileUUIDsAndNames().
               get(originalUUID);
         String name = null;
         do {
            if (name != null) {
               JOptionPane.showMessageDialog(parent,
                     String.format("The name \"%s\" is already in use.", name),
                     "Error Duplicating User Profile", JOptionPane.ERROR_MESSAGE);
            }
            name = JOptionPane.showInputDialog(parent,
                  "Please name the new user profile:",
                  String.format("Duplicate User Profile \"%s\"", originalName),
                  JOptionPane.QUESTION_MESSAGE);
            if (name == null) {
               return;
            }
         } while (!isProfileNameAvailable(name));
         UUID uuid = admin_.duplicateProfile(originalUUID, name);
         admin_.setCurrentUserProfile(uuid);
      }
      catch (IOException e) {
         ReportingUtils.showError(e,
               "There was an error creating the User Profile",
               parent);
      }
   }

   private void handleRenameProfile() {
      Component parent = panel_.getTopLevelAncestor();
      try {
         UUID uuid = getSelectedProfileUUID();
         if (uuid == null) { // Just in case
            return;
         }
         String originalName = admin_.getProfileUUIDsAndNames().get(uuid);
         String newName = null;
         do {
            if (newName != null) {
               JOptionPane.showMessageDialog(parent,
                     String.format("The name \"%s\" is already in use.", newName),
                     "Error Renaming User Profile", JOptionPane.ERROR_MESSAGE);
            }
            newName = JOptionPane.showInputDialog(parent,
                  String.format(
                        "Please enter a new name for user profile \"%s\":",
                        originalName),
                  "Rename User Profile",
                  JOptionPane.QUESTION_MESSAGE);
            if (newName == null) {
               return;
            }
         } while (!isProfileNameAvailable(newName));
         admin_.renameProfile(uuid, newName);
         admin_.setCurrentUserProfile(uuid);
      }
      catch (IOException e) {
         ReportingUtils.showError(e,
               "There was an error creating the User Profile",
               parent);
      }
   }

   private void handleDeleteProfile() {
      Component parent = panel_.getTopLevelAncestor();
      try {
         UUID uuid = getSelectedProfileUUID();
         if (uuid == null) { // Just in case
            return;
         }
         String profileName = admin_.getProfileUUIDsAndNames().get(uuid);
         int answer = JOptionPane.showConfirmDialog(parent,
               String.format(
                     "<html>Are you sure you want to delete the user profile " +
                           "\"%s\"?<br />" +
                           "This cannot be undone.</html>",
                     profileName),
               "Delete User Profile",
               JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
         if (answer != JOptionPane.YES_OPTION) {
            return;
         }
         admin_.removeProfile(uuid);
      }
      catch (IOException e) {
         ReportingUtils.showError(e,
               "There was an error deleting the User Profile",
               parent);
      }
   }

   private boolean isProfileNameAvailable(String name) throws IOException {
      return !admin_.getProfileUUIDsAndNames().containsValue(name);
   }

   private void uiWasShown() {
      currentProfileListener_ = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            setSelectedProfileUUID(admin_.getUUIDOfCurrentProfile());
         }
      };
      admin_.addCurrentProfileChangeListener(currentProfileListener_);

      profileIndexListener_ = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            populateComboBox();
         }
      };
      admin_.addIndexChangeListener(profileIndexListener_);

      populateComboBox();
   }

   private void uiWasHidden() {
      admin_.removeCurrentProfileChangeListener(currentProfileListener_);
      currentProfileListener_ = null;

      admin_.removeIndexChangeListener(profileIndexListener_);
      profileIndexListener_ = null;
   }

   public static void main(String[] args) {
      try {
         ProfileSelectionUIController c = ProfileSelectionUIController.create(
               UserProfileAdmin.create());
         JFrame f = new JFrame();
         f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         f.add(c.getUI());
         f.pack();
         f.setVisible(true);
      }
      catch (IOException ex) {
         Logger.getLogger(ProfileSelectionUIController.class.getName()).
               log(Level.SEVERE, null, ex);
      }
   }
}