/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.utils;

import java.beans.ExceptionListener;
import java.io.IOException;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.micromanager.UserProfile;
import org.micromanager.profile.internal.DefaultUserProfile;
import org.micromanager.profile.internal.UserProfileAdmin;

/**
 * Backward compatibility adapter for user profile access.
 * <p>
 * There are lots of places where the user profile is accessed via a static
 * method inside MMStudio, so until those are updated, this adapter is used.
 *
 * @author Mark A. Tsuchida
 */
@Deprecated
public final class UserProfileStaticInterface {
   private static final UserProfileAdmin admin_;
   private static DefaultUserProfile profile_;

   static {
      admin_ = UserProfileAdmin.create();
      admin_.addCurrentProfileChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            synchronized (UserProfileStaticInterface.class) {
               if (profile_ != null) {
                  try {
                     profile_.close();
                  }
                  catch (InterruptedException ex) {
                     Thread.currentThread().interrupt();
                  }
                  profile_ = null;
               }
            }
         }
      });
   }

   public static void handleSaveError(Exception e) {
      // TODO User should probably receive warning for the first error.
      ReportingUtils.logError(e, "Error saving user profile");
   }

   public static UserProfileAdmin getAdmin() {
      return admin_;
   }

   public static UserProfile getInstance() {
      synchronized (UserProfileStaticInterface.class) {
         if (profile_ == null) {
            try {
               profile_ = (DefaultUserProfile) admin_.getAutosavingProfile(
                     admin_.getUUIDOfCurrentProfile(), new ExceptionListener() {
                        @Override
                        public void exceptionThrown(Exception e) {
                           handleSaveError(e);
                        }
                     });
            }
            catch (IOException ex) {
               ex.printStackTrace();
               // TODO Notify user of error
               // TODO Virtual profile?
            }
         }
         return profile_;
      }
   }

   public static void shutdown() throws InterruptedException {
      synchronized (UserProfileStaticInterface.class) {
         if (profile_ != null) {
            profile_.close();
            profile_ = null;
         }
         admin_.shutdownAutosaves();
      }
   }
}