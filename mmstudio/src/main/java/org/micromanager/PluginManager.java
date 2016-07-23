///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager;

import java.util.HashMap;

import org.micromanager.data.ProcessorPlugin;
import org.micromanager.display.DisplayGearMenuPlugin;
import org.micromanager.display.InspectorPlugin;
import org.micromanager.display.OverlayPlugin;
import org.micromanager.quickaccess.QuickAccessPlugin;

/**
 * This class provides access to plugins that have been detected by
 * Micro-Manager at startup. You can access the PluginManager via
 * Studio.plugins() or Studio.getPluginManager().
 */
public interface PluginManager {
   /**
    * Return a HashMap that maps plugin class names to ProcessorPlugin
    * instances. ProcessorPlugins are used to construct pipelines that modify
    * images as they are acquired (see DataManager.createPipeline()).
    * @return HashMap that maps plugin class names to ProcessorPlugin instances
    */
   public HashMap<String, ProcessorPlugin> getProcessorPlugins();

   /**
    * Return a HashMap that maps plugin class names to OverlayPlugin instances.
    * OverlayPlugins are used to draw on top of images in the image display
    * window.
    * @return HashMap that maps plugin class names to OverlayPlugin instances
    */
   public HashMap<String, OverlayPlugin> getOverlayPlugins();

   /**
    * Return a HashMap that maps plugin class names to MenuPlugin instances.
    * MenuPlugins are normally available under the "Plugins" menu; this method
    * allows plugins to talk to each other without explicitly depending on
    * each other.
    */
   public HashMap<String, MenuPlugin> getMenuPlugins();

   /**
    * Return a HashMap that maps plugin class names to AutofocusPlugin
    * instances. AutofocusPlugins are used to perform autofocus operations.
    */
   public HashMap<String, AutofocusPlugin> getAutofocusPlugins();

   /**
    * Return a HashMap that maps plugin class names to IntroPlugin instances.
    * IntroPlugins are used to customize the intro dialog at the start of the
    * program. Ordinarily there should only be at most one of this kind of
    * plugin installed at a time.
    */
   public HashMap<String, IntroPlugin> getIntroPlugins();

   /**
    * Return a HashMap that maps plugin class names to QuickAccessPlugin
    * instances. QuickAccessPlugins are for controls that can be included in
    * the Quick-Access Window.
    */
   public HashMap<String, QuickAccessPlugin> getQuickAccessPlugins();

   /**
    * Return a HashMap that maps plugin class names to InspectorPlugin
    * instances. InspectorPlugins create the panels in the Inspector windows.
    */
   public HashMap<String, InspectorPlugin> getInspectorPlugins();

   /**
    * Return a HashMap that maps plugin class names to DisplayGearMenuPlugin
    * instances. DisplayGearMenuPlugins add entries to the gear menus in
    * DisplayWindows.
    */
   public HashMap<String, DisplayGearMenuPlugin> getDisplayGearMenuPlugins();
}
