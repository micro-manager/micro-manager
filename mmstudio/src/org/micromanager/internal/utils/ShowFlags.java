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
//
// CVS:          $Id$
//
package org.micromanager.internal.utils;

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
   
   public void load(Class<?> c) {
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      cameras_ = profile.getBoolean(c, SHOW_CAMERAS, cameras_);
      shutters_ = profile.getBoolean(c, SHOW_SHUTTERS, shutters_);
      stages_ = profile.getBoolean(c, SHOW_STAGES, stages_);
      state_ = profile.getBoolean(c, SHOW_STATE, state_);
      other_ = profile.getBoolean(c, SHOW_OTHER, other_);
   }
   
   public void save(Class<?> c) {
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      profile.setBoolean(c, SHOW_CAMERAS, cameras_);
      profile.setBoolean(c, SHOW_SHUTTERS, shutters_);
      profile.setBoolean(c, SHOW_STAGES, stages_);
      profile.setBoolean(c, SHOW_STATE, state_);
      profile.setBoolean(c, SHOW_OTHER, other_);
   }
 }
