///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 1, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id$
//

package org.micromanager.internal.utils;

import com.google.common.io.Files;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

public final class TextUtils {

   public static  String readTextFile(String path) throws IOException {
      String newLine = System.getProperty("line.separator");
      StringBuffer sb = new StringBuffer();
      BufferedReader input = new BufferedReader(new FileReader(path));
      String line;
      while (null != (line = input.readLine())) {
         if (sb.length() > 0) {
            sb.append(newLine);
         }
         sb.append(line);
      }

      return sb.toString();
   }

   public static void writeTextFile(String path, String content) throws IOException {
      BufferedWriter output = new BufferedWriter(new FileWriter(path));
      output.append(content);
      output.close();
   }

   public static final DecimalFormat FMT0 = new DecimalFormat("#0");
   public static final DecimalFormat FMT1 = new DecimalFormat("#0.0");
   public static final DecimalFormat FMT2 = new DecimalFormat("#0.00");
   public static final DecimalFormat FMT3 = new DecimalFormat("#0.000");

   /**
    * Given a filename, truncate it by a) removing file extension, and b)
    * replacing inner content by ellipses, to meet the provided max length.
    */
   public static String truncateFilename(String name, int maxLen) {
      if (maxLen < 6) {
         throw new IllegalArgumentException("Max len too short");
      }
      if (name.length() > maxLen) {
         name = Files.getNameWithoutExtension(name);
         if (name.length() > maxLen) {
            int len = name.length();
            name = name.substring(0, maxLen / 2 - 3) + "..."
                  + name.substring(len - maxLen / 2, len);
         }
      }
      return name;
   }

   /**
    * Given an input string (presumably formatted by one of our formatters),
    * remove the leading negative sign if the numerals are all zero. E.g. turns
    * "-0.0" into "0.0".
    * See http://stackoverflow.com/questions/11929096/negative-sign-in-case-of-zero-in-java
    */
   public static String removeNegativeZero(String input) {
      return input.replaceAll("^-(?=0(.0*)?$)", "");
   }
}
