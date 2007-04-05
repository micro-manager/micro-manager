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

import java.util.prefs.Preferences;

/**
 * Options data for MMStudio.
 */
public class MMOptions {
   private static final String DEBUG_LOG = "DebugLog";
   private static final String PREF_DIR = "MMOptions";
   private static final String MT_ACQ = "MultiThread";
   
   public boolean debugLogEnabled = false;
   public boolean multiThreadedAcqEnabled = false;
   
   public void saveSettings() {
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      Preferences prefs = root.node(root.absolutePath() + "/" + PREF_DIR);
      
      prefs.putBoolean(DEBUG_LOG, debugLogEnabled);
      prefs.putBoolean(MT_ACQ, multiThreadedAcqEnabled);
   }
   
   public void loadSettings() {
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      Preferences prefs = root.node(root.absolutePath() + "/" + PREF_DIR);
      
      debugLogEnabled = prefs.getBoolean(DEBUG_LOG, debugLogEnabled);
      multiThreadedAcqEnabled = prefs.getBoolean(MT_ACQ, multiThreadedAcqEnabled);
   }
}
