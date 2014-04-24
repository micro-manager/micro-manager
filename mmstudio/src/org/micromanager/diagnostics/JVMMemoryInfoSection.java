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

class JVMMemoryInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "JVM memory information"; }

   public String getReport() {
      StringBuilder sb = new StringBuilder();

      java.lang.management.MemoryMXBean memMXB = java.lang.management.ManagementFactory.getMemoryMXBean();
      sb.append("JVM heap memory usage: ").append(formatMemUsage(memMXB.getHeapMemoryUsage())).append('\n');
      sb.append("JVM non-heap memory usage: ").append(formatMemUsage(memMXB.getNonHeapMemoryUsage()));

      return sb.toString();
   }

   private String formatMemUsage(java.lang.management.MemoryUsage usage) {
      StringBuilder sb = new StringBuilder();
      sb.append("used = ").append(formatMemSize(usage.getUsed())).
         append("; committed = ").append(formatMemSize(usage.getCommitted())).
         append("; max = ").append(formatMemSize(usage.getMax()));
      return sb.toString();
   }

   private String formatMemSize(long size) {
      if (size == -1) {
         return "unavailable";
      }
      if (size < 1024) {
         return Long.toString(size) + " bytes";
      }

      double bytes = size;
      java.text.NumberFormat format = new java.text.DecimalFormat("#.0");

      if (size < 1024 * 1024) {
         return Long.toString(size) + " (" + format.format(bytes / 1024) + " KiB)";
      }
      if (size < 1024 * 1024 * 1024) {
         return Long.toString(size) + " (" + format.format(bytes / (1024 * 1024)) + " MiB)";
      }
      return Long.toString(size) + " (" + format.format(bytes / (1024 * 1024 * 1024)) + " GiB)";
   }
}
