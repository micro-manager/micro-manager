package org.micromanager.imagedisplay.link;

import org.micromanager.api.data.Coords;
import org.micromanager.api.display.DisplaySettings;
import org.micromanager.api.display.DisplayWindow;

/**
 * The ImageCoordsLinker links a specific axis of the ImageCoords attribute.
 */
public class ImageCoordsLinker implements SettingsLinker {
   private String axis_;
   private DisplayWindow parent_;

   public ImageCoordsLinker(String axis, DisplayWindow parent) {
      axis_ = axis;
      parent_ = parent;
   }

   /**
    * We care about changes if the change is an ImageCoordsEvent and the axis
    * we are linked for is different.
    */
   @Override
   public boolean getShouldApplyChanges(DisplayWindow sourceWindow,
         DisplaySettingsEvent changeEvent) {
      if (changeEvent instanceof ImageCoordsEvent) {
         ImageCoordsEvent event = (ImageCoordsEvent) changeEvent;
         return event.getImageCoords().getPositionAt(axis_) != parent_.getDisplaySettings().getImageCoords().getPositionAt(axis_);
      }
      return false;
   }

   /**
    * Copy over just the position we are linked for.
    */
   @Override
   public void applyChange(DisplaySettingsEvent changeEvent) {
      ImageCoordsEvent event = (ImageCoordsEvent) changeEvent;
      DisplaySettings settings = parent_.getDisplaySettings();
      Coords curCoords = settings.getImageCoords();
      Coords newCoords = event.getImageCoords();
      curCoords = curCoords.copy().position(axis_, newCoords.getPositionAt(axis_)).build();
      settings = settings.copy().imageCoords(curCoords).build();
      parent_.setDisplaySettings(settings);
   }
}
