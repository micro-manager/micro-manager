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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Times CPU usage on the current thread.
 *
 * <p>This is intended for use around a time-consuming, single-threaded task.
 * Accuracy is platform-dependent.
 * Recommended for tasks taking 10 ms or longer.
 *
 * @see WallTimer
 * @author Mark A. Tsuchida
 */
public final class CPUTimer {
   private static final ThreadMXBean threadMXB_ =
         ManagementFactory.getThreadMXBean();
   private final long threadId_;
   private final long startNs_;

   public static CPUTimer createStarted() {
      return new CPUTimer();
   }

   private CPUTimer() {
      threadId_ = Thread.currentThread().getId();
      startNs_ = getCPUTimeNs();
   }

   public long getNs() {
      if (Thread.currentThread().getId() != threadId_) {
         return -1;
      }
      return getCPUTimeNs() - startNs_;
   }

   public double getMs() {
      return getNs() / 1000000.0;
   }

   private long getCPUTimeNs() {
      return threadMXB_.getCurrentThreadCpuTime();
   }
}
