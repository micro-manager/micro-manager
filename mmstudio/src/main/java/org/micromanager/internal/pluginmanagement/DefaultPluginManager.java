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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.micromanager.data.ProcessorPlugin;
import org.micromanager.display.OverlayPlugin;
import org.micromanager.MenuPlugin;
import org.micromanager.MMPlugin;
import org.micromanager.PluginManager;
import org.micromanager.Studio;

import org.micromanager.events.internal.NewPluginEvent;
import org.micromanager.internal.utils.ReportingUtils;

public class DefaultPluginManager implements PluginManager {

   private Studio studio_;
   private JMenu menu_;
   private Thread loadingThread_;

   private HashMap<Class, ArrayList<MMPlugin>> pluginTypeToPlugins_;

   // Maps plugin submenu headers to the submenus themselves.
   private HashMap<String, JMenu> subMenus_;

   public DefaultPluginManager(Studio studio, JMenuBar menuBar) {
      studio_ = studio;
      menu_ = new JMenu("Plugins");
      menuBar.add(menu_);

      subMenus_ = new HashMap<String, JMenu>();

      pluginTypeToPlugins_ = new HashMap<Class, ArrayList<MMPlugin>>();
      pluginTypeToPlugins_.put(ProcessorPlugin.class,
            new ArrayList<MMPlugin>());
      pluginTypeToPlugins_.put(OverlayPlugin.class,
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
            ReportingUtils.logDebugMessage("Found plugin " + plugin);
            addPlugin(plugin);
         }
         catch (InstantiationException e) {
            ReportingUtils.logError(e, "Error instantiating plugin class " + pluginClass);
         }
         catch (IllegalAccessException e) {
            ReportingUtils.logError(e, "Access exception instantiating plugin class " + pluginClass);
         }
      }
   }

   /**
    * Load the provided plugin. We set its context, add it to our
    * pluginTypeToPlugins_ map, insert it into a submenu if appropriate,
    * and post a NewPluginEvent.
    */
   private void addPlugin(final MMPlugin plugin) {
      plugin.setContext(studio_);
      // TODO: ideally this enumeration (and the similar enumeration in
      // the constructor) should not be needed.
      if (plugin instanceof ProcessorPlugin) {
         pluginTypeToPlugins_.get(ProcessorPlugin.class).add(plugin);
         // Add it to the "On-the-fly image processing" plugin menu.
         addProcessorPluginToMenu((ProcessorPlugin) plugin);
      }
      else if (plugin instanceof OverlayPlugin) {
         pluginTypeToPlugins_.get(OverlayPlugin.class).add(plugin);
      }
      if (plugin instanceof MenuPlugin) {
         // Add it to the menu.
         addSubMenuItem(((MenuPlugin) plugin).getSubMenu(),
               plugin.getName(),
               new Runnable() {
                  @Override
                  public void run() {
                     ((MenuPlugin) plugin).onPluginSelected();
                  }
               }
         );
      }
      studio_.events().post(new NewPluginEvent(plugin));
   }

   /**
    * Create a new item in the specified submenu of the Plugins menu.
    */
   private void addSubMenuItem(String subMenu, String title,
         final Runnable selectAction) {
      JMenuItem item = new JMenuItem(title);
      item.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            selectAction.run();
         }
      });
      if (subMenu.equals("")) {
         // Add it to the root menu.
         menu_.add(item);
      }
      else {
         if (!subMenus_.containsKey(subMenu)) {
            // Create a new menu.
            JMenu menu = new JMenu(subMenu);
            menu_.add(menu);
            subMenus_.put(subMenu, menu);
         }
         subMenus_.get(subMenu).add(item);
      }
   }

   /**
    * Add a new ProcessorPlugin entry in the Plugins menu. ProcessorPlugins,
    * when selected, will bring up the Pipeline window and add the processor
    * to the current pipeline.
    */
   private void addProcessorPluginToMenu(final ProcessorPlugin plugin) {
      addSubMenuItem("On-The-Fly Image Processing",
            plugin.getName(),
            new Runnable() {
               @Override
               public void run() {
                  studio_.data().addAndConfigureProcessor(plugin);
               }
            }
      );
   }

   @Override
   public HashMap<String, ProcessorPlugin> getProcessorPlugins() {
      HashMap<String, ProcessorPlugin> result = new HashMap<String, ProcessorPlugin>();
      for (MMPlugin plugin : pluginTypeToPlugins_.get(ProcessorPlugin.class)) {
         result.put(plugin.getName(), (ProcessorPlugin) plugin);
      }
      return result;
   }

   @Override
   public HashMap<String, OverlayPlugin> getOverlayPlugins() {
      HashMap<String, OverlayPlugin> result = new HashMap<String, OverlayPlugin>();
      for (MMPlugin plugin : pluginTypeToPlugins_.get(OverlayPlugin.class)) {
         result.put(plugin.getName(), (OverlayPlugin) plugin);
      }
      return result;
   }
}
