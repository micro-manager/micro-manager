///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     AcquisitionLogger plugin
//-----------------------------------------------------------------------------
//
// DESCRIPTION:  Logs application startup and every acquisition to a plain text
//               log file that is appended to across sessions.
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.acqlogger;

import com.google.common.eventbus.Subscribe;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.micromanager.Studio;
import org.micromanager.acquisition.AcquisitionSequenceStartedEvent;
import org.micromanager.acquisition.AcquisitionStartedEvent;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Datastore;

/**
 * Does the actual logging. Subscribes to the Studio event bus and appends a
 * line to the log file at application startup and at the start of every
 * acquisition.
 *
 * <p>This class exists as compiled Java (rather than living entirely in a
 * Beanshell startup script) because the Guava EventBus discovers handler
 * methods through the {@code @Subscribe} annotation, and Beanshell-scripted
 * objects cannot carry annotations. The startup script configures and starts
 * an instance of this class.
 */
public class AcquisitionLogger {
   private static final SimpleDateFormat TIMESTAMP_FORMAT =
         new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

   private final Studio studio_;
   private final File logFile_;
   private final List<String> propertiesToLog_ = new ArrayList<String>();
   private boolean started_ = false;
   private Date sessionStart_ = null;
   private Thread shutdownHook_ = null;
   private volatile boolean exitLogged_ = false;
   private volatile boolean quitRequested_ = false;

   /**
    * Creates a logger writing to the given file.
    *
    * @param studio      the Studio instance
    * @param logFilePath full path of the log file. Parent directories are
    *                    created if needed; the file is created if it does not
    *                    exist and appended to if it does.
    */
   public AcquisitionLogger(Studio studio, String logFilePath) {
      studio_ = studio;
      logFile_ = new File(logFilePath);
   }

   /**
    * Adds a device property to be recorded with every acquisition, for
    * instance ("Nosepiece", "Label").
    *
    * @param device   device label as it appears in the hardware configuration
    * @param property property name on that device
    */
   public void addProperty(String device, String property) {
      propertiesToLog_.add(device + "-" + property);
   }

   /**
    * Removes all properties previously added with addProperty().
    */
   public void clearProperties() {
      propertiesToLog_.clear();
   }

   /**
    * Returns the full path of the log file being written.
    *
    * @return absolute path of the log file
    */
   public String getLogFilePath() {
      return logFile_.getAbsolutePath();
   }

   /**
    * Writes the startup entry and begins listening for acquisitions and for
    * application shutdown. Calling this more than once has no additional
    * effect.
    */
   public void start() {
      if (started_) {
         return;
      }
      started_ = true;
      sessionStart_ = new Date();
      studio_.events().registerForEvents(this);
      installShutdownHook();
      logStartup();
   }

   /**
    * Stops listening for acquisitions and shutdown.
    */
   public void stop() {
      if (!started_) {
         return;
      }
      started_ = false;
      studio_.events().unregisterForEvents(this);
      removeShutdownHook();
   }

   /**
    * Registers a JVM shutdown hook as a backstop.
    *
    * <p>The normal exit entry is written by onShutdownCommencing(). This hook
    * covers the cases that never post that event: the JVM being terminated
    * directly, quitting through ImageJ rather than through Micro-Manager, or
    * the process being killed. logExit() only writes once per session, so
    * whichever path happens first wins and the other becomes a no-op.
    */
   private void installShutdownHook() {
      shutdownHook_ = new Thread(new Runnable() {
         @Override
         public void run() {
            logExit();
         }
      }, "AcquisitionLogger shutdown hook");
      try {
         Runtime.getRuntime().addShutdownHook(shutdownHook_);
      } catch (IllegalStateException ex) {
         // JVM is already shutting down; nothing sensible to do.
         shutdownHook_ = null;
      }
   }

   private void removeShutdownHook() {
      if (shutdownHook_ == null) {
         return;
      }
      try {
         Runtime.getRuntime().removeShutdownHook(shutdownHook_);
      } catch (IllegalStateException ex) {
         // Shutdown already in progress; the hook will run on its own.
      }
      shutdownHook_ = null;
   }

   private void logStartup() {
      StringBuilder sb = new StringBuilder();
      sb.append(System.getProperty("line.separator"));
      sb.append("========================================")
            .append(System.getProperty("line.separator"));
      sb.append("APPLICATION STARTED  ").append(now())
            .append(System.getProperty("line.separator"));
      sb.append("   Configuration file: ").append(getConfigFile())
            .append(System.getProperty("line.separator"));
      sb.append("   User profile:       ").append(getProfileName())
            .append(System.getProperty("line.separator"));
      sb.append("   Micro-Manager:      ").append(getVersionInfo())
            .append(System.getProperty("line.separator"));
      sb.append("========================================");
      write(sb.toString());
   }

   /**
    * Called when an acquisition starts. Logs where the data will be saved,
    * the name of the data set, the time, and the configured properties.
    *
    * @param event the acquisition started event
    */
   @Subscribe
   public void onAcquisitionStarted(AcquisitionStartedEvent event) {
      try {
         StringBuilder sb = new StringBuilder();
         sb.append("ACQUISITION  ").append(now())
               .append(System.getProperty("line.separator"));

         String directory = null;
         String name = null;

         // Preferred source: the settings that drive this acquisition.
         if (event instanceof AcquisitionSequenceStartedEvent) {
            SequenceSettings settings =
                  ((AcquisitionSequenceStartedEvent) event).getSettings();
            if (settings != null) {
               if (settings.save()) {
                  directory = settings.root();
                  name = settings.prefix();
               } else {
                  directory = "(not saved to disk)";
                  name = settings.prefix();
               }
            }
         }

         // The Datastore knows the path actually used on disk, which is
         // authoritative: Micro-Manager appends a suffix (_1, _2, ...) when
         // the requested name already exists.
         Datastore store = event.getDatastore();
         if (store != null) {
            String savePath = store.getSavePath();
            if (savePath != null && !savePath.isEmpty()) {
               File f = new File(savePath);
               directory = f.getParent();
               name = f.getName();
            }
         }

         sb.append("   Directory: ").append(directory == null ? "(unknown)" : directory)
               .append(System.getProperty("line.separator"));
         sb.append("   Data set:  ").append(name == null ? "(unknown)" : name);

         for (String key : propertiesToLog_) {
            // Split on the last "-" so device labels containing "-" work.
            int split = key.lastIndexOf('-');
            if (split <= 0) {
               continue;
            }
            String device = key.substring(0, split);
            String property = key.substring(split + 1);
            sb.append(System.getProperty("line.separator"));
            sb.append("   ").append(key).append(": ")
                  .append(readProperty(device, property));
         }

         write(sb.toString());
      } catch (Exception ex) {
         // Never let a logging problem interfere with the acquisition.
         studio_.logs().logError(ex, "AcquisitionLogger: failed to log acquisition");
      }
   }

   /**
    * Writes the exit entry when the user quits Micro-Manager.
    *
    * <p>This is the entry that normally gets written. A JVM shutdown hook
    * alone is not enough, because Micro-Manager frequently exits without the
    * JVM terminating: MMStudio.closeSequence() only calls System.exit() when
    * "close the entire program on exit" is enabled AND Micro-Manager was not
    * started as an ImageJ plugin. When it was started from ImageJ (which is
    * the usual case when running from an IDE) it delegates to ImageJ's
    * quit(), and ImageJ only calls System.exit() if its own
    * exitWhenQuitting flag is set. In those configurations the process
    * simply keeps running and no shutdown hook ever fires.
    *
    * <p>The trade-off is that this event can still be cancelled afterwards,
    * either by another subscriber calling cancelShutdown() or by
    * cleanupOnClose() aborting the quit. If that happens the session
    * continues after an exit line has already been written; the next entry
    * in the log will show that. That is preferable to the previous behavior
    * of never logging an exit at all.
    *
    * @param event the shutdown commencing event
    */
   @Subscribe
   public void onShutdownCommencing(org.micromanager.events.ShutdownCommencingEvent event) {
      if (event.isCanceled()) {
         return;
      }
      quitRequested_ = true;
      logExit();
   }

   /**
    * Writes the exit entry. Called from the JVM shutdown hook, and guarded so
    * that it writes at most once per session.
    */
   private void logExit() {
      // The shutdown event and the shutdown hook can both reach this from
      // different threads; only the first one through writes an entry.
      synchronized (this) {
         if (exitLogged_) {
            return;
         }
         exitLogged_ = true;
      }
      try {
         Date exitTime = new Date();
         StringBuilder sb = new StringBuilder();
         sb.append("----------------------------------------")
               .append(System.getProperty("line.separator"));
         sb.append("APPLICATION EXITED   ").append(format(exitTime));
         if (!quitRequested_) {
            // Reached through the shutdown hook without Micro-Manager ever
            // posting a shutdown event: the process was terminated, or the
            // quit came from outside Micro-Manager.
            sb.append("  (terminated without a Micro-Manager quit)");
         }
         if (sessionStart_ != null) {
            sb.append(System.getProperty("line.separator"));
            sb.append("   Session duration:   ")
                  .append(formatDuration(exitTime.getTime() - sessionStart_.getTime()));
         }
         sb.append(System.getProperty("line.separator"));
         sb.append("----------------------------------------");
         write(sb.toString());
      } catch (Exception ex) {
         // A shutdown hook must never throw.
         System.err.println("AcquisitionLogger: failed to log exit: " + ex.getMessage());
      }
   }

   /**
    * Formats a duration in milliseconds as hh:mm:ss.
    */
   private String formatDuration(long millis) {
      final long safeMillis = millis < 0 ? 0 : millis;
      final long totalSeconds = safeMillis / 1000L;
      StringBuilder sb = new StringBuilder();
      appendPadded(sb, totalSeconds / 3600L);
      sb.append(':');
      appendPadded(sb, (totalSeconds % 3600L) / 60L);
      sb.append(':');
      appendPadded(sb, totalSeconds % 60L);
      return sb.toString();
   }

   /**
    * Appends a value, zero padded to at least two digits.
    */
   private void appendPadded(StringBuilder sb, long value) {
      if (value < 10) {
         sb.append('0');
      }
      sb.append(value);
   }

   private String readProperty(String device, String property) {
      try {
         if (!studio_.core().hasProperty(device, property)) {
            return "(no such property)";
         }
         return studio_.core().getProperty(device, property);
      } catch (Exception ex) {
         return "(error: " + ex.getMessage() + ")";
      }
   }

   /**
    * Returns the loaded hardware configuration file.
    *
    * <p>The only accessor is MMStudio.getSysConfigFile(), which lives in an
    * internal package and is not part of the public API (it is marked
    * "TODO add method to API" in the source). It is called reflectively so
    * that this plugin keeps working, minus this one field, if that method is
    * ever promoted or moved.
    */
   private String getConfigFile() {
      try {
         java.lang.reflect.Method m = studio_.getClass().getMethod("getSysConfigFile");
         Object result = m.invoke(studio_);
         if (result instanceof String && !((String) result).isEmpty()) {
            return (String) result;
         }
      } catch (Exception ex) {
         reportProblem("AcquisitionLogger: could not read config file name", ex);
      }
      return "(unknown)";
   }

   private String getProfileName() {
      try {
         String name = studio_.profile().getProfileName();
         if (name != null && !name.isEmpty()) {
            return name;
         }
      } catch (Exception ex) {
         reportProblem("AcquisitionLogger: could not read profile name", ex);
      }
      return "(unknown)";
   }

   private String getVersionInfo() {
      try {
         return studio_.compat().getVersion();
      } catch (Exception ex) {
         return "(unknown)";
      }
   }

   private String now() {
      return format(new Date());
   }

   private String format(Date date) {
      // SimpleDateFormat is not thread safe; startup, acquisitions and the
      // shutdown hook all log from different threads.
      synchronized (TIMESTAMP_FORMAT) {
         return TIMESTAMP_FORMAT.format(date);
      }
   }

   /**
    * Appends one entry to the log file, creating the file (and any missing
    * parent directories) if needed.
    */
   private synchronized void write(String entry) {
      PrintWriter writer = null;
      try {
         File parent = logFile_.getParentFile();
         if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
               reportProblem("AcquisitionLogger: could not create directory "
                     + parent.getAbsolutePath(), null);
               return;
            }
         }
         // append == true, so an existing log is continued rather than replaced.
         writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
               new FileOutputStream(logFile_, true), Charset.forName("UTF-8"))));
         writer.println(entry);
         writer.flush();
      } catch (IOException ex) {
         reportProblem("AcquisitionLogger: could not write to "
               + logFile_.getAbsolutePath(), ex);
      } finally {
         if (writer != null) {
            writer.close();
         }
      }
   }

   /**
    * Reports a logging problem to the Micro-Manager log, falling back to
    * stderr. The fallback matters during shutdown: write() is called from the
    * JVM shutdown hook, by which point the Studio log manager may already be
    * torn down.
    */
   private void reportProblem(String message, Exception ex) {
      try {
         if (ex != null) {
            studio_.logs().logError(ex, message);
         } else {
            studio_.logs().logError(message);
         }
      } catch (Exception whileLogging) {
         System.err.println(message);
         if (ex != null) {
            System.err.println("   " + ex.getMessage());
         }
      }
   }
}
