/*
 * 
 */
package edu.valelab.gaussianfit.utils;

import java.awt.event.ActionEvent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Class emulating Micro-Manager's ReportingUtils
 * Goal is to avoid dependencies on MMJ_.jar
 * 
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
