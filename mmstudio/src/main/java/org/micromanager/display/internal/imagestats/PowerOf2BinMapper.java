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
 * <p>Unlike the default {@code Integer1dBinMapper} provided by ImgLib2, which
 * only allows size-1 bins, this allows histograms to have size 1, 2, 4, 8, ...
 * bins. For example, a 256-bin histogram can be created for a 16-bit image.
 *
 * @author Mark A. Tsuchida
 */
public class PowerOf2BinMapper<T extends IntegerType<T>> implements BinMapper1d<T> {
   private final int sampleShift_;
   private final long endOfRange_;
   private final long binWidth_;
   private final long[] bins_;

   /**
    * Create a bin mapper covering full range of unsigned integer type.
    *
    * <p>Not tested for signed integer types.
    *
    * @param <T>
    * @param sampleDepthPowerOf2
    * @param binCountPowerOf2
    * @return
    */
   public static <T extends IntegerType<T>> PowerOf2BinMapper<T>
         create(int sampleDepthPowerOf2, int binCountPowerOf2) {
      return new PowerOf2BinMapper<T>(sampleDepthPowerOf2, binCountPowerOf2);
   }

   private PowerOf2BinMapper(int sampleDepthPowerOf2, int binCountPowerOf2) {
      if (sampleDepthPowerOf2 > binCountPowerOf2) {
         sampleShift_ = sampleDepthPowerOf2 - binCountPowerOf2;
      } else {
         sampleShift_ = 0;
      }
      endOfRange_ = (1L << sampleDepthPowerOf2) - 1;
      binWidth_ = 1L << sampleShift_;
      bins_ = new long[(1 << binCountPowerOf2) + 2];
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

   long getEndOfRange() {
      return endOfRange_;
   }

   @Override
   public long map(T value) {
      long longValue = value.getIntegerLong();
      if (longValue < 0L) {
         return 0L;
      }
      if (longValue > endOfRange_) {
         return bins_.length - 1;
      }
      return (longValue >> sampleShift_) + 1L;
   }

   @Override
   public void getCenterValue(long index, T value) {
      if (index <= 0) {
         value.setInteger(0);
         return;
      }
      if (index > bins_.length - 1) {
         value.setInteger(endOfRange_);
         return;
      }

      // Compute (lower + upper + 1) / 2
      getLowerBound(index, value);
      T upper = value.createVariable();
      getUpperBound(index, upper);
      value.add(upper);
      value.inc();
      final T two = value.createVariable();
      two.setInteger(2);
      value.div(two);
   }

   @Override
   public void getLowerBound(long index, T value) {
      if (index <= 0) {
         value.setReal(value.getMinValue());
      } else if (index == bins_.length - 1) {
         value.setInteger(endOfRange_);
      } else if (index >= bins_.length - 1) {
         value.setReal(value.getMaxValue());
      } else {
         value.setInteger((index - 1) << sampleShift_);
      }
   }

   @Override
   public void getUpperBound(long index, T value) {
      if (index >= bins_.length - 1) {
         value.setReal(value.getMaxValue());
      } else if (index == bins_.length - 2) {
         value.setReal(value.getMaxValue());
      } else if (index <= 0) {
         value.setReal(value.getMinValue());
      } else {
         getLowerBound(index + 1, value);
         value.dec();
      }
   }

   @Override
   public boolean includesUpperBound(long index) {
      return index > 0 && index < bins_.length - 1;
   }

   @Override
   public boolean includesLowerBound(long index) {
      return index > 0 && index < bins_.length - 1;
   }

   @Override
   public BinMapper1d<T> copy() {
      return new PowerOf2BinMapper<T>(this);
   }

}
