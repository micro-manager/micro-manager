// AUTHOR:       Mark Tsuchida
// COPYRIGHT:    2016, Open Imaging, Inc.
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.diagnostics;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import mmcorej.CMMCore;


/**
 * Log event dispatch thread hangs to the CoreLog.
 *
 * Note: Failure to call stop() before destructing the Core will result in a
 * crash.
 */
public class EDTHangLogger {
   // Implementation notes
   //
   // One way to detect hangs on the EDT is to time the dispatch of each event.
   // This can be done by replacing the AWT/Swing event queue by calling
   // EventQueue.push(). However, the EventQueue push() and pop() methods are
   // riddled with Java version dependent and platform dependent bugs and
   // behavioral differences, so that it is (at best) too complicated to follow
   // this route, especially since we desire the ability to cleanly stop
   // monitoring for hangs.
   //
   // Instead, we use the less invasive approach of allowing the EDT to
   // generate a "heartbeat" on its own, and periodically check for lapses.
   //
   // There are some complications, though. We cannot just log an EDT stack
   // trace when we detect a heartbeat lapse, because the EDT may have unstuck
   // and may be executing a perfectly innocuous event handler by that time. We
   // need to check that we are actually stuck on the same event.

   private CMMCore core_;
   private Timer timer_;

   private final long NEVER = -1;
   private final long MS_PER_NS = 1000 * 1000;

   private long heartbeatTimeoutMs_;
   private long hangCheckIntervalMs_;

   private long heartbeatTimebaseNs_ = NEVER;
   private long lastHeartbeatNs_ = NEVER;
   private long hangCheckStartNs_  = NEVER;
   private boolean missedHeartbeat_ = false;
   private WeakReference<AWTEvent> nextEventWeakRef_;


   private static EDTHangLogger instance_;

   public static void startDefault(CMMCore core, long heartbeatTimeoutMs,
         long hangCheckIntervalMs)
   {
      if (instance_ != null) {
         stopDefault();
      }
      instance_ = new EDTHangLogger(core, heartbeatTimeoutMs, hangCheckIntervalMs);
   }

   public static void stopDefault() {
      if (instance_ != null) {
         instance_.stop();
         instance_ = null;
      }
   }


   // Logging for debugging this class itself, normally disabled
   // (DEBUG_LEVEL kept non-final so that value can be overridden during
   // interactive debugging if necessary.)
   private static int DEBUG_LEVEL = 0; // 0, 1, or 2
   private void logDebug(int level, String message) {
      if (core_ != null && level <= DEBUG_LEVEL) {
         core_.logMessage("EDTHangLogger DEBUG: " + message, true);
      }
   }

   // Logging of status and detected hangs
   private void logMessage(String message) {
      if (core_ != null) {
         core_.logMessage("EDTHangLogger: " + message);
      }
   }


   public EDTHangLogger(CMMCore core, long heartbeatTimeoutMs,
         long hangCheckIntervalMs)
   {
      core_ = core;
      timer_ = new Timer("EDTHangLogger timer", true);
      heartbeatTimeoutMs_ = Math.max(0, heartbeatTimeoutMs);
      hangCheckIntervalMs_ = Math.max(0, hangCheckIntervalMs);

      setupHeartbeat();

      logMessage("Started monitoring of EDT hangs\n" +
            "[heartbeat timeout = " + heartbeatTimeoutMs_ +
            " ms, hang check interval = " + hangCheckIntervalMs_ + " ms]");
   }

   public synchronized void stop() {
      if (timer_ == null) {
         return;
      }

      logMessage("Stopping monitoring of EDT hangs");
      timer_.cancel();
      timer_ = null;
      core_ = null; // For safety
   }

   private synchronized void setupHeartbeat() {
      if (missedHeartbeat_) {
         logDebug(1, "Setting up first new heartbeat after transient hang");
         missedHeartbeat_ = false;
      }

      heartbeatTimebaseNs_ = System.nanoTime();
      lastHeartbeatNs_ = NEVER;

      logDebug(2, "Setting up heartbeat");

      EventQueue.invokeLater(new Runnable() {
         @Override public void run() {
            heartbeat();
         }
      });

      TimerTask checkTask = new TimerTask() {
         @Override public void run() {
            checkForHeartbeat(true);
         }
      };
      if (timer_ != null) {
         timer_.schedule(checkTask, heartbeatTimeoutMs_);
      }
   }

   private synchronized void heartbeat() {
      lastHeartbeatNs_ = System.nanoTime();
      if (missedHeartbeat_) {
         long elapsedSinceTimebaseMs =
            (lastHeartbeatNs_ - heartbeatTimebaseNs_) / MS_PER_NS;
         logMessage("First heartbeat after miss (" +
               elapsedSinceTimebaseMs + " ms since timebase)");
      }
      logDebug(2, "Heartbeat after " +
            (lastHeartbeatNs_ - heartbeatTimebaseNs_) + " ns");
   }

   private synchronized void checkForHeartbeat(boolean firstCheck) {
      if (lastHeartbeatNs_ != NEVER) {
         logDebug(2, "Heartbeat detected");

         setupHeartbeat();
         return;
      }

      missedHeartbeat_ = true;
      logDebug(1, "Heartbeat missed");

      // Add a sentinel to the event queue so that peekEvent() does not
      // return empty.
      EventQueue.invokeLater(new Runnable() {
         @Override public void run() {}
      });

      // Get the next event in the event queue to use as reference point. (We
      // cannot get the _current_ event since there is no method to access it
      // outside of the EDT.)
      AWTEvent nextEvent = peekEvent();

      // Now, there is still a chance that nextEvent is null, if the EDT
      // unstuck and handled the above sentinel invokeLater() task.
      if (nextEvent == null) {
         if (lastHeartbeatNs_ != NEVER) {
            logDebug(1, "Appears to have unstuck, heartbeat detected after all");
         }
         else {
            logDebug(1, "UNEXPECTED: Found no next event despite missing heartbeat");
         }
         setupHeartbeat();
         return;
      }

      nextEventWeakRef_ = new WeakReference<AWTEvent>(nextEvent);

      if (firstCheck) {
         logMessage("Missed heartbeat; waiting to see if we are stuck on a single event");
      }
      else {
         // TODO If missing HB for long time, dump stacktraces even if we can't
         // detect a hang on a single event
      }

      logDebug(1, "Scheduling hang check");
      hangCheckStartNs_ = System.nanoTime();
      TimerTask recheckTask = new TimerTask() {
         @Override public void run() {
            checkForHang(true);
         }
      };
      if (timer_ != null) {
         timer_.schedule(recheckTask, hangCheckIntervalMs_);
      }
   }

   private synchronized void checkForHang(boolean firstCheck) {
      logDebug(1, "Checking if still hung");

      // Get stack traces before checking if we are truly stuck, so as to avoid
      // a race.
      Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();

      AWTEvent previousNextEvent = nextEventWeakRef_.get();
      AWTEvent nextEvent = peekEvent();
      if (previousNextEvent == null || previousNextEvent != nextEvent) {
         logDebug(1, "Next event has changed, may not be a hang");
         checkForHeartbeat(false);
         return;
      }

      // We've been stuck on the same event for at least hangCheckIntervalMs_.
      if (firstCheck) {
         long now = System.nanoTime();
         long elapsedTimeMs = (now - hangCheckStartNs_) / MS_PER_NS;
         long elapsedSinceTimebaseMs = (now - heartbeatTimebaseNs_) / MS_PER_NS;

         logMessage("Event handling has exceeded at least " + elapsedTimeMs +
               " ms (currently " + elapsedSinceTimebaseMs +
               " ms since heartbeat timebase)\n" +
               "Stack traces follow " +
               "(note: thread states queried later than stack traces)" +
               formatStackTraces(traces));
      }

      logDebug(1, "Scheduling hang recheck");
      TimerTask recheckTask = new TimerTask() {
         @Override public void run() {
            checkForHang(false);
         }
      };
      if (timer_ != null) {
         timer_.schedule(recheckTask, hangCheckIntervalMs_);
      }
   }

   private AWTEvent peekEvent() {
      return Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent();
   }

   private String formatStackTraces(Map<Thread, StackTraceElement[]> traces) {
      StringBuilder sb = new StringBuilder();

      // Sort by thread id
      List<Thread> threads = new ArrayList<Thread>(traces.keySet());
      Collections.sort(threads, new Comparator<Thread>() {
         @Override
         public int compare(Thread t1, Thread t2) {
            return new Long(t1.getId()).compareTo(t2.getId());
         }
      });

      for (Thread thread : threads) {
         formatThreadStackTrace(sb, thread, traces.get(thread));
      }

      return sb.toString();
   }

   private void formatThreadStackTrace(StringBuilder sb, Thread thread,
         StackTraceElement[] trace)
   {
      sb.append("\n");
      sb.append("Thread " + thread.getId() + " [" + thread.getName() +
            "] " + thread.getState().toString());
      for (StackTraceElement frame : trace) {
         sb.append("\n  at ");
         sb.append(frame);
      }
   }
}
