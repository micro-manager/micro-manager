/*
 * This class uses autocorrelation to detect the movement between a reference image
 * and the given image
 */
package edu.valelab.gaussianfit;

import ij.process.FHT;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;

/**
 *
 * @author Nico Stuurman
 */
public class JitterDetector {
   private final FHT ref_;
   
   public JitterDetector(ImageProcessor reference) {
     ref_ = new FHT(reference);
     ref_.transform();
     ref_.resetMinAndMax();
   }
   
   public void getJitter(ImageProcessor test, Point2D.Double com) {
      FHT t = new FHT(test);
      t.transform();
      t.resetMinAndMax();
      
      FHT m = ref_.conjugateMultiply(t);
            
      m.inverseTransform();
      m.swapQuadrants();
      
      // return the position of the brightest pixel
      Point brightPix = new Point(0, 0);
      BrightestPixel(m, brightPix, 32);     
      
      try {
         // Gaussian fit using Nelder Mead and 3D fitting
         GaussianFit gs = new GaussianFit(3, 2);
         // halfsize of the square around brightest pixel used for Gaussian fit
         int hs = 5;
         ImageProcessor ip = m.convertToShort(true);
         ip.setRoi(brightPix.x - hs, brightPix.y - hs, 2 * hs, 2 * hs);
         ImageProcessor ipc = ip.crop();

         double[] paramsOut = gs.dogaussianfit(ipc, 100);
         com.x = paramsOut[GaussianFit.XC] - hs + brightPix.x;
         com.y = paramsOut[GaussianFit.YC] - hs + brightPix.y;

      } catch (Exception ex) { 
         // Gaussian fit failed, try second best estimate
         com.x = brightPix.x;
         com.y = brightPix.y;
      }
         
   }
   
   /**
    * Finds the brightest pixel in the center of the image m
    * only searches in the center of the image in a square with edge size searchsize
    * 
    * @param m image to be searched
    * @param brightPix point use to return coordinates of pixel found
    * @param searchSize size of edge of center square in which to look for brightest pixel 
    */
   private void BrightestPixel(FHT m, Point brightPix, int searchSize) {
      float pixels[] = (float[]) m.getPixels();
            

      int height = m.getHeight();
      int width = m.getWidth();
      int halfHeight = (height / 2);
      int halfWidth = (width / 2);
      int halfSearchSize = (searchSize / 2);
    
      double max = pixels[halfHeight * width + halfWidth];
      brightPix.x = halfWidth;
      brightPix.y = halfHeight;
      
      
      for (int y = halfHeight - halfSearchSize; 
              y < halfHeight + halfSearchSize; y++) {
         for (int x = halfWidth - halfSearchSize; 
                 x < halfWidth + halfSearchSize; x++) {
            if (pixels[y*width + x] > max) {
               max = pixels[y*width + x];
               brightPix.x = x;
               brightPix.y = y;
            }
         }
      }
      
   }
   
}
