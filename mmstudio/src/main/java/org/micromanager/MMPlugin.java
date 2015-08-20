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
 * This interface is the standard interface for all Micro-Manager plugins.
 * If you want to make a new plugin for Micro-Manager, then you need to do
 * the following:
 * - Create a class that implements this interface (or one of the more
 *   specific interfaces like org.micromanager.data.ProcessorPlugin)
 * - Annotate that class with the org.scijava.plugin.Plugin annotation
 * - Place your plugin's jar file in the mmplugins directory of your ImageJ
 *   installation.
 *
 * The annotated plugin class should look something like this:
 * @org.scijava.plugin.Plugin(type = MyPlugin.class)
 * public class MyPlugin implements org.micromanager.Plugin, SciJavaPlugin {
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
