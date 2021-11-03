package org.micromanager.asidispim.Utils;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Read and write to text files using Apache Commons.
 */
public class MyFileUtils {

    /**
     * Reads a text file with UTF-8 encoding into a String.
     * 
     * @param filePath - the location to write the text file
     * @return a String with the contents of the text file
     */
    public static String readFileToString(final String filePath) {
        String result = "";
        try {
            result = FileUtils.readFileToString(new File(filePath), "UTF-8");
        } catch (IOException e) {
            ReportingUtils.showError("IOException: " + e.getMessage());
        }
        return result;
    }
    
    /**
     * Writes contents to the text file using UTF-8 encoding.
     * 
     * @param filePath - the location to write the text file
     * @param contents - the String to write to the file
     */
    public static void writeStringToFile(final String filePath, final String contents) {
        try {
            FileUtils.writeStringToFile(new File(filePath), contents, "UTF-8");
        } catch (IOException e) {
            ReportingUtils.showError("IOException: " + e.getMessage());
        }
    }
    
}
