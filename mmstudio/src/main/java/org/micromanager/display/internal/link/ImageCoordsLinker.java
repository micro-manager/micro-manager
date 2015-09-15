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
import org.micromanager.display.internal.ScrollerPanel;

import org.micromanager.data.internal.DefaultCoords;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * The ImageCoordsLinker links a specific axis of the displayed image
 * coordinates. This is a bit different from the usual SettingsLinker as it
 * operates through an attribute that is not part of the DisplaySettings.
 */
public class ImageCoordsLinker extends SettingsLinker {
   private String axis_;
   private ScrollerPanel scrollerPanel_;
   private static final List<Class<?>> RELEVANT_EVENTS = Arrays.asList(
         new Class<?>[] {ImageCoordsEvent.class});

   public ImageCoordsLinker(String axis, DisplayWindow parent,
         ScrollerPanel scrollerPanel) {
      super(parent, RELEVANT_EVENTS);
      axis_ = axis;
      scrollerPanel_ = scrollerPanel;
      addToSiblings();
   }

   @Override
   public String getProperty() {
      return axis_ + " index";
   }

   /**
    * We care about changes if the change is an ImageCoordsEvent and the axis
    * we are linked for is different.
    */
   @Override
   public boolean getShouldApplyChanges(DisplayWindow source,
         DisplaySettingsEvent changeEvent) {
      ImageCoordsEvent event = (ImageCoordsEvent) changeEvent;
      int newPos = event.getImageCoords().getIndex(axis_);
      int oldPos = scrollerPanel_.getIndex(axis_);
      return oldPos != newPos;
   }

   /**
    * Copy over just the index we are linked for.
    */
   @Override
   public void applyChange(DisplayWindow source,
         DisplaySettingsEvent changeEvent) {
      ImageCoordsEvent event = (ImageCoordsEvent) changeEvent;
      Coords curCoords = parent_.getDisplayedImages().get(0).getCoords();
      Coords newCoords = event.getImageCoords();
      curCoords = curCoords.copy().index(axis_, newCoords.getIndex(axis_)).build();
      parent_.setDisplayedImageTo(curCoords);
   }


   /**
    * HACK: We override this method because it relies on copySettings(), which
    * doesn't do anything meaningful for us.
    */
   @Override
   public void pushState(DisplayWindow display) {
      // Get the coordinates of the displayed image in the target display,
      // then tweak them along our axis.
      Coords coords = display.getDisplayedImages().get(0).getCoords();
      // Manually override our own position in the Coords, as it may not match
      // what's in the above object (e.g. if we're linking the channel index).
      coords = coords.copy().index(axis_, scrollerPanel_.getIndex(axis_)).build();
      display.setDisplayedImageTo(coords);
   }

   @Override
   public DisplaySettings copySettings(DisplayWindow sourceDisplay,
         DisplaySettings source, DisplaySettings dest) {
      ReportingUtils.logError("copySettings() of ImageCoordsLinker should never be called.");
      return null;
   }

   /**
    * We care about the imageCoords, and specifically our axis for them.
    */
   @Override
   public int getID() {
      return DefaultCoords.class.hashCode() + axis_.hashCode();
   }
}
