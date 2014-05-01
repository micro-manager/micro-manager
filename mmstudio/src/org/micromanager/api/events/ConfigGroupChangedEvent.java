package org.micromanager.api.events;

// This class signifies when a new value is added to a config group.
public class ConfigGroupChangedEvent {
   public String groupName_;
   public String newConfig_;

   pubilc ConfigGroupChangedEvent(String groupName, String newConfig) {
      groupName_ = groupName;
      newConfig_ = newConfig;
   }
}
