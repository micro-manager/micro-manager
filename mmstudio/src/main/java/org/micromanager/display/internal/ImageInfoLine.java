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
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.display.PixelsSetEvent;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;
import org.micromanager.display.internal.events.MouseExitedEvent;
import org.micromanager.display.internal.events.MouseMovedEvent;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class displays some basic information about the image(s) being
 * displayed in the DisplayWindow: dimension, bit depth, and pixel intensity
 * data.
 */
public final class ImageInfoLine extends JPanel {
   private DefaultDisplayWindow display_;

   private JLabel imageInfo_;
   private JLabel pixelInfo_;
   // Last known location of the mouse on the image canvas.
   private int mouseX_ = -1;
   private int mouseY_ = -1;

   public ImageInfoLine(DefaultDisplayWindow display) {
      super(new MigLayout("flowx, fillx, gap 0, insets 0"));
      display_ = display;

      // This font matches the one used by HyperstackControls.
      Font font = new Font("Lucida Grande", Font.PLAIN, 10);
      imageInfo_ = new JLabel("Image info");
      imageInfo_.setFont(font);
      add(imageInfo_);
      pixelInfo_ = new JLabel("");
      pixelInfo_.setFont(font);
      add(pixelInfo_, "gapleft push");
      display.registerForEvents(this);
   }

   @Subscribe
   public void onPixelsSet(PixelsSetEvent event) {
      Image image = event.getImage();
      Metadata metadata = image.getMetadata();
      String text = "";
      Double pixelSize = metadata.getPixelSizeUm();
      int width = image.getWidth();
      int height = image.getHeight();
      if (pixelSize != null && pixelSize != 0) {
         // TODO: assuming square pixels.
         text += String.format("%.2fx%.2f\u00b5m   ",
               pixelSize * width, pixelSize * height);
      }
      text += String.format("%dx%dpx   ", width, height);
      Integer depth = metadata.getBitDepth();
      if (depth != null) {
         text += depth + "-bit   ";
      }
      text += (image.getBytesPerPixel() * width * height / 1024) + "K";
      imageInfo_.setText(text);
   }

   /**
    * User moused over the display; update our indication of pixel intensities.
    **/
   @Subscribe
   public void onMouseMoved(MouseMovedEvent event) {
      try {
         mouseX_ = event.getX();
         mouseY_ = event.getY();
         setPixelInfo(mouseX_, mouseY_);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to get image pixel info");
      }
   }

   /**
    * User's mouse left the display; stop showing pixel intensities.
    */
   @Subscribe
   public void onMouseExited(MouseExitedEvent event) {
      mouseX_ = -1;
      mouseY_ = -1;
      pixelInfo_.setVisible(false);
   }

   /**
    * The displayed image has changed, so update our pixel info display. We
    * also need to track the display FPS (the rate at which images are
    * displayed).
    */
   @Subscribe
   public void onCanvasDrawComplete(CanvasDrawCompleteEvent event) {
      Image image = display_.getDisplayedImages().get(0);
      if (mouseX_ >= 0 && mouseX_ < image.getWidth() &&
            mouseY_ >= 0 && mouseY_ < image.getHeight()) {
         setPixelInfo(mouseX_, mouseY_);
      }
   }

   /**
    * Update our pixel info text.
    */
   private void setPixelInfo(int x, int y) {
      if (x >= 0 && y >= 0) {
         String intensity = getIntensityString(x, y);
         pixelInfo_.setText(String.format("x=%dpx   y=%dpx   value=%s",
                  x, y, intensity));
         pixelInfo_.setVisible(true);
      }
      // If the pixel info display grows (e.g. due to extra digits in the
      // intensity display) then we don't want to let it shrink again, or else
      // the FPS display to its right will get bounced back and forth.
      pixelInfo_.setMinimumSize(pixelInfo_.getSize());
      // This validate call reduces the chance that the text will be truncated.
      validate();
   }

   public String getIntensityString(int x, int y) {
      Datastore store = display_.getDatastore();
      MMVirtualStack stack = display_.getStack();
      int numChannels = store.getAxisLength("channel");
      if (numChannels > 1) {
         // Multi-channel case: display each channel with a "/" in-between.
         String intensity = "(";
         for (int i = 0; i < numChannels; ++i) {
            Coords imageCoords = stack.getCurrentImageCoords().copy().channel(i).build();
            // We may not have an image yet for these coords (e.g. for the
            // second channel of a multi-channel acquisition).
            if (store.hasImage(imageCoords)) {
               Image image = store.getImage(imageCoords);
               intensity += image.getIntensityStringAt(x, y);
            }
            if (i != numChannels - 1) {
               intensity += ", ";
            }
         }
         intensity += ")";
         return intensity;
      }
      else {
         // Single-channel case; simple.
         Coords coords = stack.getCurrentImageCoords();
         if (store.hasImage(coords)) {
            Image image = store.getImage(coords);
            try {
               return image.getIntensityStringAt(x, y);
            }
            catch (IllegalArgumentException e) {
               // Our x/y values were out-of-bounds; this should never happen.
               ReportingUtils.logError("Invalid pixel coordinates " + x + ", " + y);
            }
         }
      }
      return "";
   }
}
