package org.micromanager.patternoverlay;

/**
 * Very simple Exception class to handle the lack of a live/snap window
 * @author nico
 */
public class NoLiveWindowException extends Exception {
   private final String message_ = "Live image window required.";
   
   public NoLiveWindowException() {
   }
   
   @Override
   public String getMessage() {
      return message_;
   }
}
