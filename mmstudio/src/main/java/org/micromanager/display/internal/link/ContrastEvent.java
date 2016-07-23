///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
