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

class JVMArgumentsInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "JVM arguments"; }

   public String getReport() {
      StringBuilder sb = new StringBuilder();

      java.lang.management.RuntimeMXBean rtMXB = java.lang.management.ManagementFactory.getRuntimeMXBean();
      sb.append("JVM arguments:\n");
      java.util.List<String> args = rtMXB.getInputArguments();
      for (String a : args) {
         sb.append("  ").append(a).append('\n');
      }
      sb.append("(End JVM args)");

      return sb.toString();
   }
}
