package org.micromanager.events.internal;

import org.micromanager.events.ChannelGroupChangedEvent;


/**
 * Event signaling that the "Channel Group" changed
 *
 * This event is posted on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultChannelGroupChangedEvent implements ChannelGroupChangedEvent {
   private final String channelGroup_;

   public DefaultChannelGroupChangedEvent(final String newChannelGroup) {
      channelGroup_ = newChannelGroup;
   }

   /**
    * Provides the name of the newly selected channel group
    * @return name of the newly selected ChannelGroup
    */
   @Override
   public String getNewChannelGroup() {
      return channelGroup_;
   }
}
