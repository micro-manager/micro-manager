///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       ??
//
// COPYRIGHT:    University of California, San Francisco, ??
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager.internal.dialogs.introdialogparts;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Application;
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
   private final Application app_;

   private ChangeListener currentProfileListener_;
   private ChangeListener profileIndexListener_;

   private final JPanel panel_;
   private final JComboBox<ProfileComboItem> profileComboBox_ = new JComboBox<>();
   private final PopupButton gearButton_;

   private final JMenuItem gearMenuNewProfileItem_ =
         new JMenuItem("New User Profile...");
   private final JMenuItem gearMenuDuplicateProfileItem_ =
         new JMenuItem("Duplicate Profile...");
   private final JMenuItem gearMenuRenameProfileItem_ =
         new JMenuItem("Rename Profile...");
   private final JMenuItem gearMenuDeleteProfileItem_ =
         new JMenuItem("Delete User Profile...");
   private final JCheckBoxMenuItem gearMenuReadOnlyCheckBox_ =
         new JCheckBoxMenuItem("Profile Read Only");

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

   /**
    * Create a new controller.
    *
    * @param app   Application to use
    * @param admin User profile admin to use
    * @return a new controller
    */
   public static ProfileSelectionUIController create(Application app, UserProfileAdmin admin)
         throws IOException {
      final ProfileSelectionUIController ret =
            new ProfileSelectionUIController(app, admin);

      ret.profileComboBox_.addActionListener(ret);
      ret.gearButton_.addPopupButtonListener(ret);
      for (JMenuItem item : ImmutableList.of(ret.gearMenuNewProfileItem_,
            ret.gearMenuDuplicateProfileItem_, ret.gearMenuRenameProfileItem_,
            ret.gearMenuDeleteProfileItem_, ret.gearMenuReadOnlyCheckBox_)) {
         item.addActionListener(ret);
      }

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

   private ProfileSelectionUIController(Application app, UserProfileAdmin admin) {
      app_ = app;
      admin_ = admin;

      ProfileComboItem blank = new ProfileComboItem(UUID.randomUUID(), "");
      profileComboBox_.setPrototypeDisplayValue(blank);
      profileComboBox_.setMaximumRowCount(32);
      // On Windows, right align using custom renderer
      if (UIManager.getLookAndFeel().getClass().getName().equals(
            "com.sun.java.swing.plaf.windows.WindowsLookAndFeel")) {
         profileComboBox_.setRenderer(new ComboBoxCellRenderer(340));
         profileComboBox_.setBackground(Color.WHITE);
      }


      JPopupMenu gearMenu = new JPopupMenu();
      gearMenu.add(gearMenuNewProfileItem_);
      gearMenu.add(gearMenuDuplicateProfileItem_);
      gearMenu.add(gearMenuRenameProfileItem_);
      gearMenu.add(gearMenuDeleteProfileItem_);
      gearMenu.add(gearMenuReadOnlyCheckBox_);

      gearButton_ = PopupButton.create(
            IconLoader.getIcon("/org/micromanager/icons/gear.png"),
            gearMenu);
      gearButton_.setPreferredSize(new Dimension(24, 22));

      panel_ = new JPanel(new MigLayout(
            new LC().fillX().insets("0").gridGap("0", "0")));
      panel_.add(profileComboBox_, new CC().growX().pushX());
      panel_.add(gearButton_, new CC().width("pref!").height("pref!"));
      profileComboBox_.setToolTipText(
            "<html>Select the user profile for this session.<br />"
                  + "Settings and preferences are saved in each user profile.</html>");
      gearButton_.setToolTipText(
            "Manage user profiles");
   }

   private void populateComboBox() {
      profileComboBox_.removeAllItems();
      Map<UUID, String> profiles;
      try {
         profiles = admin_.getProfileUUIDsAndNames();
      } catch (IOException e) {
         // If the profiles are not accessible, we should have gotten an error
         // before reaching here. Just leave combo box empty.
         ReportingUtils.logError(e, "Error getting profile index");
         return;
      }

      // Sort profiles by name
      Ordering<UUID> valueComparator = Ordering
            .from(String.CASE_INSENSITIVE_ORDER)
            .onResultOf(Functions.forMap(profiles));
      Map<UUID, String> sortedProfiles = ImmutableSortedMap.copyOf(profiles, valueComparator);

      // Default profile first
      UUID defaultUUID = admin_.getUUIDOfDefaultProfile();
      profileComboBox_.addItem(new ProfileComboItem(defaultUUID, profiles.get(
            defaultUUID)));

      // Remainder in sorted order
      for (Map.Entry<UUID, String> e : sortedProfiles.entrySet()) {
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

   /**
    * Get the UUID of the currently selected profile.
    *
    * @return UUID of the selected profile, or null if none selected
    */
   public UUID getSelectedProfileUUID() {
      ProfileComboItem item = (ProfileComboItem) profileComboBox_.getSelectedItem();
      if (item == null) {
         return null;
      }
      return item.getUUID();
   }

   private void setSelectedProfileUUID(UUID uuid) {
      for (int i = 0; i < profileComboBox_.getItemCount(); ++i) {
         ProfileComboItem item = profileComboBox_.getItemAt(i);
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
      } else if (event.getSource() == gearMenuNewProfileItem_) {
         handleNewProfile();
      } else if (event.getSource() == gearMenuDuplicateProfileItem_) {
         handleDuplicateProfile();
      } else if (event.getSource() == gearMenuRenameProfileItem_) {
         handleRenameProfile();
      } else if (event.getSource() == gearMenuDeleteProfileItem_) {
         handleDeleteProfile();
      } else if (event.getSource() == gearMenuReadOnlyCheckBox_) {
         handleReadOnlyAction();
      }
   }

   private void handleReadOnlyAction() {
      UUID uuid = admin_.getUUIDOfCurrentProfile();
      if (uuid != null) {
         try {
            admin_.setProfileReadOnly(gearMenuReadOnlyCheckBox_.isSelected());
         } catch (IOException e) {
            ReportingUtils.logError(e,
                  "Error setting the profile to Read Only.");
         }
      }
   }

   private void handleProfileSelection() {
      try {
         UUID uuid = getSelectedProfileUUID();
         if (uuid != null) {
            admin_.setCurrentUserProfile(uuid);
            if (app_ != null) {
               app_.skin().setSkin(app_.skin().getSkin());
            }
            gearMenuReadOnlyCheckBox_.setSelected(admin_.isProfileReadOnly());
         }
      } catch (IOException e) {
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
      } catch (IOException e) {
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
         String originalName = admin_.getProfileUUIDsAndNames().get(originalUUID);
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
      } catch (IOException e) {
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
      } catch (IOException e) {
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
                     "<html>Are you sure you want to delete the user profile "
                           + "\"%s\"?<br />" + "This cannot be undone.</html>",
                     profileName),
               "Delete User Profile",
               JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
         if (answer != JOptionPane.YES_OPTION) {
            return;
         }
         admin_.removeProfile(uuid);
      } catch (IOException e) {
         ReportingUtils.showError(e,
               "There was an error deleting the User Profile",
               parent);
      }
   }

   private boolean isProfileNameAvailable(String name) throws IOException {
      return !admin_.getProfileUUIDsAndNames().containsValue(name);
   }

   private void uiWasShown() {
      currentProfileListener_ = e -> setSelectedProfileUUID(admin_.getUUIDOfCurrentProfile());
      admin_.addCurrentProfileChangeListener(currentProfileListener_);

      profileIndexListener_ = e -> populateComboBox();
      admin_.addIndexChangeListener(profileIndexListener_);

      populateComboBox();
   }

   private void uiWasHidden() {
      admin_.removeCurrentProfileChangeListener(currentProfileListener_);
      currentProfileListener_ = null;

      admin_.removeIndexChangeListener(profileIndexListener_);
      profileIndexListener_ = null;
   }

   /**
    * Test the UI.
    *
    * @param args command line arguments
    */
   public static void main(String[] args) {
      try {
         ProfileSelectionUIController c = ProfileSelectionUIController.create(
               null, UserProfileAdmin.create());
         JFrame f = new JFrame();
         f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         f.add(c.getUI());
         f.pack();
         f.setVisible(true);
      } catch (IOException ex) {
         Logger.getLogger(ProfileSelectionUIController.class.getName())
               .log(Level.SEVERE, null, ex);
      }
   }
}