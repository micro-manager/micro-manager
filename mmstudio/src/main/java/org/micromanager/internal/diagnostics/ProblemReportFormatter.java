// AUTHOR:       Mark Tsuchida (based in part on previous version by Karl Hoover)
// COPYRIGHT:    University of California, San Francisco, 2010-2014
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.diagnostics;


public final class ProblemReportFormatter {
   static final String FORMAT_VERSION = "3";
   /*
    * Version history:
    * 2    Introduced versioning.
    * 2.1  Added Pid field.
    */

   /**
    * Generate a formatted report
    */
   public String format(ProblemReport report) {
      StringBuilder sb = new StringBuilder();

      // TODO Generate partial report on failure

      sb.append(formatPreamble(report));

      // Note: Older versions included only the configuration at the time of
      // sending the log. This was confusing.
      if (report.hasStartingConfig()) {
         sb.append(fileSection("Starting Config (" + report.getStartingConfigFileName() + ")",
                  report.getStartingConfig()));
      }
      if (report.configChangedDuringLogCapture() && report.hasEndingConfig()) {
         sb.append(fileSection("Ending Config (" + report.getEndingConfigFileName() + ")",
                  report.getEndingConfig()));
      }

      sb.append(fileSection("Captured Log", report.getCapturedLogContent()));

      if (report.hasHotSpotErrorLog()) {
         sb.append(fileSection("HotSpot Error Log (" + report.getHotSpotErrorLogFileName() + ")",
                 report.getHotSpotErrorLogContent()));
      }

      sb.append("***** END OF PROBLEM REPORT *****");
      return sb.toString();
   }

   private String sanitizeFileName(String name) {
      // Keep these symbols in ASCII order
      return name.replaceAll("[ !\"#$%&'()*/:;<>?@\\[\\\\\\]^`{|}~]", "_");
   }

   private String formatPreamble(ProblemReport report) {
      // This content is parsed by the server; its format should not be
      // changed without extreme care.
      StringBuilder sb = new StringBuilder(512);

      sb.append(preambleKeyValue("Report Format Version", FORMAT_VERSION));

      String description = report.getDescription();
      if (description == null) {
         description = "null";
      }
      description = description.replaceAll("\\s+$", ""); // Trim right
      description = description.replaceAll("(?m)^", "|  "); // Indent each line
      sb.append(preambleKeyValue("User Description",
            description.isEmpty() ? "" : "\n" + description));

      sb.append(preambleKeyValue("Pid", Integer.toString(report.getPid())));

      // Note: Older versions had a single "configuration file" key (note
      // lowercase), and included only the configuration at the time of sending
      // the log. This was confusing.
      sb.append(preambleKeyValue("Starting Config File", report.getStartingConfigFileName()));
      if (report.configChangedDuringLogCapture()) {
         sb.append(preambleKeyValue("Config Changed", "yes"));
         sb.append(preambleKeyValue("Ending Configuration File", report.getEndingConfigFileName()));
      }
      else {
         sb.append(preambleKeyValue("Config Changed", "no"));
      }

      return sb.toString();
   }

   private static String preambleKeyValue(String key, String value) {
      return "#" + key + ": " + value + "\n";
   }

   private static String fileSection(String title, String content) {
      if (title == null) {
         title = "null";
      }
      if (content == null) {
         content = "null";
      }

      StringBuilder sb = new StringBuilder(title.length() + content.length() + 128);
      sb.append("***** BEGIN " + title + " *****\n");
      sb.append(content);
      if (sb.charAt(sb.length() - 1) != '\n') {
         sb.append('\n');
      }
      sb.append("***** END " + title + " *****\n");
      return sb.toString();
   }
}
