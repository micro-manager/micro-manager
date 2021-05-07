/*
 * This class uses autocorrelation to detect the movement between a reference image
 * and the given image

Copyright (c) 2010-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */


package edu.ucsf.valelab.gaussianfit.algorithm;

import ij.process.FHT;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;

/**
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

         GaussianFit.Data fitResult = gs.dogaussianfit(ipc, 100);
         com.x = fitResult.getParms()[GaussianFit.XC] - hs + brightPix.x;
         com.y = fitResult.getParms()[GaussianFit.YC] - hs + brightPix.y;

      } catch (Exception ex) {
         // Gaussian fit failed, try second best estimate
         com.x = brightPix.x;
         com.y = brightPix.y;
      }

   }

   /**
    * Finds the brightest pixel in the center of the image m only searches in the center of the
    * image in a square with edge size searchsize
    *
    * @param m          image to be searched
    * @param brightPix  point use to return coordinates of pixel found
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
            if (pixels[y * width + x] > max) {
               max = pixels[y * width + x];
               brightPix.x = x;
               brightPix.y = y;
            }
         }
      }

   }

}
