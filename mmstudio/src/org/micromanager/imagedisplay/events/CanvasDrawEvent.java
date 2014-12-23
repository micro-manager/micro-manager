package org.micromanager.imagedisplay.events;

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
