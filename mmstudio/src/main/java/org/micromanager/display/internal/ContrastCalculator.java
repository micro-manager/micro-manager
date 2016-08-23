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

import ij.ImagePlus;
import ij.gui.Roi;
import java.awt.Rectangle;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.HistogramData;

/**
 * This class calculates histograms and contrast settings for images.
 */
public final class ContrastCalculator {

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
      private final int xStart_;
      private final int xStop_;
      private final int yStart_;
      private final int yStop_;
      private final Object pixels_;
      private final double extremaPercentage_;
      private final int depthPower_;
      private final boolean shouldCalcStdDev_;
      private final boolean shouldScaleWithROI_;

      private int minVal_; // Min value in ROI
      private int maxVal_; // Min value in ROI
      private long sumVal_; // Sum of values in ROI
      private int numPixels_; // Number of pixels that are not out of range
      private int numAllPixels_; // Number of pixels in the ROI

      public InternalCalculator(Image image, ImagePlus plus, int component,
            int binPower, int depthPower, double extremaPercentage,
            boolean shouldCalcStdDev, boolean shouldScaleWithROI) {
         width_ = image.getWidth();
         height_ = image.getHeight();
         bytesPerPixel_ = image.getBytesPerPixel();
         numComponents_ = image.getNumComponents();
         component_ = component;
         depthPower_ = depthPower;
         extremaPercentage_ = extremaPercentage;
         shouldCalcStdDev_ = shouldCalcStdDev;
         shouldScaleWithROI_ = shouldScaleWithROI;

         minVal_ = Integer.MAX_VALUE;
         maxVal_ = Integer.MIN_VALUE;
         sumVal_ = 0;
         numPixels_ = 0;
         numBins_ = (int) Math.pow(2, binPower);
         range_ = (int) Math.pow(2, depthPower);
         binSize_ = Math.max(1, range_ / numBins_);

         pixels_ = image.getRawPixels();

         int samplePower;
         if (pixels_ instanceof byte[]) {
            samplePower = 8;
         }
         else if (pixels_ instanceof short[]) {
            samplePower = 16;
         }
         else {
            throw new IllegalArgumentException("Unrecognized pixel format " + pixels_);
         }

         // Number of bins such that we won't get an
         // ArrayIndexOutOfBoundsException even if some of the data exceeds
         // what is expected from depthPower.
         // For example, if we have a short[] image (samplePower = 16) whose
         // data depth is supposed to be 14-bit (depthPower), and a 256-bin
         // histogram is requested (binPower = 8), then the 256 bins are mapped
         // to a 14-bit intensity range. But in order to accomodate
         // out-of-bounds intensity values (up to 16-bit), we need 2^10 = 1024
         // bins (safeBinPower = 10).
         final int safeBinPower = binPower + (samplePower - depthPower);
         final int safeNumBins = (int) Math.pow(2, safeBinPower);

         // We allocate safeNumBins, but only the numBins portion of the result
         // is used.
         histogram_ = new int[safeNumBins];

         // Get ROI information. This consists of a rectangle containing the
         // ROI, and then, for non-rectangular ROIs, a pixel mask (fortunately
         // not a *bit* mask though; each byte is one pixel).
         if (plus != null && shouldScaleWithROI_) {
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
            xStart_ = 0;
            xStop_ = width_;
            yStart_ = 0;
            yStop_ = height_;
         }
         else {
            xStart_ = roiRect_.x;
            xStop_ = roiRect_.x + roiRect_.width;
            yStart_ = roiRect_.y;
            yStop_ = roiRect_.y + roiRect_.height;
         }

         numAllPixels_ = (xStop_ - xStart_) * (yStop_ - yStart_);
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
         // Note that we use the full "safe" range of histogram_, not just
         // numBins_, so that min and max are correct even if some or all
         // pixels are out of range.
         for (int i = 0; i < histogram_.length; ++i) {
            numPixels_ += histogram_[i];
            if (minVal_ == Integer.MAX_VALUE && histogram_[i] > 0) {
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
         double pixelCount = numAllPixels_ * .01 * extremaPercentage_;
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
         // Fix contrast min/max in case they are beyond the histogram range
         contrastMin = Math.min(range_ - 1, contrastMin);
         contrastMax = Math.min(range_ - 1, contrastMax);

         int meanVal = (int) (sumVal_ / numAllPixels_);

         // Algorithm adapted from ImageJ's standard deviation calculations.
         double stdDev = -1;
         if (shouldCalcStdDev_) {
            double sum = 0;
            double sumSquared = 0;
            for (int i = 0; i < histogram_.length; ++i) {
               // Prevent overflow when we square the histogram value.
               double tmp = i;
               sum += histogram_[i] * tmp;
               sumSquared += histogram_[i] * (tmp * tmp);
            }
            stdDev = (numAllPixels_ * sumSquared - (sum * sum)) / numAllPixels_;
            if (stdDev > 0) {
               stdDev = Math.sqrt(stdDev / numAllPixels_);
            }
            else {
               stdDev = -1;
            }
         }

         HistogramData result = new HistogramData(histogram_,
               numPixels_, minVal_, maxVal_, contrastMin,
               contrastMax, meanVal, stdDev, depthPower_, binSize_);
         return result;
      }

      private void calculate8BitSingleComponent(byte[] pixels) {
         for (int y = yStart_; y < yStop_; ++y) {
            int base = y * width_;
            for (int x = xStart_; x < xStop_; ++x) {
               int index = base + x;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x000000ff;
               histogram_[pixelVal / binSize_]++;
               sumVal_ += pixelVal;
            }
         }
      }

      private void calculate8BitMaskedSingleComponent(byte[] pixels) {
         for (int y = yStart_; y < yStop_; ++y) {
            int base = y * width_;
            for (int x = xStart_; x < xStop_; ++x) {
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
               sumVal_ += pixelVal;
            }
         }
      }

      /**
       * HACK: completely identical to calculate8Bit except for the type of the
       * pixels array and the unsigned conversion mask.
       */
      private void calculate16BitSingleComponent(short[] pixels) {
         for (int y = yStart_; y < yStop_; ++y) {
            int base = y * width_;
            for (int x = xStart_; x < xStop_; ++x) {
               int index = base + x;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x0000ffff;
               histogram_[pixelVal / binSize_]++;
               sumVal_ += pixelVal;
            }
         }
      }

      /**
       * HACK: completely identical to calculate8BitMasked except for the type
       * of the pixels array and the unsigned conversion mask.
       */
      private void calculate16BitMaskedSingleComponent(short[] pixels) {
         for (int y = yStart_; y < yStop_; ++y) {
            int base = y * width_;
            for (int x = xStart_; x < xStop_; ++x) {
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
               sumVal_ += pixelVal;
            }
         }
      }


      private void calculate8BitMultiComponent(byte[] pixels) {
         for (int x = xStart_; x < xStop_; ++x) {
            for (int y = yStart_; y < yStop_; ++y) {
               // This index calculation is expensive and slows us down by
               // about a factor of 2 compared to the single-component version.
               int index = (y * width_ + x) * bytesPerPixel_ + component_;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x000000ff;
               histogram_[pixelVal / binSize_]++;
               sumVal_ += pixelVal;
            }
         }
      }

      private void calculate8BitMaskedMultiComponent(byte[] pixels) {
         for (int x = xStart_; x < xStop_; ++x) {
            for (int y = yStart_; y < yStop_; ++y) {
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
               sumVal_ += pixelVal;
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
         for (int x = xStart_; x < xStop_; ++x) {
            for (int y = yStart_; y < yStop_; ++y) {
               int index = (y * width_ + x) * stride + component_;
               // Java doesn't have unsigned number types, so we have to
               // manually convert; otherwise large numbers will set the sign
               // bit and show as negative.
               // This conversion logic is copied from ImageUtils.unsignedValue
               int pixelVal = ((int) pixels[index]) & 0x0000ffff;
               histogram_[pixelVal / binSize_]++;
               sumVal_ += pixelVal;
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
         for (int x = xStart_; x < xStop_; ++x) {
            for (int y = yStart_; y < yStop_; ++y) {
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
               sumVal_ += pixelVal;
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
         double extremaPercentage, boolean shouldCalcStdDev, boolean shouldScaleWithROI) {
      return new InternalCalculator(image, plus, component, binPower,
            depthPower, extremaPercentage, shouldCalcStdDev, shouldScaleWithROI).calculate();
   }

   /**
    * Calls calculateHistogram with the appropriate bit depth per the provided
    * DisplaySettings.
    */
   public static HistogramData calculateHistogramWithSettings(Image image,
         ImagePlus plus, int component, DisplaySettings settings) {
      // We span the full allowed intensity values.
      int bitDepth = 0;
      try {
         bitDepth = image.getMetadata().getBitDepth();
      }
      catch (NullPointerException e) {
         switch (image.getBytesPerPixel()) {
            case 1: bitDepth = 8; break;
            case 2: bitDepth = 16; break;
            case 4: bitDepth = 8; break;
         }
      }
      Double percentage = settings.getExtremaPercentage();
      if (percentage == null) {
         percentage = 0.0;
      }
      Boolean shouldStdDev = settings.getShouldCalculateStdDev();
      if (shouldStdDev == null) {
         shouldStdDev = false;
      }
      Boolean shouldScaleWithROI = settings.getShouldScaleWithROI();
      if (shouldScaleWithROI == null) {
         shouldScaleWithROI = true;
      }
      // We use the bit depth as the bin power, so that each individual
      // intensity gets its own bin.
      return calculateHistogram(image, plus, component, bitDepth, bitDepth,
            percentage, shouldStdDev, shouldScaleWithROI);
   }
}
