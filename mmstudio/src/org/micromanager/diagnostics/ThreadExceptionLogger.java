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

import org.micromanager.utils.ReportingUtils;


public class ThreadExceptionLogger implements Thread.UncaughtExceptionHandler {
   private static boolean setUp_ = false;
   public static void setUp() {
      if (setUp_) {
         return;
      }

      Thread.setDefaultUncaughtExceptionHandler(
            new ThreadExceptionLogger(Thread.getDefaultUncaughtExceptionHandler()));

      // Be nice: don't disable an existing handler
      if (System.getProperty("sun.awt.exception.handler") == null) {
         System.setProperty("sun.awt.exception.handler",
               ThreadExceptionLogger.class.getName());
      }

      setUp_ = true;
   }

   private final Thread.UncaughtExceptionHandler chainedHandler_;

   public ThreadExceptionLogger(Thread.UncaughtExceptionHandler chained) {
      chainedHandler_ = chained;
   }

   // For instantiation by Swing via sun.awt.exception.handler
   public ThreadExceptionLogger() {
      chainedHandler_ = null;
   }

   @Override
   public void uncaughtException(Thread t, Throwable e) {
      ReportingUtils.logMessage("Thread " + t.getId() + " (" + t.getName() +
            ") terminated with uncaught exception");
      logException(e);
      if (chainedHandler_ != null) {
         chainedHandler_.uncaughtException(t, e);
      }
   }

   // This method is called reflectively via the sun.awt.exception.handler mechanism.
   public void handle(Throwable e) {
      ReportingUtils.logMessage("Uncaught exception in AWT/Swing event dispatch thread:");
      logException(e);
   }

   // TODO Factor out (make common with ReportingUtils.logError()).
   private void logException(Throwable e) {
      ReportingUtils.logMessage(e.toString());

      for (StackTraceElement frame : e.getStackTrace()) {
         ReportingUtils.logMessage("  at " + frame.toString());
      }

      Throwable cause = e.getCause();
      if (cause != null) {
         ReportingUtils.logMessage("Caused by exception:");
         logException(cause);
      }
   }
}
