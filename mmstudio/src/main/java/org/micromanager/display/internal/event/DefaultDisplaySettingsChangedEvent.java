// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.display.internal.event;

import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplaySettingsChangedEvent;

/**
 * Standard implementation of {@code NewDisplaySettingsEvent}.
 * @author Chris Weisiger and Mark A. Tsuchida
 */
public final class DefaultDisplaySettingsChangedEvent
      implements DisplaySettingsChangedEvent
{
   private final DisplaySettings newSettings_;
   private final DisplaySettings oldSettings_;
   private final DataViewer viewer_;

   public static DefaultDisplaySettingsChangedEvent create(DataViewer viewer,
         DisplaySettings oldSettings, DisplaySettings newSettings)
   {
      return new DefaultDisplaySettingsChangedEvent(viewer,
            oldSettings, newSettings);
   }

   private DefaultDisplaySettingsChangedEvent(DataViewer viewer,
         DisplaySettings oldSettings, DisplaySettings newSettings)
   {
      viewer_ = viewer;
      oldSettings_ = oldSettings;
      newSettings_ = newSettings;
   }

   /**
    * Get the new display settings.
    * @return the new display settings
    */
   @Override
   public DisplaySettings getDisplaySettings() {
      return newSettings_;
   }

   /**
    * Get the display settings before the change being handled.
    *
    * Comparing this with the return value of {@code getDisplaySettings} can
    * reveal what exactly is to be changed.
    *
    * @return the previous display settings
    */
   @Override
   public DisplaySettings getPreviousDisplaySettings() {
      return oldSettings_;
   }

   /**
    * Get the data viewer.
    * @return the data viewer for which the new display settings is to be
    * applied
    */
   @Override
   public DataViewer getDataViewer() {
      return viewer_;
   }

   /**
    * Old name for {@code getDataViewer}.
    * @return the data viewer for which the new display settings is to be
    * applied
    * @deprecated use {@code getDataViewer} instead
    */
   @Override
   @Deprecated
   public DataViewer getDisplay() {
      return viewer_;
   }
}