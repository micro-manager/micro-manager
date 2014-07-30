package org.micromanager.api.data;

/**
 * This class signifies that an image has been added to a Datastore.
 */
public interface NewImageEvent {
   public Image getImage();
   public Coords getCoords();
}
