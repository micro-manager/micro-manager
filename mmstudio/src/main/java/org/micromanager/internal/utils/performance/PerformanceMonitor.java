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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collection of exponentially smoothed statistics for monitoring dynamic performance.
 *
 * @author Mark A. Tsuchida
 */
public final class PerformanceMonitor {
  private final double timeConstantMs_; // We could make this settable

  private final ConcurrentHashMap<String, ExponentialSmoothing> stats_ =
      new ConcurrentHashMap<String, ExponentialSmoothing>();
  private final ConcurrentHashMap<String, TimeIntervalExponentialSmoothing> intervalStats_ =
      new ConcurrentHashMap<String, TimeIntervalExponentialSmoothing>();

  public static PerformanceMonitor createWithTimeConstantMs(double timeConstantMs) {
    return new PerformanceMonitor(timeConstantMs);
  }

  private PerformanceMonitor(double timeConstantMs) {
    timeConstantMs_ = timeConstantMs;
  }

  public void sample(String statLabel, double value) {
    ExponentialSmoothing stat = stats_.get(statLabel);
    if (stat == null) {
      stat = ExponentialSmoothing.createWithTimeConstantMs(timeConstantMs_);
      stats_.putIfAbsent(statLabel, stat);
    }
    stat.sample(value);
  }

  public void sampleTimeInterval(String startLabel) {
    TimeIntervalExponentialSmoothing stat = intervalStats_.get(startLabel);
    if (stat == null) {
      stat = TimeIntervalExponentialSmoothing.createWithTimeConstantMs(timeConstantMs_);
      intervalStats_.putIfAbsent(startLabel, stat);
    }
    stat.sample();
  }

  public List<Map.Entry<String, ? extends AbstractExponentialSmoothing>> getEntries() {
    // Combine stats into a single sorted list

    List<Map.Entry<String, ? extends AbstractExponentialSmoothing>> entries =
        new ArrayList<Map.Entry<String, ? extends AbstractExponentialSmoothing>>();
    entries.addAll(stats_.entrySet());
    for (Map.Entry<String, TimeIntervalExponentialSmoothing> e : intervalStats_.entrySet()) {
      entries.add(
          new AbstractMap.SimpleEntry<String, TimeIntervalExponentialSmoothing>(
              e.getKey() + " (interval, ms)", e.getValue()));
    }

    Collections.sort(
        entries,
        new Comparator<Map.Entry<String, ? extends AbstractExponentialSmoothing>>() {
          @Override
          public int compare(
              Map.Entry<String, ? extends AbstractExponentialSmoothing> o1,
              Map.Entry<String, ? extends AbstractExponentialSmoothing> o2) {
            return o1.getKey().compareTo(o2.getKey());
          }
        });
    return entries;
  }

  public String dump() {
    List<Map.Entry<String, ? extends AbstractExponentialSmoothing>> entries = getEntries();

    int longestKeyLen = 0;
    for (Map.Entry<String, ? extends AbstractExponentialSmoothing> e : entries) {
      longestKeyLen = Math.max(longestKeyLen, e.getKey().length());
    }
    String keyFormat = String.format("%%%ds", longestKeyLen);

    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, ? extends AbstractExponentialSmoothing> e : entries) {
      sb.append(String.format(keyFormat, e.getKey()))
          .append(": ")
          .append(e.getValue().toString())
          .append("\n");
    }
    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1); // Remove trailing newline
    }
    return sb.toString();
  }
}
