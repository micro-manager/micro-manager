package de.embl.rieslab.emu.configuration.data;

public class PluginConfigurationID {

  private String name;
  private String plugin;

  public PluginConfigurationID(String name, String plugin) {
    this.name = name;
    this.plugin = plugin;
  }

  public boolean isCorrespondingPluginConfiguration(PluginConfiguration plug) {
    if (plug.getPluginName().equals(plugin) && plug.getConfigurationName().equals(name)) {
      return true;
    }
    return false;
  }
}
