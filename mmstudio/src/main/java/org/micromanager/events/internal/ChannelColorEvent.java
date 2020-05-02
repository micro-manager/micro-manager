package org.micromanager.events.internal;

import java.awt.Color;

/**
 * Signals that the user assigned a new color to a channel
 * Used to synchronize the color used to display a channel in the UI
 */
public class ChannelColorEvent {
   private final String channelGroup_;
   private final String channel_;
   private final Color color_;

   public ChannelColorEvent(String channelGroup, String channel, Color color) {
      channelGroup_ = channelGroup;
      channel_ = channel;
      color_ = color;
   }

   public String getChannelGroup() {
      return channelGroup_;
   }

   public String getChannel() {
      return channel_;
   }

   public Color getColor() {
      return color_;
   }
}
