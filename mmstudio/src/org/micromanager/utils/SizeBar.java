///////////////////////////////////////////////////////////////////////////////
//FILE:          SizeBar.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, Fabruary 12, 2011
//
// COPYRIGHT:    University of California, San Francisco, 2011
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
package org.micromanager.utils;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;

import java.awt.Font;
import java.text.NumberFormat;

/**
 * This class provides facilities for creating a scale bar)
 * It could also be used from outside Micro-Manager (i.e, as an ImageJ facility)
 *
 * @author Nico Stuurman
 * 
 */
public class SizeBar {

   public enum Position {
      TOPLEFT, TOPRIGHT, BOTTOMLEFT, BOTTOMRIGHT
   };

   private Position pos_ = Position.TOPLEFT;
   private final int OFFSET = 10;
   private ImagePlus ip_;
   private Font font_ = new Font("Helvetics", Font.PLAIN, 12);
   private double pixelSizeUm_;
   private final double MULTIPLIER = 3.3333333333333;
   private int exponent_ = 0;
   private int barWidth_, barHeight_;
   private String value_;
   private String units_ = "\u00B5" + "m";

   public SizeBar(ImagePlus ip, double pixelSizeUm) {
      ip_ = ip;
      pixelSizeUm_ = pixelSizeUm;

      if (pixelSizeUm > 0) {
         int width = ip_.getWidth();
         int height = ip_.getHeight();
         barHeight_ = (int) (height / 75);
         int minBarWidth = width / 20;
         int maxBarWidth = width / 4;
         double pixsPerUm = 1.0 / pixelSizeUm;

         double pixs = pixsPerUm;
         while (pixs < minBarWidth) {
            pixs *= MULTIPLIER;
            exponent_++;
         }
         while (pixs > maxBarWidth) {
            pixs /= MULTIPLIER;
            exponent_--;
         }
         double value = Math.pow(MULTIPLIER, exponent_);

         if (exponent_ >= 6) {
            units_ = "mm";
            value = value / 1000000;
         }
         if (exponent_ <= -6) {
            units_ = "nm";
            value = value * 1000000;
         }

         NumberFormat format = NumberFormat.getInstance();
         format.setMaximumFractionDigits(1);
         if (value > 10)
            format.setMaximumFractionDigits(0);

         value_ = format.format(value);

         barWidth_ = (int) pixs;
      }
   }

   public void setPosition(Position pos) {
      pos_ = pos;
   }

   public void setFont (Font font) {
      font_ = font;
   }

   public void addToOverlay(Overlay ol) {
      font_ = font_.deriveFont((int) (0.015 * ip_.getWidth()));
      TextRoi text = new TextRoi(10, 10, value_ + units_, font_);
      int textWidth = ip_.getProcessor().getStringWidth(value_ + units_);
      if (pos_ == Position.TOPLEFT) {
         text.setLocation((int) (OFFSET + (0.5 * barWidth_ ) - (0.5 * textWidth)),
                 OFFSET - barHeight_ - 2);
      } else if (pos_ == Position.TOPRIGHT) {
         text.setLocation((int) (ip_.getWidth() - OFFSET - (0.5 * barWidth_) - (0.5 * textWidth)),
                 OFFSET - barHeight_ - 2);
      } else if (pos_ == Position.BOTTOMLEFT) {
         text.setLocation((int) (OFFSET + (0.5 * barWidth_ ) - (0.5 * textWidth)),
                 ip_.getHeight() - OFFSET - barHeight_ - 2);
      } else if (pos_ == Position.BOTTOMRIGHT) {
         text.setLocation( (int) (ip_.getWidth() - OFFSET - (0.5 * barWidth_) - (0.5 * textWidth)),
                 ip_.getHeight() - OFFSET - barHeight_ - 2);
      }

      ol.addElement(text);


      Roi bar = new Roi(OFFSET, OFFSET + barHeight_, barWidth_, barHeight_);
      if (pos_ == Position.TOPRIGHT) {
         bar.setLocation(ip_.getWidth() - OFFSET - barWidth_, OFFSET + barHeight_);
      } else if (pos_ == Position.BOTTOMLEFT) {
         bar.setLocation(OFFSET, ip_.getHeight() - OFFSET - barHeight_);
      } else if (pos_ == Position.BOTTOMRIGHT) {
         bar.setLocation(ip_.getWidth() - OFFSET - barWidth_, ip_.getHeight() - OFFSET - barHeight_);
      }
      ol.addElement(bar);
   }

}

