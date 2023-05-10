package org.micromanager.events.internal;

import org.micromanager.Studio;
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

   public DefaultConfigGroupChangedEvent(Studio studio, String groupName, String newConfig) {
      groupName_ = groupName;
      String config  = newConfig;
      // check config with core cache.  It may have changed after this callback landed on the EDT:
      try {
         config = studio.core().getCurrentConfigFromCache(groupName);
      } catch (Exception e) {
         studio.logs().logError(e, "Exception in constructor of DefaultConfigGroupChangedEvent.");
      }
      newConfig_ = config;
   }

   /**
    * Name of the newly selected configuration (preset).
    *
    * @return The name of the newly selected configuration
    */
   public String getNewConfig() {
      return newConfig_;
   }

   /**
    * Name of the Group to which this configuration (Preset) belongs.
    *
    * @return Name of the group to which this newly selected configuration belongs.
    */
   public String getGroupName() {
      return groupName_;
   }
}

