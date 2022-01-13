///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//AUTHOR:        Mark Tsuchida, Chris Weisiger
//COPYRIGHT:     University of California, San Francisco, 2006-2015
//               100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.pipelineinterface;

import java.io.IOException;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This class wraps around a ProcessorConfigurator, allowing it to be easily
 * enabled or disabled without having any logic in the plugin side of things.
 * It also contains some backreferences to the plugin that created the
 * Configurator, for ease of use.
 */
public final class ConfiguratorWrapper {
   private final ProcessorPlugin plugin_;
   private final ProcessorConfigurator configurator_;
   private final String name_;
   private boolean isEnabled_;
   private boolean isEnabledInLive_;

   public ConfiguratorWrapper(ProcessorPlugin plugin,
         ProcessorConfigurator configurator, String name) {
      plugin_ = plugin;
      configurator_ = configurator;
      name_ = name;
      isEnabled_ = true;
      isEnabledInLive_ = true;
   }

   public ProcessorPlugin getPlugin() {
      return plugin_;
   }

   public ProcessorConfigurator getConfigurator() {
      return configurator_;
   }

   public String getName() {
      return name_;
   }

   public boolean isEnabled() {
      return isEnabled_;
   }

   public boolean isEnabledInLive() {
      return isEnabledInLive_;
   }

   public void setEnabled(boolean isEnabled) {
      isEnabled_ = isEnabled;
   }

   public void setEnabledInLive(boolean isEnabled) {
      isEnabledInLive_ = isEnabled;
   }

   /**
    * Serialize ourselves into JSON for storage.
    */
   public String toJSON() {
      try {
         JSONObject json = new JSONObject();
         json.put("name", name_);
         json.put("isEnabled", isEnabled_);
         json.put("isEnabledInLive", isEnabledInLive_);
         json.put("pluginName", plugin_.getClass().getName());
         json.put("configSettings", configurator_.getSettings().toJSON());
         return json.toString();
      } catch (JSONException e) {
         ReportingUtils.logError(e, "Unable to serialize ConfiguratorWrapper");
         return null;
      }
   }

   /**
    * Return a deserialized ConfiguratorWrapper based on the provided string.
    */
   public static ConfiguratorWrapper fromString(String contents, Studio studio) {
      try {
         JSONObject json = new JSONObject(contents);
         ProcessorPlugin plugin = studio.plugins().getProcessorPlugins().get(
               json.getString("pluginName"));
         PropertyMap settings;
         try {
            settings = PropertyMaps.fromJSON(json.getString("configSettings"));
         } catch (IOException ex) {
            throw new RuntimeException("Failed to parse pipeline config pmap JSON", ex);
         }
         ProcessorConfigurator configurator = plugin.createConfigurator(settings);
         ConfiguratorWrapper result = new ConfiguratorWrapper(plugin,
               configurator, json.getString("name"));
         result.setEnabled(json.getBoolean("isEnabled"));
         // This flag was added later.
         if (json.has("isEnabledInLive")) {
            result.setEnabledInLive(json.getBoolean("isEnabledInLive"));
         } else {
            result.setEnabledInLive(result.isEnabled());
         }
         return result;
      } catch (JSONException e) {
         ReportingUtils.logError(e,
               "Error deserializing ConfiguratorWrapper from [" + contents + "]");
         return null;
      }
   }
}
