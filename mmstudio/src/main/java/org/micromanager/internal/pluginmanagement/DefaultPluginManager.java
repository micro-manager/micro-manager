///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Nenad Amodaj, nenad@amodaj.com, Jul 18, 2005
//               Modifications by Arthur Edelstein, Nico Stuurman, Henry Pinkard
//COPYRIGHT:     University of California, San Francisco, 2006-2013
//               100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//CVS:          $Id$
//
package org.micromanager.internal.pluginmanagement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JMenuBar;

import org.micromanager.data.ProcessorPlugin;
import org.micromanager.PluginManager;

import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.MMPlugin;
import org.micromanager.Studio;

public class DefaultPluginManager implements PluginManager {

   private Studio studio_;
   private JMenuBar menuBar_;
   private Thread loadingThread_;

   private HashMap<Class, ArrayList<MMPlugin>> pluginTypeToPlugins_;

   public DefaultPluginManager(Studio studio, JMenuBar menuBar) {
      studio_ = studio;
      menuBar_ = menuBar;
      pluginTypeToPlugins_ = new HashMap<Class, ArrayList<MMPlugin>>();
      pluginTypeToPlugins_.put(ProcessorPlugin.class,
            new ArrayList<MMPlugin>());
      loadingThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            loadPlugins();
         }
      }, "Plugin loading thread");
      loadingThread_.start();
   }

   /**
    * Join the loading thread for the specified amount of time, to wait for
    * plugin loading to complete.
    */
   public void waitForInitialization(int timeoutMs) throws InterruptedException {
      loadingThread_.join(timeoutMs);
   }

   /**
    * Return true if the loading thread is done.
    */
   public boolean isInitializationComplete() {
      return !loadingThread_.isAlive();
   }

   /**
    * Scan the mmplugins directory for plugins, and insert them into the
    * pluginTypeToPlugins_ structure.
    */
   private void loadPlugins() {
      List<Class> plugins = PluginFinder.findPlugins(
            System.getProperty("user.dir") + "/mmplugins");
      for (Class pluginClass : plugins) {
         try {
            MMPlugin plugin = (MMPlugin) pluginClass.newInstance();
            if (plugin instanceof ProcessorPlugin) {
               pluginTypeToPlugins_.get(ProcessorPlugin.class).add(plugin);
            }
         }
         catch (InstantiationException e) {
            ReportingUtils.logError(e, "Error instantiating plugin class " + pluginClass);
         }
         catch (IllegalAccessException e) {
            ReportingUtils.logError(e, "Access exception instantiating plugin class " + pluginClass);
         }
      }
   }

   @Override
   public HashMap<String, ProcessorPlugin> getProcessorPlugins() {
      HashMap<String, ProcessorPlugin> result = new HashMap<String,
         ProcessorPlugin>();
      for (MMPlugin plugin : pluginTypeToPlugins_.get(ProcessorPlugin.class)) {
         result.put(plugin.getName(), (ProcessorPlugin) plugin);
      }
      return result;
   }
}
