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

import ij.gui.Roi;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.awt.Rectangle;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;

import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class calculates histograms and contrast settings for images.
 */
public class ContrastCalculator {

   /**
    * @param image Image whose pixel data we will calculate the histogram for.
    * @param plus ImageJ ImagePlus object, needed for constraining
    *        the histogram area to the current ROI. May be null, in which case
    *        we assume there is no ROI.
    * @param component The component number of the image for RGB or other
    *        multi-component images (use 0 for grayscale images).
    * @param binPower The number of bins in the histogram, expressed as a power
    *        of 2. E.g. 8 means to have 256 bins.
    * @param depthPower The range of allowed values in the histogram, expressed
    *        as a power of 2. E.g. 10 means that values from 0 to 1023 will be
    *        allowed and anything outside that range is not included in the
    *        histogram.
    * @param extremaPercentage Percentage of pixels to ignore when calculating
    *        the contrast min/max values.
    */
   public static HistogramData calculateHistogram(Image image,
         ImagePlus plus, int component, int binPower, int depthPower,
         double extremaPercentage) {
      int channel = image.getCoords().getChannel();
      int width = image.getWidth();
      int height = image.getHeight();
      int bytesPerPixel = image.getBytesPerPixel();

      int minVal = -1;
      int maxVal = -1;
      long meanVal = 0;
      int numPixels = 0;
      int numBins = (int) Math.pow(2, binPower);
      int range = (int) Math.pow(2, depthPower);
      int binSize = Math.max(1, range / numBins);

      int[] histogram = new int[numBins];

      Object pixels = image.getRawPixels();
      int divisor = image.getNumComponents();
      int exponent = 8;
      if (pixels instanceof short[]) {
         divisor *= 2;
         exponent = 16;
      }

      // Get ROI information. This consists of a rectangle containing the
      // ROI, and then, for non-rectangular ROIs, a pixel mask (fortunately
      // not a *bit* mask though; each byte is one pixel).
      byte[] maskPixels = null;
      Rectangle roiRect = null;
      if (plus != null) {
         if (plus.getMask() != null) {
            maskPixels = (byte[]) (plus.getMask().getPixels());
         }
         Roi roi = plus.getRoi();
         if (roi != null) {
            roiRect = roi.getBounds();
         }
      }

      int xMin = 0;
      int xMax = width;
      int yMin = 0;
      int yMax = height;
      if (roiRect != null) {
         xMin = roiRect.x;
         xMax = roiRect.x + roiRect.width;
         yMin = roiRect.y;
         yMax = roiRect.y + roiRect.height;
      }

      for (int x = xMin; x < xMax; ++x) {
         for (int y = yMin; y < yMax; ++y) {
            if (maskPixels != null) {
               int index = (y - roiRect.y) * roiRect.width + (x - roiRect.x);
               if (maskPixels[index] == 0) {
                  // Outside of the mask.
                  continue;
               }
            }
            numPixels++;
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
            if (pixelVal >= 0 && pixelVal < range) {
               histogram[pixelVal / binSize]++;
            }
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

      if (numPixels > 0) {
         meanVal = meanVal / numPixels;
      }

      HistogramData result = new HistogramData(histogram, numPixels,
            minVal, maxVal, contrastMin, contrastMax,
            (int) meanVal, depthPower, binSize);
      return result;
   }

   /**
    * Calls calculateHistogram with the appropriate bit depth per the provided
    * DisplaySettings.
    */
   public static HistogramData calculateHistogramWithSettings(Image image,
         ImagePlus plus, int component, DisplaySettings settings) {
      int bitDepth = settings.getSafeBitDepthIndex(
            image.getCoords().getChannel(), 0);
      if (bitDepth == 0) {
         // Use camera depth.
         bitDepth = image.getMetadata().getBitDepth();
      }
      else {
         // Add 3 to convert from index to power of 2.
         bitDepth += 3;
      }
      // 8 means 256 bins.
      int binPower = Math.min(8, bitDepth);
      Double percentage = settings.getExtremaPercentage();
      if (percentage == null) {
         percentage = 0.0;
      }
      return calculateHistogram(image, plus, component, binPower, bitDepth,
            percentage);
   }
}
