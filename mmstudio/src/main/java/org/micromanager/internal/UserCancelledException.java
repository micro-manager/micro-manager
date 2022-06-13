package org.micromanager.internal;

/**
 * Use this exception when the user cancels an action and there is no
 * obvious return value to indicate the user's desires.
 *
 * @author nico
 */
public class UserCancelledException extends Exception {
   public UserCancelledException() {
      super();
   }

   public UserCancelledException(String message) {
      super(message);
   }

}
