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

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.awt.Point;

public class RGBColorModel extends ComponentColorModel {
   private int[] mins_ = new int[] {0, 0, 0};
   private int[] maxes_ = new int[] {0, 255, 255};

   public RGBColorModel() {
      super(new ICC_ColorSpace(ICC_Profile.getInstance(ColorSpace.CS_sRGB)),
            false, false, java.awt.Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
   }

   @Override
   public int getAlpha(int pixel) {
      return 255;
   }

   @Override
   public int getRed(int pixel) {
      return 255;
   }

   @Override
   public int getGreen(int pixel) {
      return 128;
   }

   @Override
   public int getBlue(int pixel) {
      return 0;
   }
}
