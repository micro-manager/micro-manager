package org.micromanager.api.data;
/**
 * An Image is a single image plane with associated metadata. Functionally 
 * similar to TaggedImage, but because it's abstract we can change the
 * underlying implementation without breaking things.
 */
public interface Image {
   /**
    * Generate a new Image using the provided Object, which must be a Java
    * array of bytes or shorts.
    */
   abstract public Image(Object pixels, int width, int height, int bytesPerPixel);

   /**
    * Retrieve the Metadata for this Image.
    */
   abstract public Metadata getMetadata();

   /**
    * Retrieve the Coords for this Image.
    */
   abstract public Coords getCoords();
}
