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
   private static final String CLOSE_ON_EXIT = "CloseOnExit";
   private static final String SKIP_CONFIG = "SkipSplashScreen";
   private static final String BUFFSIZE_MB = "bufsize_mb";
   private static final String DISPLAY_BACKGROUND = "displayBackground";
   private static final String STARTUP_SCRIPT_FILE = "startupScript";
   private static final String AUTORELOAD_DEVICES = "autoreloadDevices";
   private static final String ENABLE_DEVICE_DISCOVERY = "enableDeviceDiscovery";
   private static final String PREF_WINDOW_MAG = "windowMag";
   
   public boolean debugLogEnabled_ = false;
   public boolean doNotAskForConfigFile_ = false;
   public boolean closeOnExit_ = true;
   public int circularBufferSizeMB_ = 25;
   public String displayBackground_ = "Day";
   public String startupScript_ = "MMStartup.bsh";
   boolean autoreloadDevices_ = false;
   double windowMag_ = 1.0;
   //public boolean enableDeviceDiscovery_  = false;
   
   public void saveSettings() {
      Preferences root = Preferences.userNodeForPackage( this.getClass());
      Preferences prefs = root.node(root.absolutePath() + "/" + PREF_DIR);
      
      prefs.putBoolean(DEBUG_LOG, debugLogEnabled_);
      prefs.putBoolean(SKIP_CONFIG, doNotAskForConfigFile_);
      prefs.putBoolean(CLOSE_ON_EXIT, closeOnExit_);
      prefs.putBoolean(AUTORELOAD_DEVICES, autoreloadDevices_);
      prefs.putInt(BUFFSIZE_MB, circularBufferSizeMB_);
      prefs.put(DISPLAY_BACKGROUND, displayBackground_);
      prefs.put(STARTUP_SCRIPT_FILE, startupScript_);
      prefs.putDouble(PREF_WINDOW_MAG, windowMag_);
      // prefs.putBoolean(ENABLE_DEVICE_DISCOVERY, enableDeviceDiscovery_);
   }
   
   public void loadSettings() {
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      Preferences prefs = root.node(root.absolutePath() + "/" + PREF_DIR);
      
      debugLogEnabled_ = prefs.getBoolean(DEBUG_LOG, debugLogEnabled_);
      doNotAskForConfigFile_ = prefs.getBoolean(SKIP_CONFIG, doNotAskForConfigFile_);
      closeOnExit_ = prefs.getBoolean(CLOSE_ON_EXIT, closeOnExit_);
      circularBufferSizeMB_ = prefs.getInt(BUFFSIZE_MB, circularBufferSizeMB_);
      displayBackground_ = prefs.get(DISPLAY_BACKGROUND, displayBackground_);
      startupScript_ = prefs.get(STARTUP_SCRIPT_FILE, startupScript_);
      autoreloadDevices_ = prefs.getBoolean(AUTORELOAD_DEVICES, autoreloadDevices_);
      windowMag_ = prefs.getDouble(PREF_WINDOW_MAG, windowMag_);
      // enableDeviceDiscovery_ = prefs.getBoolean(ENABLE_DEVICE_DISCOVERY, enableDeviceDiscovery_);
   }
}
