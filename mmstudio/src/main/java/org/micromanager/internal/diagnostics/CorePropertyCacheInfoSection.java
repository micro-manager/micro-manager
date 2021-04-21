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

class CorePropertyCacheInfoSection implements SystemInfo.SystemInfoSection {
  @Override
  public String getTitle() {
    return "Core information";
  }

  @Override
  public String getReport() {
    mmcorej.CMMCore c = org.micromanager.internal.MMStudio.getInstance().getCMMCore();

    StringBuilder sb = new StringBuilder();
    sb.append("Core property cache (\"system state cache\") contents:\n");
    final mmcorej.Configuration cachedProps = c.getSystemStateCache();
    long count = cachedProps.size();
    for (long i = 0; i < count; i++) {
      try {
        final mmcorej.PropertySetting s = cachedProps.getSetting(i);
        final String device = s.getDeviceLabel();
        final String name = s.getPropertyName();
        final String value = s.getPropertyValue();
        final String roString = s.getReadOnly() ? " [RO]" : "";
        sb.append("  ")
            .append(device)
            .append("/")
            .append(name)
            .append(roString)
            .append(" = ")
            .append(value)
            .append('\n');
      } catch (Exception e) {
        sb.append("  Error while getting cache item ")
            .append(Long.toString(i))
            .append(": ")
            .append(e.getMessage())
            .append('\n');
      }
    }
    return sb.toString();
  }
}
