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
    * @param method - 0 = 2D scatter, 1 = Gaussians, 2 = Normalized Gaussian
    * @param magnification  - factor x original size
    * @param rect - roi in the magnified image that should be rendered
    */
   public static void renderData(ImageWindow w, final MyRowData rowData,
           final int method, final double magnification, Rectangle rect, final SpotDataFilter sf) {

          
      String fsep = System.getProperty("file.separator");
      String ttmp = rowData.name_;
      if (rowData.name_.contains(fsep)) {
         ttmp = rowData.name_.substring(rowData.name_.lastIndexOf(fsep) + 1);
      }
      ttmp += magnification + "x";
      final String title = ttmp;
      final ImageWindow iw = w;

      //int mag = 1 << renderSize;

      if (rect == null) {
         rect = new Rectangle(0, 0, (int) (rowData.width_ * magnification),
                 (int) (rowData.height_ * magnification));
      }
      final double renderedPixelInNm = rowData.pixelSizeNm_ / magnification;
      final int width = rect.width;
      final int height = rect.height;
      int endx = rect.x + rect.width;
      int endy = rect.y + rect.height;
      final int size = width * height;
      double factor = (double) magnification / rowData.pixelSizeNm_;
      ImageProcessor ip = null;

      if (method == 0) {
         ip = new ShortProcessor(width, height);
         short pixels[] = new short[size];
         ip.setPixels(pixels);
         for (GaussianSpotData spot : rowData.spotList_) {
            if (sf.filter(spot)) {
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
         }
         if (ip != null) {
            ip.resetMinAndMax();
            ImagePlus sp = new ImagePlus(title, ip);
            DisplayUtils.AutoStretch(sp);
            DisplayUtils.SetCalibration(sp, (float) (rowData.pixelSizeNm_ / magnification));

            if (w == null) {
               GaussCanvas gs = new GaussCanvas(sp, rowData, method, magnification, sf);
               w = new ImageWindow(sp, gs);
               gs.setImageWindow(w);
               //w.getCanvas().addMouseListener(new GaussCanvas(w, rowData,
               //        method, magnification, sf));
            } else {
               w.setImage(sp);
            }

            w.setVisible(true);
         }

      } else if (method == 1 || method == 2) {  // Gaussian and normalized Gaussian

         Runnable doWorkRunnable = new Runnable() {

            public void run() {
               // determines whether gaussians should be normalized by their total intensity
               boolean normalize = false; 
               if (method ==2)
                  normalize = true;
               
               ImageProcessor ip = new FloatProcessor(width, height);
               float pixels[] = new float[size];
               ip.setPixels(pixels);

               ij.IJ.showStatus("Rendering Image...");
               int updateQuantum = rowData.spotList_.size() / 100;
               int counter = 0;
               int spotsUsed = 0;
               for (GaussianSpotData spot : rowData.spotList_) {
                  if (counter % updateQuantum == 0) {
                     ij.IJ.showProgress(counter, rowData.spotList_.size());
                  }
                  counter++;

                  if (sf.filter(spot)) {
                     spotsUsed++;

                     // cover 3 * precision
                     int halfWidth = (int) (2 * spot.getSigma() / renderedPixelInNm);
                     if (halfWidth == 0)
                        halfWidth = 1;

                     /*
                      * A *  exp(-((x-xc)^2+(y-yc)^2)/(2 sigy^2))+b
                      * A = params[INT]  (amplitude)
                      * b = params[BGR]  (background)
                      * xc = params[XC]
                      * yc = params[YC]
                      * sig = params[S]
                      * 
                      */
                     int xc = (int) Math.round(spot.getXCenter() / renderedPixelInNm);
                     int yc = (int) Math.round(spot.getYCenter() / renderedPixelInNm);
                     if (xc > halfWidth && xc < (width - halfWidth)
                             && yc > halfWidth && yc < (height - halfWidth)) {
                        double totalInt = 0.0;
                        int xStart = (int) xc - halfWidth;
                        int xEnd = (int) xc + halfWidth;
                        int yStart = (int) yc - halfWidth;
                        int yEnd = (int) yc + halfWidth;
                        float[][] boxPixels = new float[xEnd - xStart][yEnd - yStart];
                        for (int x = xStart; x < xEnd; x++) {
                           for (int y = yStart; y < yEnd; y++) {
                              double[] parms = {1.0, 0.0,
                                 spot.getXCenter() / renderedPixelInNm,
                                 spot.getYCenter() / renderedPixelInNm,
                                 spot.getSigma() / renderedPixelInNm};
                              double val = GaussianUtils.gaussian(parms, x, y);
                              totalInt += val;
                              boxPixels[x - xStart][y - yStart] = (float)val;
                              
                              //ip.setf(x, y, ip.getf(x, y) + (float) val);
                           }
                        }
                        // normalize if requested
                        if (normalize) {
                           for (int x = xStart; x < xEnd; x++) {
                              for (int y = yStart; y < yEnd; y++) {
                                 boxPixels[x - xStart][y - yStart] /= totalInt;
                              }
                           }

                        }
                        // now add to the image
                        for (int x = xStart; x < xEnd; x++) {
                           for (int y = yStart; y < yEnd; y++) {
                              ip.setf(x, y, ip.getf(x, y) + boxPixels[x - xStart][y - yStart]);
                           }
                        }
                     }
                  }
               }

               if (ip != null) {
                  ip.resetMinAndMax();
                  ImagePlus sp = new ImagePlus(title, ip);
                  DisplayUtils.AutoStretch(sp);
                  DisplayUtils.SetCalibration(sp, (float) (rowData.pixelSizeNm_ / magnification));

                  if (iw == null) {
                     GaussCanvas gs = new GaussCanvas(sp, rowData, method, magnification, sf);
                     ImageWindow w = new ImageWindow(sp, gs);
                     gs.setImageWindow(w);
                     w.setVisible(true);
                  } else {
                     iw.setImage(sp);
                     iw.setVisible(true);
                  }
               }

               ij.IJ.showProgress(1);
               ij.IJ.showStatus("Rendered image using " + spotsUsed + " spots.");  
               
            }
         };
         (new Thread(doWorkRunnable)).start();
      }
   }
}
