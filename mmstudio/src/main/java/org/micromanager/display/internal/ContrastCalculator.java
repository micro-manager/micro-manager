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

   /**
    * This class just encapsulates necessary state for doing the calculations.
    */
   private static class InternalCalculator {
      private final int width_;
      private final int height_;
      private final int bytesPerPixel_;
      private final int numComponents_;
      private final int component_;
      private final int numBins_;
      private final int binSize_;
      private final int range_;
      private final int[] histogram_;
      private final byte[] maskPixels_;
      private final Rectangle roiRect_;
      private final int xMin_;
      private final int xMax_;
      private final int yMin_;
      private final int yMax_;
      private final Object pixels_;
      private final double extremaPercentage_;
      private final int depthPower_;

      private int minVal_;
      private int maxVal_;
      private long meanVal_;
      private int numPixels_;

      public InternalCalculator(Image image, ImagePlus plus, int component,
            int binPower, int depthPower, double extremaPercentage) {
         width_ = image.getWidth();
         height_ = image.getHeight();
         bytesPerPixel_ = image.getBytesPerPixel();
         numComponents_ = image.getNumComponents();
         component_ = component;
         depthPower_ = depthPower;
         extremaPercentage_ = extremaPercentage;

         minVal_ = Integer.MAX_VALUE;
         maxVal_ = Integer.MIN_VALUE;
         meanVal_ = 0;
         numPixels_ = 0;
         numBins_ = (int) Math.pow(2, binPower);
         range_ = (int) Math.pow(2, depthPower);
         binSize_ = Math.max(1, range_ / numBins_);

         histogram_ = new int[numBins_];

         pixels_ = image.getRawPixels();

         // Get ROI information. This consists of a rectangle containing the
         // ROI, and then, for non-rectangular ROIs, a pixel mask (fortunately
         // not a *bit* mask though; each byte is one pixel).
         if (plus != null) {
            if (plus.getMask() != null) {
               maskPixels_ = (byte[]) (plus.getMask().getPixels());
            }
            else {
               maskPixels_ = null;
            }
            Roi roi = plus.getRoi();
            if (roi != null) {
               roiRect_ = roi.getBounds();
            }
            else {
               roiRect_ = null;
            }
         }
         else {
            maskPixels_ = null;
            roiRect_ = null;
         }

         if (roiRect_ == null) {
            xMin_ = 0;
            xMax_ = width_;
            yMin_ = 0;
            yMax_ = height_;
         }
         else {
            xMin_ = roiRect_.x;
            xMax_ = roiRect_.x + roiRect_.width;
            yMin_ = roiRect_.y;
            yMax_ = roiRect_.y + roiRect_.height;
         }
      }

      public HistogramData calculate() {
         // As an optimization, we split out the different "variants" of the
         // inner loop that populates the histogram_ array and sums the pixel
         // intensities. There's a few different variants in play here that
         // make a combinatorial explosion of function variants:
         // 1) Depending on the pixel type (8-bit vs. 16-bit)
         // 2) Depending on how many components are in the image (1 vs.
         //    multiple)
         // 3) Depending on if we have an ROI or not.
         // Single-component calculations are vastly faster than multi-
         // component calculations, as we can use much simpler logic for
         // calculating our index into the pixels_ array. Presence of a
         // non-rectangular ROI costs about a 50% slowdown (but typically ROIs
         // also vastly reduce the area under consideration).
         // I don't like having 8 different-but-almost-identical functions
         // either.
         if (numComponents_ == 1) {
            if (pixels_ instanceof byte[]) {
               if (bytesPerPixel_ != 1) {
                  // This should never happen (it would indicate a sparsely-
                  // packed image), but if it did our calculations would be
                  // wrong.
                  throw new IllegalArgumentException("Improperly-packed pixel format");
               }
               if (maskPixels_ != null) {
                  calculate8BitMaskedSingleComponent((byte[]) pixels_);
               }
               else {
                  calculate8BitSingleComponent((byte[]) pixels_);
               }
            }
            else if (pixels_ instanceof short[]) {
               if (bytesPerPixel_ != 2) {
                  // This should never happen, but if it did our calculations
                  // would be wrong.
                  throw new IllegalArgumentException("Improperly-packed pixel format");
               }
               if (maskPixels_ != null) {
                  calculate16BitMaskedSingleComponent((short[]) pixels_);
               }
               else {
                  calculate16BitSingleComponent((short[]) pixels_);
               }
            }
            else {
               throw new IllegalArgumentException("Unrecognized pixel format " + pixels_);
            }
         }
         else {
            if (pixels_ instanceof byte[]) {
               if (maskPixels_ != null) {
                  calculate8BitMaskedMultiComponent((byte[]) pixels_);
               }
               else {
                  calculate8BitMultiComponent((byte[]) pixels_);
               }
            }
            else if (pixels_ instanceof short[]) {
               if (maskPixels_ != null) {
                  calculate16BitMaskedMultiComponent((short[]) pixels_);
               }
               else {
                  calculate16BitMultiComponent((short[]) pixels_);
               }
            }
            else {
               throw new IllegalArgumentException("Unrecognized pixel format " + pixels_);
            }
         }

         // Calculate number of pixels and min/max values. The min and max are
         // subject to inaccuracies because we only know what bin the pixel
         // is in, not the intensity of the pixel itself. Not typically a
         // problem as our bin size is usually 1.
         for (int i = 0; i < histogram_.length; ++i) {
            numPixels_ += histogram_[i];
            if (minVal_ == Integer.MAX_VALUE) {
               minVal_ = i * binSize_;
            }
         }
         for (int i = histogram_.length - 1; i >= 0; --i) {
            if (histogram_[i] > 0 && maxVal_ == Integer.MIN_VALUE) {
               maxVal_ = (i + 1) * binSize_ - 1;
               break;
            }
         }

         int contrastMin = -1;
         int contrastMax = -1;
         // Need to interpolate into the histogram to get the correct
         // contrast min/max values. This number is the number of the pixel
         // whose intensity we want.
         double pixelCount = width_ * height_ * .01 * extremaPercentage_;
         // Start with finding the min.
         double curCount = pixelCount;
         for (int i = 0; i < histogram_.length; ++i) {
            if (curCount >= histogram_[i]) {
               curCount -= histogram_[i];
               continue;
            }
            // The target min pixel intensity is somewhere in this bucket.
            // Linearly interpolate between the min and max values of the
            // bucket based on the current count. Note that at the ends of
            // the histogram, the interpolation limits are minVal/maxVal.
            int interpMin = Math.max(minVal_, i * binSize_);
            int interpMax = Math.min((i + 1) * binSize_, maxVal_);
            double frac = curCount / histogram_[i];
            contrastMin = (int) (frac * (interpMax - interpMin) + interpMin);
            break;
         }
         // Now find the max. Same as above, except we start counting from the
         // top of the histogram instead of the bottom.
         curCount = pixelCount;
         for (int i = histogram_.length - 1; i >= 0; --i) {
            if (curCount >= histogram_[i]) {
               curCount -= histogram_[i];
               continue;
            }
            int interpMin = Math.max(minVal_, i * binSize_);
            int interpMax = Math.min((i + 1) * binSize_, maxVal_);
            double frac = curCount / histogram_[i];
            contrastMax = (int) (frac * (interpMax - interpMin) + interpMin);
            break;
         }

         if (numPixels_ > 0) {
            meanVal_ = meanVal_ / numPixels_;
         }

         HistogramData result = new HistogramData(histogram_,
               numPixels_, minVal_, maxVal_, contrastMin,
               contrastMax, (int) meanVal_, depthPower_, binSize_);
         return result;
      }

      private void calculate8BitSingleComponent(byte[] pixels) {
         for (int y = yMin_; y < yMax_; ++y) {
            int base = y * width_;
            for (int x = xMin_; x < xMax_; ++x) {
               int index = base + x;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x000000ff;
               histogram_[pixelVal / binSize_]++;
               meanVal_ += pixelVal;
            }
         }
      }

      private void calculate8BitMaskedSingleComponent(byte[] pixels) {
         for (int y = yMin_; y < yMax_; ++y) {
            int base = y * width_;
            for (int x = xMin_; x < xMax_; ++x) {
               // This mask check slows us down by 2-3x compared to the
               // unmasked version of the function (assuming we have to scan
               // the same number of pixels).
               int maskIndex = (y - roiRect_.y) * roiRect_.width +
                  (x - roiRect_.x);
               if (maskPixels_[maskIndex] == 0) {
                  // Outside of the mask.
                  continue;
               }
               int index = base + x;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x000000ff;
               histogram_[pixelVal / binSize_]++;
               meanVal_ += pixelVal;
            }
         }
      }

      /**
       * HACK: completely identical to calculate8Bit except for the type of the
       * pixels array and the unsigned conversion mask.
       */
      private void calculate16BitSingleComponent(short[] pixels) {
         for (int y = yMin_; y < yMax_; ++y) {
            int base = y * width_;
            for (int x = xMin_; x < xMax_; ++x) {
               int index = base + x;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x0000ffff;
               histogram_[pixelVal / binSize_]++;
               meanVal_ += pixelVal;
            }
         }
      }

      /**
       * HACK: completely identical to calculate8BitMasked except for the type
       * of the pixels array and the unsigned conversion mask.
       */
      private void calculate16BitMaskedSingleComponent(short[] pixels) {
         for (int y = yMin_; y < yMax_; ++y) {
            int base = y * width_;
            for (int x = xMin_; x < xMax_; ++x) {
               int maskIndex = (y - roiRect_.y) * roiRect_.width +
                  (x - roiRect_.x);
               if (maskPixels_[maskIndex] == 0) {
                  // Outside of the mask.
                  continue;
               }
               int index = base + x;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x0000ffff;
               histogram_[pixelVal / binSize_]++;
               meanVal_ += pixelVal;
            }
         }
      }


      private void calculate8BitMultiComponent(byte[] pixels) {
         for (int x = xMin_; x < xMax_; ++x) {
            for (int y = yMin_; y < yMax_; ++y) {
               // This index calculation is expensive and slows us down by
               // about a factor of 2 compared to the single-component version.
               int index = (y * width_ + x) * bytesPerPixel_ + component_;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x000000ff;
               histogram_[pixelVal / binSize_]++;
               meanVal_ += pixelVal;
            }
         }
      }

      private void calculate8BitMaskedMultiComponent(byte[] pixels) {
         for (int x = xMin_; x < xMax_; ++x) {
            for (int y = yMin_; y < yMax_; ++y) {
               int maskIndex = (y - roiRect_.y) * roiRect_.width +
                  (x - roiRect_.x);
               if (maskPixels_[maskIndex] == 0) {
                  // Outside of the mask.
                  continue;
               }
               int index = (y * width_ + x) * bytesPerPixel_ + component_;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x000000ff;
               histogram_[pixelVal / binSize_]++;
               meanVal_ += pixelVal;
            }
         }
      }

      /**
       * HACK: completely identical to calculate8Bit except for the type of the
       * pixels array and the unsigned conversion mask.
       */
      private void calculate16BitMultiComponent(short[] pixels) {
         // Since we step 2 bytes at a time in a short[] array.
         int stride = bytesPerPixel_ / 2;
         for (int x = xMin_; x < xMax_; ++x) {
            for (int y = yMin_; y < yMax_; ++y) {
               int index = (y * width_ + x) * stride + component_;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x0000ffff;
               histogram_[pixelVal / binSize_]++;
               meanVal_ += pixelVal;
            }
         }
      }

      /**
       * HACK: completely identical to calculate8BitMasked except for the type
       * of the pixels array and the unsigned conversion mask.
       */
      private void calculate16BitMaskedMultiComponent(short[] pixels) {
         // Since we step 2 bytes at a time in a short[] array.
         int stride = bytesPerPixel_ / 2;
         for (int x = xMin_; x < xMax_; ++x) {
            for (int y = yMin_; y < yMax_; ++y) {
               int maskIndex = (y - roiRect_.y) * roiRect_.width +
                  (x - roiRect_.x);
               if (maskPixels_[maskIndex] == 0) {
                  // Outside of the mask.
                  continue;
               }
               int index = (y * width_ + x) * stride + component_;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x0000ffff;
               histogram_[pixelVal / binSize_]++;
               meanVal_ += pixelVal;
            }
         }
      }
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
      return new InternalCalculator(image, plus, component, binPower,
            depthPower, extremaPercentage).calculate();
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
