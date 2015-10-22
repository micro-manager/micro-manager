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

package org.micromanager.quickaccess;

import javax.swing.ImageIcon;

import org.micromanager.MMPlugin;

/**
 * QuickAccessPlugins are used for controls that can show up in the Quick-
 * Access Window, which shows frequently-used controls.
 */
public interface QuickAccessPlugin extends MMPlugin {
   /**
    * The width of a single cell in the Quick-Access Window.
    */
   public static final int CELL_WIDTH = 100;

   /**
    * The height of a single cell in the Quick-Access Window.
    */
   public static final int CELL_HEIGHT = 50;
   /**
    * Provide an icon to use to represent this plugin when configuring the
    * Quick-Access Window. May be null, in which case a rendering of the
    * plugin's controls will be used instead.
    */
   public ImageIcon getIcon();
}
