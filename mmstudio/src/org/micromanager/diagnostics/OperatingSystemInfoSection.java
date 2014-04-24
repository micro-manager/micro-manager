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

class OperatingSystemInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "Operating system version information"; }

   public String getReport() {
      StringBuilder sb = new StringBuilder();
      sb.append("Operating system: ").append(System.getProperty("os.name")).
         append(" (").append(System.getProperty("os.arch")).append(") ").
         append(System.getProperty("os.version")).append('\n');
      return sb.toString();
   }
}
