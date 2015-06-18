// AUTHOR:       Mark Tsuchida
// COPYRIGHT:    University of California, San Francisco, 2014
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.diagnostics;

import java.util.ArrayList;
import java.util.List;

public final class SystemInfo {
   public static void dumpAllToCoreLog(boolean includeUnchanging) {
      List<SystemInfoSection> sections = getAllSections(includeUnchanging);
      for (SystemInfoSection section : sections) {
         mmcorej.CMMCore core = org.micromanager.MMStudio.getInstance().getCore();
         core.logMessage(section.getReport());
      }
   }

   public static String getAllAsText(boolean includeUnchanging) {
      StringBuilder sb = new StringBuilder();
      List<SystemInfoSection> sections = getAllSections(includeUnchanging);
      for (SystemInfoSection section : sections) {
         sb.append(section.getReport());
      }
      return sb.toString();
   }

   public interface SystemInfoSection {
      String getTitle();
      String getReport();

      // In addition to implementing the above methods, implementing classes
      // must satisfy the following:
      // - Objects should be immutable (information should be collected at
      //   construction time)
      // - Constructors should not throw.
   }

   // Private for the time being
   private static List<SystemInfoSection> getAllSections(boolean includeUnchanging) {
      List<SystemInfoSection> sections = new ArrayList<SystemInfoSection>();

      if (includeUnchanging) {
         sections.add(new MMVersionInfoSection());
         sections.add(new ImageJInfoSection());
         sections.add(new OperatingSystemInfoSection());
         sections.add(new JVMInfoSection());
         sections.add(new JVMArgumentsInfoSection());
         sections.add(new JavaSystemPropertiesInfoSection()); // Strictly speaking, not unchanging.
         sections.add(new ProcessorInfoSection());
      }
      sections.add(new PhysicalMemoryInfoSection());
      sections.add(new JVMMemoryInfoSection());
      sections.add(new JVMThreadInfoSection());
      sections.add(new JVMDeadlockedThreadInfoSection());
      sections.add(new CoreBasicInfoSection());
      sections.add(new CorePropertyCacheInfoSection());

      return sections;
   }
}
