package org.micromanager.imagedisplay.link;

import org.micromanager.api.display.DisplaySettings;

/**
 * Signifies that the contrast settings for a specific channel of the display
 * have been changed.
 */
public class ContrastEvent implements DisplaySettingsEvent {
   private int channelIndex;
   private DisplaySettings newSettings_;
   
   public ContrastEvent(int channelIndex, DisplaySettings newSettings) {
      channelIndex = channelIndex;
      newSettings_ = newSettings;
   }

   public int getIndex() {
      return channelIndex;
   }

   public DisplaySettings getDisplaySettings() {
      return newSettings_;
   }
}
