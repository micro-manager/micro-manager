/**
 * Find local maxima in an Image (or ROI) using the algorithm described in Neubeck and Van Gool.
 * Efficient non-maximum suppression. Pattern Recognition (2006) vol. 3 pp. 850-855
 * <p>
 * Jonas Ries brought this to my attention and send me C code implementing one of the described
 * algorithms
 * <p>
 * Copyright (c) 2012-2017, Regents of the University of California All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * The views and conclusions contained in the software and documentation are those of the authors
 * and should not be interpreted as representing official policies, either expressed or implied, of
 * the FreeBSD Project.
 */


package edu.ucsf.valelab.gaussianfit.algorithm;

import ij.ImagePlus;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;
import java.awt.Polygon;
import java.awt.Rectangle;


/**
 * @author nico
 */
public class FindLocalMaxima {

   private static final GaussianBlur FILTER = new GaussianBlur();
   private static final ImageCalculator IMAGECALCULATOR = new ImageCalculator();

   public enum FilterType {
      NONE,
      GAUSSIAN1_5
   }

   /**
    * Static utility function to find local maxima in an Image
    *
    * @param iPlus      - ImagePlus object in which to look for local maxima
    * @param n          - minimum distance to other local maximum
    * @param threshold  - value below which a maximum will be rejected
    * @param filterType - Prefilter the image.  Either none or Gaussian1_5
    * @return Polygon with maxima
    */
   public static Polygon FindMax(ImagePlus iPlus, int n, int threshold, FilterType filterType) {
      Polygon maxima = new Polygon();

      ImageProcessor iProc = iPlus.getProcessor();
      Rectangle roi = iProc.getRoi();
      // HACK: need to figure out the underlying cause, but make it workable for now
      if (roi.height == 0 && roi.width == 0) {
         roi.x = 0;
         roi.y = 0;
         roi.height = iProc.getHeight();
         roi.width = iProc.getWidth();
      }

      // Prefilter if needed
      switch (filterType) {
         case GAUSSIAN1_5:
            // TODO: if there is an ROI, we only need to filter_ in the ROI
            ImageProcessor iProcG1 = iProc.duplicate();
            ImageProcessor iProcG5 = iProc.duplicate();
            FILTER.blurGaussian(iProcG1, 0.4, 0.4, 0.01);
            FILTER.blurGaussian(iProcG5, 2.0, 2.0, 0.01);
            ImagePlus p1 = new ImagePlus("G1", iProcG1);
            ImagePlus p5 = new ImagePlus("G5", iProcG5);
            IMAGECALCULATOR.run("subtract", p1, p5);
            iProc = p1.getProcessor();

            break;
      }

      for (int x = roi.x + n; x < roi.width + roi.x - n - 1; x++) {
         for (int y = roi.y + n; y < roi.height + roi.y - n - 1; y++) {
            // Is this a local maximum?
            boolean failed = false;
            for (int mx = x - n; mx < x + n && !failed; mx++) {
               for (int my = y - n; my < y + n && !failed; my++) {
                  if (iProc.get(mx, my) > iProc.get(x, y)) {
                     failed = true;
                  } else if (iProc.get(mx, my) == iProc.get(x, y)) {
                     // special handling of pixels of equal intensity
                     if (mx > x || my > y) {
                        // avoid excluding x,y itself, when there are multiple 
                        // pixel of same intensity in the box, take the first one
                        failed = true;
                     }
                  }
               }
            }
            if (!failed) {
               int cornerAverage = (iProc.get(x - n, y - n) + iProc.get(x - n, y + n) +
                     iProc.get(x + n, y - n) + iProc.get(x + n, y + n)) / 4;
               if (iProc.get(x, y) - threshold > cornerAverage) {
                  maxima.addPoint(x, y);
               }
            }
         }
      }
      
      
            
 /*      
     
      // divide the image up in blocks of size n and find local maxima
      int n2 = 2*n + 1;
      // calculate borders once
      int xRealEnd = roi.x + roi.width;
      int xEnd = xRealEnd - n;
      int yRealEnd = roi.y + roi.height;
      int yEnd = yRealEnd - n;
      for (int i=roi.x; i <= xEnd - n - 1; i+=n2) {
         for (int j=roi.y; j <= yEnd - n - 1; j+=n2) {
            int mi = i;
            int mj = j;
            for (int i2=i; i2 < i + n2 && i2 < xRealEnd; i2++) {
               for (int j2=j; j2 < j + n2 && j2 < yRealEnd; j2++) {
                  // revert getPixel to get after debugging
                  if (iProc.getPixel(i2, j2) > iProc.getPixel(mi, mj)) {
                     mi = i2;
                     mj = j2;
                  }
               }
            }
            // is the candidate really a local maximum?
            // check surroundings (except for the pixels that we already checked)
            boolean stop = false;
            // columns in block to the left
            if (mi - n < i && i>0) {
               for (int i2=mi-n; i2<i; i2++) {
                  for (int j2=mj-n; j2<=mj+n; j2++) {
                     if (iProc.getPixel(i2, j2) > iProc.getPixel(mi, mj)) {
                        stop = true;
                     }
                  }
               }
            }
            // columns in block to the right
            if (!stop && mi + n >= i + n2 ) {
               for (int i2=i+n2; i2<=mi+n; i2++) {
                   for (int j2=mj-n; j2<=mj+n; j2++) {
                     if (iProc.getPixel(i2, j2) > iProc.getPixel(mi, mj)) {
                        stop = true;
                     }
                  }
               }
            }
            // rows on top of the block
            if (!stop && mj - n < j && j > 0) {
               for (int j2 = mj - n; j2 < j; j2++) {
                  for (int i2 = mi - n; i2 <= mi + n; i2++) {
                     if (iProc.getPixel(i2, j2) > iProc.getPixel(mi, mj))
                        stop = true;
                  }
               }
            }
            // rows below the block
            if (!stop && mj + n >= j + n2) {
               for (int j2 = j + n2; j2 <= mj + n; j2++) {
                  for (int i2 = mi - n; i2 <= mi + n; i2++) {
                     if (iProc.getPixel(i2, j2) > iProc.getPixel(mi, mj))
                        stop = true;
                  }
               }
            }
            if (!stop && (threshold == 0 || 
                    (iProc.getPixel(mi, mj) - 
                      ( (iProc.getPixel(mi - n , mj - n) + iProc.getPixel(mi -n, mj + n) +
                       iProc.getPixel(mi + n, mj  - n) + iProc.getPixel(mi + n, mj + n)) / 4) ) 
                    > threshold))
               maxima.addPoint(mi, mj);
         }
      }
*/

      return maxima;
   }


   // Filters local maxima list using the ImageJ findMaxima Threshold algorithm
   public static Polygon noiseFilter(ImageProcessor iProc, Polygon inputPoints, int threshold) {
      Polygon outputPoints = new Polygon();

      for (int i = 0; i < inputPoints.npoints; i++) {
         int x = inputPoints.xpoints[i];
         int y = inputPoints.ypoints[i];
         int value = iProc.getPixel(x, y) - threshold;
         if (value > iProc.getPixel(x - 1, y - 1) ||
               value > iProc.getPixel(x - 1, y) ||
               value > iProc.getPixel(x - 1, y + 1) ||
               value > iProc.getPixel(x, y - 1) ||
               value > iProc.getPixel(x, y + 1) ||
               value > iProc.getPixel(x + 1, y - 1) ||
               value > iProc.getPixel(x + 1, y) ||
               value > iProc.getPixel(x + 1, y + 1)
         ) {
            outputPoints.addPoint(x, y);
         }
      }

      return outputPoints;
   }

}
