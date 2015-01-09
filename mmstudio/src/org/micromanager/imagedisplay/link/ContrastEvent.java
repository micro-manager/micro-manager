package org.micromanager.imagedisplay.link;

import org.micromanager.api.display.DisplaySettings;

/**
 * Signifies that the contrast settings for a specific channel of the display
 * have been changed.
 */
public class ContrastEvent implements DisplaySettingsEvent {
   private int channelIndex_;
   private DisplaySettings newSettings_;
   
   public ContrastEvent(int channelIndex, DisplaySettings newSettings) {
      channelIndex_ = channelIndex;
      newSettings_ = newSettings;
   }

   public int getIndex() {
      return channelIndex_;
   }

   public DisplaySettings getDisplaySettings() {
      return newSettings_;
   }

   public String toString() {
      return "<ContrastEvent for channel " + channelIndex_ + ">";
   }
}
