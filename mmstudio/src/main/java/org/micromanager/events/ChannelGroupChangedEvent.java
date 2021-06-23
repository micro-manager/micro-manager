package org.micromanager.events;

import org.micromanager.MMEvent;


/**
 * Event signaling that the "Channel Group" changed
 *
 * The default implementation of this event is posted on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public interface ChannelGroupChangedEvent extends MMEvent {

   /**
    * Provides the name of the newly selected channel group
    * @return name of the newly selected ChannelGroup
    */
   String getNewChannelGroup();
}
