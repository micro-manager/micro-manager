package org.micromanager.pluginmanagement;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.micromanager.api.MMBasePlugin;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.MMProcessorPlugin;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 * Utility class used to to assemble information about the plugin
 */
public class PluginItem {
   // raw class as input by caller
   private Class<?> pluginClass_ = null; 
   // MMBasePlugin instance generated in PluginItem
   private MMBasePlugin plugin_ = null;
   // Enum indicating the type of this plugin for when we need to treat
   // different kinds differently (e.g. standard plugin vs. ProcessorPlugin).
   private PluginType pluginType_ = PluginType.PLUGIN_STANDARD;
   // className deduced from pluginClass
   private String className_ = "";
   // menuText deduced from plugin_
   private String menuItem_ = "undefined";
   // tooltip deduced from plugin_
   private String tooltip_ = "";
   // dir in which the class lives (relative to plugin root dir)
   private String directory_ = "";
   // message generated during inspection of pluginClass_
   private String msg_ = "";

   public PluginItem(Class<?> pluginClass, String className, 
         PluginType pluginType, String menuItem, String tooltip, 
         String directory, String msg) {
      pluginClass_ = pluginClass;
      className_ = className;
      pluginType_ = pluginType;
      menuItem_ = menuItem;
      tooltip_ = tooltip;
      directory_ = directory;
      msg_ = msg;
   }
   
   public PluginItem(PluginItem pio) {
      pluginClass_ = pio.pluginClass_;
      className_ = pio.className_;
      pluginType_ = pio.pluginType_;
      menuItem_ = pio.menuItem_;
      plugin_ = pio.plugin_;
      directory_ = pio.directory_;
      msg_ = pio.msg_;
   }
   
   public PluginType getPluginType() {return pluginType_; }
   public String getMenuItem() { return menuItem_; }
   public String getMessage() { return msg_; }
   public String getClassName() { return className_; }
   public String getTooltip() {return tooltip_; }
   public MMBasePlugin getPlugin() {return plugin_; }

   /**
    * Return the menu hierarchy path, including the leaf item name.
    */
   public List<String> getMenuPath() {
      final String sepPat = Pattern.quote(File.separator);
      List<String> menuPath = new ArrayList<String>(Arrays.asList(directory_.split(sepPat)));
      if (directory_.equals("")) {
         // String.split returns a length 1 array containing the empty
         // string when invoked on the empty string. We don't want that.
         menuPath.clear();
      }
      for (int i = 0; i < menuPath.size(); i++) {
         menuPath.set(i, menuPath.get(i).replace('_', ' '));
      }
      menuPath.add(menuItem_);
      return menuPath;
   }
   
   public void instantiate() {
      try {
         if (plugin_ == null) {
            switch (pluginType_) {
               case PLUGIN_STANDARD:
                  plugin_ = (MMPlugin) pluginClass_.newInstance();
                  break;
               case PLUGIN_PROCESSOR:
                  plugin_ = (MMProcessorPlugin) pluginClass_.newInstance();
                  break;
               default:
                  ReportingUtils.logError("Can't instantiate unrecognized plugin type " + pluginType_);
            }
         }
      } catch (InstantiationException e) {
         ReportingUtils.logError("Failed instantiating plugin: " + e);
      } catch (IllegalAccessException e) {
         ReportingUtils.logError("Failed instantiating plugin: " + e);
      }
      if (pluginType_ == PluginType.PLUGIN_STANDARD) {
         ((MMPlugin) plugin_).setApp(MMStudio.getInstance());
      }
   }

   public void dispose() {
      if (pluginType_ == PluginType.PLUGIN_STANDARD && 
            plugin_ != null) {
         ((MMPlugin) plugin_).dispose();
      }
   }
}
