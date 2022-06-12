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
 * Compute rolling exponential average of a time series.
 *
 * @author Mark A. Tsuchida
 * @see <a href="https://en.wikipedia.org/wiki/Exponential_smoothing">Exponential Smoothing</a> on Wikipedia
 * @see TimeIntervalExponentialSmoothing
 */
public final class ExponentialSmoothing extends AbstractExponentialSmoothing {
   public static ExponentialSmoothing createWithTimeConstantMs(double timeConstantMs) {
      return new ExponentialSmoothing(timeConstantMs);
   }

   private ExponentialSmoothing(double timeConstantMs) {
      super(timeConstantMs);
   }

   public void sample(double x) {
      if (!isTimingStarted()) {
         markTime();
         initializeStats(x);
      }
      else {
         updateStats(markTimeAndGetDeltaTMs(), x);
      }
   }
}
