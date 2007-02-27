///////////////////////////////////////////////////////////////////////////////
//FILE:          ShowFlags.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 29, 2006
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
//
package org.micromanager.utils;

import java.util.prefs.Preferences;

/**
 * Utility class for the PropertyBrowser to specify which devices
 * are currently visible.
 *
 */
public class ShowFlags {
   public boolean cameras_ = true;
   public boolean shutters_ = true;
   public boolean stages_ = true;
   public boolean state_ = true;
   public boolean other_ = true;
   
   private static final String SHOW_CAMERAS = "show_cameras";
   private static final String SHOW_SHUTTERS = "show_shutters";
   private static final String SHOW_STAGES = "show_stages";
   private static final String SHOW_STATE = "show_state";
   private static final String SHOW_OTHER = "show_other";
   
   public void load(Preferences prefs) {
      cameras_ = prefs.getBoolean(SHOW_CAMERAS, cameras_);
      shutters_ = prefs.getBoolean(SHOW_SHUTTERS, shutters_);
      stages_ = prefs.getBoolean(SHOW_STAGES, stages_);
      state_ = prefs.getBoolean(SHOW_STATE, state_);
      other_ = prefs.getBoolean(SHOW_OTHER, other_);
   }
   
   public void save(Preferences prefs) {
      prefs.putBoolean(SHOW_CAMERAS, cameras_);
      prefs.putBoolean(SHOW_SHUTTERS, shutters_);
      prefs.putBoolean(SHOW_STAGES, stages_);
      prefs.putBoolean(SHOW_STATE, state_);
      prefs.putBoolean(SHOW_OTHER, other_);
   }
 }
