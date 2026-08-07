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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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

   /**
    * A device property to be recorded with every acquisition.
    *
    * <p>The device label and the property name are kept as separate fields
    * rather than being joined into a single string. Both may legitimately
    * contain a hyphen (for instance the property "Trigger-Mode"), so there is
    * no separator that could be split on again without risking corruption.
    */
   private static final class PropertyRef {
      private final String device;
      private final String property;

      private PropertyRef(String device, String property) {
         this.device = device;
         this.property = property;
      }

      /**
       * Returns the label used for this property in the log file.
       */
      private String label() {
         return device + "-" + property;
      }
   }

   /** Poison pill placed on the queue to tell the writer thread to finish. */
   private static final String STOP_MARKER = new String("stop");

   /**
    * How long shutdown waits for queued entries to reach disk. Bounded so an
    * unreachable network volume delays quitting by at most this much.
    */
   private static final long FLUSH_TIMEOUT_MS = 5000L;

   private final Studio studio_;
   private final File logFile_;
   private final List<PropertyRef> propertiesToLog_ = new ArrayList<PropertyRef>();
   private final BlockingQueue<String> writeQueue_ = new LinkedBlockingQueue<String>();
   private boolean started_ = false;
   private Date sessionStart_ = null;
   private Thread writerThread_ = null;
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
      propertiesToLog_.add(new PropertyRef(device, property));
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
    * Returns true if this logger is currently listening for events.
    *
    * @return true if start() has been called and stop() has not
    */
   public boolean isStarted() {
      return started_;
   }

   /**
    * Returns true if this logger writes to the given path.
    *
    * @param logFilePath path to compare against
    * @return true if the paths refer to the same file
    */
   public boolean writesTo(String logFilePath) {
      return logFile_.getAbsolutePath().equals(new File(logFilePath).getAbsolutePath());
   }

   /**
    * Writes the startup entry and begins listening for acquisitions and for
    * application shutdown.
    *
    * <p>Calling this again on an already-started logger does nothing. That is
    * what keeps a re-run of the startup script from writing a second
    * APPLICATION STARTED entry for one application session: the script gets
    * the same instance back from startLogging() and its start() call is a
    * no-op.
    */
   public void start() {
      if (started_) {
         return;
      }
      started_ = true;
      sessionStart_ = new Date();
      startWriterThread();
      studio_.events().registerForEvents(this);
      installShutdownHook();
      logStartup();
   }

   /**
    * Stops listening for acquisitions and shutdown, and flushes anything
    * still queued to disk before returning.
    *
    * <p>This does not write an exit entry, because the application is still
    * running. Use shutdown() when the log file is being closed out for good.
    */
   public void stop() {
      if (!started_) {
         return;
      }
      started_ = false;
      studio_.events().unregisterForEvents(this);
      removeShutdownHook();
      flushAndStopWriter();
   }

   /**
    * Writes the exit entry and then stops, leaving the log file with a
    * properly terminated session.
    *
    * <p>Used when this logger is being replaced by one writing somewhere
    * else: without it the old file would end with a startup entry that never
    * gets an exit entry, which is the marker for an abnormal end.
    */
   public void shutdown() {
      if (!started_) {
         return;
      }
      // logExit() flushes and stops the writer thread itself.
      logExit();
      started_ = false;
      studio_.events().unregisterForEvents(this);
      removeShutdownHook();
   }

   /**
    * Starts the single thread that owns all writes to the log file.
    *
    * <p>It is deliberately not a daemon thread: a daemon would be killed
    * mid-write when the JVM exits, which is exactly when the last entry (the
    * exit line) is being written. flushAndStopWriter() is responsible for
    * ending it.
    */
   private void startWriterThread() {
      writerThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            processWriteQueue();
         }
      }, "AcquisitionLogger writer");
      writerThread_.setDaemon(false);
      writerThread_.start();
   }

   /**
    * Signals the writer thread to finish the queue and waits briefly for it.
    *
    * <p>Bounded so that a log file on an unreachable network volume cannot
    * hang Micro-Manager's shutdown. If the wait times out, whatever is still
    * queued is written directly on the calling thread as a last resort.
    */
   private void flushAndStopWriter() {
      Thread writer = writerThread_;
      writerThread_ = null;
      if (writer == null) {
         return;
      }
      writeQueue_.offer(STOP_MARKER);
      try {
         writer.join(FLUSH_TIMEOUT_MS);
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
      if (writer.isAlive()) {
         reportProblem("AcquisitionLogger: timed out flushing the log to "
               + logFile_.getAbsolutePath(), null);
         return;
      }
      // The writer stopped at the marker; anything queued behind it (an exit
      // entry logged concurrently, say) still needs to reach disk.
      drainRemaining();
   }

   /**
    * Writes anything left in the queue on the calling thread.
    */
   private void drainRemaining() {
      String entry;
      while ((entry = writeQueue_.poll()) != null) {
         if (entry == STOP_MARKER) {
            continue;
         }
         writeToDisk(entry);
      }
   }

   /**
    * Registers a JVM shutdown hook as a backstop.
    *
    * <p>The normal exit entry is written by onShutdownCommencing(). This hook
    * covers orderly JVM terminations that never post that event: a call to
    * System.exit(), the last non-daemon thread finishing, or an interrupt
    * signal such as Ctrl-C or a normal SIGTERM. In practice that means
    * quitting through ImageJ rather than through Micro-Manager.
    *
    * <p>It does NOT cover forced termination: SIGKILL, Task Manager's "End
    * task", Runtime.halt(), a JVM crash, or loss of power. Shutdown hooks are
    * not run in any of those cases, so no exit entry is written and the
    * session simply ends without one. A session whose startup entry has no
    * matching exit entry should be read as "ended abnormally, at an unknown
    * time" rather than as a logging bug.
    *
    * <p>logExit() only writes once per session, so whichever path happens
    * first wins and the other becomes a no-op.
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

         for (PropertyRef ref : propertiesToLog_) {
            sb.append(System.getProperty("line.separator"));
            sb.append("   ").append(ref.label()).append(": ")
                  .append(readProperty(ref.device, ref.property));
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
    * Writes the exit entry, and flushes it to disk.
    *
    * <p>Called from onShutdownCommencing() on a normal quit, and from the JVM
    * shutdown hook otherwise. Guarded so that it writes at most once per
    * session. Forced termination reaches neither path, in which case no exit
    * entry is written at all; see installShutdownHook().
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
            // posting a shutdown event: the JVM shut down in an orderly way,
            // but the quit came from outside Micro-Manager.
            sb.append("  (JVM shutdown without a Micro-Manager quit)");
         }
         if (sessionStart_ != null) {
            sb.append(System.getProperty("line.separator"));
            sb.append("   Session duration:   ")
                  .append(formatDuration(exitTime.getTime() - sessionStart_.getTime()));
         }
         sb.append(System.getProperty("line.separator"));
         sb.append("----------------------------------------");
         write(sb.toString());
         // The exit entry is the last thing written, so it has to be flushed
         // here: nothing runs after this to drain the queue.
         flushAndStopWriter();
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
    * Hands an entry to the writer thread. Returns immediately.
    *
    * <p>This must not touch the disk. Micro-Manager's EventManager dispatches
    * on a plain (synchronous) Guava EventBus, so subscribers run on the
    * posting thread and the acquisition engine does not start its image sink
    * until the post returns. Opening, writing and closing the log inline
    * would therefore add its full latency to the start of every acquisition,
    * which is unbounded when the log lives on a slow or unreachable network
    * volume.
    */
   private void write(String entry) {
      if (!writeQueue_.offer(entry)) {
         // The queue is unbounded, so this should not happen; fall back to
         // writing directly rather than dropping the entry.
         writeToDisk(entry);
      }
   }

   /**
    * Drains the queue until the stop marker is seen. Runs on the writer
    * thread.
    */
   private void processWriteQueue() {
      while (true) {
         String entry;
         try {
            entry = writeQueue_.take();
         } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
         }
         // Reference comparison: only the marker instance ends the loop, so a
         // log entry that happens to read "stop" is written normally.
         if (entry == STOP_MARKER) {
            return;
         }
         writeToDisk(entry);
      }
   }

   /**
    * Appends one entry to the log file, creating the file (and any missing
    * parent directories) if needed. Called on the writer thread, and directly
    * from the shutdown path once the writer has finished.
    */
   private synchronized void writeToDisk(String entry) {
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
         // PrintWriter never throws on write or flush: it swallows the
         // IOException and raises an internal error flag instead. Without
         // this check, a disk that filled up or a volume that went away after
         // the stream was opened would silently drop the entry.
         if (writer.checkError()) {
            throw new IOException("write failed (the disk may be full or the "
                  + "volume may have become unavailable)");
         }
         // close() flushes the BufferedWriter, so it can fail too; checked
         // here rather than in finally so the error is reported.
         writer.close();
         if (writer.checkError()) {
            throw new IOException("closing the log file failed; the entry may "
                  + "be incomplete");
         }
         writer = null;
      } catch (IOException ex) {
         reportProblem("AcquisitionLogger: could not write to "
               + logFile_.getAbsolutePath(), ex);
      } finally {
         // Only reached if an exception was thrown before the explicit close.
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
