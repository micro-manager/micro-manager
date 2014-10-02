///////////////////////////////////////////////////////////////////////////////
//FILE:          MMOptions.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Sept 12, 2006
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

package org.micromanager;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Options data for MMStudio.
 */
public class MMOptions {
   private static final String DEBUG_LOG = "DebugLog";
   private static final String PREF_DIR = "MMOptions";
   private static final String CLOSE_ON_EXIT = "CloseOnExit";
   private static final String SKIP_CONFIG = "SkipSplashScreen";
   private static final String BUFFSIZE_MB = "bufsize_mb";
   private static final String DISPLAY_BACKGROUND = "displayBackground";
   private static final String STARTUP_SCRIPT_FILE = "startupScript";
   private static final String AUTORELOAD_DEVICES = "autoreloadDevices"; // No longer used but should not be reused
   private static final String PREF_WINDOW_MAG = "windowMag";
   private static final String MPTIFF_METADATA_FILE = "MakeMetadataFileWithMultipageTiff";
   private static final String MPTIFF_SEPARATE_FILES_FOR_POSITIONS = "SplitXYPostionsInFilesMPTiff";
   private static final String SYNCEXPOSUREMAINANDMDA = "SyncExposureBetweenMainAndMDAWindows";
   private static final String HIDE_MDA_DISPLAY = "HideMDADisplay";
   private static final String FAST_STORAGE = "FastStorage"; // No longer used but should not be reused
   private static final String DELETE_OLD_CORELOGS = "DeleteOldCoreLogs";
   private static final String DELETE_CORELOG_AFTER_DAYS =
      "DeleteCoreLogAfterDays";

   public boolean debugLogEnabled_;
   public boolean doNotAskForConfigFile_;
   public boolean closeOnExit_;
   public int circularBufferSizeMB_;
   public String displayBackground_;
   public String startupScript_;
   public double windowMag_;
   public boolean mpTiffMetadataFile_;
   public boolean mpTiffSeparateFilesForPositions_;
   public boolean syncExposureMainAndMDA_;
   public boolean hideMDADisplay_;
   public boolean deleteOldCoreLogs_;
   public int deleteCoreLogAfterDays_;

   public MMOptions() {
      setDefaultValues();
   }

   private void setDefaultValues() {
      debugLogEnabled_ = false;
      doNotAskForConfigFile_ = false;
      closeOnExit_ = true;
      boolean is64BitJVM =
         System.getProperty("sun.arch.data.model", "32").equals("64");
      circularBufferSizeMB_ = is64BitJVM ? 250 : 25;
      displayBackground_ = "Day";
      startupScript_ = "MMStartup.bsh";
      windowMag_ = 1.0;
      mpTiffMetadataFile_ = false;
      mpTiffSeparateFilesForPositions_ = true;
      syncExposureMainAndMDA_ = false;
      hideMDADisplay_ = false;
      deleteOldCoreLogs_ = false;
      deleteCoreLogAfterDays_ = 7;
   }

   private Preferences getPrefNode() {
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      Preferences prefs = root.node(root.absolutePath() + "/" + PREF_DIR);
      return prefs;
   }

   public void saveSettings() {
      Preferences prefs = getPrefNode();

      prefs.putBoolean(DEBUG_LOG, debugLogEnabled_);
      prefs.putBoolean(SKIP_CONFIG, doNotAskForConfigFile_);
      prefs.putBoolean(CLOSE_ON_EXIT, closeOnExit_);
      prefs.putInt(BUFFSIZE_MB, circularBufferSizeMB_);
      prefs.put(DISPLAY_BACKGROUND, displayBackground_);
      prefs.put(STARTUP_SCRIPT_FILE, startupScript_);
      prefs.putDouble(PREF_WINDOW_MAG, windowMag_);
      prefs.putBoolean(MPTIFF_METADATA_FILE, mpTiffMetadataFile_);
      prefs.putBoolean(MPTIFF_SEPARATE_FILES_FOR_POSITIONS, mpTiffSeparateFilesForPositions_);
      prefs.putBoolean(SYNCEXPOSUREMAINANDMDA, syncExposureMainAndMDA_);
      prefs.putBoolean(HIDE_MDA_DISPLAY, hideMDADisplay_);
      prefs.putBoolean(DELETE_OLD_CORELOGS, deleteOldCoreLogs_);
      prefs.putInt(DELETE_CORELOG_AFTER_DAYS, deleteCoreLogAfterDays_);
   }

   public void loadSettings() {
      Preferences prefs = getPrefNode();

      debugLogEnabled_ = prefs.getBoolean(DEBUG_LOG, debugLogEnabled_);
      doNotAskForConfigFile_ = prefs.getBoolean(SKIP_CONFIG, doNotAskForConfigFile_);
      closeOnExit_ = prefs.getBoolean(CLOSE_ON_EXIT, closeOnExit_);
      circularBufferSizeMB_ = prefs.getInt(BUFFSIZE_MB, circularBufferSizeMB_);
      displayBackground_ = prefs.get(DISPLAY_BACKGROUND, displayBackground_);
      startupScript_ = prefs.get(STARTUP_SCRIPT_FILE, startupScript_);
      windowMag_ = prefs.getDouble(PREF_WINDOW_MAG, windowMag_);
      mpTiffMetadataFile_ = prefs.getBoolean(MPTIFF_METADATA_FILE, mpTiffMetadataFile_);
      mpTiffSeparateFilesForPositions_ = prefs.getBoolean(MPTIFF_SEPARATE_FILES_FOR_POSITIONS, mpTiffSeparateFilesForPositions_);
      syncExposureMainAndMDA_ = prefs.getBoolean(SYNCEXPOSUREMAINANDMDA, syncExposureMainAndMDA_);
      hideMDADisplay_ = prefs.getBoolean(HIDE_MDA_DISPLAY, hideMDADisplay_);
      deleteOldCoreLogs_ =
         prefs.getBoolean(DELETE_OLD_CORELOGS, deleteOldCoreLogs_);
      deleteCoreLogAfterDays_ =
         prefs.getInt(DELETE_CORELOG_AFTER_DAYS, deleteCoreLogAfterDays_);
   }

   public void resetSettings() throws BackingStoreException {
      getPrefNode().clear();
      setDefaultValues();
   }
}
