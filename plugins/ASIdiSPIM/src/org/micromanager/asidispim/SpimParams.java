///////////////////////////////////////////////////////////////////////////////
//FILE:          SpimParams.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim;

import java.util.HashMap;
import java.util.prefs.Preferences;


/**
 *
 * @author nico
 */
public class SpimParams {

   private final HashMap<String, Integer> integerInfo_;
   private final HashMap<String, String> sidesInfo_;
   private final HashMap<String, Float> floatInfo_;
   private Preferences prefs_;
   
   public static final String NSIDES = "NSides";
   public static final String NREPEATS = "NRepeats";
   public static final String NSHEETSA = "NSheetsA";
   public static final String NSHEETSB = "NSHeetsB";
   public static final String NLINESCANSPERSHEETA = "NLinesScansPerSheetA";
   public static final String NLINESCANSPERSHEETB = "NLinesScansPerSheetB";
   public static final String LINESCANPERIODA = "LineScanPeriodA";
   public static final String LINESCANPERIODB = "LineScanPeriodB";
   public static final String DELAYBEFORESHEETA = "DelayBeforeSheetA";
   public static final String DELAYBEFORESHEETB = "DelayBeforeSheetB";
   public static final String DELAYBEFORESIDEA = "DelayBeforeSideA";
   public static final String DELAYBEFORESIDEB = "DelayBeforeSideB";
   private static final String[] INTS = {
         NSIDES, NREPEATS, NSHEETSA, NSHEETSB, NLINESCANSPERSHEETA, 
         NLINESCANSPERSHEETB, LINESCANPERIODA, LINESCANPERIODB,
         DELAYBEFORESHEETA, DELAYBEFORESHEETB, DELAYBEFORESIDEA, DELAYBEFORESIDEB};

   public static final String FIRSTSIDE = "FirstSide";
   public static final String A = "A";
   public static final String B = "B";
   
   
  // private static final String[] FLOATS = {LINESCANPERIODA, LINESCANPERIODB,
   //      DELAYBEFORESHEETA, DELAYBEFORESHEETB, DELAYBEFORESIDEA, DELAYBEFORESIDEB};
   
     
   public SpimParams () {
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      
      integerInfo_ = new HashMap<String, Integer>();
      sidesInfo_ = new HashMap<String, String>();
      floatInfo_ = new HashMap<String, Float>();
   
      for  (String iInfo : INTS) {
         integerInfo_.put(iInfo, prefs_.getInt(iInfo, 1));
      }
      
      sidesInfo_.put(FIRSTSIDE, prefs_.get(FIRSTSIDE, "A"));
      
     // for (String fInfo : FLOATS) {
     //    floatInfo_.put(fInfo, prefs_.getFloat(fInfo, 0.0f));
      //}
   }
   
   public void putIntInfo(String key, int val) {
      synchronized (integerInfo_) {
         if (integerInfo_.containsKey(key)) {
            integerInfo_.put(key, val);
         }
      }
   }

   public int getIntInfo(String key) {
      synchronized (integerInfo_) {
         return integerInfo_.get(key);
      }
   }
   
   public void putSidesInfo(String key, String val) {
      synchronized (sidesInfo_) {
         if (val.equals(A) || val.equals(B) && sidesInfo_.containsKey(key)) {
            sidesInfo_.put(key, val);
         }
      }
   }

   public String getSidesInfo(String key) {
      synchronized (sidesInfo_) {
         return sidesInfo_.get(key);
      }
   }
   
   public void putFloatInfo(String key, float val) {
      synchronized (floatInfo_) {
         if (floatInfo_.containsKey(key)) {
            floatInfo_.put(key, val);
         }
      }
   }
   
   public float getFloatInfo(String key) {
      synchronized(floatInfo_) {
         return floatInfo_.get(key);
      }
   }
   
       /**
    * Writes info_ back to Preferences
    */
   public  void saveSettings() {
      for (String myI : INTS) {
         prefs_.putInt(myI, integerInfo_.get(myI));
      }
      prefs_.put(FIRSTSIDE, sidesInfo_.get(FIRSTSIDE));
     
     // for (String f : FLOATS) {
     //    prefs_.putFloat(f, floatInfo_.get(f));
     // }
         
   }
   
}
