package org.micromanager.display.inspector.internal.panels.intensity;

import org.micromanager.display.internal.imagestats.ComponentStats;

/**
 * Converts between histogram bin indices (the long values stored in
 * ComponentDisplaySettings for float images) and actual pixel values.
 *
 * <p>For float images the long stored in ComponentDisplaySettings represents
 * a bin index (0..binCount). This gives ~256 discrete slider positions
 * regardless of the pixel-value dynamic range.
 */
final class FloatCoordinateMapper {
   private final double rangeMin_;
   private final double binWidth_;
   private final int binCount_;

   FloatCoordinateMapper(ComponentStats stats) {
      rangeMin_ = stats.getHistogramRangeMinDouble();
      binWidth_ = stats.getBinWidthDouble();
      binCount_ = stats.getHistogramBinCount();
   }

   long pixelValueToBinIndex(double pixelValue) {
      if (binWidth_ == 0.0) {
         return 0;
      }
      long idx = Math.round((pixelValue - rangeMin_) / binWidth_);
      return Math.max(0, Math.min(binCount_, idx));
   }

   double binIndexToPixelValue(long binIndex) {
      return rangeMin_ + binIndex * binWidth_;
   }

   String formatBinIndex(long binIndex) {
      double v = binIndexToPixelValue(binIndex);
      if (Math.abs(binWidth_) < 0.01) {
         return String.format("%.4g", v);
      }
      if (Math.abs(binWidth_) < 0.1) {
         return String.format("%.3g", v);
      }
      return String.format("%.2g", v);
   }

   int getBinCount() {
      return binCount_;
   }

   double getRangeMin() {
      return rangeMin_;
   }

   double getBinWidth() {
      return binWidth_;
   }
}
