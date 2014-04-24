// AUTHOR:       Mark Tsuchida
// COPYRIGHT:    University of California, San Francisco, 2013-2014
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.diagnostics;

import java.lang.management.ThreadInfo;

class JVMDeadlockedThreadInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "Deadlocked Java threads"; }

   public String getReport() {
      StringBuilder sb = new StringBuilder();

      java.lang.management.ThreadMXBean threadMXB = java.lang.management.ManagementFactory.getThreadMXBean();
      long[] deadlockedThreadIds = threadMXB.findDeadlockedThreads();

      if (deadlockedThreadIds != null && deadlockedThreadIds.length > 0) {
         sb.append("Deadlocked Java threads: ").
            append(Integer.toString(deadlockedThreadIds.length)).append(" detected\n");

         java.util.Arrays.sort(deadlockedThreadIds);
         ThreadInfo[] deadlockedInfos = threadMXB.getThreadInfo(deadlockedThreadIds, true, true);
         for (ThreadInfo tInfo : deadlockedInfos) {
            sb.append("Deadlocked Java thread: id ").append(Long.toString(tInfo.getThreadId())).
               append(" (\"").append(tInfo.getThreadName()).append("\"):").append('\n');

            java.lang.management.LockInfo blockingLock = tInfo.getLockInfo();
            sb.append("  Blocked waiting to lock ").append(blockingLock.getClassName()).append(' ').
               append(Integer.toString(blockingLock.getIdentityHashCode())).append('\n');

            java.lang.management.MonitorInfo[] monitors = tInfo.getLockedMonitors();
            java.lang.management.LockInfo[] synchronizers = tInfo.getLockedSynchronizers();
            StackTraceElement[] trace = tInfo.getStackTrace();
            for (StackTraceElement frame : trace) {
               sb.append("    at ").append(frame.toString()).append('\n');

               for (java.lang.management.MonitorInfo monitor : monitors) {
                  if (monitor.getLockedStackFrame().equals(frame)) {
                     sb.append("      where monitor was locked: ").
                        append(monitor.getClassName()).append(' ').
                        append(Integer.toString(monitor.getIdentityHashCode())).append('\n');
                  }
               }
            }
            for (java.lang.management.LockInfo sync : synchronizers) {
               sb.append("  Ownable synchronizer is locked: ").
                  append(sync.getClassName()).append(' ').
                  append(Integer.toString(sync.getIdentityHashCode())).append('\n');
            }
         }
      }
      else {
         sb.append("Deadlocked Java threads: none detected");
      }

      return sb.toString();
   }

   // TODO Put this in a proper test suite.
   private void createDeadlockedThreadPair() {
      final Object a = new Object();
      final Object b = new Object();
      Thread th0 = new Thread("DeadLockTestThread0") {
         public void run() {
            try {
               synchronized (a) {
                  Thread.sleep(100);
                  synchronized (b) {
                     Thread.sleep(1);
                  }
               }
            } catch (InterruptedException e) {
            }
         }
      };
      Thread th1 = new Thread("DeadLockTestThread1") {
         public void run() {
            try {
               synchronized (b) {
                  Thread.sleep(100);
                  synchronized (a) {
                     Thread.sleep(1);
                  }
               }
            } catch (InterruptedException e) {
            }
         }
      };
      th0.start();
      th1.start();

      try {
         Thread.sleep(200);
      } catch (InterruptedException exc) {
      }
   }
}
