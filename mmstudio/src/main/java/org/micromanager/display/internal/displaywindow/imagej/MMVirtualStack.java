// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.display.internal.displaywindow.imagej;

import ij.ImageStack;
import ij.VirtualStack;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.awt.image.ColorModel;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImageJConverter;

/**
 * Proxy for ImageJ's {@code VirtualStack}.
 *
 * Backs an ImageJ stack with Micro-Manager images provided by the "parent"
 * (ImageJBridge in this case).
 * Main logic concerns itself with translating bewteen ImageJ coordinates
 * (i.e. flatIndex into the ImageJ stack, ImageJ c,z,t coordinates) and
 * Micro-Manager Coords
 *
 *
 * @author Mark A. Tsuchida, based on older version by Chris Weisiger
 */
public final class MMVirtualStack extends VirtualStack {
   private final ImageJBridge parent_;

   private boolean pretendToHaveOnlyOneImage_ = false;

   private Rectangle roi_; // Replace the role of ij.ImageStack's 'roi'

   // TODO XXX Issue warning alerts for 'set' actions that are ignored

   static MMVirtualStack create(ImageJBridge parent) {
      return new MMVirtualStack(parent);
   }

   private MMVirtualStack(ImageJBridge parent) {
      parent_ = parent;
   }

   void setSingleImageMode(boolean enable) {
      pretendToHaveOnlyOneImage_ = enable;
   }

   @Override
   public void addSlice(String label) {
      // Not supported
   }

   @Override
   public void addSlice(String label, Object pixels) {
      // Not supported
   }

   @Override
   public void addSlice(ImageProcessor ip) {
      // Not supported
   }

   @Override
   public void addSlice(String label, ImageProcessor ip) {
      // Not supported
   }

   @Override
   public void addSlice(String label, ImageProcessor ip, int n) {
      // Not supported
   }

   @Override
   public void deleteSlice(int flatIndex) {
      // Not supported
   }

   @Override
   public void deleteLastSlice() {
      // Not supported
   }

   @Override
   public int getWidth() {
      return parent_.getMMWidth();
   }

   @Override
   public int getHeight() {
      return parent_.getMMHeight();
   }

   @Override
   public void setRoi(Rectangle roi) {
      roi_ = roi;
   }

   @Override
   public Rectangle getRoi() {
      if (roi_ == null) {
         return new Rectangle(0, 0, getWidth(), getHeight());
      }
      return roi_;
   }

   @Override
   public void update(ImageProcessor ip) {
      // Ignore
   }

   @Override
   public Object getPixels(int flatIndex) {
      Coords coords = parent_.getMMCoordsForIJFlatIndex(flatIndex);
      Image image = parent_.getMMImage(coords);
      return image.getRawPixelsCopy();
   }

   @Override
   public void setPixels(Object pixels, int n) {
      // Not supported
   }

   @Override
   public ImageProcessor getProcessor(int flatIndex) {
      Coords coords = parent_.getMMCoordsForIJFlatIndex(flatIndex);
      Image image = parent_.getMMImage(coords);
      return DefaultImageJConverter.createProcessor(image, true);
   }

   @Override
   public void setProcessor(ImageProcessor ip, int n) {
      // Not supported
   }

   @Override
   public int saveChanges(int n) {
      return -1; // Same as ij.VirtualStack.
   }

   @Override
   public int getSize() {
      if (pretendToHaveOnlyOneImage_) {
         return 1;
      }
      return parent_.getMMNumberOfTimePoints() *
            parent_.getMMNumberOfZSlices() *
            parent_.getMMNumberOfChannels();
   }

   @Override
   public String getSliceLabel(int n) {
      return Integer.toString(n);
   }

   @Override
   public String[] getSliceLabels() {
      int nSlices = getSize();
      if (nSlices == 0) {
         return null;
      }
      String[] ret = new String[nSlices];
      for (int i = 0; i < nSlices; ++i) {
         ret[i] = getSliceLabel(i);
      }
      return ret;
   }

   @Override
   public String getShortSliceLabel(int n) {
      return getSliceLabel(n);
   }

   @Override
   public void setSliceLabel(String label, int n) {
      // Not supported
   }

   @Override
   public Object[] getImageArray() {
      return null; // Same as ij.VirtualStack
   }

   @Override
   public ColorModel getColorModel() {
      // TODO
      return null;
   }

   @Override
   public void setColorModel(ColorModel cm) {
      // Ignore
   }

   @Override
   public boolean isRGB() {
      // TODO
      return false;
   }

   @Override
   public boolean isHSB() {
      return false;
   }

   @Override
   public boolean isLab() {
      return false;
   }

   @Override
   public boolean isVirtual() {
      return true;
   }

   @Override
   public void trim() {
      // Not supported
   }

   @Override
   public String toString() {
      // Use "(MM)" instead of the standard "(V)"
      return String.format("stack[%dx%dx%d(MM)]", getWidth(), getHeight(),
            getSize());
   }

   // @Override
   // public double getVoxel(int x, int y, int z);
   // Cannot override because final (TODO: Use Javassist)

   // @Override
   // public void setVoxel(int x, int y, int z, double value);
   // Cannot override because final (TODO: Use Javassist)

   @Override
   public float[] getVoxels(int x, int y, int z, int w, int h, int d, float[] voxels) {
      throw new UnsupportedOperationException();
   }

   @Override
   public float[] getVoxels(int x, int y, int z, int w, int h, int d, float[] voxels, int channel) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void setVoxels(int x, int y, int z, int w, int h, int d, float[] voxels) {
      // Not supported
   }

   @Override
   public void setVoxels(int x, int y, int z, int w, int h, int d, float[] voxels, int channel) {
      // Not supported
   }

   @Override
   public void drawSphere(double radius, int x, int y, int z) {
      // Not supported
   }

   @Override
   public int getBitDepth() {
      // TODO 8/16/24/32 for BYTE/SHORT/RGB/FLOAT, or 0 if unknown
      return 0;
   }

   @Override
   public void setBitDepth(int bitDepth) {
      // Not supported
   }

   @Override
   public ImageStack duplicate() {
      return crop(0, 0, 0, getWidth(), getHeight(), getSize());
   }

   @Override
   public ImageStack crop(int x, int y, int z, int w, int h, int d) {
      if (x < 0 || y < 0 || z < 0 ||
            w > getWidth() || h > getWidth() || d > getSize())
      {
         throw new IllegalArgumentException("Crop region is out of range");
      }

      ImageStack ret = new ImageStack(w, h, getColorModel());
      for (int slice = z; slice < z + d; ++slice) {
         ImageProcessor ip = getProcessor(slice + 1);
         ip.setRoi(x, y, w, h);
         ret.addSlice(getSliceLabel(slice + 1), ip.crop());
      }
      return ret;
   }

   @Override
   public ImageStack convertToFloat() {
      ImageStack ret = new ImageStack(getWidth(), getHeight(), getColorModel());
      int nSlices = getSize();
      for (int slice = 1; slice <= nSlices; ++slice) {
         ImageProcessor ip = getProcessor(slice).convertToFloat();
         ret.addSlice(getSliceLabel(slice), ip);
      }
      return ret;
   }

   @Override
   public String getDirectory() {
      return null;
   }

   @Override
   public String getFileName(int n) {
      return null;
   }

   @Override
   public ImageStack sortDicom(String[] s, String[] i, int m) {
      return this; // Not that I expect this method to be called...
   }
}