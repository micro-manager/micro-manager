/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

import ij.IJ;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author bidc
 */
public class Log {
    
    public static void log(String message) {
        ReportingUtils.logMessage(message);
        IJ.log(message);
    }
    
}
