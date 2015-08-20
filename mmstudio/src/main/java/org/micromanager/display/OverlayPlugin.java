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
 * This interface must be implemented by plugins that want to appear in the
 * menu of pattern overlays available in image display windows.
 * Additionally, the plugin class must be annotated with the @Plugin
 * annotation; see the MMPlugin documentation for more information.
 * Note: names of overlay plugins must be unique.
 */
public interface OverlayPlugin extends MMPlugin {
   /**
    * Create a new OverlayPanelFactory object for creating OverlayPanels.
    */
   public OverlayPanelFactory createFactory();
}
