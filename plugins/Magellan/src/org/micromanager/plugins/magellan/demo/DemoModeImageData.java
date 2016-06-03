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

package org.micromanager.plugins.magellan.demo;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import java.awt.Frame;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.plugins.magellan.acq.MultiResMultipageTiffStorage;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.JavaUtils;
import org.micromanager.plugins.magellan.misc.Log;

/**
 *
 * @author Henry
 */
public class DemoModeImageData {
      
   private static ImagePlus img_;
   private static int pixelSizeZ_ = 3;
   private static int imageSizeZ_ = 399;
   private static volatile MultiResMultipageTiffStorage storage_;
   private static int numChannels_ = 6;
   
   public static int getNumChannels() {
      return numChannels_;
   }
   
   public DemoModeImageData() {
         //open LN magellan dataset
         //386x386 per tile
//         Thread opener = new Thread(new Runnable() {
//            @Override
//            public void run() {
//               try {
//                  storage_ = new MultiResMultipageTiffStorage("/Users/henrypinkard/Desktop/LNData/Left LN_2");
//                  Magellan.getCore().setProperty("Camera", "OnCameraCCDXSize", 386);
//                  Magellan.getCore().setProperty("Camera", "OnCameraCCDYSize", 386);
//               } catch (Exception ex) {
//                  Log.log(ex);
//               }
//            }
//         });
//         opener.start();

      
      String name= "lung.tif";
      IJ.runMacro("run(\"TIFF Virtual Stack...\", \"open=[/Users/henrypinkard/Desktop/ForHenry/lung.tif]\");");
      img_ = WindowManager.getImage(name);
      pixelSizeZ_ = (int) img_.getCalibration().pixelDepth;
      imageSizeZ_ = img_.getDimensions()[3] * pixelSizeZ_;
      numChannels_ = 1;
         
      
//      String name = "wholeIVMwindow_1.tif";
//      IJ.runMacro("run(\"TIFF Virtual Stack...\", \"open=[/Users/henrypinkard/Desktop/ForHenry/wholeIVMwindow_1.tif]\");");
//      img_ = WindowManager.getImage(name);
//      pixelSizeZ_ = (int) img_.getCalibration().pixelDepth;
//      imageSizeZ_ = img_.getDimensions()[3] * pixelSizeZ_;

//      IJ.runMacro("run(\"TIFF Virtual Stack...\", \"open=[./Magellan_demo_data.tif]\");");
//      img_ =  WindowManager.getImage("Magellan_demo_data.tif");
   


      img_.getWindow().setState(Frame.ICONIFIED);
   }
   
    //this demo data has z spaceing of 3, from 0-399
   //byte data in a short container
   public static short[] getShortPixelData(int channel, int x, int y, int z, int width, int height) {
      int fullWidth = img_.getWidth();
      int fullHeight = img_.getHeight();      
      while (x < 0) x+= fullWidth;
      while (y < 0) y+= fullHeight;
      while (z < 0) z+= imageSizeZ_;
      x = x % fullWidth;
      y = y % fullHeight;
      int sliceIndex = (z % imageSizeZ_) / pixelSizeZ_;
      short[] fullSlice = (short[]) img_.getStack().getPixels(6 * sliceIndex + channel + 1);
      short[] pixels = new short[width * height];
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
   
//    public static byte[] getBytePixelData(int channel, int x, int y, int z, int width, int height) {
//      int xOffset = -50;
//      int yOffset = -50;
//      int sliceIndex = (int) (z / 4.5);
//      int frame = 2;
//      return (byte[]) storage_.getImageForDisplay(channel, sliceIndex, frame, 0, x+xOffset, y+yOffset, width, height).pix;
//   }
   
   public static byte[] getBytePixelData(int channel, int x, int y, int z, int width, int height) {
      int fullWidth = img_.getWidth();
      int fullHeight = img_.getHeight();      
      while (x < 0) x+= fullWidth;
      while (y < 0) y+= fullHeight;
      while (z < 0) z+= imageSizeZ_;
      x = x % fullWidth;
      y = y % fullHeight;
      int sliceIndex = (z % imageSizeZ_) / pixelSizeZ_;
      byte[] fullSlice = (byte[]) img_.getStack().getPixels(6 * sliceIndex + channel + 1);
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
