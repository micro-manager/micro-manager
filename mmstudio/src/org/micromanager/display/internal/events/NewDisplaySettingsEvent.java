///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
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

package org.micromanager.display.internal.events;

import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

/**
 * This class signifies that new display settings have been set for a 
 * Datastore.
 */
public class NewDisplaySettingsEvent implements org.micromanager.display.NewDisplaySettingsEvent {
   private DisplaySettings settings_;
   private DisplayWindow display_;
   public NewDisplaySettingsEvent(DisplaySettings settings, DisplayWindow display) {
      settings_ = settings;
      display_ = display;
   }

   @Override
   public DisplaySettings getDisplaySettings() {
      return settings_;
   }

   @Override
   public DisplayWindow getDisplay() {
      return display_;
   }
}
