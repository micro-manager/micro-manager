package org.micromanager.api.data;

/**
 * This class signifies that an image has been added to a Datastore.
 */
public class NewImageEvent {
   public Image image_;
   public Coords coords_;
   
   public NewImageEvent(Image image, Coords coords) {
      image_ = image;
      coords_ = coords;
   }

   public Image getImage() {
      return image_;
   }

   public Coords getCoords() {
      return coords_;
   }
}
