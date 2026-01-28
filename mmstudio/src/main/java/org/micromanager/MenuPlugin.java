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

/**
 * A MenuPlugin is a plugin that should appear under the "Plugins" menu.
 * Note that certain types of plugins automatically appear in certain contexts,
 * and do not need to implement this interface:
 * - ProcessorPlugins are managed via the "On-The-Fly Image Processing" menu
 * item in the Plugins menu, which opens the Image Processing Pipeline window.
 * - All OverlayPlugins appear in the Inspector frame's "Overlays" section.
 * Currently, adding plugins to menus other than the Plugins menu is not
 * supported.
 */
public interface MenuPlugin extends MMPlugin {
   /**
    * Indicate which sub-menu of the Plugins menu this plugin should appear
    * in. If that sub-menu does not exist, it will be created. If an empty
    * string is returned, then the plugin will be inserted directly into the
    * Plugins menu, instead of into a sub-menu.
    *
    * @return Sub-menu of the Plugins menu hosting this entry
    */
   String getSubMenu();

   /**
    * This method will be called when the plugin is selected from the
    * PluginsMenu.
    */
   void onPluginSelected();
}
