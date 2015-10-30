///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
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

package org.micromanager.display.internal;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;

import org.micromanager.internal.utils.ImageUtils;

/**
 * This class calculates histograms and contrast settings for images.
 */
public class ContrastCalculator {

   public static HistogramData calculateHistogram(Image image,
         int component, int binPower, double extremaPercentage) {
      int channel = image.getCoords().getChannel();
      int width = image.getWidth();
      int height = image.getHeight();
      int bytesPerPixel = image.getBytesPerPixel();

      int minVal = -1;
      int maxVal = -1;
      long meanVal = 0;
      int numBins = (int) Math.pow(2, binPower);
      int[] histogram = new int[numBins];

      Object pixels = image.getRawPixels();
      int divisor = image.getNumComponents();
      int exponent = 8;
      if (pixels instanceof short[]) {
         divisor *= 2;
         exponent = 16;
      }
      for (int x = 0; x < width; ++x) {
         for (int y = 0; y < height; ++y) {
            // TODO this code is copied not-quite-verbatim from
            // Image.getComponentIntensityAt(). The chief difference being that
            // we operate on the Java pixels array instead of the Image's
            // native array buffer, and of course we get the intensity for
            // every pixel in the image.
            int pixelVal = 0;
            for (int i = 0; i < bytesPerPixel / divisor; ++i) {
               // NB Java will let you use "<<=" in this situation.
               pixelVal = pixelVal << exponent;
               int index = y * width + x + component + i;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               int addend = 0;
               if (pixels instanceof byte[]) {
                  addend = ImageUtils.unsignedValue(
                        ((byte[]) pixels)[index]);
               }
               else if (pixels instanceof short[]) {
                  addend = ImageUtils.unsignedValue(
                        ((short[]) pixels)[index]);
               }
               pixelVal += addend;
            }
            histogram[pixelVal / numBins]++;
            if (minVal == -1) {
               minVal = pixelVal;
               maxVal = pixelVal;
            }
            else {
               minVal = Math.min(minVal, pixelVal);
               maxVal = Math.max(maxVal, pixelVal);
            }
            meanVal += pixelVal;
         }
      }
      int bitDepth = image.getMetadata().getBitDepth();
      int binSize = (int) (Math.pow(2, bitDepth) / numBins);
      int contrastMin = -1;
      int contrastMax = -1;
      // Need to interpolate into the histogram to get the correct
      // contrast min/max values. This number is the number of the pixel
      // whose intensity we want.
      double pixelCount = width * height * .01 * extremaPercentage;
      // Start with finding the min.
      double curCount = pixelCount;
      for (int i = 0; i < histogram.length; ++i) {
         if (curCount >= histogram[i]) {
            curCount -= histogram[i];
            continue;
         }
         // The target min pixel intensity is somewhere in this bucket.
         // Linearly interpolate between the min and max values of the
         // bucket based on the current count. Note that at the ends of
         // the histogram, the interpolation limits are minVal/maxVal.
         int interpMin = Math.max(minVal, i * binSize);
         int interpMax = Math.min((i + 1) * binSize, maxVal);
         double frac = curCount / histogram[i];
         contrastMin = (int) (frac * (interpMax - interpMin) + interpMin);
         break;
      }
      // Now find the max. Same as above, except we start counting from the
      // top of the histogram instead of the bottom.
      curCount = pixelCount;
      for (int i = histogram.length - 1; i >= 0; --i) {
         if (curCount >= histogram[i]) {
            curCount -= histogram[i];
            continue;
         }
         int interpMin = Math.max(minVal, i * binSize);
         int interpMax = Math.min((i + 1) * binSize, maxVal);
         double frac = curCount / histogram[i];
         contrastMax = (int) (frac * (interpMax - interpMin) + interpMin);
         break;
      }
      HistogramData result = new HistogramData(histogram, minVal, maxVal,
            contrastMin, contrastMax,
            (int) (meanVal / (width * height)), bitDepth, binSize);
      return result;
   }
}
