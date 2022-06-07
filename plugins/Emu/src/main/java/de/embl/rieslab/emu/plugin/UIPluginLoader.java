package de.embl.rieslab.emu.plugin;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.controller.utils.GlobalSettings;
import de.embl.rieslab.emu.plugin.UIPlugin;
import de.embl.rieslab.emu.plugin.examples.ibeamsmart.IBeamSmartPlugin;
import de.embl.rieslab.emu.plugin.examples.simpleui.SimpleUIPlugin;
import de.embl.rieslab.emu.ui.ConfigurableMainFrame;
import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ServiceLoader;
import java.util.TreeMap;
/**
 * Loader of EMU plugins. It uses the java.util.ServiceLoader to detect and load
 * the plugins from the EMU folders in the Micro-manager installation folder.
 *
 * @author Joran Deschamps
 * @see UIPlugin
 */
public class UIPluginLoader {

    private SystemController controller_;
    private HashMap<String, UIPlugin> plugins_;

    /**
     * Constructor. The UIPluginLoader loads the example plugins and all plugins found
     * in a .jar in the EMU home folder.
     *
     * @param controller EMU system controller.
     */
    public UIPluginLoader(SystemController controller) {
        controller_ = controller;

        plugins_ = new HashMap<String, UIPlugin>();

        //////////// Adds known plugins
        UIPlugin ibeamsmart = new IBeamSmartPlugin();
        UIPlugin simpleui = new SimpleUIPlugin();
        plugins_.put(ibeamsmart.getName(), ibeamsmart);
        plugins_.put(simpleui.getName(), simpleui);

        //////////// Discovers additional plugins
        File loc = new File(GlobalSettings.HOME);

        File[] flist = loc.listFiles(new FileFilter() {
            public boolean accept(File file) {
                return file.getPath().toLowerCase().endsWith(".jar");
            }
        });

        URL[] urls = new URL[flist.length];
        for (int i = 0; i < flist.length; i++) {
            try {
                urls[i] = flist[i].toURI().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        URLClassLoader ucl = new URLClassLoader(urls, UIPlugin.class.getClassLoader());

        ServiceLoader<UIPlugin> serviceLoader = ServiceLoader.load(UIPlugin.class, ucl);
        for (UIPlugin uiPlugin : serviceLoader) {
            plugins_.put(uiPlugin.getName(), uiPlugin);
        }

    }

    /**
     * Returns the number of known plugins.
     *
     * @return Number of plugins
     */
    public int getPluginNumber() {
        return plugins_.size();
    }

    /**
     * Checks if {@code pluginName} corresponds to the name of a known plugin.
     *
     * @param pluginName Name of the plugin
     * @return True if the plugin is known, false otherwise.
     */
    public boolean isPluginAvailable(String pluginName) {
        return plugins_.containsKey(pluginName);
    }

    /**
     * Returns an instantiated ConfigurableMainFrame that corresponds to the main
     * frame of the plugin {@code pluginName}.
     *
     * @param pluginName     Name of the plugin to load.
     * @param pluginSettings Plugin settings.
     * @return Main frame of the plugin
     */
    public ConfigurableMainFrame loadPlugin(String pluginName, TreeMap<String, String> pluginSettings) {
        return plugins_.get(pluginName).getMainFrame(controller_, pluginSettings);
    }

    /**
     * Returns an array of known plugin names.
     *
     * @return Array of plugin names.
     */
    public String[] getPluginList() {
        String[] s = plugins_.keySet().toArray(new String[0]);
        Arrays.sort(s);
        return s;
    }
}