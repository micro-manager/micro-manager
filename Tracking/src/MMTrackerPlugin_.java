import java.awt.Rectangle;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

/*
 * Created on Dec 8, 2006
 * author: Nenad Amodaj
 */

/**
 * ImageJ plugin wrapper for uManager.
 */
public class MMTrackerPlugin_ implements PlugIn {
   
   ImagePlus imp;
   int nSlices;
   int width;
   int height;
   
   static final int OFFSET = 100;
   static final int RESOLUTION = 5;

   public void run(String arg) {
      
      // get stack
      imp = WindowManager.getCurrentImage();
      if (imp==null)
         {IJ.noImage(); return;}
      nSlices = imp.getStackSize();
      width = imp.getWidth();
      height = imp.getHeight();
               
      if (nSlices<3)
         {IJ.error("Stack of size 3 or larger requred"); return;}
      
      // obtain ROI
      Roi roi = imp.getRoi();
      if (roi == null || roi.getType() != Roi.RECTANGLE) {
         IJ.error("Rectangular roi requred");
         return;
      }
      
      Rectangle r = roi.getBoundingRect();
      imp.setSlice(0);
      ImageProcessor ipPrevious = imp.getProcessor();
      ImageProcessor ipCurrent;
            
      // iterate on all slices
      for (int m=1; m<nSlices; m++) { 
         imp.setSlice(m);
         ipCurrent = imp.getProcessor();
         
         // iterate on all offsets
         int kMax = 0;
         int lMax = 0;
         
         r = roi.getBoundingRect();
         IJ.write("ROI pos: " + r.x + "," + r.y);
         
         double corScale = r.width * r.height;
         
         double maxCor = 0; // <<<
         for (int k=-OFFSET; k<OFFSET; k += RESOLUTION) {
            for (int l=-OFFSET; l<OFFSET; l += RESOLUTION) {

               // calculate correlation
               double sum = 0.0;
               for (int i=0; i<r.height; i++) {
                  for (int j=0; j<r.width; j++) {
                     int pixPrev = ipPrevious.getPixel(r.x + j + l, r.y + i + k);
                     int pixCur = ipCurrent.getPixel(r.x + j + l, r.y + i + k);
                     sum += (double)pixPrev*pixCur;
                  }
               }
               sum /= corScale;
               
               // check foer max value
               if (sum > maxCor) {
                  maxCor = sum;
                  kMax = k;
                  lMax = l;
               }
            }
         }
         
         IJ.write("Slice " + m + ", maxc=" + maxCor + ", offset=(" + lMax + "," + kMax + ")");
         ipPrevious = ipCurrent;
         
         // move the roi
         roi.setLocation(r.x+lMax, r.y+kMax);
      }
   }
}
