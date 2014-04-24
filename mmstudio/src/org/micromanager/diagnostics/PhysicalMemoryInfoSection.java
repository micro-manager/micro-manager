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

class PhysicalMemoryInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "Physical memory information"; }

   public String getReport() {
      StringBuilder sb = new StringBuilder();

      java.lang.management.OperatingSystemMXBean osMXB =
         java.lang.management.ManagementFactory.getOperatingSystemMXBean();

      try { // Use HotSpot extensions if available
         Class<?> sunOSMXBClass = Class.forName("com.sun.management.OperatingSystemMXBean");

         java.lang.reflect.Method totalMemMethod = sunOSMXBClass.getMethod("getTotalPhysicalMemorySize");
         long totalRAM = ((Long) totalMemMethod.invoke(osMXB)).longValue();
         sb.append("Total physical memory (caveats apply if JVM is 32-bit): ").
            append(formatMemSize(totalRAM)).append('\n');

         try {
            java.lang.reflect.Method committedMemMethod = sunOSMXBClass.getMethod("getCommittedVirtualMemorySize");
            long committedVM = ((Long) committedMemMethod.invoke(osMXB)).longValue();
            sb.append("Committed virtual memory size: ").
               append(formatMemSize(committedVM)).append('\n');
         }
         catch (Exception e) {
            sb.append("Committed virtual memory size: unavailable\n");
         }

         java.lang.reflect.Method freeMemMethod = sunOSMXBClass.getMethod("getFreePhysicalMemorySize");
         long freeRAM = ((Long) freeMemMethod.invoke(osMXB)).longValue();
         sb.append("Free physical memory (may be meaningless if JVM is 32-bit): ").
            append(formatMemSize(freeRAM)).append('\n');
      }
      catch (Exception e) {
         // Possible exceptions: ClassNotFoundException, NoSuchMethodException,
         // IllegalAccessException, java.lang.reflect.InvocationTargetException
         sb.append("Physical memory information: unavailable");
      }

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
