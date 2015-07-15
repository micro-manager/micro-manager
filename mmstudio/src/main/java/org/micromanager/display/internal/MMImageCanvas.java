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

import ij.gui.ImageCanvas;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;


import org.micromanager.display.DisplaySettings;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.internal.events.CanvasDrawEvent;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;
import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
import org.micromanager.display.internal.events.LayoutChangedEvent;
import org.micromanager.display.internal.events.MouseMovedEvent;

/**
 * MMImageCanvas is a customization of ImageJ's ImageCanvas class with
 * specialized drawing logic and some other minor customizations.
 */
class MMImageCanvas extends ImageCanvas {
   private ImagePlus ijImage_;
   private DefaultDisplayWindow display_;
   private BufferedImage bufferedImage_;

   public MMImageCanvas(ImagePlus ijImage, DefaultDisplayWindow display) {
      super(ijImage);
      ijImage_ = ijImage;
      display_ = display;
      display_.registerForEvents(this);
      // Publish information on the mouse position.
      addMouseMotionListener(new MouseAdapter() {
         @Override
         public void mouseMoved(MouseEvent event) {
            publishMouseInfo(event.getX(), event.getY());
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
    * - render to a BufferedImage (and then render from that to our actual
    *   normal image), so that a copy of the drawn image (with overlays, etc.)
    *   is available when we post a CanvasDrawCompleteEvent. Effectively we're
    *   doing manual double-buffering.
    * TODO: ideally we'd replace the manual double-buffering with an API
    * method that hands the DisplayWindow a Graphics object to draw to.
    */
   @Override
   public void paint(Graphics g) {
      if (bufferedImage_ == null || bufferedImage_.getWidth() != getWidth() ||
            bufferedImage_.getHeight() != getHeight()) {
         // Dimensions have changed; must recreate the BufferedImage.
         bufferedImage_ = new BufferedImage(getWidth(), getHeight(),
               BufferedImage.TYPE_INT_RGB);
      }
      Graphics bufG = bufferedImage_.createGraphics();
      // Draw the actual canvas image
      super.paint(bufG);

      // Determine the color to use for the border (default is black).
      if (ijImage_.isComposite()) {
         Color color = ((MMCompositeImage) ijImage_).getChannelColor();
         // Re-implement the same hack that ImageWindow uses in its
         // paint() method...
         if (Color.green.equals(color)) {
            color = new Color(0, 180, 0);
         }
         bufG.setColor(color);
      }
      else {
         bufG.setColor(Color.BLACK);
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
         bufG.drawRect(rect.x - 1, rect.y - 1,
               rect.width + 1, rect.height + 1);
      }

      display_.postEvent(new CanvasDrawEvent(bufG, this));
      // Drawing to the buffered image is done; now draw to ourselves.
      bufG.dispose();
      g.drawImage(bufferedImage_, 0, 0, null);
      display_.postEvent(new CanvasDrawCompleteEvent(bufferedImage_));
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
      display_.postEvent(new DefaultRequestToDrawEvent());
   }

   /**
    * Post a MouseMovedEvent indicating the coordinates of the pixel underneath
    * the mouse. This requires us to account for the zoom level and how much
    * of the canvas is actually in-view (which may apply an offset into the
    * image coordinates).
    */
   private void publishMouseInfo(int x, int y) {
      x /= magnification;
      y /= magnification;
      x += getSrcRect().x;
      y += getSrcRect().y;
      display_.postEvent(new MouseMovedEvent(x, y));
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

   /**
    * Our preferred size is dictated for us by our window, because it depends
    * on how much available space there is.
    */
   @Override
   public Dimension getPreferredSize() {
      Dimension maxSize = display_.getMaxCanvasSize();
      updateSize(maxSize);
      return getSize();
   }
}
