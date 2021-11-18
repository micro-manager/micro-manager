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
    
    /**
     * Returns a unique file path. Do not include the "." in the file extension.
     * 
     * @param directory the file directory
     * @param name the file name
     * @param extension the file extension
     * @return a unique file path
     */
    public static String createUniquePath(final String directory, final String name, final String extension) {
    	// check if the file path is available => early exit if true
    	if (!(new File(directory + File.separator + name + "." + extension).exists())) {
    		return directory + File.separator + name + "." + extension;
    	}
    	// otherwise look for an unused file path
    	String path = "";
		int count = 0;
		boolean found = false;
		while (!found && count < 1000) {
			path = directory + File.separator + name + "_" + count + "." + extension;
			found = new File(directory).exists();
			count++;
		}
    	return path;
    }
}
