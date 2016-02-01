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
import org.micromanager.display.HistogramData;

import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class calculates histograms and contrast settings for images.
 */
public class ContrastCalculator {

   /** Simple container class for parameters used when processing images. */
   private static class InternalStats {
      private int width;
      private int height;
      private int bytesPerPixel;
      private int numComponents;
      private int component;
      private int minVal;
      private int maxVal;
      private long meanVal;
      private int numPixels;
      private int numBins;
      private int binSize;
      private int range;
      private int[] histogram;
      private byte[] maskPixels;
      private Rectangle roiRect;
      private int xMin;
      private int xMax;
      private int yMin;
      private int yMax;
   }

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
      InternalStats stats = new InternalStats();
      stats.width = image.getWidth();
      stats.height = image.getHeight();
      stats.bytesPerPixel = image.getBytesPerPixel();
      stats.numComponents = image.getNumComponents();
      stats.component = component;

      stats.minVal = -1;
      stats.maxVal = -1;
      stats.meanVal = 0;
      stats.numPixels = 0;
      stats.numBins = (int) Math.pow(2, binPower);
      stats.range = (int) Math.pow(2, depthPower);
      stats.binSize = Math.max(1, stats.range / stats.numBins);

      stats.histogram = new int[stats.numBins];

      Object pixels = image.getRawPixels();

      // Get ROI information. This consists of a rectangle containing the
      // ROI, and then, for non-rectangular ROIs, a pixel mask (fortunately
      // not a *bit* mask though; each byte is one pixel).
      stats.maskPixels = null;
      stats.roiRect = null;
      if (plus != null) {
         if (plus.getMask() != null) {
            stats.maskPixels = (byte[]) (plus.getMask().getPixels());
         }
         Roi roi = plus.getRoi();
         if (roi != null) {
            stats.roiRect = roi.getBounds();
         }
      }

      stats.xMin = 0;
      stats.xMax = stats.width;
      stats.yMin = 0;
      stats.yMax = stats.height;
      if (stats.roiRect != null) {
         stats.xMin = stats.roiRect.x;
         stats.xMax = stats.roiRect.x + stats.roiRect.width;
         stats.yMin = stats.roiRect.y;
         stats.yMax = stats.roiRect.y + stats.roiRect.height;
      }

      // As an optimization, we split out the different "variants" of the
      // inner loop, so we don't have to examine the type of the pixels array
      // once for every component of every pixel.
      if (pixels instanceof byte[]) {
         stats = calculate8Bit((byte[]) pixels, stats);
      }
      else if (pixels instanceof short[]) {
         stats = calculate16Bit((short[]) pixels, stats);
      }
      else {
         throw new IllegalArgumentException("Unrecognized pixel format " + pixels);
      }

      int contrastMin = -1;
      int contrastMax = -1;
      // Need to interpolate into the histogram to get the correct
      // contrast min/max values. This number is the number of the pixel
      // whose intensity we want.
      double pixelCount = stats.width * stats.height * .01 * extremaPercentage;
      // Start with finding the min.
      double curCount = pixelCount;
      for (int i = 0; i < stats.histogram.length; ++i) {
         if (curCount >= stats.histogram[i]) {
            curCount -= stats.histogram[i];
            continue;
         }
         // The target min pixel intensity is somewhere in this bucket.
         // Linearly interpolate between the min and max values of the
         // bucket based on the current count. Note that at the ends of
         // the histogram, the interpolation limits are minVal/maxVal.
         int interpMin = Math.max(stats.minVal, i * stats.binSize);
         int interpMax = Math.min((i + 1) * stats.binSize, stats.maxVal);
         double frac = curCount / stats.histogram[i];
         contrastMin = (int) (frac * (interpMax - interpMin) + interpMin);
         break;
      }
      // Now find the max. Same as above, except we start counting from the
      // top of the histogram instead of the bottom.
      curCount = pixelCount;
      for (int i = stats.histogram.length - 1; i >= 0; --i) {
         if (curCount >= stats.histogram[i]) {
            curCount -= stats.histogram[i];
            continue;
         }
         int interpMin = Math.max(stats.minVal, i * stats.binSize);
         int interpMax = Math.min((i + 1) * stats.binSize, stats.maxVal);
         double frac = curCount / stats.histogram[i];
         contrastMax = (int) (frac * (interpMax - interpMin) + interpMin);
         break;
      }

      if (stats.numPixels > 0) {
         stats.meanVal = stats.meanVal / stats.numPixels;
      }

      HistogramData result = new HistogramData(stats.histogram,
            stats.numPixels, stats.minVal, stats.maxVal, contrastMin,
            contrastMax, (int) stats.meanVal, depthPower, stats.binSize);
      return result;
   }

   private static InternalStats calculate8Bit(byte[] pixels,
         InternalStats stats) {
      for (int x = stats.xMin; x < stats.xMax; ++x) {
         for (int y = stats.yMin; y < stats.yMax; ++y) {
            if (stats.maskPixels != null) {
               int index = (y - stats.roiRect.y) * stats.roiRect.width +
                  (x - stats.roiRect.x);
               if (stats.maskPixels[index] == 0) {
                  // Outside of the mask.
                  continue;
               }
            }
            stats.numPixels++;
            int index = y * stats.width + x + stats.component;
            // Java doesn't have unsigned number types, so we have to
            // manually convert; otherwise large numbers will set the sign
            // bit and show as negative.
            int pixelVal = ImageUtils.unsignedValue(pixels[index]);
            if (pixelVal >= 0 && pixelVal < stats.range) {
               stats.histogram[pixelVal / stats.binSize]++;
            }
            if (stats.minVal == -1) {
               stats.minVal = pixelVal;
               stats.maxVal = pixelVal;
            }
            else {
               stats.minVal = Math.min(stats.minVal, pixelVal);
               stats.maxVal = Math.max(stats.maxVal, pixelVal);
            }
            stats.meanVal += pixelVal;
         }
      }
      return stats;
   }

   /**
    * HACK: completely identical to calculate8Bit except for the type of the
    * pixels array.
    */
   private static InternalStats calculate16Bit(short[] pixels,
         InternalStats stats) {
      for (int x = stats.xMin; x < stats.xMax; ++x) {
         for (int y = stats.yMin; y < stats.yMax; ++y) {
            if (stats.maskPixels != null) {
               int index = (y - stats.roiRect.y) * stats.roiRect.width +
                  (x - stats.roiRect.x);
               if (stats.maskPixels[index] == 0) {
                  // Outside of the mask.
                  continue;
               }
            }
            stats.numPixels++;
            int index = y * stats.width + x + stats.component;
            // Java doesn't have unsigned number types, so we have to
            // manually convert; otherwise large numbers will set the sign
            // bit and show as negative.
            int pixelVal = ImageUtils.unsignedValue(pixels[index]);
            if (pixelVal >= 0 && pixelVal < stats.range) {
               stats.histogram[pixelVal / stats.binSize]++;
            }
            if (stats.minVal == -1) {
               stats.minVal = pixelVal;
               stats.maxVal = pixelVal;
            }
            else {
               stats.minVal = Math.min(stats.minVal, pixelVal);
               stats.maxVal = Math.max(stats.maxVal, pixelVal);
            }
            stats.meanVal += pixelVal;
         }
      }
      return stats;
   }

   /**
    * Calls calculateHistogram with the appropriate bit depth per the provided
    * DisplaySettings.
    */
   public static HistogramData calculateHistogramWithSettings(Image image,
         ImagePlus plus, int component, DisplaySettings settings) {
      // We span the full allowed intensity values.
      int bitDepth = image.getMetadata().getBitDepth();
      Double percentage = settings.getExtremaPercentage();
      if (percentage == null) {
         percentage = 0.0;
      }
      // We use the bit depth as the bin power, so that each individual
      // intensity gets its own bin.
      return calculateHistogram(image, plus, component, bitDepth, bitDepth,
            percentage);
   }
}
