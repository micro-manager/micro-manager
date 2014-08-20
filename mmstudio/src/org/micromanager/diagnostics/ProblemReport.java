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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import mmcorej.CMMCore;


public class ProblemReport {
   private final CMMCore core_;

   private File reportDir_; // null if non-persistent
   private File leftoverDir_;

   // Designed for serialization via GSON.
   private static class Metadata {
      // Used boxed types to allow null
      public Integer pid;
      public Date date;
      public String mmStudioVersion;
      public String startCfgFilename;
      public String endCfgFilename;
      public String userName;
      public String userOrganization;
      public String userEmail;
      public String description;
      public String macAddress;
      public String ipAddress;
      public String hostName;
      public String userLogin;
      public String currentDir;
   }

   private Metadata metadata_;

   private Integer logFileHandle_;
   private String logFileName_;

   private NamedTextFile startCfg_;
   private String capturedLogContent_;
   private NamedTextFile endCfg_;
   private NamedTextFile hotSpotErrorLog_;

   private Timer deferredSyncTimer_ = null;

   // Filenames and strings for persistence
   private static final String LOG_CAPTURE_FILENAME = "CoreLogCapture.txt";
   private static final String START_CFG_FILENAME = "StartConfig.cfg";
   private static final String END_CFG_FILENAME = "EndConfig.cfg";
   private static final String METADATA_FILENAME = "ReportInfo.txt";
   private static final String README_FILENAME = "README.txt";

   /**
    * Create a new problem report.
    *
    * The report will not be backed by persistent storage, and therefore will
    * not be crash-proof.
    *
    * Note that although core is used for logging control, but the information
    * logged may come from global state (the system information classes obtain
    * the Core from the MMStudio singleton).
    *
    * @param core the Core.
    */
   public static ProblemReport NewReport(CMMCore core) {
      return new ProblemReport(core);
   }

   /**
    * Create a new disk-backed report.
    *
    * Note that although core is used for logging control, but the information
    * logged may come from global state (the system information classes obtain
    * the Core from the MMStudio singleton).
    *
    * If there are any errors writing to storageDirectory, they are silently
    * ignored (and the report will behave as if it were non-persistent).
    *
    * @param core the Core.
    * @param storageDirectory where to save the report data.
    */
   public static ProblemReport NewPersistentReport(CMMCore core,
         File storageDirectory) {
      return new ProblemReport(core, storageDirectory);
   }

   /**
    * Create a report by loading a leftover disk-backed report.
    *
    * @param storageDirectory where to load the report data from.
    */
   public static ProblemReport LoadFromPersistence(File storageDirectory) {
      return new ProblemReport(storageDirectory);
   }

   private ProblemReport(CMMCore core) {
      core_ = core;

      metadata_ = new Metadata();
      metadata_.date = new Date();

      collectHostInformation();
   }

   private ProblemReport(CMMCore core, File storageDirectory) {
      this(core);
      reportDir_ = storageDirectory;
      syncMetadata();
   }

   private ProblemReport(File storageDirectory) {
      core_ = null;
      reportDir_ = null; // No further saving to disk
      loadReport(storageDirectory);
   }

   /**
    * Return true of the report contains anything substantial.
    */
   public boolean isUsefulReport() {
      if (metadata_ == null)
         return false;
      if (capturedLogContent_ != null && !capturedLogContent_.isEmpty())
         return true;
      return false;
   }

   /**
    * Set the user full name.
    *
    * This is for the name entered by the user, not the login name.
    * @param name the name.
    */
   public void setUserName(String name) {
      metadata_.userName = name;
      deferredSyncMetadata();
   }

   /**
    * Get the user full name.
    *
    * This is the human-readable name entered by the user, not the login name.
    * @return the name.
    */
   public String getUserName() {
      return metadata_.userName;
   }

   /**
    * Set the user organization name.
    * @param organization the organization name.
    */
   public void setUserOrganization(String organization) {
      metadata_.userOrganization = organization;
      deferredSyncMetadata();
   }

   /**
    * Get the user organization name.
    * @return the organization name.
    */
   public String getUserOrganization() {
      return metadata_.userOrganization;
   }

   /**
    * Set the user email address.
    * @param email the email address.
    */
   public void setUserEmail(String email) {
      metadata_.userEmail = email;
      deferredSyncMetadata();
   }

   /**
    * Get the user email address.
    * @return the email address.
    */
   public String getUserEmail() {
      return metadata_.userEmail;
   }

   /**
    * Set the user-entered description.
    * @param description the description.
    */
   public void setDescription(String description) {
      metadata_.description = description;
      deferredSyncMetadata();
   }

   /**
    * Get the user-entered description.
    * @return the description.
    */
   public String getDescription() {
      return metadata_.description;
   }

   /**
    * Start CoreLog capture.
    *
    * Save the current hardware configuration file, then start capturing the
    * CoreLog.
    */
   public void startCapturingLog(boolean useCrashRobust) {
      startCfg_ = getCurrentConfigFile();
      syncStartingConfig();

      // Should not happen, but in case we are called erroneously:
      if (logFileHandle_ != null) {
         cancelLogCapture();
      }

      File logFile = null;
      if (reportDir_ != null) {
         logFile = new File(reportDir_, LOG_CAPTURE_FILENAME);
         if (!logFile.exists()) {
            // Touch, so that we can test canWrite below
            try {
               new FileOutputStream(logFile).close();
            }
            catch (java.io.FileNotFoundException dealWithLater) {
            }
            catch (java.io.IOException ignore) {
            }
         }
      }
      else {
         try {
            logFile = File.createTempFile("MMCoreLogCapture", ".txt");
         }
         catch (java.io.IOException dealWithLater) {
         }
      }
      if (logFile == null || !logFile.canWrite()) {
         capturedLogContent_ =
            "<<<Cannot write to temporary file for log capture>>>";
         return;
      }
      String filename = logFile.getAbsolutePath();

      try {
         logFileHandle_ = core_.startSecondaryLogFile(filename,
               true, true, useCrashRobust);
      }
      catch (Exception e) {
         capturedLogContent_ = "<<<Failed to start log capture>>>";
      }
      logFileName_ = filename;
      core_.logMessage("Problem Report: Start of log capture");
   }

   /**
    * Stop CoreLog capture and discard captured log.
    */
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

      new File(logFileName_).delete();
      logFileName_ = null;
   }

   /**
    * Stop CoreLog capture and record the captured log as part of the report.
    *
    * After stopping CoreLog capture, the current hardware configuration file
    * is also recorded.
    */
   public void finishCapturingLog() {
      if (logFileHandle_ == null) {
         // Either starting the capture failed, or we were erroneously called
         // when capture is not running.
         endCfg_ = getCurrentConfigFile();
         syncEndingConfig();
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
      capturedLogContent_ = readTextFile(logFile);
      if (capturedLogContent_ == null) {
         capturedLogContent_ = "<<<Failed to read captured log file>>>";
      }
      if (reportDir_ == null) { // We used an ad-hoc temporary file
         logFile.delete();
      }

      endCfg_ = getCurrentConfigFile();
      syncEndingConfig();
   }

   public void deleteStorage() {
      deleteReportDir(reportDir_);
      reportDir_ = null;
      deleteReportDir(leftoverDir_);
      leftoverDir_ = null;
   }

   /**
    * Dump system information to the CoreLog.
    *
    * This is intended to be called after startCapturingLog().
    *
    * @param incremental if true, only dump info that is likely to change.
    */
   public void logSystemInfo(boolean incremental) {
      final String inc = incremental ? " (incremental)" : "";
      core_.logMessage("***** BEGIN Problem Report System Info" + inc + " *****");
      SystemInfo.dumpAllToCoreLog(!incremental);
      core_.logMessage("***** END Problem Report System Info" + inc + " *****");
   }

   public void logUserComment(String comment) {
      core_.logMessage("##### User remark: " + comment);
   }

   /*
    * Package-private accessors used by report formatter
    */

   boolean hasStartingConfig() {
      return startCfg_ != null;
   }

   boolean hasEndingConfig() {
      return endCfg_ != null;
   }

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
      return startCfg_.getFilename();
   }

   String getEndingConfigFileName() {
      if (endCfg_ == null) {
         return null;
      }
      return endCfg_.getFilename();
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

   boolean hasHotSpotErrorLog() {
      return hotSpotErrorLog_ != null;
   }

   String getHotSpotErrorLogFileName() {
      if (hotSpotErrorLog_ == null) {
         return null;
      }
      return hotSpotErrorLog_.getFilename();
   }

   String getHotSpotErrorLogContent() {
      if (hotSpotErrorLog_ == null) {
         return null;
      }
      return hotSpotErrorLog_.getContent();
   }

   String getMACAddress() {
      return metadata_.macAddress;
   }

   String getHostName() {
      return metadata_.hostName;
   }

   String getIPAddress() {
      return metadata_.ipAddress;
   }

   String getUserId() {
      return metadata_.userLogin;
   }

   int getPid() {
      return metadata_.pid;
   }

   Date getDate() {
      return (Date) metadata_.date.clone();
   }

   /*
    * Private helper methods and classes
    */

   private void collectHostInformation() {
      java.lang.management.RuntimeMXBean rtMXB =
         java.lang.management.ManagementFactory.getRuntimeMXBean();
      final String jvmName = rtMXB.getName();
      try {
         metadata_.pid = Integer.parseInt(jvmName.split("@")[0]);
      }
      catch (NumberFormatException e) {
         metadata_.pid = null;
      }

      metadata_.macAddress = null;
      mmcorej.StrVector addrs = core_.getMACAddresses();
      if (addrs.size() > 0) {
         String addr = addrs.get(0);
         if (addr.length() > 0) {
            metadata_.macAddress = addr;
         }
      }

      metadata_.hostName = null;
      try {
         metadata_.hostName = java.net.InetAddress.getLocalHost().getHostName();
      }
      catch (java.io.IOException ignore) {
      }

      metadata_.ipAddress = null;
      try {
         metadata_.ipAddress = java.net.InetAddress.getLocalHost().getHostAddress();
      }
      catch (java.io.IOException ignore) {
      }

      metadata_.userLogin = core_.getUserId();
      metadata_.currentDir = System.getProperty("user.dir");
   }

   private static String readTextFile(java.io.File file) {
      // Important: do NOT try to use java.nio mapped file channel to read.
      // Windows will not be able to delete a file once it has been mapped in
      // the current process, no matter how correctly we "close" it.
      Reader reader = null;
      try {
         reader = new FileReader(file);
      }
      catch (java.io.FileNotFoundException e) {
         return e.getMessage();
      }
      StringBuilder sb = new StringBuilder();
      try {
         int read;
         char[] buf = new char[8192];
         while ((read = reader.read(buf)) > 0) {
            sb.append(buf, 0, read);
         }
      }
      catch (java.io.IOException e) {
         return e.getMessage();
      }
      finally {
         try {
            reader.close();
         }
         catch (java.io.IOException ignore) {
         }
      }
      return sb.toString();
   }

   private static void writeTextFile(java.io.File file, String text) {
      FileOutputStream outputStream;
      try {
         outputStream = new FileOutputStream(file);
      }
      catch (java.io.FileNotFoundException e) {
         return;
      }
      OutputStreamWriter writer = null;
      try {
         writer = new OutputStreamWriter(outputStream, "UTF-8");
      }
      catch (java.io.UnsupportedEncodingException wontHappen) {
         // "UTF-8" is guaranteed to be supported.
      }
      try {
         writer.write(text);
      }
      catch (java.io.IOException e) {
         return;
      }
      finally {
         try {
            writer.close();
         }
         catch (java.io.IOException ignore) {
         }
      }
   }

   private void createReportDir() {
      if (reportDir_ == null) {
         return;
      }

      if (reportDir_.mkdirs()) {
         File readmeFile = new File(reportDir_, README_FILENAME);
         if (!readmeFile.isFile()) {
            String readme =
               "This directory contains an in-progress (or crashed) \n" +
               "Micro-Manager Problem Report. It is safe to delete.";
            writeTextFile(readmeFile, readme);
         }
      }
      // Ignore errors.
   }

   private void deleteReportDir(File directory) {
      if (directory != null) {
         new File(directory, LOG_CAPTURE_FILENAME).delete();
         new File(directory, START_CFG_FILENAME).delete();
         new File(directory, END_CFG_FILENAME).delete();
         new File(directory, METADATA_FILENAME).delete();
         new File(directory, README_FILENAME).delete();
         directory.delete();
      }
   }

   private Gson makeGson() {
      final DateFormat format =
         new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

      class MyDateSerializer implements JsonSerializer<Date> {
         @Override public JsonElement serialize(Date src,
               Type srcType,
               JsonSerializationContext context) {
            return new JsonPrimitive(format.format(src));
         }
      }

      class MyDateDeserializer implements JsonDeserializer<Date> {
         @Override public Date deserialize(JsonElement json, Type dstType,
               JsonDeserializationContext context) throws JsonParseException {
            try {
               return format.parse(json.getAsJsonPrimitive().getAsString());
            }
            catch (java.text.ParseException e) {
               return null;
            }
         }
      }

      return new GsonBuilder().
         registerTypeAdapter(Date.class, new MyDateSerializer()).
         registerTypeAdapter(Date.class, new MyDateDeserializer()).
         create();
   }

   private synchronized void deferredSyncMetadata() {
      if (reportDir_ == null) {
         return;
      }

      if (deferredSyncTimer_ == null) {
         TimerTask task = new TimerTask() {
            @Override public void run() {
               syncMetadata();
            }
         };
         deferredSyncTimer_ = new Timer("ProblemReportMetadataSync", true);
         deferredSyncTimer_.schedule(task, 1000);
      }
   }

   private synchronized void syncMetadata() {
      if (reportDir_ == null) {
         return;
      }
      createReportDir();

      // I wanted to first write to a temp file and then atomically rename it
      // to the destination file. But Windows fails to rename even if I delete
      // the destination file just before the rename (it does work if there is
      // some pause between the delete and rename, but that is not a viable
      // workaround). So I'm giving up and just overwriting the file.
      File metadataFile = new File(reportDir_, METADATA_FILENAME);
      Gson gson = makeGson();
      writeTextFile(metadataFile, gson.toJson(metadata_));

      if (deferredSyncTimer_ != null) {
         deferredSyncTimer_.cancel();
         deferredSyncTimer_ = null;
      }
   }

   private void syncStartingConfig() {
      if (reportDir_ == null) {
         return;
      }
      createReportDir();

      if (startCfg_ != null) {
         metadata_.startCfgFilename = startCfg_.getFilename();
         syncMetadata();
         writeTextFile(new File(reportDir_, START_CFG_FILENAME),
               startCfg_.getContent());
      }
      else if (metadata_.startCfgFilename != null) {
         new File(reportDir_, START_CFG_FILENAME).delete();
         metadata_.startCfgFilename = null;
      }
   }

   private void syncEndingConfig() {
      if (reportDir_ == null) {
         return;
      }
      createReportDir();

      if (endCfg_ != null) {
         metadata_.endCfgFilename = endCfg_.getFilename();
         syncMetadata();
         writeTextFile(new File(reportDir_, END_CFG_FILENAME),
               endCfg_.getContent());
      }
      else if (metadata_.endCfgFilename != null) {
         new File(reportDir_, END_CFG_FILENAME).delete();
         metadata_.endCfgFilename = null;
      }
   }

   private void loadReport(File directory) {
      if (!directory.isDirectory()) {
         return;
      }

      leftoverDir_ = directory;

      File metadataFile = new File(directory, METADATA_FILENAME);
      if (!metadataFile.isFile()) {
         return;
      }
      String metadataJson = readTextFile(metadataFile);
      if (metadataJson == null) {
         return;
      }
      Gson gson = makeGson();
      metadata_ = gson.fromJson(metadataJson, Metadata.class);

      if (metadata_.startCfgFilename != null) {
         startCfg_ = new NamedTextFile(metadata_.startCfgFilename,
               new File(directory, START_CFG_FILENAME));
      }

      if (metadata_.endCfgFilename != null) {
         endCfg_ = new NamedTextFile(metadata_.endCfgFilename,
               new File(directory, END_CFG_FILENAME));
      }

      capturedLogContent_ =
         readTextFile(new File(directory, LOG_CAPTURE_FILENAME));

      if (metadata_.pid != null) {
         loadHotSpotErrorLogForPid(metadata_.pid);
      }
   }

   private void loadHotSpotErrorLogForPid(int pid) {
      String logFilename = "hs_err_pid" + Integer.toString(pid) + ".log";
      File logDir;
      if (metadata_.currentDir != null) {
         logDir = new File(metadata_.currentDir);
      }
      else {
         // Try the current current directory, in case we get lucky.
         logDir = new File(System.getProperty("user.dir"));
      }
      File logFile = new File(logDir, logFilename);
      if (logFile.isFile()) {
         hotSpotErrorLog_ = new NamedTextFile(logFile);
      }
   }

   private static NamedTextFile getCurrentConfigFile() {
      String fileName = org.micromanager.MMStudio.getInstance().getSysConfigFile();
      if (fileName == null || fileName.isEmpty()) {
         return null;
      }
      return new NamedTextFile(fileName);
   }

   // A text file's name and content.
   private static class NamedTextFile {
      final private String filename_;
      final private String content_;

      public NamedTextFile(String filename) {
         this(filename, new File(filename));
      }

      public NamedTextFile(File file) {
         this(file.getAbsolutePath(), file);
      }

      public NamedTextFile(String filename, File file) {
         filename_ = filename;
         content_ = readTextFile(file);
      }

      public boolean equals(NamedTextFile rhs) {
         if (this == rhs) {
            return true;
         }

         if (!filename_.equals(rhs.filename_)) {
            return false;
         }

         return content_.equals(rhs.content_);
      }

      public String getFilename() {
         return filename_;
      }

      public String getContent() {
         return content_;
      }
   }
}
