package org.micromanager.pluginmanagement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

import mmcorej.TaggedImage;

import org.micromanager.api.DataProcessor;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.MMProcessorPlugin;
import org.micromanager.MMStudio;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;

/*
 * This class is responsible for general plugin management.
 */
public class PluginManager {
   private JMenu pluginMenu_;
   private final Map<String, JMenu> pluginSubMenus_ = new HashMap<String, JMenu>();
   private final PluginLoader pluginLoader_;

   private MMStudio studio_;
   private JMenuBar menuBar_;

   public PluginManager(MMStudio studio, JMenuBar menuBar) {
      studio_ = studio;
      menuBar_ = menuBar;
      pluginLoader_ = new PluginLoader(this);
   }

   public Thread initializePlugins() {
      pluginMenu_ = GUIUtils.createMenuInMenuBar(menuBar_, "Plugins");

      Thread loadThread = new Thread(new ThreadGroup("Plugin loading"),
            new Runnable() {
               @Override
               public void run() {
                  pluginLoader_.loadPlugins();
               }
      });
      // Needed for loading clojure-based jars:
      loadThread.setContextClassLoader(getClass().getClassLoader());
      loadThread.start();
      return loadThread;
   }

   /**
    * Adds plugin items to the plugins menu
    * Adds submenus (currently only 1 level deep)
    * @param plugin - plugin to be added to the menu
    */
   public void addPluginToMenu(final PluginItem plugin) {
      List<String> path = plugin.getMenuPath();
      final PluginManager thisInstance = this;
      if (path.size() == 1) { // Add plugin to top-level menu
         GUIUtils.addMenuItem(pluginMenu_, plugin.getMenuItem(), plugin.getTooltip(),
                 new Runnable() {
            public void run() {
               thisInstance.displayPlugin(plugin);
            }
         });
      }
      else if (path.size() == 2) { // Add plugin to submenu
         String groupName = path.get(0);
         JMenu submenu = pluginSubMenus_.get(groupName);
         if (submenu == null) {
            submenu = new JMenu(groupName);
            pluginSubMenus_.put(groupName, submenu);
            submenu.validate();
            pluginMenu_.add(submenu);
            if (groupName.equals("On-The-Fly Processors")) {
               // Sorry for this hard-coded special behavior
               addImageProcessorConfigureMenuItem(submenu);
               submenu.addSeparator();
            }
         }
         GUIUtils.addMenuItem(submenu, plugin.getMenuItem(), plugin.getTooltip(),
                 new Runnable() {
            public void run() {
               thisInstance.displayPlugin(plugin);
            }
         });
      }
      else {
         // Ignore plugins in deeper paths.
      }

      pluginMenu_.validate();
      menuBar_.validate();
   }

   private void addImageProcessorConfigureMenuItem(JMenu menu) {
      GUIUtils.addMenuItem(menu, "Configure Processors...",
            "Set up the image processor pipeline",
            new Runnable() {
               public void run() {
                  studio_.showPipelinePanel();
               }
            });
   }

   // Handle a plugin being selected from the Plugins menu.
   private void displayPlugin(final PluginItem plugin) {
      ReportingUtils.logMessage("Plugin command: " + plugin.getMenuItem());
      plugin.instantiate();
      switch (plugin.getPluginType()) {
         case PLUGIN_STANDARD:
            // Standard plugin; create its UI.
            ((MMPlugin) plugin.getPlugin()).show();
            break;
         case PLUGIN_PROCESSOR:
            // Processor plugin; check for existing processor of 
            // this type and show its UI if applicable; otherwise
            // create a new one.
            // First show the pipeline GUI.
            studio_.showPipelinePanel();
            MMProcessorPlugin procPlugin = (MMProcessorPlugin) plugin.getPlugin();
            String procName = PluginLoader.getNameForPluginClass(procPlugin.getClass());
            DataProcessor<TaggedImage> pipelineProcessor = studio_.getAcquisitionEngine().getProcessorRegisteredAs(procName);
            if (pipelineProcessor == null) {
               // No extant processor of this type; make a new one,
               // which automatically adds it to the pipeline.
               pipelineProcessor = studio_.getAcquisitionEngine().makeProcessor(procName, studio_);
            }
            if (pipelineProcessor != null) {
               // Show the GUI for this processor. The extra null check is 
               // because making the processor (above) could have failed.
               pipelineProcessor.makeConfigurationGUI();
            }
            break;
         default:
            // Unrecognized plugin type; just skip it. 
            ReportingUtils.logError("Unrecognized plugin type " + plugin.getPluginType());
      }
   }

   public void disposePlugins() {
      pluginLoader_.disposePlugins();
   }   
}
