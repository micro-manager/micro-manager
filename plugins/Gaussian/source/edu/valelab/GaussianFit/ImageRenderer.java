/*
 * Utilities to render localization microscopy data
 */
package edu.valelab.GaussianFit;

import edu.valelab.GaussianFit.DataCollectionForm.MyRowData;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Rectangle;

/**
 *
 * @author Nico Stuurman
 */
public class ImageRenderer {
   
   
   /*
    * Renders spotdata using various renderModes
    * 
    * @param rowData - MyRowData structure to be rendered
    * @param method - 0 = 2D scatter, 1 = Gaussians
    * @param magnification  - factor x original size
    */
      public static void renderData(ImageWindow w, MyRowData rowData, 
              int method, double magnification, Rectangle rect) {
         
      String fsep = System.getProperty("file.separator");
      String title = rowData.name_;
      if (rowData.name_.contains(fsep))
         title = rowData.name_.substring(rowData.name_.lastIndexOf(fsep) + 1);
      title += magnification + "x";
      
      
      
      //int mag = 1 << renderSize;
      
      if (rect == null) {
         rect = new Rectangle( 0, 0, (int) (rowData.width_ * magnification), 
                 (int) (rowData.height_ * magnification) );
      }
      double renderedPixelInNm = rowData.pixelSizeNm_ / magnification;
      int width = rect.width;
      int height = rect.height;
      int endx = rect.x + rect.width;
      int endy = rect.y + rect.height;
      int size = width * height;
      double factor = (double) magnification / rowData.pixelSizeNm_;
      ImageProcessor ip = null;

      if (method == 0) {
         ip = new ShortProcessor(width, height);
         short pixels[] = new short[size];
         ip.setPixels(pixels);
         for (GaussianSpotData spot : rowData.spotList_) {
            int x = (int) (factor * spot.getXCenter());
            int y = (int) (factor * spot.getYCenter());
            if (x > rect.x && x < endx && y > rect.y && y < endy) {
               int index = (y * width) + x;
               if (index < size && index > 0) {
                  if (pixels[index] != -1) {
                     pixels[index] += 1;
                  }
               }
            }
         }
      } else if (method == 1) {  // Gaussian
         ip = new FloatProcessor(width, height);
         float pixels[] = new float[size];
         ip.setPixels(pixels);
         
         // setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
         
         for (GaussianSpotData spot : rowData.spotList_) {
            // cover 3 * precision
            int halfWidth = (int) (2 * spot.getSigma() / renderedPixelInNm);
            if (halfWidth > 3) {
               halfWidth = 3;
            }
            /*
             * A *  exp(-((x-xc)^2+(y-yc)^2)/(2 sigy^2))+b
             * A = params[INT]  (total intensity)
             * b = params[BGR]  (background)
             * xc = params[XC]
             * yc = params[YC]
             * sig = params[S]
             * 
             */
            double xc = spot.getXCenter() / renderedPixelInNm;
            double yc = spot.getYCenter() / renderedPixelInNm;
            if (xc > halfWidth && xc < width - halfWidth
                    && yc > halfWidth && yc < height - halfWidth) {
               for (int x = (int) xc - halfWidth; x < (int) xc + halfWidth; x++) {
                  for (int y = (int) yc - halfWidth; y < (int) yc + halfWidth; y++) {
                     double[] parms = {1.0, 0.0,
                        spot.getXCenter() / renderedPixelInNm,
                        spot.getYCenter() / renderedPixelInNm,
                        spot.getSigma() / renderedPixelInNm};
                     double val = GaussianUtils.gaussian(parms, x, y);
                     ip.setf(x, y, ip.getf(x, y) + (float) val);
                  }
               }
            }
         }
         
         //setCursor(Cursor.getDefaultCursor());              
      }

      if (ip != null) {
         ip.resetMinAndMax();
         ImagePlus sp = new ImagePlus(title, ip);
         DisplayUtils.AutoStretch(sp);
         DisplayUtils.SetCalibration(sp, (float) (rowData.pixelSizeNm_ / magnification) );

         if (w == null) {
            w = new ImageWindow(sp);
            w.getCanvas().addMouseListener(new ImageWindowListener(w, rowData, 
                    method, magnification));
         }
         else 
            w.setImage(sp);
    
         w.setVisible(true);
      }

   }
   
}
