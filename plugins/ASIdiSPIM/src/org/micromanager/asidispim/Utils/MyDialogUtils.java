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

import javax.swing.JOptionPane;

import org.micromanager.asidispim.ASIdiSPIM;

import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Jon
 */
public class MyDialogUtils {

   
   public MyDialogUtils() {
   }
   
   /**
    * Shows a confirmation diaglog and returns true if OK/Yes was selected.
    * Centers over the plugin frame
    * @param prompt the string to display
    * @param optionType one of JOptionPane.YES_NO_OPTION or JOptionPane.OK_CANCEL_OPTION
    * @return true if user said "Yes" or "OK", false otherwise
    */
   public static boolean getConfirmDialogResult(String prompt, int optionType) {
      int dialogResult = JOptionPane.showConfirmDialog(ASIdiSPIM.getFrame(),
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
    * @param message
    * @param hideErrors true if errors are to be logged only and not displayed; false is default
    */
   public static void showError(String message, boolean hideErrors) {
      if (hideErrors) {
         ReportingUtils.logError(message);
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
    * @param message
    * @param hideErrors true if errors are to be logged only and not displayed; false is default
    */
   public static void showError(Throwable e, String message, boolean hideErrors) {
      if (hideErrors) {
         ReportingUtils.logError(e, message);
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
      } else {
         ReportingUtils.showError(e, ASIdiSPIM.getFrame());
      }
   }
}
