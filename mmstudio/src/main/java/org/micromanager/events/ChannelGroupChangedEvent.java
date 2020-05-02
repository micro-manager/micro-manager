package org.micromanager.events;

public interface ChannelGroupChangedEvent {

   /**
    * Provides the name of the newly selected channel group
    * @return name of the newly selected ChannelGroup
    */
   public String getNewChannelGroup();
}
