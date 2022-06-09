package de.embl.rieslab.emu.configuration.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * Class holding the list of known {@link PluginConfiguration}s and the name of
 * the currently loaded configuration.
 *
 * @author Joran Deschamps
 */
public class GlobalConfiguration {

    /**
     * Value given to unallocated UIProperty.
     */
    public final static String KEY_UNALLOCATED = "Unallocated";
    private final static String KEY_NONE = "None";

    private String currentConfiguration;
    private TreeMap<String, String> globalSettings;
    private ArrayList<PluginConfiguration> pluginconfigs;

    /**
     * Constructor for an empty GlobalConfiguration.
     *
     * @param globalSettings Default global settings (can be empty).
     */
    public GlobalConfiguration(TreeMap<String, String> globalSettings) {
        this.currentConfiguration = KEY_NONE;
        this.pluginconfigs = new ArrayList<PluginConfiguration>();
        this.globalSettings = globalSettings;
    }

    /**
     * Constructor for configurations read from a file. Builds a GlobalConfiguration from a {@link GlobalConfigurationWrapper}.
     *
     * @param configuration {@link GlobalConfigurationWrapper} read out from a file.
     */
    @SuppressWarnings("unchecked")
    public GlobalConfiguration(GlobalConfigurationWrapper configuration) {
        this.currentConfiguration = configuration.getDefaultConfigurationName();
        this.globalSettings = configuration.getGlobalSettings();
        this.pluginconfigs = (ArrayList<PluginConfiguration>) configuration.getPluginConfigurations().clone();
    }

    /**
     * Returns a list of plugin configurations.
     *
     * @return List of plugin configurations.
     */
    public ArrayList<PluginConfiguration> getPluginConfigurations() {
        return pluginconfigs;
    }

    /**
     * Number of known plugin configurations.
     *
     * @return Number of plugin configurations.
     */
    public int getPluginsNumber() {
        return pluginconfigs.size();
    }

    /**
     * Returns the name of the current configuration.
     *
     * @return Configuration's name.
     */
    public String getCurrentConfigurationName() {
        return currentConfiguration;
    }

    /**
     * Generates a {@link GlobalConfigurationWrapper} from this GlobalConfiguration in order to write the
     * GlobalConfiguration to a file.
     *
     * @return Wrapper.
     * @see GlobalConfigurationWrapper
     */
    public GlobalConfigurationWrapper getGlobalConfigurationWrapper() {
        GlobalConfigurationWrapper conf = new GlobalConfigurationWrapper();
        conf.setDefaultConfigurationName(currentConfiguration);
        conf.setGlobalSettings(globalSettings);
        conf.setPluginConfigurations(pluginconfigs);
        return conf;
    }

    /**
     * Returns the name of the current plugin.
     *
     * @return Current plugin's name.
     */
    public String getCurrentPluginName() {
        if (getCurrentPluginConfiguration() == null) {
            return KEY_NONE;
        }
        return getCurrentPluginConfiguration().getPluginName();
    }

    /**
     * Sets the current configuration to {@code new_default}. If {@code new_default} is unknown, the methods
     * returns false, and true if it had successfully changed the configuration.
     *
     * @param new_default New current configuration.
     * @return True if the change was successful, false if {@code new_default} is unknown.
     */
    public boolean setCurrentConfiguration(String new_default) {
        Iterator<PluginConfiguration> it = pluginconfigs.iterator();
        while (it.hasNext()) {
            PluginConfiguration plugin = it.next();

            if (plugin.getConfigurationName().equals(new_default)) {
                currentConfiguration = plugin.getConfigurationName();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the current {@link PluginConfiguration}.
     *
     * @return The current plugin configuration.
     */
    public PluginConfiguration getCurrentPluginConfiguration() {
        return getPluginConfiguration(currentConfiguration);
    }

    /**
     * Returns the {@link PluginConfiguration} called {@code name}.
     *
     * @param name Name of the PluginConfiguration.
     * @return The PluginConfiguration, or null there is no PluginConfiguration called {@code name}.
     */
    public PluginConfiguration getPluginConfiguration(String name) {
        Iterator<PluginConfiguration> it = pluginconfigs.iterator();
        while (it.hasNext()) {
            PluginConfiguration plugin = it.next();

            if (plugin.getConfigurationName().equals(name)) {
                return plugin;
            }
        }
        return null;
    }

    /**
     * Returns a sorted String array containing the name of all configurations compatible
     * with the {@code pluginName} plugin.
     *
     * @param pluginName Name of the plugin.
     * @return Sorted string array with the names of all compatible configurations.
     */
    public String[] getCompatibleConfigurations(String pluginName) {

        ArrayList<String> configs = new ArrayList<String>();
        Iterator<PluginConfiguration> it = pluginconfigs.iterator();

        while (it.hasNext()) {
            PluginConfiguration plug = it.next();

            if (plug.getPluginName().equals(pluginName)) {
                configs.add(plug.getConfigurationName());
            }
        }

        String[] confs = configs.toArray(new String[0]);
        Arrays.sort(confs);

        return confs;
    }

    /**
     * If a {@link PluginConfiguration} in the list of plugin configurations has the same name
     * than {@code pluginConfiguration}, then {@code pluginConfiguration} is substituted for the
     * former configuration in the list. If no such configuration exist, nothing happens.
     *
     * @param pluginConfiguration New plugin configuration to substitute.
     */
    public void substituteConfiguration(PluginConfiguration pluginConfiguration) {
        for (int i = 0; i < pluginconfigs.size(); i++) {
            // PluginConfiguration implements the Comparable interface and are compared solely on their name
            if (pluginconfigs.get(i).compareTo(pluginConfiguration) == 0) {
                pluginconfigs.remove(i);
                pluginconfigs.add(pluginConfiguration);
            }
        }
    }

    /**
     * Attempts to add {@code pluginConfiguration} to the plugin configuration list. If a PluginConfiguration
     * with the same name exists, then it returns {@code false}.
     *
     * @param pluginConfiguration PluginConfiguration to be added.
     * @return True if the configuration was added, false otherwise.
     */
    public boolean addConfiguration(PluginConfiguration pluginConfiguration) {

        boolean exists = false;
        for (int i = 0; i < pluginconfigs.size(); i++) {
            if (pluginconfigs.get(i).compareTo(pluginConfiguration) == 0) { // compare the names (Comparable interface)
                exists = true;
            }
        }

        if (!exists) {
            pluginconfigs.add(pluginConfiguration);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if a {@link PluginConfiguration} bearing the name {@code configName} exists.
     *
     * @param configName Name of the configuration.
     * @return True if the configuration exists, false otherwise.
     */
    public boolean doesConfigurationExist(String configName) {
        Iterator<PluginConfiguration> it = pluginconfigs.iterator();

        while (it.hasNext()) {
            PluginConfiguration plug = it.next();

            if (plug.getConfigurationName().equals(configName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the global settings.
     *
     * @return Global settings.
     */
    public TreeMap<String, String> getGlobalSettings() {
        return globalSettings;
    }

    /**
     * Sets the global settings.
     *
     * @param globset New global settings.
     */
    public void setGlobalSettings(TreeMap<String, String> globset) {
        globalSettings = globset;
    }
}
