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

package org.micromanager.diagnostics;


public class ProblemReport {
   private final mmcorej.CMMCore core_;
   private final org.micromanager.MMOptions prefs_;

   private String userName_;
   private String userOrganization_;
   private String userEmail_;
   private String description_;

   private String macAddress_;
   private String ipAddress_;
   private String hostName_;

   private final java.util.Date date_; // Treat as if immutable!

   private ConfigFile startCfg_;
   private String capturedLogContent_;
   private ConfigFile endCfg_;

   public ProblemReport(mmcorej.CMMCore core, org.micromanager.MMOptions prefs) {
      core_ = core;
      prefs_ = prefs;

      date_ = new java.util.Date();

      collectHostInformation();
   }

   public void setUserName(String name) {
      userName_ = name;
   }

   public String getUserName() {
      return userName_;
   }

   public void setUserOrganization(String organization) {
      userOrganization_ = organization;
   }

   public String getUserOrganization() {
      return userOrganization_;
   }

   public void setUserEmail(String email) {
      userEmail_ = email;
   }

   public String getUserEmail() {
      return userEmail_;
   }

   public void setDescription(String description) {
      description_ = description;
   }

   public String getDescription() {
      return description_;
   }

   public void startCapturingLog() {
      startCfg_ = getCurrentConfigFile();
      core_.clearLog();
      core_.logMessage("CoreLog cleared by problem reporter");
      core_.enableDebugLog(true);
      core_.logMessage("Problem Report: Start of log capture");
   }

   public void cancelLogCapture() {
      core_.enableDebugLog(prefs_.debugLogEnabled_);
   }

   public void finishCapturingLog() {
      core_.logMessage("Problem Report: End of log capture");

      // Hack: The end of the logged info gets truncated, due to the behavior
      // of the Core logger. Until it is fixed, just wait a bit to remedy.
      for (;;) {
         try {
            Thread.sleep(200);
            break;
         }
         catch (InterruptedException e) {
         }
      }

      core_.enableDebugLog(prefs_.debugLogEnabled_);
      java.io.File logGzFile = new java.io.File(core_.saveLogArchive());
      capturedLogContent_ = readCapturedLogContent(logGzFile);
      logGzFile.delete();

      endCfg_ = getCurrentConfigFile();
   }

   public void logSystemInfo(boolean incremental) {
      final String inc = incremental ? " (incremental)" : "";
      core_.logMessage("***** BEGIN Problem Report System Info" + inc + " *****");
      SystemInfo.dumpAllToCoreLog(!incremental);
      core_.logMessage("***** END Problem Report System Info" + inc + " *****");
   }

   public void logUserComment(String comment) {
      core_.logMessage("User remark: " + comment);
   }

   /*
    * Package-private accessors used by report formatter
    */

   boolean configChangedDuringLogCapture() {
      return !startCfg_.equals(endCfg_);
   }

   String getStartingConfigFileName() {
      return startCfg_.getFileName();
   }

   String getEndingConfigFileName() {
      return endCfg_.getFileName();
   }

   String getStartingConfig() {
      return startCfg_.getContent();
   }

   String getEndingConfig() {
      return endCfg_.getContent();
   }

   String getCapturedLogContent() {
      return capturedLogContent_;
   }

   String getMACAddress() {
      return macAddress_;
   }

   String getHostName() {
      return hostName_;
   }

   String getIPAddress() {
      return ipAddress_;
   }

   String getUserId() {
      return core_.getUserId();
   }

   java.util.Date getDate() {
      return (java.util.Date)date_.clone();
   }

   /*
    * Private helper methods and classes
    */

   private void collectHostInformation() {
      macAddress_ = null;
      mmcorej.StrVector addrs = core_.getMACAddresses();
      if (addrs.size() > 0) {
         String addr = addrs.get(0);
         if (addr.length() > 0) {
            macAddress_ = addr;
         }
      }

      hostName_ = null;
      try {
         hostName_ = java.net.InetAddress.getLocalHost().getHostName();
      }
      catch (java.io.IOException ignore) {
      }

      ipAddress_ = null;
      try {
         ipAddress_ = java.net.InetAddress.getLocalHost().getHostAddress();
      }
      catch (java.io.IOException ignore) {
      }
   }

   private static String readCapturedLogContent(java.io.File gzFile) {
      // This is a hack. It is rather pointless to gzip-compress the
      // communication between the Core and us and this should be removed.

      java.io.FileInputStream compressedInput;
      try {
         compressedInput = new java.io.FileInputStream(gzFile);
      }
      catch (java.io.FileNotFoundException e) {
         return e.getMessage();
      }

      try {
         java.io.InputStream logInput = new java.util.zip.GZIPInputStream(compressedInput);
         java.io.Reader reader = new java.io.InputStreamReader(logInput, "UTF-8");

         char[] cbuf = new char[8192];
         StringBuilder sb = new StringBuilder();
         int read;
         while ((read = reader.read(cbuf)) > 0) {
            sb.append(cbuf, 0, read);
         }
         return sb.toString();
      }
      catch (java.io.IOException e) {
         return e.getMessage();
      }
      finally {
         try {
            compressedInput.close();
         }
         catch (java.io.IOException ignore) {
         }
      }
   }

   private static ConfigFile getCurrentConfigFile() {
      String fileName = org.micromanager.MMStudioMainFrame.getInstance().getSysConfigFile();
      return new ConfigFile(fileName);
   }

   private static class ConfigFile {
      final private String fileName_;
      final private String content_;

      public ConfigFile(String fileName) {
         fileName_ = fileName;
         String content = null;

         java.io.File file = new java.io.File(fileName);
         java.io.Reader reader = null;
         try {
            reader = new java.io.FileReader(file);
         }
         catch (java.io.FileNotFoundException e) {
            content = e.getMessage();
         }

         StringBuilder sb = new StringBuilder();
         if (reader != null) {
            try {
               int read;
               char[] buf = new char[8192];
               while ((read = reader.read(buf)) > 0) {
                  sb.append(buf, 0, read);
               }
            }
            catch (java.io.IOException e) {
               content = e.getMessage();
            }
            finally {
               try {
                  reader.close();
               }
               catch (java.io.IOException ignore) {
               }
            }
         }

         if (content == null) {
            content = sb.toString();
         }

         content_ = content;
      }

      public boolean equals(ConfigFile rhs) {
         if (this == rhs) {
            return true;
         }

         if (!fileName_.equals(rhs.fileName_)) {
            return false;
         }

         // This could be optimized using MD5 hashes, but why bother.
         return content_.equals(rhs.content_);
      }

      public String getFileName() {
         return fileName_;
      }

      public String getContent() {
         return content_;
      }
   }
}
