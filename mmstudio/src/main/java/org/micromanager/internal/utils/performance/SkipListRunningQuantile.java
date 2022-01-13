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

package org.micromanager.internal.utils.performance;

import java.util.List;
import java.util.Map;

/**
 * Efficiently compute running median and quantiles from a dynamic time series.
 *
 * @author Mark A. Tsuchida
 */
public class SkipListRunningQuantile implements RunningQuantile {
   private final int size_;
   private final IndexableOrderedSkipList<Double, Integer> values_;


   public static SkipListRunningQuantile create(int size) {
      // A 4-level skip list is reasonable for size 100. Need to auto-select
      // a good number of levels if using for multiple purposes.
      return new SkipListRunningQuantile(size, 4);
   }

   private SkipListRunningQuantile(int size, int skipListLevels) {
      size_ = size;
      values_ = IndexableOrderedSkipList.create(skipListLevels);
   }

   @Override
   public void sample(double value) {
      values_.insert(value, 0);
      if (values_.size() > size_) {
         values_.removeOldest();
      }
   }

   @Override
   public double getQuantile(double q) {
      if (q < 0.0 || q > 1.0) {
         throw new IllegalArgumentException("Out of allowed range (0.0-1.0)");
      }
      int size = values_.size();
      if (size == 0) {
         return 0.0;
      }
      if (size == 1) {
         return values_.get(0).getKey();
      }
      double realIndex = q * (size - 1);
      int floor = (int) Math.floor(realIndex);
      int ceiling = floor + 1;
      List<Map.Entry<Double, Integer>> pair = values_.sublist(floor, 2);
      return (ceiling - realIndex) * pair.get(0).getKey()
            + (realIndex - floor) * pair.get(1).getKey();
   }
}