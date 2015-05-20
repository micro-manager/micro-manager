///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
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

package org.micromanager.display.internal.events;

import ij.gui.ImageCanvas;

import java.awt.Graphics;


/**
 * This class is used to signify when the canvas is performing a paint event.
 */
public class CanvasDrawEvent {
   private Graphics graphics_;
   private ImageCanvas canvas_;

   public CanvasDrawEvent(Graphics graphics, ImageCanvas canvas) {
      graphics_ = graphics;
      canvas_ = canvas;
   }

   public Graphics getGraphics() {
      return graphics_;
   }

   public ImageCanvas getCanvas() {
      return canvas_;
   }
}
