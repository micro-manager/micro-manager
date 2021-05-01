/* @author - Nico Stuurman,  2012
 * 
 * 
Copyright (c) 2012-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */
package edu.ucsf.valelab.gaussianfit.utils;

import java.awt.event.ActionEvent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Class emulating Micro-Manager's ReportingUtils Goal is to avoid dependencies on MMJ_.jar
 *
 * @author nico
 */
public class ReportingUtils {

   public static void showMessage(String msg) {
      JOptionPane.showMessageDialog(null, msg);
   }

   public static void logError(Throwable e, String msg) {
      if (e != null) {
         String stackTrace = getStackTraceAsString(e);
         System.out.println(msg + "\n" + e.toString() + " in "
               + Thread.currentThread().toString() + "\n" + stackTrace + "\n");
      } else {
         System.out.println("Error: " + msg);
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
         JOptionPane
               .showMessageDialog(null, fullMsg, "Micro-Manager Error", JOptionPane.ERROR_MESSAGE);
      } else {
         JTextArea area = new JTextArea(fullMsg);
         area.setRows(maxNrLines);
         area.setColumns(50);
         area.setLineWrap(true);
         JScrollPane pane = new JScrollPane(area);
         JOptionPane
               .showMessageDialog(null, pane, "Micro-Manager Error", JOptionPane.ERROR_MESSAGE);
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
      Throwable cause = aThrowable.getCause();
      if (cause != null) {
         return result + "Caused by: " + cause.toString() + "\n" + getStackTraceAsString(cause);
      } else {
         return result;
      }
   }

   public static void showError(ActionEvent e) {
      throw new UnsupportedOperationException("Not yet implemented");
   }


}
