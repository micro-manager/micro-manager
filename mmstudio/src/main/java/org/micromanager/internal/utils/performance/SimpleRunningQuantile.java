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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Compute running median and quantiles from a dynamic time series.
 *
 * <p>This implementation simply keeps the last N values, and sorts them when requested.
 *
 * @author Mark A. Tsuchida
 */
public class SimpleRunningQuantile implements RunningQuantile {
  private final int size_;
  private final Deque<Double> values_ = new ArrayDeque<Double>();

  public static SimpleRunningQuantile create(int size) {
    return new SimpleRunningQuantile(size);
  }

  private SimpleRunningQuantile(int size) {
    size_ = size;
  }

  @Override
  public synchronized void sample(double value) {
    values_.addLast(value);
    while (values_.size() > size_) {
      values_.removeFirst();
    }
  }

  @Override
  public synchronized double getQuantile(double q) {
    if (q < 0.0 || q > 1.0) {
      throw new IllegalArgumentException("Out of allowed range (0.0-1.0)");
    }
    int size = values_.size();
    if (size == 0) {
      return 0.0;
    }
    if (size == 1) {
      return values_.getFirst();
    }

    List<Double> sorted = new ArrayList<Double>(values_);
    Collections.sort(sorted);
    double realIndex = q * (size - 1);
    int floor = (int) Math.floor(realIndex);
    int ceiling = floor + 1;
    double quantile =
        (ceiling - realIndex) * sorted.get(floor) + (realIndex - floor) * sorted.get(ceiling);
    return quantile;
  }
}
