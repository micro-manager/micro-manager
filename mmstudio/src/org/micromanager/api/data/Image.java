package org.micromanager.api.data;

import net.imglib2.meta.ImgPlus;

/**
 * An Image is a single image plane with associated metadata. Functionally 
 * similar to TaggedImage, but with more rigidly-defined metadata and 
 * dataset positioning information.
 */
public interface Image {
   /**
    * Retrieve the ImgPlus that provides access to the image's pixel data.
    */
   public ImgPlus getPixels();
   /**
    * Retrieve the Metadata for this Image.
    */
   public Metadata getMetadata();

   /**
    * Retrieve the Coords for this Image.
    */
   public Coords getCoords();
}
