/*
 * Utilities to render localization microscopy data
 */
package edu.valelab.GaussianFit;

import edu.valelab.GaussianFit.utils.GaussianUtils;
import edu.valelab.GaussianFit.utils.RowData;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 *
 * @author Nico Stuurman
 */
public class ImageRenderer {
    private int[][] iceLut_ = new int[256][];
      

   
    static int[][] zLut_ = new int[256][];
   
      
   /*
    * Renders spotdata using various renderModes
    * 
    * @param rowData - MyRowData structure to be rendered
    * @param method - 0 = 2D scatter, 1 = Gaussians, 2 = Normalized Gaussian
    * @param magnification  - factor x original size
    * @param rect - roi in the magnified image that should be rendered
    */
   public static ImageProcessor renderData(final RowData rowData,
           final int method, final double magnification, Rectangle rect, final SpotDataFilter sf) {

      ImageProcessor ip = null;

      // downside of using statics is that we have to read this everytime we render
      // it may be better to put luts in their own singleton class
      if (rowData.hasZ_) {
         readLut("icelut.txt");
      }

      //int mag = 1 << renderSize;

      if (rect == null) {
         rect = new Rectangle(0, 0, (int) (rowData.width_ * magnification),
                 (int) (rowData.height_ * magnification));
      }
      final double renderedPixelInNm = rowData.pixelSizeNm_ / magnification;
      final int width = rect.width;
      final int height = rect.height;
      final int fullWidth = (int) (rowData.width_ * magnification);
      final int fullHeight = (int) (rowData.height_ * magnification);
      int endx = rect.x + rect.width;
      int endy = rect.y + rect.height;
      final int size = width * height;
      double factor = magnification / rowData.pixelSizeNm_;


      try {
         if (method == 0) {
            if (!rowData.hasZ_) {
               ip = new ShortProcessor(width, height);
               short pixels[] = new short[size];
               ip.setPixels(pixels);
               for (GaussianSpotData spot : rowData.spotList_) {
                  if (sf.filter(spot)) {
                     int x = (int) (factor * spot.getXCenter());
                     int y = (int) (factor * spot.getYCenter());
                     if (x > rect.x && x < endx && y > rect.y && y < endy) {
                        x -= rect.x;
                        y -= rect.y;
                        int index = (y * width) + x;
                        if (index < size && index > 0) {
                           if (pixels[index] != -1) {
                              pixels[index] += 1;
                           }
                        }
                     }
                  }
               }
            } else if (rowData.hasZ_) {
               ShortProcessor[] sp = new ShortProcessor[3];
               short[][] pixels = new short[3][size];
               for (int i = 0; i < 3; i++) {
                  sp[i] = new ShortProcessor(width, height);
                  sp[i].setPixels(pixels[i]);
               }
               double spread = rowData.maxZ_ - rowData.minZ_;
               for (GaussianSpotData spot : rowData.spotList_) {
                  if (sf.filter(spot)) {
                     int x = (int) (factor * spot.getXCenter());
                     int y = (int) (factor * spot.getYCenter());
                     if (x > rect.x && x < endx && y > rect.y && y < endy) {
                        x -= rect.x;
                        y -= rect.y;
                        int index = (y * width) + x;
                        if (index < size && index > 0) {
                           int zIndex = (int) (256 * (spot.getZCenter() - rowData.minZ_) / spread);
                           if (zIndex < 0) {
                              zIndex = 0;
                           }
                           if (zIndex > 255) {
                              zIndex = 255;
                           }
                           for (int i = 0; i < 3; i++) {
                              pixels[i][index] += zLut_[zIndex][i];
                           }
                        }
                     }
                  }
               }
               // we have 3 ShortProcessors.  Combine into a color image:
               ColorProcessor cp = new ColorProcessor(width, height);
               byte[][] colorPixels = new byte[3][];
               for (int i = 0; i < 3; i++) {
                  colorPixels[i] = new byte[size];
               }
               //ip.setPixels(colorPixels);
               double max = sp[0].getMax();
               for (int i = 1; i < 3; i++) {
                  if (sp[i].getMax() > max) {
                     max = sp[i].getMax();
                  }
               }
               for (int p = 0; p < size; p++) {
                  for (int i = 0; i < 3; i++) {
                     colorPixels[i][p] = (byte) (256.0 * pixels[i][p] / max);
                  }

               }
               cp.setRGB(colorPixels[0], colorPixels[1], colorPixels[2]);
               ip = cp;
            }

         } else if (method == 1 || method == 2) {  // Gaussian and normalized Gaussian


            // determines whether gaussians should be normalized by their total intensity
            boolean normalize = false;
            if (method == 2) {
               normalize = true;
            }

            ip = new FloatProcessor(width, height);
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


               if (sf.filter(spot)) {


                  // cover 3 * precision
                  int halfWidth = (int) (2 * spot.getSigma() / renderedPixelInNm);
                  if (halfWidth == 0) {
                     halfWidth = 2;
                  }

                  /*
                   * A *  exp(-((x-xc)^2+(y-yc)^2)/(2 sigy^2))+b
                   * A = params[INT]  (amplitude)
                   * b = params[BGR]  (background)
                   * xc = params[XC]
                   * yc = params[YC]
                   * sig = params[S]
                   * 
                   */
                  int xc = (int) (factor * spot.getXCenter());
                  int yc = (int) (factor * spot.getYCenter());
                  //int xc = (int) Math.round(spot.getXCenter() / renderedPixelInNm);
                  //int yc = (int) Math.round(spot.getYCenter() / renderedPixelInNm);


                  if (xc > rect.x + halfWidth && xc < endx - halfWidth
                          && yc > rect.y + halfWidth && yc < endy - halfWidth) {

                     if (xc > halfWidth && xc < (fullWidth - halfWidth)
                             && yc > halfWidth && yc < (fullHeight - halfWidth)) {
                        counter++;
                        spotsUsed++;
                        double totalInt = 0.0;
                        int xStart = xc - halfWidth;
                        int xEnd = xc + halfWidth;
                        int yStart = yc - halfWidth;
                        int yEnd = yc + halfWidth;
                        float[][] boxPixels = new float[xEnd - xStart][yEnd - yStart];
                        for (int x = xStart; x < xEnd; x++) {
                           for (int y = yStart; y < yEnd; y++) {
                              double[] parms = {1.0, 0.0,
                                 spot.getXCenter() / renderedPixelInNm,
                                 spot.getYCenter() / renderedPixelInNm,
                                 spot.getSigma() / renderedPixelInNm};
                              double val = GaussianUtils.gaussian(parms, x, y);
                              totalInt += val;
                              if (normalize) {
                                 boxPixels[x - xStart][y - yStart] = (float) val;
                              } else {
                                 ip.setf(x, y, ip.getf(x, y) + (float) val);
                              }
                           }
                        }
                        // normalize if requested
                        if (normalize && totalInt > 0) {
                           for (int x = xStart; x < xEnd; x++) {
                              for (int y = yStart; y < yEnd; y++) {
                                 boxPixels[x - xStart][y - yStart] /= totalInt;
                                 if (boxPixels[x - xStart][y - yStart] != boxPixels[x - xStart][y - yStart]) {
                                    System.out.println("<0");
                                 }
                              }
                           }
                           // now add to the image
                           for (int x = xStart; x < xEnd; x++) {
                              for (int y = yStart; y < yEnd; y++) {
                                 try {
                                    ip.setf(x - rect.x, y - rect.y,
                                            ip.getf(x - rect.x, y - rect.y) + boxPixels[x - xStart][y - yStart]);
                                 } catch (Exception ex) {
                                    System.out.println("Exception");
                                 }
                              }
                           }
                        }

                     }
                  }
               }
            }


            ij.IJ.showProgress(1);
            ij.IJ.showStatus("Rendered image using " + spotsUsed + " spots.");

         }
      } catch (java.lang.OutOfMemoryError ome) {
         // report out of memory
         ij.IJ.showMessage("Out of Memory", "Not enought memory to draw image at this resolution");
      }



      if (ip != null) {
         ip.resetMinAndMax();
      }
      /*
         sp = new ImagePlus(title, ip);
         DisplayUtils.AutoStretch(sp);
         DisplayUtils.SetCalibration(sp, (float) (rowData.pixelSizeNm_ / magnification));
      }
      
       */

      return ip;    
   }
   
   
      /*
    * Renders spotdata using various renderModes
    * 
    * @param rowData - MyRowData structure to be rendered
    * @param method - 0 = 2D scatter, 1 = Gaussians, 2 = Normalized Gaussian
    * @param magnification  - factor x original size
    * @param rect - roi in the magnified image that should be rendered
    */
   public static ImageStack renderData3D(final RowData rowData,
           final int method, final double magnification, Rectangle rect, final SpotDataFilter sf) {
   
      
      //int mag = 1 << renderSize;

      if (rect == null) {
         rect = new Rectangle(0, 0, (int) (rowData.width_ * magnification),
                 (int) (rowData.height_ * magnification));
      }
      final double renderedPixelInNm = rowData.pixelSizeNm_ / magnification;
      final int width = rect.width;
      final int height = rect.height;
      final int fullWidth = (int) (rowData.width_ * magnification);
      final int fullHeight = (int) (rowData.height_ * magnification);
      double tmp =  1000.0 * (rowData.maxZ_ - rowData.minZ_ ) / (2* renderedPixelInNm);
      final int nrZs = (int) tmp;
      int endx = rect.x + rect.width;
      int endy = rect.y + rect.height;
      final int size = width * height;
      double factor = magnification / rowData.pixelSizeNm_;

      ImageStack is = new ImageStack(width, height);
      ImageProcessor[] ip = new ImageProcessor[nrZs];
      
      if (method == 0) {
         
         short pixels[][] = new short[nrZs][size];
         for (int i = 0; i < nrZs; i++) {
            ip[i] = new ShortProcessor(width, height);
            ip[i].setPixels(pixels[i]);
            is.addSlice(ip[i]);
         }

         for (GaussianSpotData spot : rowData.spotList_) {
            if (sf.filter(spot)) {
               int x = (int) (factor * spot.getXCenter());
               int y = (int) (factor * spot.getYCenter());
               int z = (int) (factor * (spot.getZCenter() - rowData.minZ_) * 500.0);
               if (x > rect.x && x < endx && y > rect.y && y < endy) {
                  x -= rect.x;
                  y -= rect.y;
                  int index = (y * width) + x;
                  if (index < size && index > 0 && z < nrZs && z > 0) {
                     if (pixels[z][index] != -1) {
                        pixels[z][index] += 1;
                     }
                  }
               }
            }
         }
      }
 
      
      return is;
   }

   /**
    * Reads a file enclosed in this jar that is created by copying the output of
    * the List command in ImageJ (Image>Color>ShowLut).
    * @param lutName - name of file containing Lut data
    */
   static private void readLut(String lutName) {
      InputStream fin = ImageRenderer.class.getResourceAsStream(lutName);
      if (fin == null) {
         return;
      }
      BufferedReader br = new BufferedReader(new InputStreamReader(fin));
      String line;
      try {
         while ((line = br.readLine()) != null) {
            String[] tokens = line.split("\t");
            int index = Integer.parseInt(tokens[0]);
            if (index < zLut_.length) {
               zLut_[index] = new int[]{Integer.parseInt(tokens[1]),
                  Integer.parseInt(tokens[2]),
                  Integer.parseInt(tokens[3])};
            }
         }
      } catch (IOException ioex) {
         System.out.println("IOException" + ioex.getMessage());
      }
   }
   
   
}
  
  