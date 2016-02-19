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
import java.util.HashSet;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.AutofocusPlugin;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.display.InspectorPlugin;
import org.micromanager.display.OverlayPlugin;
import org.micromanager.IntroPlugin;
import org.micromanager.MenuPlugin;
import org.micromanager.MMPlugin;
import org.micromanager.PluginManager;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.Studio;

import org.micromanager.events.internal.NewPluginEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;

public class DefaultPluginManager implements PluginManager {

   /**
    * Simple extension of JMenu whose menu items remained alphabetically
    * ordered.
    */
   private static class SortedMenu extends JMenu {
      private HashSet<JMenuItem> unsortedItems_;
      public SortedMenu(String title) {
         super(title);
         unsortedItems_ = new HashSet<JMenuItem>();
      }

      // Allow users to bypass the sorted nature
      public JMenuItem addUnsorted(JMenuItem item) {
         unsortedItems_.add(item);
         return super.add(item);
      }

      @Override
      public JMenuItem add(JMenuItem item) {
         // Find the insertion point.
         for (int i = 0; i < getItemCount(); ++i) {
            JMenuItem curItem = getItem(i);
            if (unsortedItems_.contains(curItem)) {
               // Skip this item because it's outside the sorted logic.
               continue;
            }
            if (curItem == null) {
               // Separator.
               continue;
            }
            if (item.getText().compareTo(curItem.getText()) < 0) {
               insert(item, i);
               return item;
            }
         }
         // Add it at the end instead.
         return super.add(item);
      }
   }

   // List of the types of plugins we allow.
   private static final ArrayList<Class> VALID_CLASSES = new ArrayList<Class>();
   static {
      VALID_CLASSES.add(AutofocusPlugin.class);
      VALID_CLASSES.add(ProcessorPlugin.class);
      VALID_CLASSES.add(InspectorPlugin.class);
      VALID_CLASSES.add(OverlayPlugin.class);
      VALID_CLASSES.add(IntroPlugin.class);
      VALID_CLASSES.add(MenuPlugin.class);
      VALID_CLASSES.add(QuickAccessPlugin.class);
      VALID_CLASSES.add(MMPlugin.class);
   }

   private static final String PROCESSOR_MENU = "On-The-Fly Image Processing";

   private Studio studio_;
   private JMenu menu_;
   private Thread loadingThread_;

   private HashMap<Class, ArrayList<MMPlugin>> pluginTypeToPlugins_;

   // Maps plugin submenu headers to the submenus themselves.
   private HashMap<String, JMenu> subMenus_;

   public DefaultPluginManager(Studio studio, JMenuBar menuBar) {
      studio_ = studio;
      menu_ = new SortedMenu("Plugins");
      menuBar.add(menu_);

      subMenus_ = new HashMap<String, JMenu>();

      pluginTypeToPlugins_ = new HashMap<Class, ArrayList<MMPlugin>>();
      for (Class classType : VALID_CLASSES) {
         pluginTypeToPlugins_.put(classType, new ArrayList<MMPlugin>());
      }
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
    * Load all plugins.
    * TODO: for now, autofocus plugins are in a separate directory from
    * regular plugins.
    */
   private void loadPlugins() {
      long startTime = System.currentTimeMillis();
      loadPlugins(PluginFinder.findPlugins(
            System.getProperty("org.micromanager.plugin.path",
               System.getProperty("user.dir") + "/mmplugins")));

      loadPlugins(PluginFinder.findPlugins(
            System.getProperty("org.micromanager.autofocus.path",
               System.getProperty("user.dir") + "/mmautofocus")));

      // We need to use our normal class loader to load stuff from the MMJ_.jar
      // file, since otherwise we won't be able to cast the new plugin to
      // MMPlugin in loadPlugins(), below.
      loadPlugins(PluginFinder.findPluginsWithLoader(
            getClass().getClassLoader()));

      ReportingUtils.logMessage("Plugin loading took " +
            (System.currentTimeMillis() - startTime) + "ms");
   }

   /**
    * Insert the provided plugins into the pluginTypeToPlugins_ structure,
    * instantiate them, add them to menus, etc.
    */
   private void loadPlugins(List<Class> pluginClasses) {
      for (Class pluginClass : pluginClasses) {
         try {
            // HACK: We can load a bunch of scijava stuff from the MMJ_.jar
            // file, which of course isn't really relevant to our interests, so
            // skip things that aren't actually MMPlugins.
            if (!MMPlugin.class.isAssignableFrom(pluginClass)) {
               continue;
            }
            MMPlugin plugin = (MMPlugin) pluginClass.newInstance();
            ReportingUtils.logMessage("Found plugin " + plugin);
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
      for (Class pluginClass : VALID_CLASSES) {
         if (pluginClass.isInstance(plugin)) {
            pluginTypeToPlugins_.get(pluginClass).add(plugin);
         }
      }
      if (ProcessorPlugin.class.isInstance(plugin)) {
         // Add it to the "On-the-fly image processing" plugin menu.
         addProcessorPluginToMenu((ProcessorPlugin) plugin);
      }
      if (MenuPlugin.class.isInstance(plugin)) {
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
            SortedMenu menu = new SortedMenu(subMenu);
            // HACK: if this is the processor menu, add a couple of items
            // to it first.
            if (subMenu.equals(PROCESSOR_MENU)) {
               JMenuItem configure = new JMenuItem("Configure Processors...");
               configure.addActionListener(new ActionListener() {
                  @Override
                  public void actionPerformed(ActionEvent e) {
                     ((MMStudio) studio_).showPipelineFrame();
                  }
               });
               menu.addUnsorted(configure);
               menu.addSeparator();
            }
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
      addSubMenuItem(PROCESSOR_MENU, plugin.getName(),
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
         result.put(plugin.getClass().getName(), (ProcessorPlugin) plugin);
      }
      return result;
   }

   @Override
   public HashMap<String, OverlayPlugin> getOverlayPlugins() {
      HashMap<String, OverlayPlugin> result = new HashMap<String, OverlayPlugin>();
      for (MMPlugin plugin : pluginTypeToPlugins_.get(OverlayPlugin.class)) {
         result.put(plugin.getClass().getName(), (OverlayPlugin) plugin);
      }
      return result;
   }

   @Override
   public HashMap<String, IntroPlugin> getIntroPlugins() {
      HashMap<String, IntroPlugin> result = new HashMap<String, IntroPlugin>();
      for (MMPlugin plugin : pluginTypeToPlugins_.get(IntroPlugin.class)) {
         result.put(plugin.getClass().getName(), (IntroPlugin) plugin);
      }
      return result;
   }

   @Override
   public HashMap<String, MenuPlugin> getMenuPlugins() {
      HashMap<String, MenuPlugin> result = new HashMap<String, MenuPlugin>();
      for (MMPlugin plugin : pluginTypeToPlugins_.get(MenuPlugin.class)) {
         result.put(plugin.getClass().getName(), (MenuPlugin) plugin);
      }
      return result;
   }

   @Override
   public HashMap<String, AutofocusPlugin> getAutofocusPlugins() {
      HashMap<String, AutofocusPlugin> result = new HashMap<String, AutofocusPlugin>();
      for (MMPlugin plugin : pluginTypeToPlugins_.get(AutofocusPlugin.class)) {
         result.put(plugin.getClass().getName(), (AutofocusPlugin) plugin);
      }
      return result;
   }

   @Override
   public HashMap<String, QuickAccessPlugin> getQuickAccessPlugins() {
      HashMap<String, QuickAccessPlugin> result = new HashMap<String, QuickAccessPlugin>();
      for (MMPlugin plugin : pluginTypeToPlugins_.get(QuickAccessPlugin.class)) {
         result.put(plugin.getClass().getName(), (QuickAccessPlugin) plugin);
      }
      return result;
   }

   @Override
   public HashMap<String, InspectorPlugin> getInspectorPlugins() {
      HashMap<String, InspectorPlugin> result = new HashMap<String, InspectorPlugin>();
      for (MMPlugin plugin : pluginTypeToPlugins_.get(InspectorPlugin.class)) {
         result.put(plugin.getClass().getName(), (InspectorPlugin) plugin);
      }
      return result;
   }
}
