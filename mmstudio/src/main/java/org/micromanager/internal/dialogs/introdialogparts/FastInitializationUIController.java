package org.micromanager.internal.dialogs.introdialogparts;

import java.awt.event.HierarchyEvent;
import java.io.IOException;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;
import mmcorej.CMMCore;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.UserProfile;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.profile.internal.UserProfileAdmin;


/**
 * UI element of the IntroDialog: Shows the fast initialization checkbox.
 */
public class FastInitializationUIController {
   // Test in CheckBox
   private static final String FAST_INIT = "Use fast initialization (uncheck when problematic)";
   // Core Feature name doubles as our profile key:
   private static final String FAST_CORE_FEATURE = "ParallelDeviceInitialization";
   private final UserProfileAdmin admin_;
   private ChangeListener currentProfileListener_;
   private final JPanel panel_;
   private final JCheckBox fastInitCheckBox_ = new JCheckBox(FAST_INIT);
   private final boolean coreHasFeature_;

   /**
    * Use this instead of constructor.
    *
    * @param admin Profile
    * @return Controller.
    */
   public static FastInitializationUIController create(UserProfileAdmin admin) {
      final FastInitializationUIController ret =
               new FastInitializationUIController(admin);

      ret.fastInitCheckBox_.addActionListener(e -> ret.handleSelection());

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

   private FastInitializationUIController(UserProfileAdmin admin) {
      admin_ = admin;
      panel_ = new JPanel(new MigLayout(
               new LC().fillX().insets("0").gridGap("0", "0")));
      panel_.add(fastInitCheckBox_, new CC().growX().pushX());
      fastInitCheckBox_.setToolTipText(
               "<html>Enables parallel initialization of hardware, which is faster. <br />"
                  + "Use this unless you run into errors while initializing the hardware.</html>");
      boolean fastInit = admin_.getProfile().getSettings(this.getClass()).getBoolean(
               FAST_CORE_FEATURE, true);
      try {
         CMMCore.enableFeature(FAST_CORE_FEATURE, fastInit);
      } catch (Exception ex) {
         coreHasFeature_ = false;
         return;
      }
      coreHasFeature_ = true;
   }


   /**
    * Returns the GUI element, or null when this core feature can not be changed.
    *
    * @return GUI element, can be null.
    */
   public JComponent getUI() {
      if (coreHasFeature_) {
         return panel_;
      }
      return null;
   }


   public Boolean getSelected() {
      return fastInitCheckBox_.isSelected();
   }


   /**
    * Sets the checkbox.
    *
    * @param selected boolean
    */
   public void setSelected(Boolean selected) {
      if (coreHasFeature_) {
         try {
            fastInitCheckBox_.setSelected(selected);
            CMMCore.enableFeature(FAST_CORE_FEATURE, selected);
            admin_.getProfile().getSettings(this.getClass()).putBoolean(
                     FAST_CORE_FEATURE, selected);
         } catch (Exception ex) {
            ReportingUtils.showError(ex, "Failed to set Fast Init feature in the Core.");
         }
      }
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
      UserProfile profile;
      try {
         profile = admin_.getNonSavingProfile(admin_.getUUIDOfCurrentProfile());
      } catch (IOException ex) {
         ReportingUtils.logError(ex, "There was an error reading the user profile");
         return;
      }
      fastInitCheckBox_.setSelected(profile.getSettings(this.getClass()).getBoolean(
               FAST_CORE_FEATURE, true));
   }

   private void handleSelection() {
      if (coreHasFeature_) {
         try {
            CMMCore.enableFeature(FAST_CORE_FEATURE, fastInitCheckBox_.isSelected());
            admin_.getProfile().getSettings(this.getClass()).putBoolean(FAST_CORE_FEATURE,
                     fastInitCheckBox_.isSelected());
         } catch (Exception ex) {
            ReportingUtils.showError(ex, "Failed to set Fast Init feature in the Core.");
         }
      }

   }

   /**
    * Useful?.
    *
    * @param args ignored
    */
   public static void main(String[] args) {
      FastInitializationUIController c = FastInitializationUIController.create(
               UserProfileAdmin.create());
      JFrame f = new JFrame();
      f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      f.add(c.getUI());
      f.pack();
      f.setVisible(true);
   }
}
