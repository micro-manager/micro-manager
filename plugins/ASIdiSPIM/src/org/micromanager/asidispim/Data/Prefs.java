///////////////////////////////////////////////////////////////////////////////
//FILE:          Prefs.java
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

package org.micromanager.asidispim.Data;

import java.util.prefs.Preferences;


/**
 * Central class for reading/writing preferences
 * 
 * @author Jon
 */
public class Prefs {

   private final Preferences myPrefs_;
   
   /**
    * Constructor
    */
   public Prefs(Preferences prefs) {
      myPrefs_ = prefs;
   }// constructor

   // useful static constants 
   public static enum Keys {
      JOYSTICK("Joystick"), 
      RIGHT_WHEEL("Right Wheel"),
      LEFT_WHEEL("Left Wheel"),
      CAMERA("Camera"),
      TAB_INDEX("tabIndex"),
      WIN_LOC_X("xlocation"),
      WIN_LOC_Y("ylocation"),
      ENABLE_POSITION_UPDATES("EnablePositionUpdates"),
      ENABLE_ILLUM_PIEZO_HOME("EnableIllumPiezoHome"),
      SHEET_BEAM_ENABLED("SheetBeamEnabled"),
      SHEET_SCAN_ENABLED("SheetScanEnabled"),
      EPI_BEAM_ENABLED("EpiBeamEnabled"),
      EPI_SCAN_ENABLED("EpiBeamEnabled"),
      ENABLE_BEAM_SETTINGS("EnableBeamSettings"),
      SPIM_EXPOSURE("AcquisitionExposure"),
      NONE("None");
      private final String text;
      Keys(String text) {
         this.text = text;
      }
      @Override
      public String toString() {
         return text;
      }
   };
   
   private String getPrefKey(String node, Prefs.Keys key) {
      return node + "_" + key.toString();
   }
   
   private String getPrefKey(String node, String strKey) {
      return node + "_" + strKey;
   }
   
   
   public void putBoolean(String node, Prefs.Keys key, boolean value) {
      myPrefs_.putBoolean(getPrefKey(node, key), value);
   }
   
   public boolean getBoolean(String node, Prefs.Keys key, boolean defaultValue) {
      return myPrefs_.getBoolean(getPrefKey(node, key), defaultValue);
   }
   
   public void putString(String node, Prefs.Keys key, String value) {
      myPrefs_.put(getPrefKey(node, key), value);
   }
   
   public String getString(String node, Prefs.Keys key, String defaultValue) {
      return myPrefs_.get(getPrefKey(node, key), defaultValue);
   }
   
   public void putInt(String node, Prefs.Keys key, int value) {
      myPrefs_.putInt(getPrefKey(node, key), value);
   }
   
   public int getInt(String node, Prefs.Keys key, int defaultValue) {
      return myPrefs_.getInt(getPrefKey(node, key), defaultValue);
   }
   
   public void putString(String node, String strKey, String value) {
      myPrefs_.put(getPrefKey(node, strKey), value);
   }
   
   public String getString(String node, String strKey, String defaultValue) {
      return myPrefs_.get(getPrefKey(node, strKey), defaultValue);
   }
   
   
}
