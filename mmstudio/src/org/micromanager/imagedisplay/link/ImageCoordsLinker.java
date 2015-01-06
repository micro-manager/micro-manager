package org.micromanager.imagedisplay.link;

import java.util.Arrays;
import java.util.List;

import org.micromanager.api.data.Coords;
import org.micromanager.api.display.DisplaySettings;
import org.micromanager.api.display.DisplayWindow;

import org.micromanager.data.DefaultCoords;

/**
 * The ImageCoordsLinker links a specific axis of the ImageCoords attribute.
 */
public class ImageCoordsLinker implements SettingsLinker {
   private String axis_;
   private DisplayWindow parent_;
   private static final List<Class<?>> relevantEvents_ = Arrays.asList(
         new Class<?>[] {ImageCoordsEvent.class});

   public ImageCoordsLinker(String axis, DisplayWindow parent) {
      axis_ = axis;
      parent_ = parent;
   }

   @Override
   public List<Class<?>> getRelevantEventClasses() {
      return relevantEvents_;
   }

   /**
    * We care about changes if the change is an ImageCoordsEvent and the axis
    * we are linked for is different.
    */
   @Override
   public boolean getShouldApplyChanges(DisplaySettingsEvent changeEvent) {
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

   /**
    * We care about the imageCoords, and specifically our axis for them.
    */
   @Override
   public int getID() {
      return DefaultCoords.class.hashCode() + axis_.hashCode();
   }
}
