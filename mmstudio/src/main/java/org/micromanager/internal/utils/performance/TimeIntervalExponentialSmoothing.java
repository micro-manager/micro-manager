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
 * A specialized version of {@link ExponentialSmoothing} for tracking the time
 * interval of recurring events.
 *
 * @author Mark A. Tsuchida
 */
public final class TimeIntervalExponentialSmoothing
      extends AbstractExponentialSmoothing {
   public static TimeIntervalExponentialSmoothing createWithTimeConstantMs(double timeConstantMs) {
      return new TimeIntervalExponentialSmoothing(timeConstantMs);
   }

   private TimeIntervalExponentialSmoothing(double timeConstantMs) {
      super(timeConstantMs);
   }

   public void sample() {
      if (!isTimingStarted()) {
         markTime();
      }
      double deltaTMs = markTimeAndGetDeltaTMs();
      if (!isStatsInitialized()) {
         initializeStats(deltaTMs);
      } else {
         updateStats(deltaTMs, deltaTMs);
      }
   }
}