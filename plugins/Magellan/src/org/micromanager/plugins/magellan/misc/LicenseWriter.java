///////////////////////////////////////////////////////////////////////////////
//FILE:          VirtualAcquisitionDisplay.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.plugins.magellan.misc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;


public class LicenseWriter {
   
   private static final String MAGELLAN_HEADER = "///////////////////////////////////////////////////////////////////////////////\r\n" +
"// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com\r\n" +
"//\r\n" +
"// COPYRIGHT:    University of California, San Francisco, 2015\r\n" +
"//\n" +
"// LICENSE:      This file is distributed under the BSD license.\r\n" +
"//               License text is included with the source distribution.\r\n" +
"//\r\n" +
"//               This file is distributed in the hope that it will be useful,\r\n" +
"//               but WITHOUT ANY WARRANTY; without even the implied warranty\r\n" +
"//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.\r\n" +
"//\r\n" +
"//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR\r\n" +
"//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,\r\n" +
"//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.\r\n" +
"//\r\n";
   
   public static void main(String[] args) throws FileNotFoundException, IOException {
      String path = "/Users/henrypinkard/Documents/NetbeansSources/MM/trunk/plugins/Magellan/";
      for (File dir : new File(path).listFiles()) {
         if (dir.isDirectory() && !dir.getName().equals("json")) {
            for (File f : dir.listFiles()) {
               System.out.println("File: " + f.getName());
               if (f.getName().endsWith(".java")) {
                  String fileString = fileToString(f);
                  fileString = removeNetbeansDefault(fileString);
                  if ( !fileString.startsWith("/")) {
                     System.out.println("Overwriting");
                     String withLic = MAGELLAN_HEADER + fileString;
                     FileWriter fw = new FileWriter(f, false);
                     fw.write(withLic);
                     fw.flush();
                     fw.close();
                  }     
               }
            }
         }
      }
      
   }
   
   //null if it doesn't match, chop off if it does
   public static String removeNetbeansDefault(String s) {
      //13 is \ r
      String h1 = "/*" + "\r\n" +
              " * To change this license header, choose License Headers in Project Properties." + "\r\n" +
              " * To change this template file, choose Tools | Templates"+ "\r\n" +
             " * and open the template in the editor." + "\r\n" +
              "*/";
      String h2 = "/*" + "\r\n" +
              " * To change this template, choose Tools | Templates" +  "\r\n" +
              " * and open the template in the editor." + "\r\n" +
              " */";
          
      if (  s.startsWith(h1)   ) {
        return s.substring(h1.length());
      } else if (s.startsWith(h2)) {
         return s.substring(h2.length());
      }
      return s;
   }
   
   public static String fileToString(File f) throws FileNotFoundException  {
    String s = "";
      Scanner scanner = new Scanner(f);
      scanner.useDelimiter("\r\n");
    while(scanner.hasNext()) {
       s += scanner.next() + "\r\n";
    }
    scanner.close();
    return s;
}
   
}
