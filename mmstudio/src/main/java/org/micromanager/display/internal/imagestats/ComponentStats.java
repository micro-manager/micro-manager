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

import java.util.Arrays;

/**
 * Statistics of a single color component of an image; immutable.
 * @author Mark A. Tsuchida
 */
class ComponentStats {
   final long[] histogram_;
   final int binWidthPowerOf2_;
   final long pixelCount_;
   final long minimum_;
   final long maximum_;
   final long sum_;
   final long sumOfSquares_;
   transient long[] cumulativeDistrib_;

   static class Builder {
      long[] histogram_;
      int binWidthPowerOf2_;
      long pixelCount_;
      long minimum_;
      long maximum_;
      long sum_;
      long sumOfSquares_;

      private Builder() {
      }

      Builder histogram(long[] binsIncludingOutOfRange, int binWidthPowerOf2) {
         histogram_ = binsIncludingOutOfRange;
         binWidthPowerOf2_ = binWidthPowerOf2;
         return this;
      }

      Builder pixelCount(long count) {
         pixelCount_ = count;
         return this;
      }

      Builder minimum(long min) {
         minimum_ = min;
         return this;
      }

      Builder maximum(long max) {
         maximum_ = max;
         return this;
      }

      Builder sum(long sum) {
         sum_ = sum;
         return this;
      }

      Builder sumOfSquares(long ssq) {
         sumOfSquares_ = ssq;
         return this;
      }

      ComponentStats build() {
         return new ComponentStats(this);
      }
   }

   static Builder builder() {
      return new Builder();
   }

   private ComponentStats(Builder b) {
      histogram_ = b.histogram_ != null ?
            Arrays.copyOf(b.histogram_, b.histogram_.length) :
            null;
      binWidthPowerOf2_ = b.binWidthPowerOf2_;
      pixelCount_ = b.pixelCount_;
      minimum_ = b.minimum_;
      maximum_ = b.maximum_;
      sum_ = b.sum_;
      sumOfSquares_ = b.sumOfSquares_;
   }

   long[] getInRangeHistogram() {
      if (histogram_ == null) {
         return null;
      }
      return Arrays.copyOfRange(histogram_, 1, histogram_.length - 1);
   }

   long getPixelCountBelowRange() {
      if (histogram_ == null) {
         return 0;
      }
      return histogram_[0];
   }

   long getPixelCountAboveRange() {
      if (histogram_ == null) {
         return 0;
      }
      return histogram_[histogram_.length - 1];
   }

   int getHistogramBinCount() {
      if (histogram_ == null) {
         return 0;
      }
      return histogram_.length - 2;
   }

   int getHistogramBinWidth() {
      if (histogram_ == null) {
         return 0;
      }
      return 1 << binWidthPowerOf2_;
   }

   long getHistogramRangeMin() {
      return 0;
   }

   long getHistogramRangeMax() {
      if (histogram_ == null) {
         return 0;
      }
      return getHistogramBinWidth() * getHistogramBinCount() - 1;
   }

   long getPixelCount() {
      return pixelCount_;
   }

   long getMeanIntensity() {
      if (pixelCount_ == 0) {
         return 0;
      }
      return Math.round(((double) sum_) / pixelCount_);
   }

   long getMinIntensity() {
      return minimum_;
   }

   long getMaxIntensity() {
      return maximum_;
   }

   double getQuantile(double q) {
      if (histogram_ == null) {
         return 0;
      }

      double countBelowQuantile = q * pixelCount_;
      final long[] cumDistrib = getCumulativeDistribution();
      if (countBelowQuantile <= cumDistrib[0]) {
         // Quantile is below histogram range
         return getHistogramRangeMin();
      }
      if (countBelowQuantile > cumDistrib[cumDistrib.length - 2]) {
         // Quantile is above histogram range
         return getHistogramRangeMax();
      }
      int binIndex = binarySearch(cumDistrib, 1, cumDistrib.length - 1,
            (long) Math.floor(countBelowQuantile));
      int binWidth = getHistogramBinWidth();
      long leftEdge = (binIndex - 1) * binWidth;
      double binFraction =
            (countBelowQuantile - cumulativeDistrib_[binIndex - 1]) /
            (cumulativeDistrib_[binIndex] - cumulativeDistrib_[binIndex - 1]);
      return leftEdge + binFraction * binWidth;
   }

   private long[] getCumulativeDistribution() {
      if (cumulativeDistrib_ == null) {
         cumulativeDistrib_ = new long[histogram_.length];
         cumulativeDistrib_[0] = histogram_[0];
         for (int i = 1; i < histogram_.length; ++i) {
            cumulativeDistrib_[i] = cumulativeDistrib_[i - 1] + histogram_[i];
         }
      }
      return cumulativeDistrib_;
   }

   private int binarySearch(long[] sorted, int startIndex, int endIndex,
         long value)
   {
      if (endIndex - startIndex <= 1) {
         return startIndex;
      }
      int middleIndex = (startIndex + endIndex) / 2;
      if (value >= sorted[middleIndex - 1]) {
         return binarySearch(sorted, middleIndex, endIndex, value);
      }
      else {
         return binarySearch(sorted, startIndex, middleIndex, value);
      }
   }

   long getSumOfSquares() {
      return sumOfSquares_;
   }

   double getStandardDeviation() {
      if (pixelCount_ == 0) {
         return Double.NaN;
      }
      double meanSq = ((double) sumOfSquares_) / pixelCount_;
      double mean = getMeanIntensity();
      return Math.sqrt(meanSq - (mean * mean));
   }
}