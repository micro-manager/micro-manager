package de.embl.rieslab.emu.plugin;


import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.ui.ConfigurableMainFrame;
import java.util.TreeMap;

/**
 * Interface for an EMU plugin. The plugin main class should implement this interface and
 * the jar should contain META-INF/services/embl.rieslab.emu.plugin.EMUPlugin file
 * containing the package location of the plugin (example: "myui.myplugin").
 *
 * @author Joran Deschamps
 */
public interface UIPlugin {

    /**
     * Returns the name of the plugin, used to identify it and load if set in the configuration.
     *
     * @return Plugin's name.
     */
    public String getName();


    /**
     * Returns a PropertyMainFrame. If {@code pluginSettings} is empty, then the default settings are used.
     *
     * @param controller     EMU system controller.
     * @param pluginSettings Plugin settings.
     * @return ConfigurableMainFrame of the plugin
     */
    public ConfigurableMainFrame getMainFrame(SystemController controller, TreeMap<String, String> pluginSettings);
}
