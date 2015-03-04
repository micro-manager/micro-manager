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

package org.micromanager.display.internal.link;

import java.util.Arrays;
import java.util.List;

import org.micromanager.data.Coords;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

import org.micromanager.data.internal.DefaultCoords;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * The ImageCoordsLinker links a specific axis of the ImageCoords attribute.
 */
public class ImageCoordsLinker extends SettingsLinker {
   private String axis_;
   private static final List<Class<?>> RELEVANT_EVENTS = Arrays.asList(
         new Class<?>[] {ImageCoordsEvent.class});

   public ImageCoordsLinker(String axis, DisplayWindow parent) {
      super(parent, RELEVANT_EVENTS);
      axis_ = axis;
   }

   /**
    * We care about changes if the change is an ImageCoordsEvent and the axis
    * we are linked for is different.
    */
   @Override
   public boolean getShouldApplyChanges(DisplayWindow source,
         DisplaySettingsEvent changeEvent) {
      ImageCoordsEvent event = (ImageCoordsEvent) changeEvent;
      if (parent_.getDisplaySettings().getImageCoords() == null) {
         // We don't have image coordinates, so we must de facto differ.
         return true;
      }
      int newPos = event.getImageCoords().getIndex(axis_);
      int oldPos = parent_.getDisplaySettings().getImageCoords().getIndex(axis_);
      return oldPos != newPos;
   }

   /**
    * Copy over just the index we are linked for.
    */
   @Override
   public void applyChange(DisplayWindow source,
         DisplaySettingsEvent changeEvent) {
      ImageCoordsEvent event = (ImageCoordsEvent) changeEvent;
      DisplaySettings settings = parent_.getDisplaySettings();
      Coords curCoords = settings.getImageCoords();
      Coords newCoords = event.getImageCoords();
      if (curCoords == null) {
         // Gotta have some valid coords to start; just copy the new ones.
         curCoords = newCoords;
      }
      curCoords = curCoords.copy().index(axis_, newCoords.getIndex(axis_)).build();
      settings = settings.copy().imageCoords(curCoords).build();
      parent_.setDisplaySettings(settings);
   }

   @Override
   public DisplaySettings copySettings(DisplayWindow sourceDisplay,
         DisplaySettings source, DisplaySettings dest) {
      Coords sourceCoords = source.getImageCoords();
      Coords destCoords = dest.getImageCoords();
      if (destCoords == null) {
         destCoords = sourceCoords;
      }
      if (sourceCoords == null ||
            sourceCoords.getIndex(axis_) == destCoords.getIndex(axis_)) {
         // Nothing can/should be done.
         return dest;
      }
      destCoords = destCoords.copy().index(axis_, sourceCoords.getIndex(axis_)).build();
      return dest.copy().imageCoords(destCoords).build();
   }

   /**
    * We care about the imageCoords, and specifically our axis for them.
    */
   @Override
   public int getID() {
      return DefaultCoords.class.hashCode() + axis_.hashCode();
   }
}
