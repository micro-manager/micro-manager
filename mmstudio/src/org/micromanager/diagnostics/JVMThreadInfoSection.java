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

class JVMThreadInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "Java thread information"; }

   public String getReport() {
      StringBuilder sb = new StringBuilder();

      sb.append("All Java threads:\n");
      java.lang.management.ThreadMXBean threadMXB = java.lang.management.ManagementFactory.getThreadMXBean();
      long[] tids = threadMXB.getAllThreadIds();
      java.util.Arrays.sort(tids);
      ThreadInfo[] threadInfos = threadMXB.getThreadInfo(tids);
      for (ThreadInfo tInfo : threadInfos) {
         sb.append("  id ").append(Long.toString(tInfo.getThreadId())).
            append(" (\"").append(tInfo.getThreadName()).append("\"): ").
            append(tInfo.getThreadState().name()).append('\n');
      }
      sb.append("(End all Java threads)");

      return sb.toString();
   }
}
