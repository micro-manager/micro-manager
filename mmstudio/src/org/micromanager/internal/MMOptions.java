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

package org.micromanager.internal;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Options data for MMStudio.
 */
public class MMOptions {
   private static final String PREF_DIR = "MMOptions";
   private static final String BUFFSIZE_MB = "bufsize_mb";
   private static final String AUTORELOAD_DEVICES = "autoreloadDevices"; // No longer used but should not be reused
   private static final String MPTIFF_METADATA_FILE = "MakeMetadataFileWithMultipageTiff";
   private static final String MPTIFF_SEPARATE_FILES_FOR_POSITIONS = "SplitXYPostionsInFilesMPTiff";
   private static final String HIDE_MDA_DISPLAY = "HideMDADisplay";
   private static final String FAST_STORAGE = "FastStorage"; // No longer used but should not be reused

   public int circularBufferSizeMB_;
   public boolean mpTiffMetadataFile_;
   public boolean mpTiffSeparateFilesForPositions_;

   public MMOptions() {
      setDefaultValues();
   }

   private void setDefaultValues() {
      boolean is64BitJVM =
         System.getProperty("sun.arch.data.model", "32").equals("64");
      circularBufferSizeMB_ = is64BitJVM ? 250 : 25;
      mpTiffMetadataFile_ = false;
      mpTiffSeparateFilesForPositions_ = true;
   }

   private Preferences getPrefNode() {
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      Preferences prefs = root.node(root.absolutePath() + "/" + PREF_DIR);
      return prefs;
   }

   public void saveSettings() {
      Preferences prefs = getPrefNode();

      prefs.putInt(BUFFSIZE_MB, circularBufferSizeMB_);
      prefs.putBoolean(MPTIFF_METADATA_FILE, mpTiffMetadataFile_);
      prefs.putBoolean(MPTIFF_SEPARATE_FILES_FOR_POSITIONS, mpTiffSeparateFilesForPositions_);
   }

   public void loadSettings() {
      Preferences prefs = getPrefNode();

      circularBufferSizeMB_ = prefs.getInt(BUFFSIZE_MB, circularBufferSizeMB_);
      mpTiffMetadataFile_ = prefs.getBoolean(MPTIFF_METADATA_FILE, mpTiffMetadataFile_);
      mpTiffSeparateFilesForPositions_ = prefs.getBoolean(MPTIFF_SEPARATE_FILES_FOR_POSITIONS, mpTiffSeparateFilesForPositions_);
   }

   public void resetSettings() throws BackingStoreException {
      getPrefNode().clear();
      setDefaultValues();
   }
}
