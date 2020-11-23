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

package com.asiimaging.crisp;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.PatternSyntaxException;

/**
 * This class provides generic methods for interacting with the file system.
 * 
 * TODO: when porting this to MM2 check for improved file methods in Java 8.
 */
public final class FileUtils {

	/**
	 * Returns the contents of the text file.
	 * 
	 * @param filepath Path to the file.
	 * @return ArrayList of Strings.
	 */
	public static ArrayList<String> loadTextFile(final String filepath) {
		final ArrayList<String> strings = new ArrayList<String>();
		
		// open file and extract the contents
		FileReader fileReader = null;
		BufferedReader bufferedReader = null;
		try {
			fileReader = new FileReader(filepath);
			bufferedReader = new BufferedReader(fileReader);
			
			// add each line to the ArrayList
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				strings.add(line);
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (PatternSyntaxException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} finally {
			close(bufferedReader);
		}
		return strings;
	}
	
	public static void saveTextFile(final String filepath) {
		// TODO: implement this feature to make saving a focus curve possible
	}
	
	/**
	 * Check for null to prevent NullPointerException when you try 
	 * to close the object in the finally block of try/catch.
	 * 
	 * @param closeable The object that implements the Closeable interface.
	 */
	private static void close(final Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
