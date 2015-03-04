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

import com.google.common.eventbus.EventBus;

import ij.gui.ImageCanvas;

import java.awt.Graphics;

import javax.swing.JPanel;

import org.micromanager.data.Image;

/**
 * An OverlayPanel provides a GUI for configuring how to draw an overlay
 * on top of an image canvas. See the API function registerOverlay() for how
 * to attach these panels to image display windows.
 */
public abstract class OverlayPanel extends JPanel {
   /**
    * Receive the EventBus for the image display. This is mostly useful to
    * overlay panels so that they can request a redraw by posting a 
    * DrawEvent to the bus.
    */
   
   public void setBus(EventBus bus) {};
   /**
    * Draw the overlay using the provided Graphics object. This is called
    * immediately after the canvas has been drawn.
    */
   public abstract void drawOverlay(Graphics g, DisplayWindow display,
         Image image, ImageCanvas canvas);
}
