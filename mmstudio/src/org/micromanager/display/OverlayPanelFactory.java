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

import ij.gui.ImageCanvas;

import java.awt.Graphics;

import javax.swing.JPanel;

import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;

/**
 * An OverlayPanelFactory is a class which can create OverlayPanels. A
 * separate OverlayPanel is needed for each DisplayWindow; this class is
 * responsible for creating them. 
 */
public interface OverlayPanelFactory {
   /**
    * Create a single OverlayPanel for the specified DisplayWindow.
    * @param display The DisplayWindow the panel will be embedded into.
    * @return An OverlayPanel instance for controlling the overlay.
    */
   public OverlayPanel createOverlayPanel(DisplayWindow display);
}
