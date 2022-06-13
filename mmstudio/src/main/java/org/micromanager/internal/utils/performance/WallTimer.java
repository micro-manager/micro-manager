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
 * Measure wall-clock time duration.
 *
 * @author Mark A. Tsuchida
 * @see CPUTimer
 */
public class WallTimer {
   private final long startNs_;

   public static WallTimer createStarted() {
      return new WallTimer();
   }

   private WallTimer() {
      startNs_ = System.nanoTime();
   }

   public long getNs() {
      return System.nanoTime() - startNs_;
   }

   public double getMs() {
      return getNs() / 1000000.0;
   }
}