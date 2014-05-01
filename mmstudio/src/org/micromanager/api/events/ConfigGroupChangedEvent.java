package org.micromanager.api.events;

// This class signifies when a new value is added to a config group.
public class ConfigGroupChangedEvent {
   private String groupName_;
   private String newConfig_;

   public ConfigGroupChangedEvent(String groupName, String newConfig) {
      groupName_ = groupName;
      newConfig_ = newConfig;
   }
   public String getNewConfig() {
      return newConfig_;
   }
   public String getGroupName() {
      return groupName_;
   }
}
