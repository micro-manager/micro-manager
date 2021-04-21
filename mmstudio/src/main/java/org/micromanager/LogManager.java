///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
//               Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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

package org.micromanager;

import java.awt.*;

/**
 * Provides access to logging and message display routines. Available in the Beanshell scripting
 * interface as "mm.logs()" or "mm.getLogManager()".
 */
public interface LogManager {
  /**
   * Adds a message to the Micro-Manager log (found in Corelogtxt).
   *
   * @param msg - message to be added to the log
   */
  public void logMessage(String msg);

  /**
   * Shows a message in the UI.
   *
   * @param msg - message to be shown
   */
  public void showMessage(String msg);

  /**
   * Shows a message in the UI.
   *
   * @param msg - message to be shown
   * @param parent - parent component over which this message should be centered
   */
  public void showMessage(String msg, Component parent);

  /**
   * Writes the stacktrace and a message to the Micro-Manager log (Corelog.txt).
   *
   * @param e - Java exception to be logged
   * @param msg - message to be shown
   */
  public void logError(Exception e, String msg);

  /**
   * Writes a stacktrace to the Micro-Manager log.
   *
   * @param e - Java exception to be logged
   */
  public void logError(Exception e);

  /**
   * Writes an error to the Micro-Manager log (same as logMessage).
   *
   * @param msg - message to be logged
   */
  public void logError(String msg);

  /**
   * Shows an error in the UI and logs stacktrace to the Micro-Manager log.
   *
   * @param e - Java exception to be shown and logged
   * @param msg - Error message to be shown and logged
   */
  public void showError(Exception e, String msg);

  /**
   * Shows and logs a Java exception.
   *
   * @param e - Java excpetion to be shown and logged
   */
  public void showError(Exception e);

  /**
   * Shows an error message in the UI and logs to the Micro-Manager log.
   *
   * @param msg - error message to be shown and logged
   */
  public void showError(String msg);

  /**
   * Shows an error in the UI and logs stacktrace to the Micro-Manager log.
   *
   * @param e - Java exception to be shown and logged
   * @param msg - Error message to be shown and logged
   * @param parent - frame in which to show dialog, or null for caller
   */
  public void showError(Exception e, String msg, Component parent);

  /**
   * Shows and logs a Java exception.
   *
   * @param e - Java exception to be shown and logged
   * @param parent - frame in which to show dialog, or null for caller
   */
  public void showError(Exception e, Component parent);

  /**
   * Shows an error message in the UI and logs to the Micro-Manager log.
   *
   * @param msg - error message to be shown and logged
   * @param parent - frame in which to show dialog, or null for caller
   */
  public void showError(String msg, Component parent);

  /**
   * Log a message to the core log, only if debug logging is currently enabled.
   *
   * @param message Message to be logged.
   */
  public void logDebugMessage(String message);
}
