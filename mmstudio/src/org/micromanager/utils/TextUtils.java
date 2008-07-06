///////////////////////////////////////////////////////////////////////////////
//FILE:          TextUtils.java
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
package org.micromanager.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;

public class TextUtils {
   
   static public String readTextFile(String path) throws IOException {
      String newLine = System.getProperty("line.separator");
      StringBuffer sb = new StringBuffer();
      BufferedReader input = new BufferedReader(new FileReader(path));
      String line;
      while (null != (line = input.readLine())) {
         if (sb.length() > 0)
            sb.append(newLine);
         sb.append(line);
      }
      
      return sb.toString();
   }
   
   public static final DecimalFormat FMT0 = new DecimalFormat("#0");
   public static final DecimalFormat FMT1 = new DecimalFormat("#0.0");
   public static final DecimalFormat FMT2 = new DecimalFormat("#0.00");
   public static final DecimalFormat FMT3 = new DecimalFormat("#0.000");

}
