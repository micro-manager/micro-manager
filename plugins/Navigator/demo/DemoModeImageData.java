/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package demo;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.macro.Interpreter;
import java.awt.Frame;
import java.util.Arrays;
import org.micromanager.utils.JavaUtils;

/**
 *
 * @author Henry
 */
public class DemoModeImageData {
      
   private static ImagePlus img_;
   
   public DemoModeImageData() {
//      Interpreter.batchMode = true; //batch mode makes everything ridiculously slow for some reason
      
      if (JavaUtils.isMac()) {
         //Laptop         
         IJ.runMacro("run(\"TIFF Virtual Stack...\", \"open=[/Applications/Micro-Manager1.4/Navigator demo LN.tif]\");");
      } else {
         //BIDC computer
         IJ.runMacro("run(\"TIFF Virtual Stack...\", \"open=[C:/Program Files/Micro-Manager-1.4/Navigator demo LN.tif]\");");
      }
      img_ = WindowManager.getImage("Navigator demo LN.tif");
      img_.getWindow().setState(Frame.ICONIFIED);
   }
   
   //this demo data has z spaceing of 3, from 0-399
   public static byte[] getPixelData(int channel, int x, int y, int z, int width, int height) {
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
