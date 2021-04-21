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

class CoreBasicInfoSection implements SystemInfo.SystemInfoSection {
  @Override
  public String getTitle() {
    return "Core information";
  }

  @Override
  public String getReport() {
    mmcorej.CMMCore c = org.micromanager.internal.MMStudio.getInstance().getCMMCore();

    StringBuilder sb = new StringBuilder();
    sb.append("MMCore version: ").append(c.getVersionInfo()).append('\n');
    sb.append("Circular buffer size (MB): ")
        .append(Long.toString(c.getCircularBufferMemoryFootprint()));
    return sb.toString();
  }
}
