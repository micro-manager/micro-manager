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

package org.micromanager.display.internal;

import com.google.common.eventbus.Subscribe;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageCanvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;
import org.micromanager.display.internal.events.CanvasDrawEvent;
import org.micromanager.display.internal.events.HistogramRecalcEvent;
import org.micromanager.display.internal.events.LayoutChangedEvent;
import org.micromanager.display.internal.events.MouseExitedEvent;
import org.micromanager.display.internal.events.MouseMovedEvent;

/**
 * MMImageCanvas is a customization of ImageJ's ImageCanvas class with
 * specialized drawing logic and some other minor customizations.
 */
public final class MMImageCanvas extends ImageCanvas {
   private final ImagePlus ijImage_;
   private DefaultDisplayWindow display_;

   public MMImageCanvas(ImagePlus ijImage, DefaultDisplayWindow display) {
      super(ijImage);
      ijImage_ = ijImage;
      display_ = display;
      display_.registerForEvents(this);
      // Publish information on the mouse position.
      addMouseMotionListener(new MouseAdapter() {
         @Override
         public void mouseMoved(MouseEvent event) {
            // Post a MouseMovedEvent indicating the coordinates of the pixel
            // underneath the mouse. This requires us to account for the zoom
            // level and how much of the canvas is actually in-view (which may
            // apply an offset into the image coordinates).
            int x = event.getX();
            int y = event.getY();
            x /= magnification;
            y /= magnification;
            x += getSrcRect().x;
            y += getSrcRect().y;
            display_.postEvent(new MouseMovedEvent(x, y));
         }
      });
      // Listen for the mouse leaving the canvas (oddly, not covered under
      // addMouseMotionListener) and for mouse click events that can signal
      // changes in the ROI.
      addMouseListener(new MouseAdapter() {
         @Override
         public void mouseExited(MouseEvent event) {
            // Inform clients that the mouse has left the canvas.
            display_.postEvent(new MouseExitedEvent());
         }

         @Override
         public void mouseReleased(MouseEvent event) {
            display_.postEvent(new HistogramRecalcEvent(null));
         }
      });
   }

   /**
    * The container that holds us wants us to resize to fill the specified
    * size, keeping in mind the provided aspect ratio of our data, and the
    * limitations we place on our zoom factor.
    */
   public void updateSize(Dimension size) {
      double dataAspect = ((double) ijImage_.getWidth()) / ijImage_.getHeight();
      double viewAspect = ((double) size.width) / size.height;
      // Derive canvas view width/height based on maximum available space
      // along the appropriate axis, for an aspect-ratio-constrained resize.
      int viewWidth = size.width;
      int viewHeight = size.height;
      if (viewAspect > dataAspect) { // Wide view; Y constrains growth
         viewWidth = (int) (viewHeight * dataAspect);
      }
      else { // Tall view; X constrains growth
         viewHeight = (int) (viewWidth / dataAspect);
      }
      // Check the maximum size we allow ourselves to have at our current
      // zoom level. If we exceed this size, then our pixels get fuzzy and
      // irregular, which is no good.
      double maxWidth = ijImage_.getWidth() * getMagnification();
      double maxHeight = ijImage_.getHeight() * getMagnification();
      int targetWidth = (int) Math.ceil(Math.min(maxWidth, viewWidth));
      int targetHeight = (int) Math.ceil(Math.min(maxHeight, viewHeight));
      setDrawingSize(targetWidth, targetHeight);
      // Reset the "source rect", i.e. the sub-area being viewed when
      // the image won't fit into the window. Try to maintain the same
      // center as the current rect has.
      // Fun fact: there's setSourceRect and setSrcRect, but no
      // getSourceRect.
      Rectangle curRect = getSrcRect();
      int xCenter = curRect.x + (curRect.width / 2);
      int yCenter = curRect.y + (curRect.height / 2);
      double curMag = getMagnification();
      int newWidth = Math.min(ijImage_.getWidth(),
            (int) Math.ceil(viewWidth / curMag));
      int newHeight = Math.min(ijImage_.getHeight(),
            (int) Math.ceil(viewHeight / curMag));
      int xCorner = Math.max(0, xCenter - newWidth / 2);
      int yCorner = Math.max(0, yCenter - newHeight / 2);
      Rectangle viewRect = new Rectangle(xCorner, yCorner,
            newWidth, newHeight);
      setSourceRect(viewRect);
   }

   /**
    * Draw the canvas, with our own embellishments.
    * In addition to drawing the image canvas, we also:
    * - draw a border around it, using a color that indicates the current
    *   active channel
    * - post a CanvasDrawEvent so that other objects may add their own
    *   embellishments
    */
   @Override
   public void paint(Graphics g) {
      paintToGraphics(g);
   }

   /**
    * Paint to the provided Graphics object. This allows outside code to get
    * a copy of the "image as rendered".
    */
   public void paintToGraphics(Graphics g) {
      // Draw the actual canvas image
      super.paint(g);

      // Determine the color to use for the border (default is black).
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

      // This rectangle is relative to the panel we're in, but we're drawing
      // relative to ourselves, so its corner needs to be reset.  Also, shrink
      // it slightly -- if we draw the bounds directly then we end up drawing
      // exactly out of bounds and the border ends up invisible.
      Rectangle rect = getBounds();
      rect.x = 1;
      rect.y = 1;
      rect.width -= 2;
      rect.height -= 2;
      if (!Prefs.noBorder && !IJ.isLinux()) {
         g.drawRect(rect.x - 1, rect.y - 1,
               rect.width + 1, rect.height + 1);
      }

      display_.postEvent(new CanvasDrawEvent(g, this));
      display_.postEvent(new CanvasDrawCompleteEvent(g));
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
      // Propagate change to the display.
      DisplaySettings settings = display_.getDisplaySettings().copy()
         .magnification(this.magnification)
         .build();
      display_.setDisplaySettings(settings);
      display_.postEvent(new LayoutChangedEvent());
   }

   /**
    * Zoom the canvas in or out, accounting for the specified change in
    * magnification.
    */
   public void zoomCanvas(double mag) {
      if (mag == this.magnification) {
         return;
      }
      Rectangle bounds = getBounds();
      if (mag < this.magnification) {
         zoomOut((int) (bounds.width * mag / magnification),
               (int) (bounds.height * mag / magnification));
      }
      else {
         zoomIn((int) (bounds.width * magnification / mag),
               (int) (bounds.height * magnification / mag));
      }
      display_.requestRedraw();
   }

   /**
    * Our magnification may change when the display settings do
    */
   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      Double newMag = event.getDisplaySettings().getMagnification();
      if (newMag != null) {
         this.magnification = newMag;
      }
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      display_.unregisterForEvents(this);
   }

   @Override
   public Dimension getMaximumSize() {
      return new Dimension((int) (ijImage_.getWidth() * this.magnification),
            (int) (ijImage_.getHeight() * this.magnification));
   }

   @Override
   public Dimension getMinimumSize() {
      return new Dimension(16, 16);
   }
}
