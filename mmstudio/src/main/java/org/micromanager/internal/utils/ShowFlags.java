///////////////////////////////////////////////////////////////////////////////
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

package org.micromanager.internal.utils;

import org.micromanager.Studio;
import org.micromanager.UserProfile;

/**
 * Utility class for the PropertyBrowser to specify which devices
 * are currently visible.
 */
public final class ShowFlags {
   public boolean cameras_ = true;
   public boolean shutters_ = true;
   public boolean stages_ = true;
   public boolean state_ = true;
   public boolean other_ = true;
   public String searchFilter_ = "";
   
   private final Studio studio_;

   private static final String SHOW_CAMERAS = "show_cameras";
   private static final String SHOW_SHUTTERS = "show_shutters";
   private static final String SHOW_STAGES = "show_stages";
   private static final String SHOW_STATE = "show_state";
   private static final String SHOW_OTHER = "show_other";
   private static final String SEARCH_FILTER = "search_filter";
   
   public ShowFlags(Studio studio) {
      studio_ = studio;
   }

   public void load(Class<?> c) {
      UserProfile profile = studio_.getUserProfile();
      cameras_ = profile.getSettings(c).getBoolean(SHOW_CAMERAS, cameras_);
      shutters_ = profile.getSettings(c).getBoolean(SHOW_SHUTTERS, shutters_);
      stages_ = profile.getSettings(c).getBoolean(SHOW_STAGES, stages_);
      state_ = profile.getSettings(c).getBoolean(SHOW_STATE, state_);
      other_ = profile.getSettings(c).getBoolean(SHOW_OTHER, other_);
      searchFilter_ = profile.getSettings(c).getString(SEARCH_FILTER, searchFilter_);
   }

   public void save(Class<?> c) {
      UserProfile profile = studio_.getUserProfile();
      profile.getSettings(c).putBoolean(SHOW_CAMERAS, cameras_);
      profile.getSettings(c).putBoolean(SHOW_SHUTTERS, shutters_);
      profile.getSettings(c).putBoolean(SHOW_STAGES, stages_);
      profile.getSettings(c).putBoolean(SHOW_STATE, state_);
      profile.getSettings(c).putBoolean(SHOW_OTHER, other_);
      profile.getSettings(c).putString(SEARCH_FILTER, searchFilter_);
   }
}
