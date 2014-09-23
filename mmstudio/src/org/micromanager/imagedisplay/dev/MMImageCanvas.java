package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;

import ij.gui.ImageCanvas;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

import java.lang.Math;

import org.micromanager.imagedisplay.CanvasDrawEvent;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.MMImagePlus;

/**
 * MMImageCanvas is a customization of ImageJ's ImageCanvas class with
 * specialized drawing logic and some other minor customizations.
 */
class MMImageCanvas extends ImageCanvas {
   ImagePlus ijImage_;
   MMImagePlus plus_;
   EventBus displayBus_;
   
   public MMImageCanvas(ImagePlus ijImage, MMImagePlus plus,
         EventBus displayBus) {
      super(plus);
      ijImage_ = ijImage;
      plus_ = plus;
      displayBus_ = displayBus;
   }

   @Override
   public void paint(Graphics g) {
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
      Rectangle rect = getBounds();
      // Tighten the rect down to what the canvas is actually drawing, as
      // opposed to the space it is taking up in the window as a 
      // Component.
      int drawnWidth = (int) (plus_.getWidth() * getMagnification());
      int drawnHeight = (int) (plus_.getHeight() * getMagnification());
      int widthSlop = rect.width - drawnWidth;
      int heightSlop = rect.height - drawnHeight;
      rect.x += widthSlop / 2;
      rect.y += heightSlop / 2;
      rect.width = drawnWidth;
      rect.height = drawnHeight;
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
}
