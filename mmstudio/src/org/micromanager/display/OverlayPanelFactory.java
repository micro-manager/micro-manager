///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display API
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

import org.micromanager.display.DisplayWindow;

/**
 * An OverlayPanelFactory is a class which can create OverlayPanels. As it is
 * possible to have multiple inspector windows with their own overlay controls,
 * and for that matter it's possible to have multiple overlays of the same
 * type in a single inspector window, we need an object capable of generating
 * new overlay panels on demand.
 */
public interface OverlayPanelFactory {
   /**
    * Create a single OverlayPanel.
    * @return An OverlayPanel instance for controlling the overlay.
    */
   public OverlayPanel createOverlayPanel();

   /**
    * Provide a short textual description of the OverlayPanel this factory
    * creates. This string must be unique across all OverlayPanelFactories
    * registered with the program (see DisplayManager.registerOverlay()).
    * @return A short description (2-3 words) of the overlay.
    */
   public String getTitle();
}
