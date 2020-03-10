///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//
package org.micromanager.magellan.internal.demo;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import java.awt.Frame;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import org.micromanager.magellan.internal.datasaving.MultiResMultipageTiffStorage;

/**
 *
 * @author Henry
 */
public class DemoModeImageData {

   private static ImagePlus img_;
   private static int pixelSizeZ_ = 3;
   private static int imageSizeZ_ = 399;
   private static int numChannels_ = 6;
   
   private static Random rand_ = new Random();
   
   private static DemoModeImageData singleton_;

   public DemoModeImageData() {
      singleton_ = this;
      //make covariance matrix
      
      
      
   }
   
   public static int getNumChannels() {
      return numChannels_;
      
      
   }
   


   private static IntUnaryOperator pixelIndToGaussProcess(boolean x) {
      return new IntUnaryOperator() {
         @Override
         public int applyAsInt(int t) {
         rand_.setSeed(t + (x ? 23423 : 0));
         double gaussian = rand_.nextGaussian();
         return (int) (gaussian * 20000);
         }
      };

   }
   
   public static byte[] getBytePixelData(int channel, int x, int y, int z, int width, int height) {
      Random randX = new Random();
      Random randY = new Random();
      
      IntStream yStream = IntStream.range(y, y + height).map(pixelIndToGaussProcess(true));
      
      
      
      
//      for (int yPix = y; yPix < y + height; yPix++) {
//         randY.setSeed(y);
//      }
//      for (int xPix = x; xPix < x + width; xPix++) {
//         randY.setSeed(x);
//      }

      int fullWidth = img_.getWidth();
      int fullHeight = img_.getHeight();
      while (x < 0) {
         x += fullWidth;
      }
      while (y < 0) {
         y += fullHeight;
      }
      while (z < 0) {
         z += imageSizeZ_;
      }
      x = x % fullWidth;
      y = y % fullHeight;
      int sliceIndex = (z % imageSizeZ_) / pixelSizeZ_;
//      int sliceIndex = (z) / pixelSizeZ_;

      byte[] fullSlice = (byte[]) img_.getStack().getPixels(numChannels_ * sliceIndex + channel + 1);
      byte[] pixels = new byte[width * height];
      for (int line = 0; line < height; line++) {
         try {
            if (y + line >= fullHeight) {
               y -= fullHeight; //reset to top if go over
            }
            System.arraycopy(fullSlice, (y + line) * fullWidth + x, pixels, line * width, Math.min(width, fullWidth - x));
            //Copy rest of line if spill over in x
            System.arraycopy(fullSlice, (y + line) * fullWidth, pixels, line * width + Math.min(width, fullWidth - x), width - Math.min(width, fullWidth - x));
         } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
         }
      }
      return pixels;
   }

}
