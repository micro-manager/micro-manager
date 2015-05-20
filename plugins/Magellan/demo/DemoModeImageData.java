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

package demo;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import java.awt.Frame;
import main.Magellan;
import misc.JavaUtils;

/**
 *
 * @author Henry
 */
public class DemoModeImageData {
      
   private static ImagePlus img_;
   
   public DemoModeImageData() {
//      Interpreter.batchMode = true; //batch mode makes everything ridiculously slow for some reason
      
      String name = "Navigator demo LN" + 
              (Magellan.getCore().getBytesPerPixel() > 1 ? "16Bit" : "")+ ".tif";
      if (JavaUtils.isMac()) {
         //Laptop         
         IJ.runMacro("run(\"TIFF Virtual Stack...\", \"open=[/Applications/Micro-Manager1.4/Navigator demo LN.tif]\");");
      } else {
         //BIDC computer
         IJ.runMacro("run(\"TIFF Virtual Stack...\", \"open=[C:/Program Files/Micro-Manager-1.4/" + name+ "]\");");
      }
      img_ = WindowManager.getImage(name);
      img_.getWindow().setState(Frame.ICONIFIED);
   }
   
    //this demo data has z spaceing of 3, from 0-399
   //byte data in a short container
   public static short[] getShortPixelData(int channel, int x, int y, int z, int width, int height) {
      int fullWidth = img_.getWidth();
      int fullHeight = img_.getHeight();      
      while (x < 0) x+= fullWidth;
      while (y < 0) y+= fullHeight;
      while (z < 0) z+= 399;
      x = x % fullWidth;
      y = y % fullHeight;
      int sliceIndex = (z % 399) / 3;
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
   
   //this demo data has z spaceing of 3, from 0-399
   public static byte[] getBytePixelData(int channel, int x, int y, int z, int width, int height) {
      int fullWidth = img_.getWidth();
      int fullHeight = img_.getHeight();      
      while (x < 0) x+= fullWidth;
      while (y < 0) y+= fullHeight;
      while (z < 0) z+= 399;
      x = x % fullWidth;
      y = y % fullHeight;
      int sliceIndex = (z % 399) / 3;
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
