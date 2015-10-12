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
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.awt.Point;

/**
 * This class is used to give us control over how the individual components
 * in an RGB image are displayed.
 */
public class RGBColorModel extends ComponentColorModel {
   private int[] mins_ = new int[] {0, 0, 0};
   private int[] maxes_ = new int[] {0, 255, 255};

   public RGBColorModel() {
      super(ColorSpace.getInstance(ColorSpace.CS_sRGB),
            false, false, java.awt.Transparency.OPAQUE, DataBuffer.TYPE_INT);
   }

   @Override
   public int getAlpha(int pixel) {
      return 255;
   }

   @Override
   public int getRed(int pixel) {
      return 0;
   }

   @Override
   public int getGreen(int pixel) {
      return 255;
   }

   @Override
   public int getBlue(int pixel) {
      return 0;
   }

   /**
    * ComponentColorModel only expects to work with byte- and short-based
    * image packings, but ImageJ uses packed int[] arrays instead, so we need
    * to adapt the "compatible"-related methods to work with int[]s instead.
    */
   @Override
   public WritableRaster createCompatibleWritableRaster(int width, int height) {
      return Raster.createPackedRaster(DataBuffer.TYPE_INT,
            width, height, 3, 8, null);
   }

   /**
    * For the same reason that we have to override
    * createCompatibleWritableRaster, we have to override this method.
    */
   @Override
   public SampleModel createCompatibleSampleModel(int width, int height) {
      return new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT,
            width, height, new int[] {0xff000000, 0xff0000, 0xff00});
   }

   /**
    * For the same reason that we have to override
    * createCompatibleWritableRaster, we have to override this method.
    */
   @Override
   public boolean isCompatibleRaster(Raster raster) {
      return isCompatibleSampleModel(raster.getSampleModel());
   }

   /**
    * For the same reason that we have to override
    * createCompatibleWritableRaster, we have to override this method.
    */
   @Override
   public boolean isCompatibleSampleModel(SampleModel model) {
      return (model instanceof SinglePixelPackedSampleModel &&
            model.getNumBands() == getNumComponents() &&
            model.getTransferType() == getTransferType());
   }
}
