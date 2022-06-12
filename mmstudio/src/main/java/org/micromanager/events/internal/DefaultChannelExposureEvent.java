package org.micromanager.events.internal;

import org.micromanager.events.ChannelExposureEvent;

/**
 * Event signalling that the channel's default exposure time changed.
 */
public class DefaultChannelExposureEvent implements ChannelExposureEvent {
   private final double newExposureTime_;
   private final String channelGroup_;
   private final String channel_;
   private final boolean isMainExposureTime_;

   /**
    * Constructs the event signalling that a channel's default exposure time changed.
    *
    * @param newExposureTime    New Exposure Time (ms)
    * @param channelGroup       Group to which this channel belongs
    * @param channel            Channel (Preset)
    * @param isMainExposureTime True if this channel is currently the "active" channel
    */
   public DefaultChannelExposureEvent(double newExposureTime, String channelGroup,
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
    *
    * @return true if this channel is the currently-active channel.
    */
   public boolean isMainExposureTime() {
      return isMainExposureTime_;
   }

   /**
    * Indicates if this channel is the currently active channel.
    *
    * @return true if this channel is the currently-active channel.
    * @deprecated use {@link #isMainExposureTime()} instead
    */
   @Deprecated
   public boolean getIsMainExposureTime() {
      return isMainExposureTime();
   }
}
