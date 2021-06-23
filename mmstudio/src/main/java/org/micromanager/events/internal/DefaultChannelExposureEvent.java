package org.micromanager.events.internal;

public class DefaultChannelExposureEvent {
   private final double newExposureTime_;
   private final String channelGroup_;
   private final String channel_;
   private final boolean isMainExposureTime_;

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
    *
    * @return true if this channel is the currently-active channel.
    * @deprecated use {@link #isMainExposureTime()} instead
    */
   @Deprecated
   public boolean getIsMainExposureTime() {
      return isMainExposureTime();
   }
}
