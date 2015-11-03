///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display API
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

package org.micromanager.display;

/**
 * This class signifies that new display settings have been set for a
 * DataViewer, and provides access to those DisplaySettings.
 */
public class NewDisplaySettingsEvent {
   private DisplaySettings settings_;
   private DataViewer display_;

   /**
    * Create the event.
    * @param settings The new DisplaySettings.
    * @param display The DataViewer whose DisplaySettings have been updated.
    */
   public NewDisplaySettingsEvent(DisplaySettings settings, DataViewer display) {
      settings_ = settings;
      display_ = display;
   }

   /**
    * Provide access to the new DisplaySettings.
    */
   public DisplaySettings getDisplaySettings() {
      return settings_;
   }

   /**
    * Provide the DataViewer that the new DisplaySettings are for.
    */
   public DataViewer getDisplay() {
      return display_;
   }
}
