package org.micromanager.events.internal;

import org.micromanager.events.ChannelGroupChangedEvent;

public class DefaultChannelGroupChangedEvent implements ChannelGroupChangedEvent {
   private final String channelGroup_;

   public DefaultChannelGroupChangedEvent(final String newChannelGroup) {
      channelGroup_ = newChannelGroup;
   }

   @Override
   public String getNewChannelGroup() {
      return channelGroup_;
   }
}
