package org.micromanager.events.internal;

import org.micromanager.events.ConfigGroupChangedEvent;

public class DefaultConfigGroupChangedEvent implements ConfigGroupChangedEvent {
  private final String groupName_;
  private final String newConfig_;

  public DefaultConfigGroupChangedEvent(String groupName, String newConfig) {
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
