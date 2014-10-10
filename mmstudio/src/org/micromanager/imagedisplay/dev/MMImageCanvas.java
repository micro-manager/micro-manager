package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;

import ij.gui.ImageCanvas;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Graphics;
import java.awt.Rectangle;

import java.lang.Math;

import org.micromanager.imagedisplay.CanvasDrawEvent;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.MouseMovedEvent;
import org.micromanager.utils.ReportingUtils;

/**
 * MMImageCanvas is a customization of ImageJ's ImageCanvas class with
 * specialized drawing logic and some other minor customizations.
 */
class MMImageCanvas extends ImageCanvas {
   ImagePlus ijImage_;
   EventBus displayBus_;
   
   public MMImageCanvas(ImagePlus ijImage, EventBus displayBus) {
      super(ijImage);
      ijImage_ = ijImage;
      displayBus_ = displayBus;
      // Publish information on the mouse position.
      addMouseMotionListener(new MouseAdapter() {
         @Override
         public void mouseMoved(MouseEvent event) {
            publishMouseInfo(event.getX(), event.getY());
         }
      });
   }

   /**
    * In addition to drawing the image canvas, we also draw a border around it,
    * using a color that indicates the current active channel.
    */
   @Override
   public void paint(Graphics g) {
      // Draw the actual canvas image
      super.paint(g);

      // Determine the color to use (default is black).
      if (ijImage_.isComposite()) {
         Color color = ((MMCompositeImage) ijImage_).getChannelColor();
         // Re-implement the same hack that ImageWindow uses in its
         // paint() method...
         if (Color.green.equals(color)) {
            color = new Color(0, 180, 0);
         }
         g.setColor(color);
      }
      else {
         g.setColor(Color.BLACK);
      }

      Rectangle rect = getBounds();
      // Shrink it slightly -- if we draw the bounds directly then we end up
      // drawing exactly out of bounds and the border ends up invisible.
      rect.x += 1;
      rect.y += 1;
      rect.width -= 2;
      rect.height -= 2;
      // Not sure why we need to do this exactly, except that if we don't
      // the rectangle draws in the wrong place on narrow windows.
      rect.y -= getBounds().y;
      if (!Prefs.noBorder && !IJ.isLinux()) {
         g.drawRect(rect.x - 1, rect.y - 1,
               rect.width + 1, rect.height + 1);
      }

      displayBus_.post(new CanvasDrawEvent(g, this));
   }

   /**
    * By default, an ImageJ ImageCanvas resets the window title whenever the
    * zoom changes. We don't like that, so we disable that functionality here.
    */
   @Override
   public void setMagnification(double mag) {
      // Apply the same range clamping that ImageJ does.
      mag = Math.max(Math.min(32.0, mag), .03125);
      this.magnification = mag;
   }

   /**
    * Post a MouseMovedEvent indicating the coordinates of the pixel underneath
    * the mouse. This is made unpleasant by the fact that we have two methods
    * of zooming the display available to us: first by using the zoom tool,
    * and second by simply resizing the window the canvas is in. The canvas'
    * "magnification" field only accounts for the first of these.
    */
   private void publishMouseInfo(int x, int y) {
      // Derive an effective zoom level by comparing the size we take up in
      // the window to the size of the image we are displaying.
      int pixelWidth = ijImage_.getWidth();
      int displayedWidth = getSize().width;
      double effectiveZoom = ((double) displayedWidth) / pixelWidth;
      int pixelX = (int) (x / magnification / effectiveZoom);
      int pixelY = (int) (y / magnification / effectiveZoom);
      displayBus_.post(new MouseMovedEvent(pixelX, pixelY));
   }
}
