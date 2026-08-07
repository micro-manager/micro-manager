///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     AcquisitionLogger plugin
//-----------------------------------------------------------------------------
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

import org.micromanager.MMPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Entry point for the Acquisition Logger.
 *
 * <p>This plugin deliberately does not appear anywhere in the user interface.
 * It implements MMPlugin rather than MenuPlugin: Micro-Manager's
 * DefaultPluginManager calls setContext() on every MMPlugin it discovers, but
 * builds the Plugins menu only from plugins registered as MenuPlugin. So this
 * class is still loaded and initialized at startup while contributing no menu
 * entry.
 *
 * <p>The logger is created and started from a Beanshell startup script
 * (MMStartup.bsh), which is where the log file location and the list of
 * properties to record are configured. See AcquisitionLogger.bsh in the
 * scripts directory.
 */
@Plugin(type = MMPlugin.class)
public class AcquisitionLoggerPlugin implements SciJavaPlugin, MMPlugin {
   private Studio studio_;

   /** Holds the logger started by the startup script, so that a script run
    *  later in the session can find it. */
   private static AcquisitionLogger currentLogger_ = null;

   /**
    * Returns the logger to use for this application session, creating it on
    * the first call. Intended to be called from a startup script.
    *
    * <p>Re-running the startup script must not make it look as though the
    * application restarted. If a logger is already running for this session
    * and writes to the same file, that same instance is returned with its
    * property list cleared, ready to be configured again; its start() is then
    * a no-op, so exactly one APPLICATION STARTED entry is written per
    * application session no matter how often the script is run.
    *
    * <p>If the requested path differs from the running logger's, that is a
    * genuine change of destination rather than a re-run: the old logger is
    * shut down (writing its exit entry, so the old file is not left with an
    * unterminated session) and a new one is created for the new path.
    *
    * <p>The returned logger may not be started yet: call addProperty() as
    * needed and then start() on it, so that the properties are known before
    * the first entry is written.
    *
    * @param studio      the Studio instance
    * @param logFilePath full path of the log file
    * @return the logger for this session, ready to be configured and started
    */
   public static synchronized AcquisitionLogger startLogging(Studio studio,
         String logFilePath) {
      if (currentLogger_ != null) {
         if (currentLogger_.writesTo(logFilePath)) {
            // Same destination: keep the running session. Properties are
            // cleared so the script's list replaces the previous one rather
            // than being appended to it.
            currentLogger_.clearProperties();
            return currentLogger_;
         }
         // Different destination: close out the old log properly.
         currentLogger_.shutdown();
      }
      currentLogger_ = new AcquisitionLogger(studio, logFilePath);
      return currentLogger_;
   }

   /**
    * Returns the currently active logger, or null if logging was never
    * started in this session.
    *
    * @return the active AcquisitionLogger, or null
    */
   public static synchronized AcquisitionLogger getCurrentLogger() {
      return currentLogger_;
   }

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Acquisition Logger";
   }

   @Override
   public String getHelpText() {
      return "Logs application startup, exit, and every acquisition to a text file.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Altos Labs, 2026";
   }
}
