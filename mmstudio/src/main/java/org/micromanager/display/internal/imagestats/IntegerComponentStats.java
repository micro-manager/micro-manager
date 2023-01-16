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
public final class IntegerComponentStats {
   private final Integer bitDepth_;
   private final long[] histogram_;
   private final int binWidthPowerOf2_;
   private final long pixelCount_;
   private final boolean usedROI_;
   private final long minimum_;
   private final long maximum_;
   private final long sum_;
   private final long sumOfSquares_;
   private final transient long[] cumulativeDistrib_;

   public static class Builder {
      private Integer bitDepth_;
      private long[] histogram_;
      private int binWidthPowerOf2_;
      private long pixelCount_;
      private boolean usedROI_;
      private long minimum_;
      private long maximum_;
      private long sum_;
      private long sumOfSquares_;

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

      public Builder pixelCount(long count) {
         pixelCount_ = count;
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

      public IntegerComponentStats build() {
         return new IntegerComponentStats(this);
      }
   }

   public static Builder builder() {
      return new Builder();
   }

   private IntegerComponentStats(Builder b) {
      bitDepth_ = b.bitDepth_;
      histogram_ = b.histogram_ != null
            ? Arrays.copyOf(b.histogram_, b.histogram_.length) :
            null;
      binWidthPowerOf2_ = b.binWidthPowerOf2_;
      pixelCount_ = b.pixelCount_;
      usedROI_ = b.usedROI_;
      minimum_ = b.minimum_;
      maximum_ = b.maximum_;
      sum_ = b.sum_;
      sumOfSquares_ = b.sumOfSquares_;
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

   public long getHistogramRangeMin() {
      return 0;
   }

   public long getHistogramRangeMax() {
      if (histogram_ == null) {
         return 0;
      }
      return getHistogramBinWidth() * getHistogramBinCount() - 1;
   }

   public long getPixelCount() {
      return pixelCount_;
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

   public long getMinIntensity() {
      return minimum_;
   }

   public long getMaxIntensity() {
      return maximum_;
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

   // Note: return value is in range 0 to (1 + range max), because it is in the
   // coordinates of bin edges, not centers.
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
         return getHistogramRangeMin();
      }
      if (countBelowQuantile > cumDistrib[cumDistrib.length - 2]) {
         // Quantile is above histogram range
         return getHistogramRangeMax() + 1.0;
      }

      int binIndex;
      // The binary seatch will find _a_ bin with the desired cumulative count,
      // but it may not be the _only_ such bin, if the histogram contains bins
      // with zero count. This is not an issue when 0 < q < 1 (for our use of
      // the quantile for limiting scaling range), but when q = 0 or q = 1, we
      // need to find the exact edge of the non-zero part of the histogram.
      if (countBelowQuantile == 0) {
         return minimum_;
      } else if (countBelowQuantile == pixelCount_) {
         return maximum_ + 1;
      }

      binIndex = binarySearch(cumDistrib, 1, cumDistrib.length - 1,
            (long) Math.floor(countBelowQuantile));

      int binWidth = getHistogramBinWidth();
      long leftEdge = (binIndex - 1) * binWidth;
      double binFraction =
            (countBelowQuantile - cumDistrib[binIndex - 1])
                  / (cumDistrib[binIndex] - cumDistrib[binIndex - 1]);
      return leftEdge + binFraction * binWidth;
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

   public long getSumOfSquares() {
      return sumOfSquares_;
   }

   public double getStandardDeviation() {
      if (pixelCount_ == 0) {
         return Double.NaN;
      }
      double meanSq = ((double) sumOfSquares_) / pixelCount_;
      double mean = getMeanIntensity();
      return Math.sqrt(meanSq - (mean * mean));
   }
}