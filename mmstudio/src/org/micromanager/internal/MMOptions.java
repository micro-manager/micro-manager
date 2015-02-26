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


   public MMOptions() {
      setDefaultValues();
   }

   private void setDefaultValues() {
   }

   private Preferences getPrefNode() {
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      Preferences prefs = root.node(root.absolutePath() + "/" + PREF_DIR);
      return prefs;
   }

   public void saveSettings() {
      Preferences prefs = getPrefNode();

   }

   public void loadSettings() {
      Preferences prefs = getPrefNode();

   }

   public void resetSettings() throws BackingStoreException {
      getPrefNode().clear();
      setDefaultValues();
   }
}
