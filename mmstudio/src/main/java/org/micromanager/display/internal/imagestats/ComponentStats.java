// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.display.internal.imagestats;

import com.google.common.base.Preconditions;
import java.util.Arrays;

/**
 * Statistics of a single color component of an image; immutable.
 *
 * @author Mark A. Tsuchida
 */
public final class ComponentStats {
   private final Integer bitDepth_;
   private final long[] histogram_;
   private final int binWidthPowerOf2_;
   // For float images: actual pixel-value start of bin 1 and width of each bin.
   // For integer images these default to 0.0 and (1 << binWidthPowerOf2_) respectively,
   // reproducing the previous behavior exactly.
   private final double rangeMin_;
   private final double binWidthFloat_;
   private final boolean isFloat_;
   private final long pixelCount_;
   private final long pixelCountExcludingZeros_;
   private final boolean usedROI_;
   private final long minimum_;
   private final long minimumExcludingZeros_;
   private final long maximum_;
   private final long sum_;
   private final long sumOfSquares_;
   // Untruncated counterparts of the fields above, for float images. NaN when unset, in
   // which case the long values apply. The longs cannot represent float statistics (a mean
   // of -0.16 rounds to 0), so the real values are carried alongside rather than replacing
   // them, leaving the integer bin/quantile machinery untouched.
   private final double floatMinimum_;
   private final double floatMinimumExcludingZeros_;
   private final double floatMaximum_;
   private final double floatSum_;
   private final double floatSumOfSquares_;
   private final transient long[] cumulativeDistrib_;

   public static class Builder {
      private Integer bitDepth_;
      private long[] histogram_;
      private int binWidthPowerOf2_;
      private double rangeMin_ = 0.0;
      private double binWidthFloat_ = -1.0; // sentinel: -1 → use 1 << binWidthPowerOf2_
      private boolean isFloat_ = false;
      private long pixelCount_;
      private long pixelCountExcludingZeros_;
      private boolean usedROI_;
      private long minimum_;
      private long minimumExcludingZeros_;
      private long maximum_;
      private long sum_;
      private long sumOfSquares_;
      private double floatMinimum_ = Double.NaN;
      private double floatMinimumExcludingZeros_ = Double.NaN;
      private double floatMaximum_ = Double.NaN;
      private double floatSum_ = Double.NaN;
      private double floatSumOfSquares_ = Double.NaN;

      private Builder() {
      }

      public Builder bitDepth(Integer depth) {
         Preconditions.checkArgument(depth == null || depth >= 0);
         if (depth == 0) {
            depth = null;
         }
         bitDepth_ = depth;
         return this;
      }

      public Builder histogram(long[] binsIncludingOutOfRange, int binWidthPowerOf2) {
         Preconditions.checkArgument(binsIncludingOutOfRange.length >= 2);
         histogram_ = binsIncludingOutOfRange;
         binWidthPowerOf2_ = binWidthPowerOf2;
         return this;
      }

      public Builder rangeMin(double min) {
         rangeMin_ = min;
         return this;
      }

      public Builder binWidthFloat(double width) {
         Preconditions.checkArgument(width >= 0.0);
         binWidthFloat_ = width;
         return this;
      }

      public Builder isFloat(boolean isFloat) {
         isFloat_ = isFloat;
         return this;
      }

      public Builder pixelCount(long count) {
         pixelCount_ = count;
         return this;
      }

      public Builder pixelCountExcludingZeros(long count) {
         pixelCountExcludingZeros_ = count;
         return this;
      }

      public Builder usedROI(boolean used) {
         usedROI_ = used;
         return this;
      }

      public Builder minimum(long min) {
         minimum_ = min;
         return this;
      }

      public Builder minimumExcludingZeros(long min) {
         minimumExcludingZeros_ = min;
         return this;
      }

      public Builder maximum(long max) {
         maximum_ = max;
         return this;
      }

      public Builder sum(long sum) {
         sum_ = sum;
         return this;
      }

      public Builder sumOfSquares(long ssq) {
         sumOfSquares_ = ssq;
         return this;
      }

      /**
       * Sets the untruncated minimum, for float images.
       *
       * @param min minimum pixel value, or NaN to leave unset
       * @return this builder
       */
      public Builder floatMinimum(double min) {
         floatMinimum_ = min;
         return this;
      }

      /**
       * Sets the untruncated minimum of the non-zero pixels, for float images.
       *
       * @param min minimum non-zero pixel value, or NaN to leave unset
       * @return this builder
       */
      public Builder floatMinimumExcludingZeros(double min) {
         floatMinimumExcludingZeros_ = min;
         return this;
      }

      /**
       * Sets the untruncated maximum, for float images.
       *
       * @param max maximum pixel value, or NaN to leave unset
       * @return this builder
       */
      public Builder floatMaximum(double max) {
         floatMaximum_ = max;
         return this;
      }

      /**
       * Sets the untruncated sum of pixel values, for float images.
       *
       * @param sum sum of pixel values, or NaN to leave unset
       * @return this builder
       */
      public Builder floatSum(double sum) {
         floatSum_ = sum;
         return this;
      }

      /**
       * Sets the untruncated sum of squared pixel values, for float images.
       *
       * @param ssq sum of squares, or NaN to leave unset
       * @return this builder
       */
      public Builder floatSumOfSquares(double ssq) {
         floatSumOfSquares_ = ssq;
         return this;
      }

      public ComponentStats build() {
         return new ComponentStats(this);
      }
   }

   public static Builder builder() {
      return new Builder();
   }

   /**
    * Merge two stats objects by combining their histograms and scalar fields.
    * Both must have the same bin layout (same bit depth / bin width).
    *
    * @param a first stats
    * @param b second stats
    * @return merged stats representing the union of both pixel populations
    */
   public static ComponentStats merge(
            ComponentStats a, ComponentStats b) {
      long[] histA = getFullHistogram(a);
      long[] histB = getFullHistogram(b);
      long[] merged = new long[histA.length];
      for (int i = 0; i < merged.length; i++) {
         merged[i] = histA[i] + histB[i];
      }
      int binWidthPow2 = Integer.numberOfTrailingZeros(a.getHistogramBinWidth());
      boolean isFloat = a.isFloat() || b.isFloat();
      Builder builder = builder()
            .histogram(merged, binWidthPow2)
            .isFloat(isFloat)
            .pixelCount(a.getPixelCount() + b.getPixelCount())
            .pixelCountExcludingZeros(
                  a.getPixelCountExcludingZeros()
                        + b.getPixelCountExcludingZeros())
            .minimum(Math.min(a.getMinIntensity(), b.getMinIntensity()))
            .minimumExcludingZeros(Math.min(
                  a.getMinIntensityExcludingZeros(),
                  b.getMinIntensityExcludingZeros()))
            .maximum(Math.max(a.getMaxIntensity(), b.getMaxIntensity()))
            .sum(a.getSum() + b.getSum())
            .sumOfSquares(a.getSumOfSquares() + b.getSumOfSquares())
            .usedROI(a.isROIStats() || b.isROIStats());
      if (isFloat) {
         // Without these the merged stats would claim to be float while every float field
         // was NaN, so the accessors would silently fall back to the rounded longs. The
         // axis must be carried too: it is not recoverable from binWidthPow2, which is
         // always 0 for float stats.
         builder.rangeMin(Math.min(a.getHistogramRangeMinDouble(),
                     b.getHistogramRangeMinDouble()))
               .binWidthFloat(Math.max(a.getBinWidthDouble(), b.getBinWidthDouble()))
               .floatMinimum(minIgnoringNaN(
                     a.getFloatMinIntensity(), b.getFloatMinIntensity()))
               .floatMinimumExcludingZeros(minIgnoringNaN(
                     a.getFloatMinIntensityExcludingZeros(),
                     b.getFloatMinIntensityExcludingZeros()))
               .floatMaximum(maxIgnoringNaN(
                     a.getFloatMaxIntensity(), b.getFloatMaxIntensity()))
               .floatSum(a.getFloatSum() + b.getFloatSum())
               .floatSumOfSquares(a.getFloatSumOfSquares() + b.getFloatSumOfSquares());
      }
      return builder.build();
   }

   private static double minIgnoringNaN(double x, double y) {
      if (Double.isNaN(x)) {
         return y;
      }
      return Double.isNaN(y) ? x : Math.min(x, y);
   }

   private static double maxIgnoringNaN(double x, double y) {
      if (Double.isNaN(x)) {
         return y;
      }
      return Double.isNaN(y) ? x : Math.max(x, y);
   }

   private static long[] getFullHistogram(ComponentStats s) {
      long[] inRange = s.getInRangeHistogram();
      long[] full = new long[inRange.length + 2];
      full[0] = s.getPixelCountBelowRange();
      System.arraycopy(inRange, 0, full, 1, inRange.length);
      full[full.length - 1] = s.getPixelCountAboveRange();
      return full;
   }

   private ComponentStats(Builder b) {
      bitDepth_ = b.bitDepth_;
      histogram_ = b.histogram_ != null
            ? Arrays.copyOf(b.histogram_, b.histogram_.length) :
            null;
      binWidthPowerOf2_ = b.binWidthPowerOf2_;
      rangeMin_ = b.rangeMin_;
      binWidthFloat_ = (b.binWidthFloat_ >= 0.0) ? b.binWidthFloat_ : (1 << b.binWidthPowerOf2_);
      isFloat_ = b.isFloat_;
      pixelCount_ = b.pixelCount_;
      pixelCountExcludingZeros_ = b.pixelCountExcludingZeros_;
      usedROI_ = b.usedROI_;
      minimum_ = b.minimum_;
      minimumExcludingZeros_ = b.minimumExcludingZeros_;
      maximum_ = b.maximum_;
      sum_ = b.sum_;
      sumOfSquares_ = b.sumOfSquares_;
      floatMinimum_ = b.floatMinimum_;
      floatMinimumExcludingZeros_ = b.floatMinimumExcludingZeros_;
      floatMaximum_ = b.floatMaximum_;
      floatSum_ = b.floatSum_;
      floatSumOfSquares_ = b.floatSumOfSquares_;
      cumulativeDistrib_ = computeCumulativeDistribution();
   }

   public Integer getBitDepth() {
      // This is usually equal to the bit depth corresponding to the bin count
      // and bin width, but may be null if the camera bit depth was unknown.
      // I.e., this is based on knowledge of the data, not the integer type
      // used to contain it.
      return bitDepth_;
   }

   public long[] getInRangeHistogram() {
      if (histogram_ == null) {
         return null;
      }
      return Arrays.copyOfRange(histogram_, 1, histogram_.length - 1);
   }

   public long getPixelCountBelowRange() {
      if (histogram_ == null) {
         return 0;
      }
      return histogram_[0];
   }

   public long getPixelCountAboveRange() {
      if (histogram_ == null) {
         return 0;
      }
      return histogram_[histogram_.length - 1];
   }

   public int getHistogramBinCount() {
      if (histogram_ == null) {
         return 0;
      }
      return histogram_.length - 2;
   }

   public int getHistogramBinWidth() {
      if (histogram_ == null) {
         return 0;
      }
      return 1 << binWidthPowerOf2_;
   }

   public double getBinWidthDouble() {
      return binWidthFloat_;
   }

   public double getHistogramRangeMinDouble() {
      return rangeMin_;
   }

   public long getHistogramRangeMin() {
      return (long) Math.floor(rangeMin_);
   }

   public boolean isFloat() {
      return isFloat_;
   }

   public long getHistogramRangeMax() {
      if (histogram_ == null) {
         return 0;
      }
      // For float images with binWidthFloat_ == 0 (constant image, all pixels identical),
      // the range collapses to a single point: return ceil(rangeMin_).
      // For all other float images, the upper bound is ceil(fMax).
      // For integer images, the last representable value is
      // binWidth*nBins - 1 (e.g. 255 for 8-bit).
      if (binWidthFloat_ == 0.0) {
         return (long) Math.ceil(rangeMin_);
      }
      double rawMax = rangeMin_ + binWidthFloat_ * getHistogramBinCount();
      long ceiled = (long) Math.ceil(rawMax);
      if (!isFloat_) {
         // Integer image: last valid sample value is one below the bin-count boundary
         return ceiled - 1;
      }
      return ceiled;
   }

   public long getPixelCount() {
      return pixelCount_;
   }

   public long getPixelCountExcludingZeros() {
      return pixelCountExcludingZeros_;
   }

   public boolean isROIStats() {
      return usedROI_;
   }

   public long getMeanIntensity() {
      if (pixelCount_ == 0) {
         return 0;
      }
      return Math.round(((double) sum_) / pixelCount_);
   }

   public long getMeanIntensityExcludingZeros() {
      if (pixelCountExcludingZeros_ == 0) {
         return 0;
      }
      return Math.round(((double) sum_) / pixelCountExcludingZeros_);
   }

   public long getMinIntensity() {
      return minimum_;
   }

   public long getMinIntensityExcludingZeros() {
      return minimumExcludingZeros_;
   }

   public long getMaxIntensity() {
      return maximum_;
   }

   /**
    * Minimum pixel value, without the truncation applied to {@link #getMinIntensity()}.
    *
    * @return the minimum; equal to the long value for integer images
    */
   public double getFloatMinIntensity() {
      return Double.isNaN(floatMinimum_) ? (double) minimum_ : floatMinimum_;
   }

   /**
    * Minimum non-zero pixel value, without truncation.
    *
    * @return the minimum excluding zeros; equal to the long value for integer images
    */
   public double getFloatMinIntensityExcludingZeros() {
      return Double.isNaN(floatMinimumExcludingZeros_)
            ? (double) minimumExcludingZeros_ : floatMinimumExcludingZeros_;
   }

   /**
    * Maximum pixel value, without the truncation applied to {@link #getMaxIntensity()}.
    *
    * @return the maximum; equal to the long value for integer images
    */
   public double getFloatMaxIntensity() {
      return Double.isNaN(floatMaximum_) ? (double) maximum_ : floatMaximum_;
   }

   /**
    * Mean pixel value, computed without truncation.
    *
    * <p>{@link #getMeanIntensity()} rounds to a whole number, which discards the entire
    * value for float data whose mean lies between -1 and 1.
    *
    * @return the mean, or 0 when there are no pixels
    */
   public double getFloatMeanIntensity() {
      if (pixelCount_ == 0) {
         return 0.0;
      }
      return getFloatSum() / pixelCount_;
   }

   /**
    * Mean of the non-zero pixel values, computed without truncation.
    *
    * <p>The full sum is divided by the non-zero count, matching
    * {@link #getMeanIntensityExcludingZeros()}. That is correct because zero pixels
    * contribute nothing to the sum.
    *
    * @return the mean excluding zeros, or 0 when there are no such pixels
    */
   public double getFloatMeanIntensityExcludingZeros() {
      if (pixelCountExcludingZeros_ == 0) {
         return 0.0;
      }
      return getFloatSum() / pixelCountExcludingZeros_;
   }

   /**
    * Standard deviation, computed without truncation.
    *
    * <p>Unlike {@link #getStandardDeviation()}, this uses the unrounded sum and mean. That
    * matters for float data: the rounded mean both inflates the result and can make the
    * variance come out negative, yielding NaN. The variance is clamped at zero so rounding
    * noise cannot produce NaN here.
    *
    * @return the standard deviation, or NaN when there are no pixels
    */
   public double getFloatStandardDeviation() {
      if (pixelCount_ == 0) {
         return Double.NaN;
      }
      double meanSq = getFloatSumOfSquares() / pixelCount_;
      double mean = getFloatMeanIntensity();
      return Math.sqrt(Math.max(0.0, meanSq - (mean * mean)));
   }

   /**
    * Standard deviation of the non-zero pixel values, computed without truncation.
    *
    * @return the standard deviation excluding zeros, or NaN when there are no such pixels
    */
   public double getFloatStandardDeviationExcludingZeros() {
      if (pixelCountExcludingZeros_ == 0) {
         return Double.NaN;
      }
      double meanSq = getFloatSumOfSquares() / pixelCountExcludingZeros_;
      double mean = getFloatMeanIntensityExcludingZeros();
      return Math.sqrt(Math.max(0.0, meanSq - (mean * mean)));
   }

   public long getAutoscaleMinForQuantile(double q) {
      long rangeMin = getHistogramRangeMin();
      if (q >= 0.5) {
         // Safe, in-range value that is less than max
         return Math.max(rangeMin, Math.round(getQuantile(0.5)) - 1L);
      }
      return Math.max(rangeMin, Math.round(getQuantile(q)));
   }

   public long getAutoscaleMinForQuantileIgnoringZeros(double q) {
      long rangeMin = getHistogramRangeMin();
      if (q >= 0.5) {
         // Safe, in-range value that is less than max
         return Math.max(rangeMin, Math.round(getQuantileIgnoringZeros(0.5)) - 1L);
      }
      return Math.max(rangeMin, Math.round(getQuantileIgnoringZeros(q)));
   }

   // Guarantees that the returned (max - min) >= 2. Also guarantees that,
   // if all pixels have the same value, they will not be at the edge of the
   // range unless all pixels are zero or saturated.
   public void getAutoscaleMinMaxForQuantile(double q, long[] minMax) {
      Preconditions.checkNotNull(minMax);
      Preconditions.checkArgument(minMax.length == 2);

      long min = Math.round(getQuantile(q));
      // Subtract 1 to convert from bin edge index to intensity value
      long max = Math.round(getQuantile(1.0 - q)) - 1L;

      if (max - min < 2) { // Range width < 3
         // For example, if all pixels have the same intensity I, we will
         // reach here and 'mid' will equal I.
         // We may also reach here if all pixels have intensity I or I + 1;
         // in this case we will end up increasing the range by 1 bin, but
         // this should be harmless.
         long mid = (min + max) / 2;
         if (mid <= getHistogramRangeMin()) {
            min = getHistogramRangeMin();
            max = min + 2;
         } else if (mid >= getHistogramRangeMax()) {
            max = getHistogramRangeMax();
            min = max - 2;
         } else {
            min = mid - 1;
            max = mid + 1;
         }
      }

      minMax[0] = min;
      minMax[1] = max;
   }

   public long getAutoscaleMaxForQuantileIgnoringZeros(double q) {
      if (q >= 0.5) {
         // Safe, in-range value that is greater than min
         return Math.max(1L, Math.round(getQuantileIgnoringZeros(0.5)));
      }
      return Math.round(getQuantileIgnoringZeros(1.0 - q)) - 1L;
   }

   /**
    * Autoscale bounds as real pixel values, for float images.
    *
    * <p>The long-valued {@link #getAutoscaleMinMaxForQuantile(double, long[])} rounds the
    * quantiles to whole numbers, which destroys the range of float data: values spanning
    * -0.65 to 1.04 collapse to 0 and 1, and the integer widening below then stretches the
    * range wider than the data. Here the quantiles are used as they come, and a degenerate
    * range is widened by a fraction of the data rather than by whole units.
    *
    * @param q fraction (0-1) of pixels to leave out at each end
    * @param minMax two-element array receiving the minimum and maximum
    */
   public void getFloatAutoscaleMinMaxForQuantile(double q, double[] minMax) {
      Preconditions.checkNotNull(minMax);
      Preconditions.checkArgument(minMax.length == 2);

      double min = getQuantile(q);
      double max = getQuantile(1.0 - q);
      // Keep the range at least one bin wide. When one bin holds most of the pixels, both
      // quantiles land inside it and the range collapses to near-nothing, which would
      // render the image pure black and white.
      double floor = binWidthFloat_ > 0.0 ? binWidthFloat_ : padFor(min);
      if (max - min < floor) {
         double mid = 0.5 * (min + max);
         min = mid - 0.5 * floor;
         max = mid + 0.5 * floor;
      }
      minMax[0] = min;
      minMax[1] = max;
   }

   /**
    * Autoscale maximum as a real pixel value, ignoring zero pixels, for float images.
    *
    * @param q fraction (0-1) of pixels to leave out at the top
    * @return the maximum
    */
   public double getFloatAutoscaleMaxForQuantileIgnoringZeros(double q) {
      double max = getQuantileIgnoringZeros(q >= 0.5 ? 0.5 : 1.0 - q);
      double min = getFloatMinIntensity();
      if (max <= min) {
         return min + padFor(min);
      }
      return max;
   }

   /**
    * Amount by which to widen a range that came out empty.
    *
    * @param value the value the range collapsed to
    * @return a positive, non-zero padding
    */
   private static double padFor(double value) {
      double pad = Math.abs(value) * 1e-6;
      return pad > 0.0 ? pad : Math.max(Math.ulp(value), Double.MIN_NORMAL);
   }

   /**
    * Lowest pixel value, as the quantile machinery should report it.
    *
    * <p>For float stats this is the real minimum; {@code minimum_} is floored, so a
    * minimum of -0.65 would otherwise be reported as -1.
    *
    * @return the low edge of the data
    */
   private double quantileLowEdge() {
      return isFloat_ ? getFloatMinIntensity() : (double) minimum_;
   }

   /**
    * Highest pixel value, as the quantile machinery should report it.
    *
    * <p>The integer convention is "one past the maximum", since integer bins are unit
    * wide. Float bins are not, so the real maximum is the correct upper edge.
    *
    * @return the high edge of the data
    */
   private double quantileHighEdge() {
      return isFloat_ ? getFloatMaxIntensity() : (double) (maximum_ + 1);
   }

   /**
    * Upper edge of the histogram range, without the integer ceiling.
    *
    * @return the top of the axis
    */
   private double quantileRangeMax() {
      return isFloat_ ? rangeMin_ + binWidthFloat_ * getHistogramBinCount()
            : getHistogramRangeMax() + 1.0;
   }

   /**
    * Calculates the quantile, i.e. the bin value (ore more precise, the left bin edge)
    * for the bin where q * 100% of the pixel values are in lower bins.
    * Note: return value is in range 0 to (1 + range max), because it is in the
    * coordinates of bin edges, not centers.
    *
    * @param q Fraction (0-1) of pixels that should be lower
    * @return Value at which q * 100% of the pixels are lower
    */
   public double getQuantile(double q) {
      Preconditions.checkArgument(q >= 0.0);
      Preconditions.checkArgument(q <= 1.0);
      if (histogram_ == null) {
         return 0;
      }

      double countBelowQuantile = q * pixelCount_;
      final long[] cumDistrib = getCumulativeDistribution();

      if (countBelowQuantile <= cumDistrib[0] && cumDistrib[0] > 0) {
         // Quantile is below histogram range
         return getHistogramRangeMinDouble();
      }
      if (countBelowQuantile > cumDistrib[cumDistrib.length - 2]) {
         // Quantile is above histogram range
         return quantileRangeMax();
      }

      int binIndex;
      // The binary seatch will find _a_ bin with the desired cumulative count,
      // but it may not be the _only_ such bin, if the histogram contains bins
      // with zero count. This is not an issue when 0 < q < 1 (for our use of
      // the quantile for limiting scaling range), but when q = 0 or q = 1, we
      // need to find the exact edge of the non-zero part of the histogram.
      if (countBelowQuantile == 0) {
         return quantileLowEdge();
      } else if (countBelowQuantile == pixelCount_) {
         return quantileHighEdge();
      }

      binIndex = binarySearch(cumDistrib, 1, cumDistrib.length - 1,
            (long) Math.floor(countBelowQuantile));

      double leftEdge = rangeMin_ + (binIndex - 1) * binWidthFloat_;
      double binFraction =
            (countBelowQuantile - cumDistrib[binIndex - 1])
                  / (cumDistrib[binIndex] - cumDistrib[binIndex - 1]);
      return leftEdge + binFraction * binWidthFloat_;
   }


   /**
    * Calculates the quantile, i.e. the bin value (ore more precise, the left bin edge)
    * for the bin where q * 100% of the pixel values are in lower bins.
    * Note: return value is in range 0 to (1 + range max), because it is in the
    * coordinates of bin edges, not centers.
    *
    * @param q Fraction (0-1) of pixels that should be lower
    * @return Value at which q * 100% of the pixels are lower
    */
   public double getQuantileIgnoringZeros(double q) {
      Preconditions.checkArgument(q >= 0.0);
      Preconditions.checkArgument(q <= 1.0);
      if (histogram_ == null) {
         return 0;
      }

      final long[] cumDistrib = getCumulativeDistribution();
      // subtract zero pixels from pixelCount, unexpectedly, zero pixels are contained in
      // histogram_[1]
      final long pixelCount = pixelCount_ - histogram_[1];
      double countBelowQuantile = q * pixelCount + histogram_[1];

      if (countBelowQuantile < cumDistrib[2] && cumDistrib[2] > 0) {
         // Quantile is below histogram range
         return getHistogramRangeMinDouble() + binWidthFloat_;
      }
      if (countBelowQuantile > cumDistrib[cumDistrib.length - 2]) {
         // Quantile is above histogram range
         return quantileRangeMax();
      }

      int binIndex;
      // The binary search will find _a_ bin with the desired cumulative count,
      // but it may not be the _only_ such bin, if the histogram contains bins
      // with zero count. This is not an issue when 0 < q < 1 (for our use of
      // the quantile for limiting scaling range), but when q = 0 or q = 1, we
      // need to find the exact edge of the non-zero part of the histogram.
      if (countBelowQuantile == 0) {
         return quantileLowEdge();
      } else if (countBelowQuantile >= pixelCount_) {
         return quantileHighEdge();
      }

      binIndex = binarySearch(cumDistrib, 2, cumDistrib.length - 1,
            (long) Math.floor(countBelowQuantile));

      double leftEdge = rangeMin_ + (binIndex - 1) * binWidthFloat_;
      double binFraction =
             (countBelowQuantile - cumDistrib[binIndex - 1])
                 / (cumDistrib[binIndex] - cumDistrib[binIndex - 1]);
      return leftEdge + binFraction * binWidthFloat_;
   }

   private long[] computeCumulativeDistribution() {
      if (histogram_ == null) {
         return null;
      }
      long[] cumulativeDistrib = new long[histogram_.length];
      cumulativeDistrib[0] = histogram_[0];
      for (int i = 1; i < histogram_.length; ++i) {
         cumulativeDistrib[i] = cumulativeDistrib[i - 1] + histogram_[i];
      }
      return cumulativeDistrib;
   }

   private long[] getCumulativeDistribution() {
      return cumulativeDistrib_;
   }

   private int binarySearch(long[] sorted, int startIndex, int endIndex,
                            long value) {
      if (endIndex - startIndex <= 1) {
         return startIndex;
      }
      int middleIndex = (startIndex + endIndex) / 2;
      if (value >= sorted[middleIndex - 1]) {
         return binarySearch(sorted, middleIndex, endIndex, value);
      } else {
         return binarySearch(sorted, startIndex, middleIndex, value);
      }
   }

   public long getSum() {
      return sum_;
   }

   public long getSumOfSquares() {
      return sumOfSquares_;
   }

   /**
    * Sum of pixel values, without the rounding applied to {@link #getSum()}.
    *
    * @return the sum; equal to the long value for integer images
    */
   public double getFloatSum() {
      return Double.isNaN(floatSum_) ? (double) sum_ : floatSum_;
   }

   /**
    * Sum of squared pixel values, without the rounding applied to
    * {@link #getSumOfSquares()}.
    *
    * @return the sum of squares; equal to the long value for integer images
    */
   public double getFloatSumOfSquares() {
      return Double.isNaN(floatSumOfSquares_) ? (double) sumOfSquares_ : floatSumOfSquares_;
   }

   public double getStandardDeviation() {
      if (pixelCount_ == 0) {
         return Double.NaN;
      }
      double meanSq = ((double) sumOfSquares_) / pixelCount_;
      double mean = getMeanIntensity();
      return Math.sqrt(meanSq - (mean * mean));
   }

   public double getStandardDeviationExcludingZeros() {
      if (pixelCountExcludingZeros_ == 0) {
         return Double.NaN;
      }
      double meanSq = ((double) sumOfSquares_) / pixelCountExcludingZeros_;
      double mean = getMeanIntensityExcludingZeros();
      return Math.sqrt(meanSq - (mean * mean));
   }

}