package org.micromanager.events.internal;

import org.micromanager.MMPlugin;

/**
 * This class represents the discovery of a new MMPlugin.
 */
public class NewPluginEvent {
   private MMPlugin plugin_;
   public NewPluginEvent(MMPlugin plugin) {
      plugin_ = plugin;
   }

   public MMPlugin getPlugin() {
      return plugin_;
   }
}
