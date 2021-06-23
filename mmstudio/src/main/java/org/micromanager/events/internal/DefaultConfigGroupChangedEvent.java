package org.micromanager.events.internal;

import org.micromanager.events.ConfigGroupChangedEvent;

/**
 * Event signaling that the "active" preset in a config group changed.
 * The default implementation of this event is posted on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultConfigGroupChangedEvent implements ConfigGroupChangedEvent {
   private final String groupName_;
   private final String newConfig_;

   public DefaultConfigGroupChangedEvent(String groupName, String newConfig) {
      groupName_ = groupName;
      newConfig_ = newConfig;
   }

   /**
    * @return The name of the newly selected configuration
    */
   public String getNewConfig() {
         return newConfig_;
      }

   /**
    * @return Name of the group to which this newly selected configuration belongs.
   */
   public String getGroupName() {
         return groupName_;
      }
}

