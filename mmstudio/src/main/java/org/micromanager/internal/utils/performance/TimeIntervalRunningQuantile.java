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

/**
 * @author Mark A. Tsuchida
 */
public class TimeIntervalRunningQuantile implements RunningQuantile {
   private final RunningQuantile impl_;
   private long lastNanoTime_ = -1;

   public static TimeIntervalRunningQuantile create(int size) {
      return createSimple(size);
   }

   public static TimeIntervalRunningQuantile createSimple(int size) {
      return new TimeIntervalRunningQuantile(
            SkipListRunningQuantile.create(size));
   }

   private TimeIntervalRunningQuantile(RunningQuantile implementation) {
      impl_ = implementation;
   }

   @Override
   public void sample(double value) {
      impl_.sample(value);
   }

   public synchronized void sample() {
      long now = System.nanoTime();
      if (lastNanoTime_ < 0) {
         lastNanoTime_ = now;
         return;
      }
      double intervalMs = (now - lastNanoTime_) / 1e6;
      lastNanoTime_ = now;
      impl_.sample(intervalMs);
   }

   @Override
   public double getQuantile(double q) {
      return impl_.getQuantile(q);
   }
}