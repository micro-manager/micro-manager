package de.embl.rieslab.emu.configuration.data;

public class PluginConfigurationID {

   private final String name;
   private final String plugin;

   public PluginConfigurationID(String name, String plugin) {
      this.name = name;
      this.plugin = plugin;
   }

   public boolean isCorrespondingPluginConfiguration(PluginConfiguration plug) {
      return plug.getPluginName().equals(plugin) && plug.getConfigurationName().equals(name);
   }
}
