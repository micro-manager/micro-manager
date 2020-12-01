///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
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

package com.asiimaging.crisp.utils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.PatternSyntaxException;

import org.micromanager.utils.ReportingUtils;

/**
 * This class provides generic methods for interacting with the file system.
 * 
 * TODO: when porting this to MM2 check for improved file methods in Java 8.
 */
public final class FileUtils {

    
    public static List<String> readTextFile(final String filePath) {
        final List<String> file = new ArrayList<String>();
        
        String line = "";
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            
            while ((line = reader.readLine()) != null) {
                file.add(line);
            }
            
        } catch (FileNotFoundException e) {
            ReportingUtils.showError("File not found:\n" + filePath);
        } catch (IOException e) {
            ReportingUtils.showError("IOException when trying to open:\n" + filePath);
        } finally {
            close(reader);
        }
        return file;
    }
    
    public static void saveTextFile(final List<String> file, final String filePath) {
        // TODO: implement this feature to make saving a focus curve possible
    }
    
    /**
     * Convenience method to prevent having to use a try/catch in a finally 
     * block to close a Closeable object.
     * 
     * @param closeable the object that implements the Closeable interface
     */
    private static void close(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                ReportingUtils.showError("Failed to close the Closeable object.");
            }
        }
    }
}
