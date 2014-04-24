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

class JavaSystemPropertiesInfoSection implements SystemInfo.SystemInfoSection {
   public String getTitle() { return "Java system properties"; }

   public String getReport() {
      String pathSep = System.getProperty("path.separator");
      if (pathSep.length() == 0) {
         pathSep = null;
      }

      StringBuilder sb = new StringBuilder();
      sb.append("Java system properties:\n");

      java.util.Properties sysProps = System.getProperties();
      java.util.List<String> propKeys = new java.util.ArrayList<String>();
      java.util.Enumeration<Object> e = sysProps.keys();
      while (e.hasMoreElements()) {
         propKeys.add((String) e.nextElement());
      }
      java.util.Collections.sort(propKeys);
      for (String k : propKeys) {
         if (pathSep != null && pathListProperties_.contains(k)) {
            sb.append("  ").append(k).append(" (split at \'").append(pathSep).append("\') =\n");
            String[] paths = sysProps.getProperty(k).split(pathSep);
            for (String path : paths) {
               sb.append("    ").append(path).append('\n');
            }
         }
         else {
            sb.append("  ").append(k).append(" = ").append(sysProps.getProperty(k)).append('\n');
         }
      }

      sb.append("(End Java system properties)");
      return sb.toString();
   }

   private static final java.util.Set<String> pathListProperties_;
   static {
      pathListProperties_ = new java.util.HashSet<String>();
      pathListProperties_.add("java.class.path");
      pathListProperties_.add("java.library.path");
      pathListProperties_.add("sun.boot.class.path");
      pathListProperties_.add("sun.boot.library.path");
   }
}
