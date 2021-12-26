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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import mmcorej.CMMCore;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * Handles generation and sharing the Micro-Manager Problem Report.
 */
public final class ProblemReport {
   private final CMMCore core_;

   private File reportDir_; // null if non-persistent
   private File leftoverDir_;

   // Designed for serialization via GSON.
   private static class Metadata {
      // Used boxed types to allow null
      public Integer pid;
      public Date date;
      public String startCfgFilename;
      public String endCfgFilename;
      public String description;
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
    * The report will not be backed by persistent storage, and therefore will
    * not be crash-proof.
    * Note that although core is used for logging control, but the information
    * logged may come from global state (the system information classes obtain
    * the Core from the MMStudio singleton).
    *
    * @param core the Core.
    */
   public static ProblemReport newReport(CMMCore core) {
      return new ProblemReport(core);
   }

   /**
    * Create a new disk-backed report.
    * Note that although core is used for logging control, but the information
    * logged may come from global state (the system information classes obtain
    * the Core from the MMStudio singleton).
    * If there are any errors writing to storageDirectory, they are silently
    * ignored (and the report will behave as if it were non-persistent).
    *
    * @param core the Core.
    * @param storageDirectory where to save the report data.
    */
   public static ProblemReport newPersistentReport(CMMCore core,
                                                   File storageDirectory) {
      return new ProblemReport(core, storageDirectory);
   }

   /**
    * Create a report by loading a leftover disk-backed report.
    *
    * @param storageDirectory where to load the report data from.
    */
   public static ProblemReport loadFromPersistence(File storageDirectory) {
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
      if (metadata_ == null) {
         return false;
      }
      return capturedLogContent_ != null && !capturedLogContent_.isEmpty();
   }

   /**
    * Sets the user-entered description.
    *
    * @param description the description.
    */
   public void setDescription(String description) {
      metadata_.description = description;
      deferredSyncMetadata();
   }

   /**
    * Gets the user-entered description.
    *
    * @return the description.
    */
   public String getDescription() {
      return metadata_.description;
   }

   /**
    * Starts CoreLog capture.    *
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
            } catch (IOException dealWithLater) {
               ReportingUtils.logError(dealWithLater);
            }
         }
      } else {
         try {
            logFile = File.createTempFile("MMCoreLogCapture", ".txt");
         } catch (IOException dealWithLater) {
            ReportingUtils.logError(dealWithLater);
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
      } catch (Exception e) {
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
      } catch (Exception ignore) {
         // Errors will be logged by the Core; there is not much else we can
         // do.
      }
      logFileHandle_ = null;

      new File(logFileName_).delete();
      logFileName_ = null;
   }

   /**
    * Stop CoreLog capture and record the captured log as part of the report.    *
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

      final String logFileName = logFileName_;

      core_.logMessage("Problem Report: End of log capture");
      try {
         core_.stopSecondaryLogFile(logFileHandle_);
      } catch (Exception ignore) {
         // This is an unlikely error unless there are programming errors.
         // Let's continue and see if we can read the file anyway.
      }
      logFileHandle_ = null;
      logFileName_ = null;

      File logFile = new File(logFileName);
      capturedLogContent_ = null;
      try {
         capturedLogContent_ = readTextFile(logFile);
      } catch (IOException ioe) {
         ReportingUtils.logError(ioe);
      }
      if (capturedLogContent_ == null) {
         capturedLogContent_ = "<<<Failed to read captured log file>>>";
      }
      if (reportDir_ == null) { // We used an ad-hoc temporary file
         logFile.delete();
      }

      endCfg_ = getCurrentConfigFile();
      syncEndingConfig();
   }

   /**
    * Deletes Problem Report files from disk.
    */
   public void deleteStorage() {
      deleteReportDir(reportDir_);
      reportDir_ = null;
      deleteReportDir(leftoverDir_);
      leftoverDir_ = null;
   }

   /**
    * Dump system information to the CoreLog.
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
      } catch (NumberFormatException e) {
         metadata_.pid = null;
      }

      metadata_.currentDir = System.getProperty("user.dir");
   }

   private static String readTextFile(File file) throws IOException {
      // On Windows, the files may contain CRLF newlines, so it is important
      // to read as text, line by line.
      return Files.lines(file.toPath()).collect(Collectors.joining("\n"));
   }

   private static void writeTextFile(java.io.File file, String text) {
      FileOutputStream outputStream;
      try {
         outputStream = new FileOutputStream(file);
      } catch (FileNotFoundException e) {
         return;
      }
      OutputStreamWriter writer;
      writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
      try {
         writer.write(text);
      } catch (IOException e) {
         return;
      } finally {
         try {
            writer.close();
         } catch (IOException ioe) {
            ReportingUtils.logError(ioe);
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
                  "This directory contains an in-progress (or crashed) \n"
                  + "Micro-Manager Problem Report. It is safe to delete.";
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
            } catch (java.text.ParseException e) {
               return null;
            }
         }
      }

      return new GsonBuilder()
            .registerTypeAdapter(Date.class, new MyDateSerializer())
            .registerTypeAdapter(Date.class, new MyDateDeserializer())
            .create();
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
      } else if (metadata_.startCfgFilename != null) {
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
      } else if (metadata_.endCfgFilename != null) {
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
      try {
         String metadataJson = readTextFile(metadataFile);
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
      } catch (IOException ioe) {
         ReportingUtils.logError(ioe, "Error in ProblemReport.loadReport().");
      }
   }

   private void loadHotSpotErrorLogForPid(int pid) throws IOException {
      String logFilename = "hs_err_pid" + pid + ".log";
      File logDir;
      if (metadata_.currentDir != null) {
         logDir = new File(metadata_.currentDir);
      } else {
         // Try the current current directory, in case we get lucky.
         logDir = new File(System.getProperty("user.dir"));
      }
      File logFile = new File(logDir, logFilename);
      if (logFile.isFile()) {
         hotSpotErrorLog_ = new NamedTextFile(logFile);
      }
   }

   private static NamedTextFile getCurrentConfigFile() {
      String fileName = org.micromanager.internal.MMStudio.getInstance().getSysConfigFile();
      if (fileName == null || fileName.isEmpty()) {
         return null;
      }
      try {
         return new NamedTextFile(fileName);
      } catch (IOException ioe) {
         ReportingUtils.logError(ioe, "Failed to open configuration file.");
      }
      return null;
   }

   // A text file's name and content.
   private static class NamedTextFile {
      final String filename_;
      final String content_;

      public NamedTextFile(String filename) throws IOException {
         this(filename, new File(filename));
      }

      public NamedTextFile(File file) throws IOException  {
         this(file.getAbsolutePath(), file);
      }

      public NamedTextFile(String filename, File file) throws IOException {
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
