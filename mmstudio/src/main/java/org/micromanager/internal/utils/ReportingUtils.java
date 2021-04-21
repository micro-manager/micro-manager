///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, June 2009
//
// COPYRIGHT:    University of California, San Francisco, 2009
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

package org.micromanager.internal.utils;

import mmcorej.CMMCore;
import org.micromanager.LogManager;
import org.micromanager.internal.MMStudio;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Calendar;

/** @author arthur */
public final class ReportingUtils {

  /**
   * This class acts as a thin wrapper around ReportingUtils to present its static methods in a
   * non-static context, for use in the API.
   */
  public static class Wrapper implements LogManager {
    @Override
    public void logMessage(String msg) {
      ReportingUtils.logMessage(msg);
    }

    @Override
    public void showMessage(String msg) {
      ReportingUtils.showMessage(msg);
    }

    @Override
    public void showMessage(String msg, Component parent) {
      ReportingUtils.showMessage(msg, parent);
    }

    @Override
    public void logError(Exception e, String msg) {
      ReportingUtils.logError(e, msg);
    }

    @Override
    public void logError(Exception e) {
      ReportingUtils.logError(e);
    }

    @Override
    public void logError(String msg) {
      ReportingUtils.logError(msg);
    }

    @Override
    public void showError(Exception e, String msg) {
      ReportingUtils.showError(e, msg);
    }

    @Override
    public void showError(Exception e) {
      ReportingUtils.showError(e);
    }

    @Override
    public void showError(String msg) {
      ReportingUtils.showError(msg);
    }

    @Override
    public void showError(Exception e, String msg, Component parent) {
      ReportingUtils.showError(e, msg, parent);
    }

    @Override
    public void showError(Exception e, Component parent) {
      ReportingUtils.showError(e, parent);
    }

    @Override
    public void showError(String msg, Component parent) {
      ReportingUtils.showError(msg, parent);
    }

    @Override
    public void logDebugMessage(String message) {
      ReportingUtils.logDebugMessage(message);
    }
  }

  private static final Wrapper staticWrapper_;

  static {
    staticWrapper_ = new Wrapper();
  }

  public static LogManager getWrapper() {
    return staticWrapper_;
  }

  private static CMMCore core_ = null;
  private static JFrame owningFrame_;
  private static boolean show_ = true;

  // Intended for setting to the main frame.
  public static void SetContainingFrame(JFrame f) {
    owningFrame_ = f;
  }

  public static void setCore(CMMCore core) {
    core_ = core;
  }

  public static void showErrorOn(boolean show) {
    show_ = show;
  }

  public static void logMessage(String msg) {
    if (core_ == null) {
      System.out.println(msg);
    } else {
      core_.logMessage(msg);
    }
  }

  public static void logDebugMessage(String msg) {
    if (core_ == null) {
      System.out.println(msg);
    } else {
      core_.logMessage(msg, true);
    }
  }

  public static void logDebugMessage(Throwable e, String msg) {
    if (e != null) {
      String stackTrace = getStackTraceAsString(e);
      msg =
          (msg
              + "\n"
              + e.toString()
              + " in "
              + Thread.currentThread().toString()
              + "\n"
              + stackTrace
              + "\n");
    }
    if (core_ == null) {
      System.out.println(msg);
    } else {
      core_.logMessage(msg, true);
    }
  }

  public static void showMessage(final String msg) {
    JOptionPane.showMessageDialog(null, msg);
  }

  public static void showMessage(final String msg, Component parent) {
    JOptionPane.showMessageDialog(parent, msg);
  }

  public static void logError(Throwable e, String msg) {
    if (e != null) {
      String stackTrace = getStackTraceAsString(e);
      logMessage(
          msg
              + "\n"
              + e.toString()
              + " in "
              + Thread.currentThread().toString()
              + "\n"
              + stackTrace
              + "\n");
    } else {
      logMessage("Error: " + msg);
    }
  }

  public static void logError(Throwable e) {
    logError(e, "");
  }

  public static void logError(String msg) {
    logError(null, msg);
  }

  public static void showError(Throwable e, String msg, Component parent) {
    logError(e, msg);

    if (!show_) return;

    String fullMsg;
    if (e != null && e.getMessage() != null && msg.length() > 0) {
      fullMsg = "Error: " + msg + "\n" + e.getMessage();
    } else if (e != null && e.getMessage() != null) {
      fullMsg = e.getMessage();
    } else if (msg.length() > 0) {
      fullMsg = "Error: " + msg;
    } else if (e != null) {
      fullMsg = "Error: " + e.getStackTrace()[0];
    } else {
      fullMsg = "Unknown error (please check CoreLog.txt file for more information)";
    }

    ReportingUtils.showErrorMessage(fullMsg, parent);
  }

  private static String formatAlertMessage(String[] lines) {
    com.google.common.escape.Escaper escaper = com.google.common.html.HtmlEscapers.htmlEscaper();
    StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    for (String line : lines) {
      sb.append("<div width='640'>");
      sb.append(escaper.escape(line));
      sb.append("</div>");
    }
    sb.append("</html>");
    return sb.toString();
  }

  private static void showErrorMessage(final String fullMsg, final Component parent) {
    int maxNrLines = 10;
    String lines[] = fullMsg.split("\n");
    if (lines.length < maxNrLines) {
      final String wrappedMsg = formatAlertMessage(lines);
      GUIUtils.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              JOptionPane.showMessageDialog(
                  parent, wrappedMsg, "Micro-Manager Error", JOptionPane.ERROR_MESSAGE);
            }
          });
    } else {
      JTextArea area = new JTextArea(fullMsg);
      area.setRows(maxNrLines);
      area.setColumns(50);
      area.setLineWrap(true);
      final JScrollPane pane = new JScrollPane(area);
      GUIUtils.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              JOptionPane.showMessageDialog(
                  parent, pane, "Micro-Manager Error", JOptionPane.ERROR_MESSAGE);
            }
          });
    }
  }

  public static void showError(Throwable e) {
    showError(e, "", MMStudio.getInstance().app().getMainWindow());
  }

  public static void showError(String msg) {
    showError(null, msg, MMStudio.getInstance().app().getMainWindow());
  }

  public static void showError(Throwable e, String msg) {
    showError(e, msg, MMStudio.getInstance().app().getMainWindow());
  }

  public static void showError(Throwable e, Component parent) {
    showError(e, "", parent);
  }

  public static void showError(String msg, Component parent) {
    showError(null, msg, parent);
  }

  private static String getStackTraceAsString(Throwable aThrowable) {
    String result = "";
    for (StackTraceElement line : aThrowable.getStackTrace()) {
      result += "  at " + line.toString() + "\n";
    }
    Throwable cause = aThrowable.getCause();
    if (cause != null) {
      return result + "Caused by: " + cause.toString() + "\n" + getStackTraceAsString(cause);
    } else {
      return result;
    }
  }

  /**
   * As above, but doesn't require a Throwable; a convenience function for logging when you want to
   * know where you were called from.
   */
  public static String getStackTraceAsString() {
    return getStackTraceAsString(100);
  }

  /** Return the stack trace of the current thread up to the specified limit of entries. */
  public static String getStackTraceAsString(int numEntries) {
    String result = "";
    StackTraceElement[] lines = Thread.currentThread().getStackTrace();
    numEntries = Math.min(lines.length, numEntries);
    for (StackTraceElement line : Arrays.copyOfRange(lines, 0, numEntries)) {
      result += "  at " + line.toString() + "\n";
    }
    return result;
  }

  /** Return the caller of the method that called getCaller(). */
  public static String getCaller() {
    // First is getStackTrace, second is us, third is whoever called us,
    // fourth is whoever called *them*.
    return java.lang.Thread.currentThread().getStackTrace()[3].toString();
  }

  public static void showError(ActionEvent e) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  public static void displayNonBlockingMessage(final String message) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              ReportingUtils.displayNonBlockingMessage(message);
            }
          });
      return;
    }

    if (null != owningFrame_) {
      Calendar c = Calendar.getInstance();
      final JOptionPane optionPane =
          new JOptionPane(
              c.getTime().toString() + " " + message,
              JOptionPane.WARNING_MESSAGE,
              JOptionPane.OK_CANCEL_OPTION);
      /* the false parameter is for not modal */
      final JDialog dialog = new JDialog(owningFrame_, "μManager Warning: ", false);
      optionPane.addPropertyChangeListener(
          new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent e) {
              String prop = e.getPropertyName();
              if (dialog.isVisible()
                  && (e.getSource() == optionPane)
                  && (prop.equals(JOptionPane.VALUE_PROPERTY))) {
                dialog.setVisible(false);
              }
            }
          });
      dialog.setContentPane(optionPane);
      /* adapting the frame size to its content */
      dialog.pack();
      dialog.setLocationRelativeTo(owningFrame_);
      dialog.setVisible(true);
    }
  }
}
