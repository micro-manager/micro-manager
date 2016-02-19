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

import javax.swing.JLabel;

import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.PixelsSetEvent;

/**
 * This class displays some basic information about the image(s) being
 * displayed in the DisplayWindow.
 */
public class ImageInfoLine extends JLabel {
   public ImageInfoLine(DisplayWindow display) {
      super("Image info");
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
      if (pixelSize != null) {
         // TODO: assuming square pixels.
         text += String.format("%.2fx%.2f \u00b5m ",
               pixelSize * width, pixelSize * height);
      }
      text += String.format("(%dx%d px), ", width, height);
      Integer depth = metadata.getBitDepth();
      if (depth != null) {
         text += depth + "-bit, ";
      }
      text += (image.getBytesPerPixel() * width * height / 1024) + "K";
      setText(text);
   }
}
