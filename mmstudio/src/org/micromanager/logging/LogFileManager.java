// AUTHOR:        Mark Tsuchida
// COPYRIGHT:     University of California, San Francisco, 2014
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.logging;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogFileManager {
   public static String makeLogFileNameForCurrentSession() {
      String filename = System.getProperty("org.micromanager.corelog.file");
      if (filename != null && filename.length() > 0) {
         return filename;
      }

      return new File(getLogFileDirectory(), makeLogFileLeafName()).
         getAbsolutePath();
   }

   public static void deleteLogFilesDaysOld(int days, String fileToExclude) {
      File excludedFile = null;
      if (fileToExclude != null && fileToExclude.length() > 0) {
         try {
            excludedFile = new File(fileToExclude).getCanonicalFile();
         }
         catch (IOException e) {
            // Leave excludedFile null. (If we can't get the canonical path for
            // it, we are probably not in danger of deleting it.)
         }
      }

      Calendar cutoffDate = Calendar.getInstance();
      cutoffDate.add(Calendar.DAY_OF_MONTH, -days);

      FilenameFilter coreLogFilter = new CoreLogFilenameFilter();

      File[] legacyLogFiles =
         getLegacyLogFileDirectory().listFiles(coreLogFilter);
      if (legacyLogFiles == null) {
         legacyLogFiles = new File[0];
      }
      for (File file : Arrays.asList(legacyLogFiles)) {
         Calendar fileDate = getLegacyLogFileDate(file.getName());
         if (fileDate != null && fileDate.before(cutoffDate)) {
            if (!fileShouldBeExcluded(file, excludedFile)) {
               file.delete();
            }
         }
      }

      File[] modernLogFiles =
         getLogFileDirectory().listFiles(coreLogFilter);
      if (modernLogFiles == null) {
         modernLogFiles = new File[0];
      }
      for (File file : Arrays.asList(modernLogFiles)) {
         Calendar fileDate = getLogFileDate(file.getName());
         if (fileDate != null && fileDate.before(cutoffDate)) {
            if (!fileShouldBeExcluded(file, excludedFile)) {
               file.delete();
            }
         }
      }
   }

   public static File getLogFileDirectory() {
      String dirname = System.getProperty("org.micromanager.corelog.dir");
      if (dirname == null || dirname.length() == 0) {
         dirname = "CoreLogs";
      }
      return new File(dirname);
   }

   // Return the directory where old-style CoreLogYYYYMMDD.txt were saved.
   // (But don't assume current dir if corelog dir is set.)
   public static File getLegacyLogFileDirectory() {
      String dirname = System.getProperty("org.micromanager.corelog.dir");
      if (dirname == null || dirname.length() == 0) {
         dirname = System.getProperty("user.dir");
      }
      return new File(dirname);
   }

   private static String makeLogFileLeafName() {
      String dateTime = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss").
         format(new java.util.Date());

      // Try to get the process id. This method is not guaranteed to give
      // the pid on all platforms/JVMs, but then, having the pid is not
      // essential either. Just make sure not to fail if it doesn't work.
      java.lang.management.RuntimeMXBean rtMXB =
         java.lang.management.ManagementFactory.getRuntimeMXBean();
      // jvmName is usually a string like 1234@host.example.com
      String jvmName = rtMXB.getName();
      String pidStr;
      try {
         pidStr = "_pid" + Integer.parseInt(jvmName.split("@")[0]);
      }
      catch (NumberFormatException e) {
         pidStr = "";
      }

      return "CoreLog" + dateTime + pidStr + ".txt";
   }

   private static class CoreLogFilenameFilter implements FilenameFilter {
      @Override
      public boolean accept(File dir, String name) {
         return name.startsWith("CoreLog") && name.endsWith(".txt");
      }
   }

   private static Calendar getLogFileDate(String filename) {
      Pattern modernPattern = Pattern.compile(
            "CoreLog(\\d{4})(\\d{2})(\\d{2})T" +
            "(\\d{2})(\\d{2})(\\d{2})(_pid\\d+)?\\.txt");
      Matcher m = modernPattern.matcher(filename);
      if (m.matches()) {
         int year = Integer.parseInt(m.group(1));
         int month = Integer.parseInt(m.group(2)) - 1;
         int day = Integer.parseInt(m.group(3));
         int hour = Integer.parseInt(m.group(4));
         int minute = Integer.parseInt(m.group(5));
         int second = Integer.parseInt(m.group(6));
         return new GregorianCalendar(year, month, day, hour, minute, second);
      }
      return null;
   }

   private static Calendar getLegacyLogFileDate(String filename) {
      Pattern legacyPattern =
         Pattern.compile("CoreLog(\\d{4})(\\d{2})(\\d{2})\\.txt");
      Matcher m = legacyPattern.matcher(filename);
      if (m.matches()) {
         int year = Integer.parseInt(m.group(1));
         int month = Integer.parseInt(m.group(2)) - 1;
         int day = Integer.parseInt(m.group(3));
         return new GregorianCalendar(year, month, day);
      }
      return null;
   }

   private static boolean fileShouldBeExcluded(File file,
         File canonicalExcludedFile) {
      try {
         if (file.getCanonicalFile().equals(canonicalExcludedFile)) {
            return true;
         }
      }
      catch (IOException e) {
         return true;
      }
      return false;
   }
}
