package org.micromanager.imagedisplay.events;

/**
 * This class is used to signify that a status string has been changed and
 * should be displayed.
 */
public class StatusEvent {
   private String status_;

   public StatusEvent(String status) {
      status_ = status;
   }

   public String getStatus() {
      return status_;
   }
}
