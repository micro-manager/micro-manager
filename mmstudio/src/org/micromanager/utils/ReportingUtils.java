/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import java.awt.event.ActionEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;

/**
 *
 * @author arthur
 */
public class ReportingUtils {

    private static CMMCore core_ = null;

    public static void setCore(CMMCore core) {
        core_ = core;
    }

    public static void logMessage(String msg) {
        if (core_ == null)
            System.out.println(msg);
        else
            core_.logMessage(msg);
    }

    public static void showMessage(String msg) {
        JOptionPane.showMessageDialog(null, msg);
    }

    public static void logError(Throwable e, String msg) {
        if (e != null) {
            logMessage(msg + "\n" + e.getMessage() + " in " + Thread.currentThread() + "\n" +getStackTraceAsString(e));
        } else {
            logMessage("Error: "+msg);
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

        if (e != null && e.getMessage() != null) {
            JOptionPane.showMessageDialog(null, "Error: "+msg + "\n"  + e.getMessage());
        } else if (msg.length() > 0) {
            JOptionPane.showMessageDialog(null, "Error: "+msg);
        } else if (msg.length()>0) {
            JOptionPane.showMessageDialog(null, "Error: "+msg);
        } else if (e!=null){
            JOptionPane.showMessageDialog(null, "Error: "+e.getStackTrace()[0]);
        } else {
            JOptionPane.showMessageDialog(null, "Unknown error (please check CoreLog.txt file for more information)");
        }
    }

    public static void showError(Throwable e) {
        showError(e, "");
    }

    public static void showError(String msg) {
        showError(null, msg);
    }

    private static String getStackTraceAsString(Throwable aThrowable) {
        final StringWriter result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        aThrowable.printStackTrace(printWriter);
        return result.toString();
    }

    public static void showError(ActionEvent e) {
        throw new UnsupportedOperationException("Not yet implemented");
    }


}
