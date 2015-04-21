///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id$
//
package org.micromanager.internal.utils;

import java.awt.Component;
import java.awt.geom.AffineTransform;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.Icon;

/**
 * This class provides high-resolution icons for systems that have higher
 * pixel densities (e.g. Macbooks with Retina displays).
 */
public class HighResIcon implements Icon {
   private BufferedImage baseImage_;
   private double scale_;

   /**
    * @param url URL of the image file to use.
    * @param scale Scaling factor to apply to the image before drawing.
    */
   public HighResIcon(URL url, double scale) {
      try {
         baseImage_ = ImageIO.read(url);
      }
      catch (java.io.IOException e) {
         ReportingUtils.logError(e, "Unable to load image file at " + url);
      }
      scale_ = scale;
   }

   /**
    * Hacky version that assumes a scaling factor of 1 if the image is small
    * or .25 if it is large.
    * @param url URL of the image file to use.
    */
   public HighResIcon(URL url) {
      try {
         baseImage_ = ImageIO.read(url);
      }
      catch (java.io.IOException e) {
         ReportingUtils.logError(e, "Unable to load image file at " + url);
      }
      if (Math.max(baseImage_.getWidth(), baseImage_.getHeight()) < 20) {
         scale_ = 1.0;
      }
      else {
         scale_ = .25;
      }
   }

   @Override
   public int getIconWidth() {
      return (int) (baseImage_.getWidth() * scale_);
   }

   @Override
   public int getIconHeight() {
      return (int) (baseImage_.getHeight() * scale_);
   }

   @Override
   public void paintIcon(Component c, Graphics g, int x, int y) {
      AffineTransform tmp = new AffineTransform();
      tmp.translate(x, y);
      tmp.scale(scale_, scale_);
      ((Graphics2D) g).drawImage(baseImage_, tmp, c);
   }
}
