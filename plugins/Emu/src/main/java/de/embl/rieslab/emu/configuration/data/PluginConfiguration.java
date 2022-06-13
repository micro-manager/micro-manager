package de.embl.rieslab.emu.configuration.data;

import java.util.Map;
import java.util.TreeMap;

/**
 * Class representing a plugin configuration. It holds the name of an EMU plugin
 * and maps of the plugin propertie, parameter and setting names and values. The Class
 * is written and read using jackson ObjectMapper by the ConfigurationIO class.
 *
 * @author Joran Deschamps
 */
public class PluginConfiguration implements Comparable<PluginConfiguration> {

   private String configurationName;
   private String pluginName;
   private TreeMap<String, String> properties;
   private TreeMap<String, String> parameters;
   private TreeMap<String, String> settings;

   public PluginConfiguration() {
      // do nothing
   }

   public void configure(String configurationName, String pluginName, Map<String, String> props,
                         Map<String, String> params, Map<String, String> settgs) {
      this.configurationName = configurationName;
      this.pluginName = pluginName;

      properties = new TreeMap<String, String>(props);
      parameters = new TreeMap<String, String>(params);
      settings = new TreeMap<String, String>(settgs);
   }

   public String getConfigurationName() {
      return configurationName;
   }

   public void setConfigurationName(String configurationName) {
      this.configurationName = configurationName;
   }

   public String getPluginName() {
      return pluginName;
   }

   public void getPluginName(String pluginName) {
      this.pluginName = pluginName;
   }

   public TreeMap<String, String> getProperties() {
      return properties;
   }

   public void setProperties(Map<String, String> properties) {
      this.properties = new TreeMap<String, String>(properties);
   }

   public TreeMap<String, String> getParameters() {
      return parameters;
   }

   public void setParameters(Map<String, String> parameters) {
      this.parameters = new TreeMap<String, String>(parameters);
   }

   public TreeMap<String, String> getPluginSettings() {
      return settings;
   }

   public void setPluginSettings(Map<String, String> settgs) {
      this.settings = new TreeMap<String, String>(settgs);
   }

   @Override
   public int compareTo(PluginConfiguration otherUIPlugin) {
      // TODO should also check if they are related to the same plugin otherwise we might
      //  have inconsistencies
      return configurationName.compareTo(otherUIPlugin.getConfigurationName());
   }

}
