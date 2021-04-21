///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, Jul 18, 2005
//               Modifications by Arthur Edelstein, Nico Stuurman, Henry Pinkard
// COPYRIGHT:     University of California, San Francisco, 2006-2013
//               100X Imaging Inc, www.100ximaging.com, 2008
// LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// CVS:          $Id$
//
package org.micromanager.internal.pluginmanagement;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import org.micromanager.AutofocusPlugin;
import org.micromanager.IntroPlugin;
import org.micromanager.MMGenericPlugin;
import org.micromanager.MMPlugin;
import org.micromanager.MenuPlugin;
import org.micromanager.PluginManager;
import org.micromanager.Studio;
import org.micromanager.acquisition.internal.AcquisitionDialogPlugin;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.inspector.InspectorPanelPlugin;
import org.micromanager.display.overlay.OverlayPlugin;
import org.micromanager.events.internal.NewPluginEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.SortedMenu;
import org.micromanager.quickaccess.QuickAccessPlugin;

/** Implementation of the {@link PluginManager} API. */
public final class DefaultPluginManager implements PluginManager {

  // List of the types of plugins we allow.
  // TODO Remove this list and just load all MMGenericPlugin instances
  private static final ArrayList<Class> VALID_CLASSES = new ArrayList<Class>();

  static {
    VALID_CLASSES.add(AcquisitionDialogPlugin.class);
    VALID_CLASSES.add(AutofocusPlugin.class);
    VALID_CLASSES.add(DisplayGearMenuPlugin.class);
    VALID_CLASSES.add(InspectorPanelPlugin.class);
    VALID_CLASSES.add(IntroPlugin.class);
    VALID_CLASSES.add(MenuPlugin.class);
    VALID_CLASSES.add(OverlayPlugin.class);
    VALID_CLASSES.add(ProcessorPlugin.class);
    VALID_CLASSES.add(QuickAccessPlugin.class);
  }

  private static final String PROCESSOR_MENU = "On-The-Fly Image Processing";

  private final Studio studio_;
  private final Thread loadingThread_;
  private final Map<Class, List<MMGenericPlugin>> pluginTypeToPlugins_ = new HashMap<>();

  public DefaultPluginManager(Studio studio) {
    studio_ = studio;

    for (Class classType : VALID_CLASSES) {
      pluginTypeToPlugins_.put(classType, new ArrayList<>());
    }
    loadingThread_ =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                loadPlugins();
              }
            },
            "Plugin loading thread");
    loadingThread_.start();
  }

  /**
   * Join the loading thread for the specified amount of time, to wait for plugin loading to
   * complete.
   *
   * @param timeoutMs return after no more than this amount of milliseconds
   * @throws java.lang.InterruptedException
   */
  public void waitForInitialization(int timeoutMs) throws InterruptedException {
    loadingThread_.join(timeoutMs);
  }

  /**
   * Return true if the loading thread is done.
   *
   * @return false if the loading thread is still running, true otherwise
   */
  public boolean isInitializationComplete() {
    return !loadingThread_.isAlive();
  }

  /**
   * Load all plugins. TODO: for now, autofocus plugins are in a separate directory from regular
   * plugins.
   */
  private void loadPlugins() {
    long startTime = System.currentTimeMillis();
    String dir =
        System.getProperty(
            "org.micromanager.plugin.path", System.getProperty("user.dir") + "/mmplugins");
    ReportingUtils.logMessage("Searching for plugins in " + dir);
    loadPlugins(PluginFinder.findPlugins(dir));

    dir =
        System.getProperty(
            "org.micromanager.autofocus.path", System.getProperty("user.dir") + "/mmautofocus");
    ReportingUtils.logMessage("Searching for plugins in " + dir);
    loadPlugins(PluginFinder.findPlugins(dir));

    ReportingUtils.logMessage("Searching for plugins in MMStudio's class loader");
    // We need to use our normal class loader to load stuff from the MMJ_.jar
    // file, since otherwise we won't be able to cast the new plugin to
    // MMPlugin in loadPlugins(), below.
    loadPlugins(
        PluginFinder.findPluginsWithLoader(((MMStudio) studio_).getClass().getClassLoader()));

    ReportingUtils.logMessage(
        "Plugin loading took " + (System.currentTimeMillis() - startTime) + "ms");
  }

  /**
   * Insert the provided plugins into the pluginTypeToPlugins_ structure, instantiate them, add them
   * to menus, etc.
   */
  private void loadPlugins(List<Class> pluginClasses) {
    for (Class pluginClass : pluginClasses) {
      try {
        // Ignore any SciJava plugins that are not MM plugins.
        if (!MMGenericPlugin.class.isAssignableFrom(pluginClass)) {
          continue;
        }

        MMGenericPlugin plugin = (MMGenericPlugin) pluginClass.newInstance();
        ReportingUtils.logMessage("Found plugin " + plugin);
        addPlugin(plugin);
      } catch (InstantiationException e) {
        ReportingUtils.logError(e, "Error instantiating plugin class " + pluginClass);
      } catch (IllegalAccessException e) {
        ReportingUtils.logError(e, "Access exception instantiating plugin class " + pluginClass);
      } catch (NoClassDefFoundError e) {
        ReportingUtils.logError(e, "Dependency not found for plugin class " + pluginClass);
      }
    }
  }

  /**
   * Load the provided plugin. We set its context, add it to our pluginTypeToPlugins_ map, and post
   * a NewPluginEvent.
   */
  private void addPlugin(final MMGenericPlugin plugin) {
    if (plugin instanceof MMPlugin) { // Legacy plugin base class
      ((MMPlugin) plugin).setContext(studio_);
    }
    for (Class pluginClass : VALID_CLASSES) {
      if (pluginClass.isInstance(plugin)) {
        pluginTypeToPlugins_.get(pluginClass).add(plugin);
      }
    }
    studio_.events().post(new NewPluginEvent(plugin));
  }

  /** Create a new item in the specified submenu of the Plugins menu. */
  private void addSubMenuItem(
      JMenu rootMenu,
      HashMap<String, JMenu> subMenus,
      String subMenu,
      String title,
      final Runnable selectAction) {
    JMenuItem item = new JMenuItem(title);
    item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            selectAction.run();
          }
        });
    if (subMenu.equals("")) {
      // Add it to the root menu.
      rootMenu.add(item);
    } else {
      if (!subMenus.containsKey(subMenu)) {
        // Create a new menu.
        SortedMenu menu = new SortedMenu(subMenu);
        // HACK: if this is the processor menu, add a couple of items
        // to it first.
        if (subMenu.equals(PROCESSOR_MENU)) {
          JMenuItem configure = new JMenuItem("Configure Processors...");
          configure.addActionListener(
              new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                  ((MMStudio) studio_).uiManager().showPipelineFrame();
                }
              });
          menu.addUnsorted(configure);
          menu.addSeparator();
        }
        rootMenu.add(menu);
        subMenus.put(subMenu, menu);
      }
      subMenus.get(subMenu).add(item);
    }
  }

  /**
   * Add a new ProcessorPlugin entry in the Plugins menu. ProcessorPlugins, when selected, will
   * bring up the Pipeline window and add the processor to the current pipeline.
   */
  private void addProcessorPluginToMenu(
      JMenu menu, HashMap<String, JMenu> subMenus, final ProcessorPlugin plugin) {
    addSubMenuItem(
        menu,
        subMenus,
        PROCESSOR_MENU,
        plugin.getName(),
        new Runnable() {
          @Override
          public void run() {
            studio_.data().addAndConfigureProcessor(plugin);
          }
        });
  }

  @Override
  public HashMap<String, ProcessorPlugin> getProcessorPlugins() {
    HashMap<String, ProcessorPlugin> result = new HashMap<>();
    for (MMGenericPlugin plugin : pluginTypeToPlugins_.get(ProcessorPlugin.class)) {
      result.put(plugin.getClass().getName(), (ProcessorPlugin) plugin);
    }
    return result;
  }

  @Override
  public HashMap<String, OverlayPlugin> getOverlayPlugins() {
    HashMap<String, OverlayPlugin> result = new HashMap<>();
    for (MMGenericPlugin plugin : pluginTypeToPlugins_.get(OverlayPlugin.class)) {
      result.put(plugin.getClass().getName(), (OverlayPlugin) plugin);
    }
    return result;
  }

  @Override
  public HashMap<String, IntroPlugin> getIntroPlugins() {
    HashMap<String, IntroPlugin> result = new HashMap<>();
    for (MMGenericPlugin plugin : pluginTypeToPlugins_.get(IntroPlugin.class)) {
      result.put(plugin.getClass().getName(), (IntroPlugin) plugin);
    }
    return result;
  }

  @Override
  public HashMap<String, MenuPlugin> getMenuPlugins() {
    HashMap<String, MenuPlugin> result = new HashMap<>();
    for (MMGenericPlugin plugin : pluginTypeToPlugins_.get(MenuPlugin.class)) {
      result.put(plugin.getClass().getName(), (MenuPlugin) plugin);
    }
    return result;
  }

  @Override
  public HashMap<String, AutofocusPlugin> getAutofocusPlugins() {
    HashMap<String, AutofocusPlugin> result = new HashMap<>();
    for (MMGenericPlugin plugin : pluginTypeToPlugins_.get(AutofocusPlugin.class)) {
      result.put(plugin.getClass().getName(), (AutofocusPlugin) plugin);
    }
    return result;
  }

  @Override
  public HashMap<String, QuickAccessPlugin> getQuickAccessPlugins() {
    HashMap<String, QuickAccessPlugin> result = new HashMap<>();
    for (MMGenericPlugin plugin : pluginTypeToPlugins_.get(QuickAccessPlugin.class)) {
      result.put(plugin.getClass().getName(), (QuickAccessPlugin) plugin);
    }
    return result;
  }

  @Override
  public HashMap<String, InspectorPanelPlugin> getInspectorPlugins() {
    HashMap<String, InspectorPanelPlugin> result = new HashMap<>();
    for (MMGenericPlugin plugin : pluginTypeToPlugins_.get(InspectorPanelPlugin.class)) {
      result.put(plugin.getClass().getName(), (InspectorPanelPlugin) plugin);
    }
    return result;
  }

  public HashMap<String, AcquisitionDialogPlugin> getAcquisitionDialogPlugins() {
    HashMap<String, AcquisitionDialogPlugin> result = new HashMap<>();
    for (MMGenericPlugin plugin : pluginTypeToPlugins_.get(AcquisitionDialogPlugin.class)) {
      result.put(plugin.getClass().getName(), (AcquisitionDialogPlugin) plugin);
    }
    return result;
  }

  @Override
  public HashMap<String, DisplayGearMenuPlugin> getDisplayGearMenuPlugins() {
    HashMap<String, DisplayGearMenuPlugin> result = new HashMap<>();
    for (MMGenericPlugin plugin : pluginTypeToPlugins_.get(DisplayGearMenuPlugin.class)) {
      result.put(plugin.getClass().getName(), (DisplayGearMenuPlugin) plugin);
    }
    return result;
  }

  public void createPluginMenu(JMenuBar menuBar) {
    JMenu menu = new SortedMenu("Plugins");
    menuBar.add(menu);
    HashMap<String, JMenu> subMenus = new HashMap<>();
    for (final MenuPlugin plugin : getMenuPlugins().values()) {
      // Add it to the menu.
      addSubMenuItem(
          menu,
          subMenus,
          plugin.getSubMenu(),
          plugin.getName(),
          new Runnable() {
            @Override
            public void run() {
              plugin.onPluginSelected();
            }
          });
    }
    for (ProcessorPlugin plugin : getProcessorPlugins().values()) {
      // Add it to the "On-the-fly image processing" sub-menu.
      addProcessorPluginToMenu(menu, subMenus, plugin);
    }
  }
}
