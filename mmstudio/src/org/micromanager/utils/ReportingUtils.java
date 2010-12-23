///////////////////////////////////////////////////////////////////////////////
//FILE:          ReportingUtils.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
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

package org.micromanager.utils;

import java.awt.event.ActionEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;


import java.util.Calendar;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import mmcorej.CMMCore;

/**
 *
 * @author arthur
 */
public class ReportingUtils {

   private static CMMCore core_ = null;
   private static JFrame owningFrame_;
   private static boolean show_ = true;

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

   public static void showMessage(String msg) {
      JOptionPane.showMessageDialog(null, msg);
   }

   public static void logError(Throwable e, String msg) {
      if (e != null) {
         String stackTrace = getStackTraceAsString(e);
         logMessage(msg + "\n" + e.toString() + " in "
                 + Thread.currentThread().toString() + "\n" + stackTrace + "\n");
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

   public static void showError(Throwable e, String msg) {
      logError(e, msg);

      if (!show_)
         return;

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

      int maxNrLines = 30;
      String test[] = fullMsg.split("\n");
      if (test.length < maxNrLines) {
         JOptionPane.showMessageDialog(null, fullMsg, "Micro-Manager Error", JOptionPane.ERROR_MESSAGE);
      } else {
         JTextArea area = new JTextArea(fullMsg);
         area.setRows(maxNrLines);
         area.setColumns(50);
         area.setLineWrap(true);
         JScrollPane pane = new JScrollPane(area);
         JOptionPane.showMessageDialog(null, pane, "Micro-Manager Error", JOptionPane.ERROR_MESSAGE);
      }
}

   public static void showError(Throwable e) {
      showError(e, "");
   }

   public static void showError(String msg) {
      showError(null, msg);
   }

   private static String getStackTraceAsString(Throwable aThrowable) {
      String result = "";
      for (StackTraceElement line : aThrowable.getStackTrace()) {
         result += "  at " + line.toString() + "\n";
      }
      return result;
   }

   public static void showError(ActionEvent e) {
      throw new UnsupportedOperationException("Not yet implemented");
   }

   public static void displayNonBlockingMessage(String message) {
      if (null != owningFrame_) {
         Calendar c = Calendar.getInstance();
         final JOptionPane optionPane = new JOptionPane(c.getTime().toString() + " " + message, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
         /* the false parameter is for not modal */
         final JDialog dialog = new JDialog(owningFrame_, "μManager Warning: ", false);
         optionPane.addPropertyChangeListener(
                 new PropertyChangeListener() {

                    public void propertyChange(PropertyChangeEvent e) {
                       String prop = e.getPropertyName();
                       if (dialog.isVisible() && (e.getSource() == optionPane) && (prop.equals(JOptionPane.VALUE_PROPERTY))) {
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
