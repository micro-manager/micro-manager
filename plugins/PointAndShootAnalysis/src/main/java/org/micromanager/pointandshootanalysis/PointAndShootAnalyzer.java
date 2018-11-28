///////////////////////////////////////////////////////////////////////////////
//FILE:          PointAndShootAnalyzer.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     PointAndShootAnalyzer plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2018
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

package org.micromanager.pointandshootanalysis;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.micromanager.Studio;

/**
 *
 * @author nico
 */
public class PointAndShootAnalyzer implements Runnable {
   final private Studio studio_;
   final private String fileName_;
   final private Map<String, Point> coordinates_;
   final private Map<Date, Point> datedCoordinates_;
   
   private static final SimpleDateFormat LOGTIME_FORMATTER = 
           new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
   
   public PointAndShootAnalyzer(Studio studio, String fileName)
   {
      studio_ = studio;
      fileName_ = fileName;
      coordinates_ = new HashMap<String, Point>();
      datedCoordinates_ = new HashMap<Date, Point>();
   }

   @Override
   public void run() {
      
      File f = new File(fileName_);
      if (!f.exists()) {
         studio_.logs().showError("File " + f.getName() + " does not exist");
         return;
      }
      if (!f.canRead()) {
         studio_.logs().showError("File " + f.getName() + " is not readable");
         return;
      }
      
      try {
         Consumer<String> action = new pointAndShootParser();
         Stream<String> fileLines = Files.lines(f.toPath());
         fileLines.forEach(action);
      } catch (IOException ex) {
         studio_.logs().showError("Error while parsing file: " + f.getName());
         return;
      }
      

      try {
         for (Map.Entry<String, Point> entry : coordinates_.entrySet()) {
            Date date = (Date)LOGTIME_FORMATTER.parse(entry.getKey());
            datedCoordinates_.put(date, entry.getValue());
         } 
      } catch (ParseException ex) {
         studio_.logs().showError("Error while parsing file contents");
         return;
      }
      
      
      
   }
   
   private class pointAndShootParser implements Consumer<String> {

      @Override
      public void accept(String t) {
         String[] parts = t.split("\t");
         if (parts.length ==3) {
            coordinates_.put(
                    parts[0], 
                    new Point( 
                        Integer.parseInt(parts[1]), 
                        Integer.parseInt(parts[2]) ) );
         }
      }
   }
   
}
