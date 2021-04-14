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

package org.micromanager.display;

import org.micromanager.MMPlugin;

/**
 * DisplayGearMenuPlugins add items to be shown in the "gear menu" shown in
 * each DisplayWindow.
 */
public interface DisplayGearMenuPlugin extends MMPlugin {
   /**
    * Indicate which sub-menu of the gear menu this plugin should appear
    * in. If that sub-menu does not exist, it will be created. If an empty
    * string is returned, then the plugin will be inserted directly into the
    * gear menu, instead of into a sub-menu.
    * @return Sub-menu of the gear menu hosting this entry, or empty string.
    */
   String getSubMenu();

   /**
    * This method will be called when the plugin is selected from the
    * gear menu.
    * @param display The display whose gear menu was interacted with.
    */
   void onPluginSelected(DisplayWindow display);
}
