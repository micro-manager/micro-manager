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

import java.io.File;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;


public class ProblemReport {
   private final mmcorej.CMMCore core_;

   private Integer logFileHandle_;
   private String logFileName_;

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

   private static class ProblemReportException extends Exception {
      public ProblemReportException(String msg) {
         super(msg);
      }
   }

   public ProblemReport(mmcorej.CMMCore core, org.micromanager.MMOptions prefs) {
      core_ = core;

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

      // Should not happen, but in case we are called erroneously:
      if (logFileHandle_ != null) {
         cancelLogCapture();
      }

      // TODO If the standard CoreLog location is writable, we should create
      // the temporary file there so that the user (and, in the future, the
      // application) can find it after a crash.
      File logFile;
      try {
         logFile =
            File.createTempFile("MMCoreLogCapture", ".txt");
      }
      catch (java.io.IOException e) {
         capturedLogContent_ =
            "<<<Failed to create temporary file for log capture>>>";
         return;
      }
      String filename = logFile.getAbsolutePath();

      try {
         logFileHandle_ = core_.startSecondaryLogFile(filename, true, true);
      }
      catch (Exception e) {
         capturedLogContent_ = "<<<Failed to start log capture>>>";
      }
      logFileName_ = filename;
      core_.logMessage("Problem Report: Start of log capture");
   }

   public void cancelLogCapture() {
      if (logFileHandle_ == null) {
         return;
      }

      core_.logMessage("Problem Report: Canceling log capture");
      try {
         core_.stopSecondaryLogFile(logFileHandle_);
      }
      catch (Exception ignore) {
         // Errors will be logged by the Core; there is not much else we can
         // do.
      }
      logFileHandle_ = null;
   }

   public void finishCapturingLog() {
      if (logFileHandle_ == null) {
         // Either starting the capture failed, or we were erroneously called
         // when capture is not running.
         endCfg_ = getCurrentConfigFile();
         return;
      }

      String logFileName = logFileName_;

      core_.logMessage("Problem Report: End of log capture");
      try {
         core_.stopSecondaryLogFile(logFileHandle_);
      }
      catch (Exception ignore) {
         // This is an unlikely error unless there are programming errors.
         // Let's continue and see if we can read the file anyway.
      }
      logFileHandle_ = null;
      logFileName_ = null;

      java.io.File logFile = new java.io.File(logFileName);
      try {
         capturedLogContent_ = readCapturedLogContent(logFile);
      }
      catch (ProblemReportException e) {
         capturedLogContent_ = "<<<Failed to read captured log file (" +
            e.getMessage() + ")>>>";
      }
      logFile.delete();

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
      if (startCfg_ == null || endCfg_ == null) {
         return startCfg_ != endCfg_;
      }
      return !startCfg_.equals(endCfg_);
   }

   String getStartingConfigFileName() {
      if (startCfg_ == null) {
         return null;
      }
      return startCfg_.getFileName();
   }

   String getEndingConfigFileName() {
      if (endCfg_ == null) {
         return null;
      }
      return endCfg_.getFileName();
   }

   String getStartingConfig() {
      if (startCfg_ == null) {
         return null;
      }
      return startCfg_.getContent();
   }

   String getEndingConfig() {
      if (endCfg_ == null) {
         return null;
      }
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

   private static String readCapturedLogContent(java.io.File file)
      throws ProblemReportException {

      // Java 7 has java.nio.charset.StandardCharsets.UTF_8.
      // In Java 6, you need 10 lines to get the same thing.
      Charset utf8Charset = null;
      try {
         utf8Charset = Charset.forName("UTF-8");
      }
      catch (java.nio.charset.IllegalCharsetNameException wontHappen) {
         // "UTF-8" is guaranteed to be available.
      }
      catch (java.nio.charset.UnsupportedCharsetException wontHappen) {
         // "UTF-8" is guaranteed to be supported.
      }
      catch (IllegalArgumentException wontHappen) {
         // This could only happen if we say Charset.forName(null).
      }

      FileInputStream inputStream;
      try {
         inputStream = new FileInputStream(file);
      }
      catch (java.io.FileNotFoundException e) {
         throw new ProblemReportException(e.getMessage());
      }
      try {
         FileChannel fChan = inputStream.getChannel();
         MappedByteBuffer mappedBuf =
            fChan.map(FileChannel.MapMode.READ_ONLY, 0, fChan.size());
         return utf8Charset.decode(mappedBuf).toString();
      }
      catch (java.io.IOException e) {
         throw new ProblemReportException(e.getMessage());
      }
      finally {
         try {
            inputStream.close();
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
