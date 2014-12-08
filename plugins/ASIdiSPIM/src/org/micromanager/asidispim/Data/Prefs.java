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

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.micromanager.asidispim.Utils.MyDialogUtils;


/**
 * Central class for reading/writing preferences.
 * 
 * Our "node" isn't the same as a Java preference node, but we
 * implement as a prefix to the preference name.
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

   // Can also use the name of the property as the key
   // for calls to pref functions.  This is preferred if 
   // there is a single corresponding property.  
   // But sometimes the same property needs to be stored
   // in several preferences, such as beam/sheet control,
   // or else there is no corresponding property.
   // In that case use pref keys.
   public static enum Keys {
      JOYSTICK("Joystick"), 
      RIGHT_WHEEL("Right Wheel"),
      LEFT_WHEEL("Left Wheel"),
      CAMERA("Camera"),
      TAB_INDEX("tabIndex"),
      WIN_LOC_X("xlocation"),
      WIN_LOC_Y("ylocation"),
      SHEET_BEAM_ENABLED("SheetBeamEnabled"),
      SHEET_SCAN_ENABLED("SheetScanEnabled"),
      EPI_BEAM_ENABLED("EpiBeamEnabled"),
      EPI_SCAN_ENABLED("EpiScanEnabled"),
      ENABLE_BEAM_SETTINGS("EnableBeamSettings"),
      SPIM_EXPOSURE("AcquisitionExposure"),
      POSITION_REFRESH_INTERVAL("PositionRefreshInterval"),
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
   
   private String getPrefKey(String node, Properties.Keys key) {
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

   public void putFloat(String node, Prefs.Keys key, float value) {
      myPrefs_.putFloat(getPrefKey(node, key), value);
   }
   
   public float getFloat(String node, Prefs.Keys key, float defaultValue) {
      return myPrefs_.getFloat(getPrefKey(node, key), defaultValue);
   }
   
   
   // same thing but using Property key instead of Prefs key
   
   public void putBoolean(String node, Properties.Keys key, boolean value) {
      myPrefs_.putBoolean(getPrefKey(node, key), value);
   }
   
   public boolean getBoolean(String node, Properties.Keys key, boolean defaultValue) {
      return myPrefs_.getBoolean(getPrefKey(node, key), defaultValue);
   }
   
   public void putString(String node, Properties.Keys key, String value) {
      myPrefs_.put(getPrefKey(node, key), value);
   }
   
   public String getString(String node, Properties.Keys key, String defaultValue) {
      return myPrefs_.get(getPrefKey(node, key), defaultValue);
   }
   
   public void putInt(String node, Properties.Keys key, int value) {
      myPrefs_.putInt(getPrefKey(node, key), value);
   }
   
   public int getInt(String node, Properties.Keys key, int defaultValue) {
      return myPrefs_.getInt(getPrefKey(node, key), defaultValue);
   }

   public void putFloat(String node, Properties.Keys key, float value) {
      myPrefs_.putFloat(getPrefKey(node, key), value);
   }
   
   public float getFloat(String node, Properties.Keys key, float defaultValue) {
      return myPrefs_.getFloat(getPrefKey(node, key), defaultValue);
   }
   
   
   
   // same thing, but using String as key
   
   public void putString(String node, String strKey, String value) {
      myPrefs_.put(getPrefKey(node, strKey), value);
   }
   
   public String getString(String node, String strKey, String defaultValue) {
      return myPrefs_.get(getPrefKey(node, strKey), defaultValue);
   }
   
   public void putFloat(String node, String strKey, float value) {
      myPrefs_.putFloat(getPrefKey(node, strKey), value);
   }
   
   public float getFloat(String node, String strKey, float defaultValue) {
      return myPrefs_.getFloat(getPrefKey(node, strKey), defaultValue);
   }
   
   
   /**
    * Searches for key in preferences.
    * 
    * @param node
    * @param key
    * @return
    */
   public boolean keyExists(String node, Properties.Keys key) {
      String allKeys[] = null;
      try {
         // this gets all the keys; our "node" is a privately-implemented thing, 
         // not the same as Java nodes which would be a sub-folder within regedit
         allKeys = myPrefs_.keys();
      } catch (BackingStoreException e) {
         MyDialogUtils.showError(e);
      }
      String lookFor = getPrefKey(node, key);
      for (String cur : allKeys) {
         if(cur.equals(lookFor)) {
            return true;
         }
      }
      return false;
   }
}
