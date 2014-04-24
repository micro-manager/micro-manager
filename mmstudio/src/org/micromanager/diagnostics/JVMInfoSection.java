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

class JVMInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "Java version information"; }

   public String getReport() {
      StringBuilder sb = new StringBuilder();

      sb.append("Java version: ").append(System.getProperty("java.version")).append('\n');

      sb.append("Java runtime: ").append(System.getProperty("java.runtime.name")).append(' ').
         append(System.getProperty("java.runtime.version")).append('\n');

      sb.append("JVM: ").append(System.getProperty("java.vm.name")).append("; ").
         append(System.getProperty("java.vm.info"));

      String jvmArch = System.getProperty("sun.arch.data.model");
      if (jvmArch != null) {
         sb.append("\nJVM architecture: ").append(jvmArch).append("-bit");
      }

      return sb.toString();
   }
}
