package de.embl.rieslab.emu.configuration.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

/**
 * Wraps a {@link GlobalConfiguration} in a simple class in order to write and read the class using
 * jackson ObjectMapper.
 *
 * @author Joran Deschamps
 */
public class GlobalConfigurationWrapper {

  private String defaultConfigurationName;
  private TreeMap<String, String> globalSettings;
  private ArrayList<PluginConfiguration> pluginConfigurations;

  public GlobalConfigurationWrapper() {
    // do nothing
  }

  public ArrayList<PluginConfiguration> getPluginConfigurations() {
    Collections.sort(this.pluginConfigurations); // alphabetical sorting
    return pluginConfigurations;
  }

  public String getDefaultConfigurationName() {
    return defaultConfigurationName;
  }

  public TreeMap<String, String> getGlobalSettings() {
    return globalSettings;
  }

  public void setPluginConfigurations(ArrayList<PluginConfiguration> pluginConfigurations) {
    this.pluginConfigurations = pluginConfigurations;
    Collections.sort(this.pluginConfigurations); // alphabetical sorting
  }

  public void setDefaultConfigurationName(String defaultConfigurationName) {
    this.defaultConfigurationName = defaultConfigurationName;
  }

  public void setGlobalSettings(TreeMap<String, String> globalSettings) {
    this.globalSettings = globalSettings;
  }
}
