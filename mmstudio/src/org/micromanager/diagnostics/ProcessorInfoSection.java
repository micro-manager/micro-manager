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

class ProcessorInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "Processor information"; }

   public String getReport() {
      StringBuilder sb = new StringBuilder();

      int ncpu = Runtime.getRuntime().availableProcessors();
      sb.append("Processors available to JVM: ").append(Integer.toString(ncpu));

      return sb.toString();
   }
}
