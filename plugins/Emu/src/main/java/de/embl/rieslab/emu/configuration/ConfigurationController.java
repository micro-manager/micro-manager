package de.embl.rieslab.emu.configuration;

import de.embl.rieslab.emu.configuration.data.GlobalConfiguration;
import de.embl.rieslab.emu.configuration.data.PluginConfiguration;
import de.embl.rieslab.emu.configuration.data.PluginConfigurationID;
import de.embl.rieslab.emu.configuration.io.ConfigurationIO;
import de.embl.rieslab.emu.configuration.ui.ConfigurationManagerUI;
import de.embl.rieslab.emu.configuration.ui.ConfigurationWizardUI;
import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.controller.utils.GlobalSettings;
import de.embl.rieslab.emu.controller.utils.SystemDialogs;
import de.embl.rieslab.emu.micromanager.mmproperties.MMPropertiesRegistry;
import de.embl.rieslab.emu.ui.ConfigurableFrame;
import de.embl.rieslab.emu.utils.settings.Setting;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * Controller class for the configuration of the current UI. This class bridges the {@link de.embl.rieslab.emu.controller.SystemController}
 * with the {@link ConfigurationWizardUI}, the {@link GlobalConfiguration}. The ConfigurationController starts the configuration wizard to allow
 * the user to modify the current configuration. It also contains inform the SystemController on the different configurations. Finally, it
 * calls the {@link ConfigurationIO} to read and write the configurations from/to files.
 *
 * @author Joran Deschamps
 */
public class ConfigurationController {

   private final SystemController controller_; // overall controller
   private ConfigurationWizardUI wizard_;
         // graphical interface to create/edit the current configuration
   private ConfigurationManagerUI manager_; // graphical interface to delete configurations
   private GlobalConfiguration globalConfiguration_; // configurations of the UI
   @SuppressWarnings("rawtypes")
   private final HashMap<String, Setting> globalSettings_; // global settings

   /**
    * Constructor.
    *
    * @param controller System controller.
    */
   public ConfigurationController(SystemController controller) {
      controller_ = controller;
      globalSettings_ = GlobalSettings.getDefaultGlobalSettings();
   }

   /**
    * Returns the default path to the configuration file as defined in {@link de.embl.rieslab.emu.controller.utils.GlobalSettings}.
    *
    * @return Default configuration file.
    */
   public File getDefaultConfigurationFile() {
      return new File(GlobalSettings.CONFIG_NAME);
   }

   /**
    * Reads the default configuration file. This method is called at the start of EMU.
    *
    * @return True if the configuration has been successfully read, false otherwise.
    */
   public boolean readDefaultConfiguration() {
      if (getDefaultConfigurationFile().exists()) {
         globalConfiguration_ = ConfigurationIO.read(getDefaultConfigurationFile());
         if (globalConfiguration_ == null) {
            globalConfiguration_ = new GlobalConfiguration(getGlobalSettingsMap()); // empty one
            return false;
         }
         readGlobalSettings();
         return true;
      }
      return false;
   }

   /**
    * Reads a configuration file {@code f}.
    *
    * @param f File to read
    * @return True if a configuration was successfully read, false otherwise.
    */
   public boolean readConfiguration(File f) {
      if (f.exists()) {
         globalConfiguration_ = ConfigurationIO.read(f);
         if (globalConfiguration_ == null) {
            globalConfiguration_ = new GlobalConfiguration(getGlobalSettingsMap()); // empty one
            return false;
         }
         readGlobalSettings();
         return true;
      }
      return false;
   }

   /**
    * Writes the configurations to the default configuration file.
    *
    * @return True if successfully written, false otherwise.
    */
   public boolean writeConfiguration() {
      return ConfigurationIO.write(getDefaultConfigurationFile(), getConfiguration());
   }

   /**
    * Writes the configurations to {@code f}.
    *
    * @param f File to write the configuration to.
    * @return True if successfully written, false otherwise.
    */
   public boolean writeConfiguration(File f) {
      return ConfigurationIO.write(f, getConfiguration());
   }

   /**
    * Returns the {@link GlobalConfiguration}.
    *
    * @return Global configuration.
    */
   public GlobalConfiguration getConfiguration() {
      return globalConfiguration_;
   }

   /**
    * Checks if a configuration called {@code configName} exists in the {@link GlobalConfiguration}.
    *
    * @param configName Name of the configuration.
    * @return True if the configuration exists, false otherwise.
    */
   public boolean doesConfigurationExist(String configName) {
      if (globalConfiguration_ == null) {
         return false;
      }
      return globalConfiguration_.doesConfigurationExist(configName);
   }

   /**
    * Returns an array containing the names of configurations compatible with {@code pluginName}.
    *
    * @param pluginName Plugin under consideration.
    * @return Array of compatible configuration names. The array can be of size 0.
    */
   public String[] getCompatibleConfigurations(String pluginName) {
      if (globalConfiguration_ != null) {
         return globalConfiguration_.getCompatibleConfigurations(pluginName);
      }
      return new String[0];
   }

   /**
    * Checks if the configuration contains all the properties and parameters as defined in the {@link de.embl.rieslab.emu.ui.ConfigurableFrame}.
    *
    * @param maininterface Current plugin' ConfigurableFrame.
    * @return True if the current configuration contains all the properties and parameters defined in the plugin's ConfigurableFrame.
    */
   public boolean configurationSanityCheck(ConfigurableFrame maininterface) {
      if (globalConfiguration_ == null) {
         return false;
      } else {
         // just checks if some UIProperties or UIParameters are missing from the configuration.
         // When editing the settings, the PropertiesTable only displays the ConfigurableFrame properties, therefore properties
         // present in the configuration but not in the ConfigurableFrame will be ignored. The same mechanism applies when
         // the controller pairs the UI and MM properties. As a result the sanity check only makes sense with respect to the
         // actual UIProperties and UIParameters of the ConfigurableFrame.

         // check if the plugin configuration contains all the UIProperties
         Set<String> uipropkeys = new HashSet<String>(maininterface.getUIProperties().keySet());
         uipropkeys.removeAll(
               globalConfiguration_.getCurrentPluginConfiguration().getProperties().keySet());
         if (uipropkeys.size() > 0) {
            return false;
         }

         // check if the plugin configuration contains all the UIParameters
         Set<String> uiparamkeys = new HashSet<String>(maininterface.getUIParameters().keySet());
         uiparamkeys.removeAll(
               globalConfiguration_.getCurrentPluginConfiguration().getParameters().keySet());
         return uiparamkeys.size() <= 0;
      }
   }


   /**
    * Sets the default configuration to {@code newDefault}.
    *
    * @param newDefault New default configuration.
    * @return True if the configuration was changed, false otherwise.
    */
   public boolean setDefaultConfiguration(String newDefault) {
      if (globalConfiguration_ == null) {
         return false;
      }

      return globalConfiguration_.setCurrentConfiguration(newDefault);
   }

   /**
    * Returns the default configuration's name. Null if there is no configuration yet.
    *
    * @return Default configuration's name.
    */
   public String getDefaultConfiguration() {
      if (globalConfiguration_ == null) {
         return null;
      }

      return globalConfiguration_.getCurrentConfigurationName();
   }

   /**
    * Returns the properties configuration. Null if there is none.
    *
    * @return Pairs of UIProperty names (keys) and MMProperty names (values), as well as UIProperty state names (keys)
    * and UIProperty state values (values)
    */
   public TreeMap<String, String> getPropertiesConfiguration() {
      if (globalConfiguration_ == null) {
         return null;
      }

      return globalConfiguration_.getCurrentPluginConfiguration().getProperties();
   }

   /**
    * Returns the parameters configuration. Null if there is no configuration.
    *
    * @return Pairs of UIParameter names (keys) and their value (values)
    */
   public TreeMap<String, String> getParametersConfiguration() {
      if (globalConfiguration_ == null) {
         return null;
      }

      return globalConfiguration_.getCurrentPluginConfiguration().getParameters();
   }

   /**
    * Returns the plugin settings configuration. Null if there is no configuration.
    *
    * @return Pairs of UIParameter names (keys) and their value (values)
    */
   public TreeMap<String, String> getPluginSettingsConfiguration() {
      if (globalConfiguration_ == null) {
         return null;
      }

      return globalConfiguration_.getCurrentPluginConfiguration().getPluginSettings();
   }

   /**
    * Returns the global settings from the configuration. Null if there is no configuration.
    *
    * @return Pairs of Settings names (keys) and their value (values)
    */
   @SuppressWarnings("rawtypes")
   public HashMap<String, Setting> getGlobalSettings() {
      return globalSettings_;
   }

   private TreeMap<String, String> getGlobalSettingsMap() {
      TreeMap<String, String> globalSettingsMap = new TreeMap<String, String>();

      for (String s : globalSettings_.keySet()) {
         globalSettingsMap.put(s, globalSettings_.get(s).getStringValue());
      }

      return globalSettingsMap;
   }

   /**
    * Reads out the global settings from the configuration.
    *
    * @param globalSettings Settings to be read.
    */
   private void readGlobalSettings() {
      TreeMap<String, String> globalSettings = globalConfiguration_.getGlobalSettings();
      Iterator<String> it = globalSettings.keySet().iterator();
      ArrayList<String> wrongvals = new ArrayList<String>();
      while (it.hasNext()) {
         String s = it.next();

         // if the setting has the expected type, then replace in the current settings
         if (globalSettings_.containsKey(s)) {
            if (globalSettings_.get(s).isValueCompatible(globalSettings.get(s))) {
               globalSettings_.get(s).setStringValue(globalSettings.get(s));
            } else {
               wrongvals.add(s);
            }
         }
      }
      if (wrongvals.size() > 0) {
         SystemDialogs.showWrongGlobalSettings(wrongvals);
      }
   }


   /**
    * Closes the ConfigurationWizard window (if running). This method is called upon closing the plugin.
    */
   public void shutDown() {
      if (wizard_ != null) {
         wizard_.shutDown();
      }
      if (manager_ != null) {
         manager_.shutDown();
      }
   }

   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   ///////////////////////////// Wizard
   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


   /**
    * Starts a new configuration wizard. If a wizard is already running, then does nothing and returns {@code false}.
    *
    * @param pluginName    Current plugin's name.
    * @param maininterface plugin's ConfigurableFrame.
    * @param mmproperties  Micro-manager properties.
    * @return True if a new wizard was started, false if it was already running.
    */
   public boolean startWizard(String pluginName, ConfigurableFrame maininterface,
                              MMPropertiesRegistry mmproperties) {
      // launch wizard
      if (!isWizardRunning()) {
         wizard_ = new ConfigurationWizardUI(this);

         if (globalConfiguration_ != null) { // start a wizard with the current configuration loaded
            wizard_.start(pluginName, globalConfiguration_, maininterface, mmproperties);
         } else { // start a fresh wizard
            globalConfiguration_ = new GlobalConfiguration(getGlobalSettingsMap());
            wizard_.start(pluginName, globalConfiguration_, maininterface, mmproperties);
         }

         return true;
      }
      return false;
   }

   /**
    * Returns true if a ConfigurationWizard is running, false otherwise.
    *
    * @return true if a ConfigurationWizard is running, false otherwise
    */
   public boolean isWizardRunning() {
      if (wizard_ == null) {
         return false;
      }
      return wizard_.isRunning();
   }

   /**
    * Retrieves the pairs of UIProperty name and MMProperty names (and UIProperty state values), as well as the pairs of UIParameter names and values,
    * and writes them to the configuration file. It then calls the {@link de.embl.rieslab.emu.controller.SystemController} to update the system.
    * This method is called by the {@link ConfigurationWizardUI} upon saving of the configuration by the user.
    *
    * @param configName   Name of the configuration.
    * @param pluginName   Name of the current plugin.
    * @param uiproperties Mapping of the UIProperties with MMProperties and their states.
    * @param uiparameters Mapping of the UIParameters' states.
    * @param plugsettings Mapping of the Settings' states used to configure the ConfigurableMainFrame.
    * @param globset      Mapping of the GlobalSettings' states.
    */
   public void applyWizardSettings(String configName, String pluginName,
                                   Map<String, String> uiproperties,
                                   Map<String, String> uiparameters,
                                   Map<String, String> plugsettings, Map<String, String> globset) {
      if (globalConfiguration_ == null) {
         return;
      }

      if (globalConfiguration_.getCurrentConfigurationName().equals(configName)) {
         // the configuration has the same name
         PluginConfiguration plugin = new PluginConfiguration();
         plugin.configure(configName, pluginName, uiproperties, uiparameters, plugsettings);
         globalConfiguration_.substituteConfiguration(plugin);
      } else {
         // new configuration has a different name
         PluginConfiguration plugin = new PluginConfiguration();
         plugin.configure(configName, pluginName, uiproperties, uiparameters, plugsettings);
         globalConfiguration_.addConfiguration(plugin);
      }

      // set global settings
      globalConfiguration_.setGlobalSettings(new TreeMap<String, String>(globset));
      readGlobalSettings();

      // set current configuration
      globalConfiguration_.setCurrentConfiguration(configName);

      writeConfiguration();

      // update system
      controller_.loadConfiguration(configName);
   }

   /**
    * Returns the current plugin's ConfigurableFrame configured with {@code plugsettings} as plugin settings.
    *
    * @param plugsettings Plugin settings to configure the ConfigurableFrame
    * @return Configured ConfigurableFrame
    */
   public ConfigurableFrame getConfigurableFrame(TreeMap<String, String> plugsettings) {
      return controller_.loadConfigurableFrame(plugsettings);
   }


   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   ///////////////////////////// Manager
   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

   public boolean startManager() {
      // launch wizard
      if (!isWizardRunning()) {
         manager_ = new ConfigurationManagerUI(this);

         if (globalConfiguration_ != null) { // start a wizard with the current configuration loaded
            manager_.start(globalConfiguration_);
            return true;
         }
      }
      return false;
   }

   public void applyManagerConfigurations(ArrayList<PluginConfigurationID> confs) {
      if (globalConfiguration_ == null) {
         return;
      }

      // Remove all the PluginConfigurations that were deleted in the ConfigurationManager
      // I don't expect large array list so the brute force O(N^2) should be fine
      ArrayList<PluginConfiguration> plugconfs = globalConfiguration_.getPluginConfigurations();
      List<PluginConfiguration> toRemove = new ArrayList<PluginConfiguration>();
      for (PluginConfiguration pc : plugconfs) {
         boolean found = false;
         for (PluginConfigurationID pcid : confs) {
            if (pcid.isCorrespondingPluginConfiguration(pc)) {
               found = true;
               break;
            }
         }

         if (!found) {
            toRemove.add(pc);
         }
      }
      plugconfs.removeAll(toRemove);

      writeConfiguration();

      controller_.updateMenu();
   }

   /**
    * Returns true if a ConfigurationManager is running, false otherwise.
    *
    * @return true if a ConfigurationManager is running, false otherwise
    */
   public boolean isManagerRunning() {
      if (manager_ == null) {
         return false;
      }
      return manager_.isRunning();
   }
}
