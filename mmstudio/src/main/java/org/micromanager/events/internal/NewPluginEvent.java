package org.micromanager.events.internal;

import org.micromanager.MMGenericPlugin;

/** This class represents the discovery of a new MMPlugin. */
public final class NewPluginEvent {
  private MMGenericPlugin plugin_;

  public NewPluginEvent(MMGenericPlugin plugin) {
    plugin_ = plugin;
  }

  public MMGenericPlugin getPlugin() {
    return plugin_;
  }
}
