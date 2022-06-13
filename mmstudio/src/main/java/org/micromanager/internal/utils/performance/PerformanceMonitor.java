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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collection of exponentially smoothed statistics for monitoring dynamic
 * performance.
 *
 * <p>Performance Monitor UI is enabled by setting the system property
 * "org.micromanager.showperfmon". This can be done from code/script panel:
 * https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#setProperty-java.lang.String-java.lang.String-
 * (System.setProperty("org.micromanager.showperfmon", "true"))
 * Or from JVM command line args:
 * https://docs.oracle.com/javase/8/docs/technotes/tools/windows/java.html
 * (-Dorg.micromanager.showperfmon=true).
 * We could document that somewhere, but I don't feel that this feature is useful to anybody who
 * doesn't already have deep knowledge of the display code -- the data it displays is
 * uninterpretable without reading the code.
 * I don't think the performance impact is that big when not enabled (it should be noted that most
 * of its uses are per display cycle, which is never over 60 Hz, I would hope, no matter how high
 * the acquisition rate), but please feel free to remove its use entirely from the viewer classes
 * (or add code that would cause it to be JIT-compiled to nothing when not enabled) if you prefer
 * it that way, whether or not there is any proof. At least that will make the viewer code cleaner,
 * and we can always temporarily re-add performance sampling when working on display performance.
 * In my hands, the insight gained from it was quite valuable, because most of our viewer
 * performance issues (with the exception of histogramming and metadata processing) are not
 * CPU-bound but rather concurrency programming bugs or nonidealities, such as waiting for things
 * incorrectly or scheduling too many things at the same time. I'm a bit worried that trying to
 * address display performance issues without this kind of tool is like shooting in the dark.
 * But it is true that it is something I quickly whipped up for my own use, hence the lack of
 * documentation. If you would like, I can make it a separate library and completely remove it
 * from MMStudio (that would maybe force me to document it...).
 * The only org.micromanager.internal.utils.performance usage that cannot be trivially removed
 * is the DisplayUIController's use of TimeIntervalRunningQuantile to compute a running median
 * (yes, it can be done) of actual past display FPS in order to adjust its target FPS. This is
 * something that seemed to work better (across a wide range of incoming frame rates and
 * processing delays) than other methods I tried, but by no means is it perfect.
 *
 * @author Mark A. Tsuchida
 */
public final class PerformanceMonitor {
   private final double timeConstantMs_; // We could make this settable

   private final ConcurrentHashMap<String, ExponentialSmoothing> stats_ = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, TimeIntervalExponentialSmoothing>
         intervalStats_ = new ConcurrentHashMap<>();

   public static PerformanceMonitor createWithTimeConstantMs(
         double timeConstantMs) {
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
         stat = TimeIntervalExponentialSmoothing.createWithTimeConstantMs(
               timeConstantMs_);
         intervalStats_.putIfAbsent(startLabel, stat);
      }
      stat.sample();
   }

   public List<Map.Entry<String, ? extends AbstractExponentialSmoothing>>
            getEntries() {
      // Combine stats into a single sorted list
      List<Map.Entry<String, ? extends AbstractExponentialSmoothing>> entries =
            new ArrayList<>();
      entries.addAll(stats_.entrySet());
      for (Map.Entry<String, TimeIntervalExponentialSmoothing> e : intervalStats_.entrySet()) {
         entries.add(new AbstractMap.SimpleEntry<>(
               e.getKey() + " (interval, ms)", e.getValue()));
      }

      Collections.sort(entries, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));
      return entries;
   }

   public String dump() {
      List<Map.Entry<String, ? extends AbstractExponentialSmoothing>> entries =
            getEntries();

      int longestKeyLen = 0;
      for (Map.Entry<String, ? extends AbstractExponentialSmoothing> e : entries) {
         longestKeyLen = Math.max(longestKeyLen, e.getKey().length());
      }
      String keyFormat = String.format("%%%ds", longestKeyLen);

      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, ? extends AbstractExponentialSmoothing> e : entries) {
         sb.append(String.format(keyFormat, e.getKey())).append(": ")
               .append(e.getValue().toString()).append("\n");
      }
      if (sb.length() > 0) {
         sb.deleteCharAt(sb.length() - 1); // Remove trailing newline
      }
      return sb.toString();
   }
}