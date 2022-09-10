///////////////////////////////////////////////////////////////////////////////
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

package org.micromanager.magellan.internal.misc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.filechooser.FileSystemView;
import org.micromanager.magellan.internal.gui.GUI;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author Henry
 */
public class GlobalSettings {

   
   private static final String SAVING_DIR = "SAVING DIRECTORY";
   private static final String FIRST_OPENING = "FIRST_OPEN";
   private static final String CHANNEL_OFFSET_PREFIX = "CHANNEL_OFFSET_";
   
   private static GlobalSettings singleton_;
   MutablePropertyMapView prefs_;
   private boolean demoMode_ = false;
   private boolean afBetweenAcqs_ = false;
   private int[] chOffsets_ = new int[8];

   public GlobalSettings() {
      singleton_ = this;
      prefs_ = Magellan.getStudio().profile().getSettings(GlobalSettings.class);


      //      //Demo mode
      //      try {
      //         String s = ((MMStudio)Magellan.getStudio()).getSysConfigFile();
      //         if (s.endsWith("MagellanDemo.cfg")) {
      //            //generate a dummy affine transformation for current pixel size config
      //            String psConfig = Magellan.getCore().getCurrentPixelSizeConfig();
      //            AffineTransform demoTransform = new AffineTransform(new double[]{1, 0, 0, 1});
      //            MagellanAffineUtils.storeAffineTransform(psConfig, demoTransform);
      //            //Set stage to the middle of the demo sample
      //            Magellan.getCore().setXYPosition(700, 700);
      //            demoMode_ = true;
      //            new DemoModeImageData();
      //         }
      //      } catch (Exception e) {
      //          Log.log("Couldn't initialize Demo mode");
      //      }

      //load channel offsets
      try {
         for (int i = 0; i < 6; i++) {
            chOffsets_[i] = prefs_.getInteger(CHANNEL_OFFSET_PREFIX
                  + Magellan.getCore().getCurrentPixelSizeConfig() + i, 0);
         }
      } catch (Exception ex) {
         Log.log("couldnt get pixel size config", true);
      }
   }
   
   public boolean firstMagellanOpening() {
      boolean first = prefs_.getBoolean(FIRST_OPENING, true);
      prefs_.putBoolean(FIRST_OPENING, false);
      return first;
   }
 
   public void storeBooleanInPrefs(String key, Boolean value) {
      prefs_.putBoolean(key, value);
   }
   
   public boolean getBooleanInPrefs(String key, Boolean defaultVal) {
      return prefs_.getBoolean(key, defaultVal);
   }
   
   public void storeIntInPrefs(String key, Integer value) {
      prefs_.putInteger(key, value);
   }
   
   public int getIntInPrefs(String key, int defualtVal) {
      return prefs_.getInteger(key, defualtVal);
   }
   
   public void storeStringInPrefs(String key, String value) {
      prefs_.putString(key, value);
   }
   
   public String getStringInPrefs(String key, String defaultValue) {
      return prefs_.getString(key, defaultValue);
   }
   
   public void storeDoubleInPrefs(String key, double d) {
      prefs_.putDouble(key, d);
   }
   
   public double getDoubleInPrefs(String key, double defaultValue) {
      return prefs_.getDouble(key, defaultValue);
   }
   
   public static GlobalSettings getInstance() {
      return singleton_;
   }
   
   public void storeSavingDirectory(String dir) {
      prefs_.putString(SAVING_DIR, dir);
   }
   
   public String getStoredSavingDirectory() {
      return prefs_.getString(SAVING_DIR, FileSystemView.getFileSystemView()
            .getHomeDirectory().getAbsolutePath());
   }
   
   public boolean getAutofocusBetweenSerialAcqusitions() {
      return afBetweenAcqs_;
   }
   
   public boolean getDemoMode() {
      return demoMode_;
   }

   /**
    * Serializes an object and stores it in Preferences.
    */
   public static void putObjectInPrefs(Preferences prefs, String key, Serializable obj) {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      try {
         ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
         objectStream.writeObject(obj);
      } catch (Exception e) {
         Log.log("Failed to save object in Preferences.");
         return;
      }
      final int MAX_LENGTH = 3 * Preferences.MAX_VALUE_LENGTH / 4;
      byte[] serialBytes = byteStream.toByteArray();
      int totalLength = serialBytes.length;
      long nChunks = (int) Math.ceil(serialBytes.length / (double) MAX_LENGTH);
      try {
         if (prefs.nodeExists(key)) {
            prefs.node(key).removeNode();
         }
      } catch (BackingStoreException ex) {
         Log.log(ex);
      }
      for (int i = 0; i < nChunks; ++i) {
         int chunkLength = Math.min(MAX_LENGTH, totalLength - i * MAX_LENGTH);
         byte[] chunk = new byte[chunkLength];
         System.arraycopy(serialBytes, i * MAX_LENGTH, chunk, 0, chunkLength);
         prefs.node(key).putByteArray(String.format("%09d", i), chunk);
      }
   }
   
   public MutablePropertyMapView getGlobalPrefernces() {
      return prefs_;
   }

   /**
    * Retrieves an object from Preferences (deserialized).
    */
   @SuppressWarnings("unchecked")
    public static <T> T getObjectFromPrefs(Preferences prefs, String key, T def) {
      ArrayList<byte[]> chunks = new ArrayList<byte[]>();
      byte[] serialBytes = new byte[0];
      int totalLength = 0;
      try {
         for (String chunkKey : prefs.node(key).keys()) {
            byte[] chunk = prefs.node(key).getByteArray(chunkKey, new byte[0]);
            chunks.add(chunk);
            totalLength += chunk.length;
         }
         int pos = 0;
         serialBytes = new byte[totalLength];
         for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, serialBytes, pos, chunk.length);
            pos += chunk.length;
         }
      } catch (BackingStoreException ex) {
         Log.log(ex);
      }

      if (serialBytes.length == 0) {
         return def;
      }
      ByteArrayInputStream byteStream = new ByteArrayInputStream(serialBytes);
      try {
         ObjectInputStream objectStream = new ObjectInputStream(byteStream);
         return (T) objectStream.readObject();
      } catch (Exception e) {
         Log.log("Failed to get object from preferences.");
         return def;
      }
   }
    
}