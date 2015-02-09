package org.micromanager.display.internal.link;

import org.micromanager.display.DisplaySettings;

/**
 * Signifies that the contrast settings for a specific channel of the display
 * have been changed.
 */
public class ContrastEvent implements DisplaySettingsEvent {
   private int channelIndex_;
   private String channelName_;
   private DisplaySettings newSettings_;
   
   public ContrastEvent(int channelIndex, String channelName,
         DisplaySettings newSettings) {
      channelIndex_ = channelIndex;
      channelName_ = channelName;
      newSettings_ = newSettings;
   }

   public int getIndex() {
      return channelIndex_;
   }

   public String getChannelName() {
      return channelName_;
   }

   public DisplaySettings getDisplaySettings() {
      return newSettings_;
   }

   public String toString() {
      return "<ContrastEvent for channel " + channelIndex_ + ">";
   }
}
