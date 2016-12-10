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

import net.imglib2.histogram.BinMapper1d;
import net.imglib2.type.numeric.IntegerType;

/**
 * A {@code BinMapper1d} for use with {@code net.imglib2.histogram.Histogram1d}.
 *
 * Unlike the default {@code Integer1dBinMapper} provided by ImgLib2, which
 * only allows size-1 bins, this allows histograms to have 1, 2, 4, 8, ...
 * bins. For example, a 256-bin histogram can be created for a 16-bit image.
 * @author Mark A. Tsuchida
 */
class PowerOf2BinMapper<T extends IntegerType<T>> implements BinMapper1d<T> {
   private final int sampleShift_;
   private final long endOfRange_;
   private final long binWidth_;
   private final long[] bins_;

   static <T extends IntegerType<T>> PowerOf2BinMapper<T>
       create(int sampleDepthPowerOf2, int binCountPowerOf2)
   {
      return new PowerOf2BinMapper<T>(sampleDepthPowerOf2, binCountPowerOf2);
   }

   private PowerOf2BinMapper(int sampleDepthPowerOf2, int binCountPowerOf2) {
      if (sampleDepthPowerOf2 > binCountPowerOf2) {
         sampleShift_ = sampleDepthPowerOf2 - binCountPowerOf2;
      }
      else {
         sampleShift_ = 0;
      }
      endOfRange_ = ((1L << sampleDepthPowerOf2) >> sampleShift_) - 1;
      binWidth_ = 1L << sampleShift_;
      bins_ = new long[1 << binCountPowerOf2 + 2];
   }

   private PowerOf2BinMapper(PowerOf2BinMapper<T> other) {
      sampleShift_ = other.sampleShift_;
      endOfRange_ = other.endOfRange_;
      binWidth_ = other.binWidth_;
      bins_ = new long[other.bins_.length];
   }

   @Override
   public boolean hasTails() {
      return true;
   }

   @Override
   public long getBinCount() {
      return bins_.length;
   }

   @Override
   public long map(T value) {
      if (value.getIntegerLong() < 0L) {
         return 0L;
      }
      if (value.getIntegerLong() > endOfRange_) {
         return bins_.length - 1;
      }
      return value.getIntegerLong() >> sampleShift_ + 1;
   }

   @Override
   public void getCenterValue(long index, T value) {
      // Compute (lower + upper) / 2
      T upper = value.createVariable();
      getLowerBound(index, value);
      getUpperBound(index, upper);
      value.add(upper);
      T two = value.createVariable();
      two.setInteger(2);
      value.div(two);
   }

   @Override
   public void getLowerBound(long index, T value) {
      if (index <= 0) {
         value.setInteger(Long.MIN_VALUE);
      }
      else if (index == bins_.length - 1) {
         value.setInteger(endOfRange_ + 1);
      }
      else if (index > bins_.length - 1) {
         value.setInteger(Long.MAX_VALUE);
      }
      else {
         value.setInteger((index - 1) << sampleShift_);
      }
   }

   @Override
   public void getUpperBound(long index, T value) {
      if (index >= bins_.length - 1) {
         value.setInteger(Long.MAX_VALUE);
      }
      else if (index < 0) {
         value.setInteger(Long.MIN_VALUE);
      }
      else {
         getLowerBound(index + 1, value);
      }
   }

   @Override
   public boolean includesUpperBound(long index) {
      return false;
   }

   @Override
   public boolean includesLowerBound(long index) {
      return index > 0 && index <= bins_.length - 1;
   }

   @Override
   public BinMapper1d<T> copy() {
      return new PowerOf2BinMapper<T>(this);
   }

}
