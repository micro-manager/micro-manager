package org.micromanager.data;


/**
 *
 *  An unchecked exception thrown when an image is put into a datastore that
 *  already has an image of a different size and that does not support images
 *  differing in size (currently non of the MM Datastores support images
 *  differing in size. Size is defined as width and height in pixels as well as
 *  bytes per pixel.
 */
public class ImagesDifferInSizeException extends UnsupportedOperationException {
   public static final String IMAGES_DIFFER_IN_SIZE =
           "The size of this image differs from previous image(s)";

   public ImagesDifferInSizeException() {
      super(IMAGES_DIFFER_IN_SIZE);
   }

}
