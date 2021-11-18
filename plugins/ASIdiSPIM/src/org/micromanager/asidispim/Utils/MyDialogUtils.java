///////////////////////////////////////////////////////////////////////////////
//FILE:          MyDialogUtils.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Jon Daniels, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.Utils;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;

import org.micromanager.asidispim.ASIdiSPIM;

import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Jon
 */
public class MyDialogUtils {

   /**Standard error reporting or delegate to JTextArea component. */
   public static boolean SEND_ERROR_TO_COMPONENT = false;

   /**The component to display the errors. */
   private static JTextArea errorLog = null;
	   
   public MyDialogUtils() {
   }
   
   /**
    * Sets the JTextArea to log errors.
    * 
    * @param textArea a reference to the object
    */
   public static void setErrorLog(final JTextArea textArea) {
       errorLog = textArea;
   }
   
   /**
    * Shows a confirmation diaglog and returns true if OK/Yes was selected.
    * Centers over the plugin frame
    * @param prompt the string to display
    * @param optionType one of JOptionPane.YES_NO_OPTION or JOptionPane.OK_CANCEL_OPTION
    * @return true if user said "Yes" or "OK", false otherwise
    */
   public static boolean getConfirmDialogResult(String prompt, int optionType) {
      final int dialogResult = JOptionPane.showConfirmDialog(ASIdiSPIM.getFrame(),
            prompt,
            "Warning",
            optionType);
      switch (optionType) {
      case JOptionPane.YES_NO_OPTION:
         return (dialogResult == JOptionPane.YES_OPTION);
      case JOptionPane.OK_CANCEL_OPTION:
         return (dialogResult == JOptionPane.OK_OPTION);
      default:
         return false;
      }
   }
   
   public static void showError(String message) {
      showError(message, false);
   }
   
   /**
    * Convenience method to show an error message (also logged) over the plugin frame.
    * Calls org.micromanager.utils.ReportingUtils() 
    * @param message the message to display
    * @param hideErrors true if errors are to be logged only and not displayed; false is default
    */
   public static void showError(String message, boolean hideErrors) {
      if (hideErrors) {
         ReportingUtils.logError(message);
         if (SEND_ERROR_TO_COMPONENT) {
        	 errorLog.append("Error: " + message + "\n");
         }
      } else {
         ReportingUtils.showError(message, ASIdiSPIM.getFrame());
      }
   }
   
   public static void showError(Throwable e, String message) {
      showError(e, message, false);
   }
   
   /**
    * Convenience method to show an error message (also logged) over the plugin frame.
    * Calls org.micromanager.utils.ReportingUtils() 
    * @param e exception
    * @param message the message to display
    * @param hideErrors true if errors are to be logged only and not displayed; false is default
    */
   public static void showError(Throwable e, String message, boolean hideErrors) {
      if (hideErrors) {
         ReportingUtils.logError(e, message);
         if (SEND_ERROR_TO_COMPONENT) {
        	 errorLog.append("Error: " + message + "\n");
         }
      } else {
         ReportingUtils.showError(e, message, ASIdiSPIM.getFrame());
      }
   }
   
   public static void showError(Throwable e) {
      showError(e, false);
   }
   
   /**
    * Convenience method to show an error message (also logged) over the plugin frame.
    * Calls org.micromanager.utils.ReportingUtils() 
    * @param e exception
    * @param hideErrors true if errors are to be logged only and not displayed; false is default
    */
   public static void showError(Throwable e, boolean hideErrors) {
      if (hideErrors) {
         ReportingUtils.logError(e);
         if (SEND_ERROR_TO_COMPONENT) {
        	 errorLog.append("Error: " + e + "\n");
         }
      } else {
         ReportingUtils.showError(e, ASIdiSPIM.getFrame());
      }
   }
   
   /**
    * Shows a customized dialog box that has a text input field.<P>
    * This is used for reporting errors in the AcquisitionTable.
    * 
    * @param frame the frame in which the dialog is displayed 
    * @param title the title string for the dialog
    * @param message the message to display above the text input
    * @return the contents of the text input field
    */
   public static String showTextEntryDialog(final JFrame frame, final String title, final String message) {
       final String result = (String)JOptionPane.showInputDialog(frame, 
    		   message, title, JOptionPane.PLAIN_MESSAGE, null, null, ""
       );
       return result;
   }
   
   /**
    * Shows a customized message dialog box, this method does not log the error.<P>
    * This is used for reporting errors in the AcquisitionTable.
    * 
    * @param frame the frame in which the dialog is displayed 
    * @param title the title string for the dialog
    * @param message the message to display
    */
   public static void showErrorMessage(final JFrame frame, final String title, final String message) {
       JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE);
   }
   
}
