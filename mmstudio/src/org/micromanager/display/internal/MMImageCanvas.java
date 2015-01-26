package org.micromanager.display.internal;

import com.google.common.eventbus.EventBus;
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
import java.awt.Rectangle;

import java.lang.Math;

import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.internal.events.CanvasDrawEvent;
import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
import org.micromanager.display.internal.events.LayoutChangedEvent;
import org.micromanager.display.internal.events.MouseMovedEvent;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * MMImageCanvas is a customization of ImageJ's ImageCanvas class with
 * specialized drawing logic and some other minor customizations.
 */
class MMImageCanvas extends ImageCanvas {
   ImagePlus ijImage_;
   DisplayWindow display_;
   
   public MMImageCanvas(ImagePlus ijImage, DisplayWindow display) {
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
      int newWidth = (int) Math.ceil(viewWidth / curMag);
      int newHeight = (int) Math.ceil(viewHeight / curMag);
      int xCorner = xCenter - newWidth / 2;
      int yCorner = yCenter - newHeight / 2;
      Rectangle viewRect = new Rectangle(xCorner, yCorner,
            newWidth, newHeight);
      setSourceRect(viewRect);
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

      display_.postEvent(new CanvasDrawEvent(g, this));
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
      display_.postEvent(new MouseMovedEvent(pixelX, pixelY));
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
}
