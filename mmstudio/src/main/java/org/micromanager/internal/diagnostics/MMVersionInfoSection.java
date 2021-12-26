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

package org.micromanager.internal.diagnostics;

class MMVersionInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() {
      return "Micro-Manager version information";
   }

   public String getReport() {
      StringBuilder sb = new StringBuilder();

      sb.append("MMStudio version: ")
            .append(org.micromanager.internal.MMVersion.VERSION_STRING).append('\n');

      mmcorej.CMMCore core = org.micromanager.internal.MMStudio.getInstance().core();
      sb.append("Core version: ").append(core.getVersionInfo()).append('\n');
      sb.append("Core device API version: ").append(core.getAPIVersionInfo());

      return sb.toString();
   }
}
