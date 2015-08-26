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

import org.micromanager.Studio;

import org.scijava.plugin.SciJavaPlugin;

/**
 * This interface is the standard interface for all Micro-Manager plugins. In
 * practice, you will not use this interface directly, rather using one of the
 * sub-interfaces, to wit:
 * - org.micromanager.MenuPlugin, for plugins that should appear in the Plugins
 *   menu.
 * - org.micromanager.data.ProcessorPlugin, for processing images as they are
 *   collected.
 * - org.micromanager.display.OverlayPlugin, for drawing on top of image
 *   windows.
 * It is allowed to implement multiple of these interfaces, in which case the
 * plugin will be available in all appropriate contexts in the program.
 *
 * To cause your plugin to be loaded when the program runs, you need to do the
 * following:
 * - Create a class that implements this interface (or more likely, one of the
 *   other interfaces listed above) and the org.scijava.plugin.SciJavaPlugin
 *   interface.
 * - Annotate that class with the org.scijava.plugin.Plugin annotation
 * - Place your plugin's jar file in the mmplugins directory of your ImageJ
 *   installation.
 *
 * The annotated plugin class should look something like this:
 * @org.scijava.plugin.Plugin(type = MMPlugin.class)
 * public class MyPlugin implements org.micromanager.MMPlugin, SciJavaPlugin {
 *    ...plugin contents go here...
 * }
 *
 * Note that all plugins must have a default (no-argument) constructor.
 */
public interface MMPlugin extends SciJavaPlugin {
   /**
    * Receive the Studio object needed to make API calls.
    */
   public void setContext(Studio studio);

   /**
    * Provide a short string identifying the plugin.
    */
   public String getName();

   /**
    * Provide a longer string describing the purpose of the plugin.
    */
   public String getHelpText();

   /**
    * Provide a version string.
    */
   public String getVersion();

   /**
    * Provide a copyright string.
    */
   public String getCopyright();
}
