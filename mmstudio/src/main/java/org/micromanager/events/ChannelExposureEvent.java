///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
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

package org.micromanager.events;

/**
 * This class signals when the exposure time for one of the channels of the
 * current channel group has been changed.
 */
public class ChannelExposureEvent {
   private final double newExposureTime_;
   private final String channelGroup_;
   private final String channel_;
   private final boolean isMainExposureTime_;

   public ChannelExposureEvent(double newExposureTime, String channelGroup,
         String channel, boolean isMainExposureTime) {
      newExposureTime_ = newExposureTime;
      channelGroup_ = channelGroup;
      channel_ = channel;
      isMainExposureTime_ = isMainExposureTime;
   }

   /**
    * Return the new exposure time for the channel.
    */
   public double getNewExposureTime() {
      return newExposureTime_;
   }

   /**
    * Return the name of the channel group in which the modified channel
    * is located.
    */
   public String getChannelGroup() {
      return channelGroup_;
   }

   /**
    * Return the channel whose exposure time has changed.
    */
   public String getChannel() {
      return channel_;
   }

   /**
    * Returns true if this channel is the currently-active channel (i.e. the
    * one used for snaps and live mode, the one whose exposure time is
    * displayed in the main window).
    */
   public boolean isMainExposureTime() {
      return isMainExposureTime_;
   }

   @Deprecated
   public boolean getIsMainExposureTime() {
      return isMainExposureTime();
   }
}
