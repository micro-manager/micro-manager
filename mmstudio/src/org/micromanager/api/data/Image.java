package org.micromanager.api.data;

import net.imglib2.meta.ImgPlus;

import mmcorej.TaggedImage;

/**
 * An Image is a single image plane with associated metadata. Functionally 
 * similar to TaggedImage, but with more rigidly-defined metadata and 
 * dataset positioning information.
 * You are not expected to implement this interface; it is here to describe how
 * you can interact with Images created by Micro-Manager itself.
 */
public interface Image {
   /**
    * Return an ImgPlus that allows for manipulation of the pixels in the image.
    */
   public ImgPlus getImgPlus();

   /**
    * Return a reference to whatever entity stores the actual pixel data for
    * this Image. Is most likely a byte[] or short[] but could be of any
    * primitive type.
    */
   public Object getRawPixels();

   /**
    * As getRawPixels(), but will split out the specified component for
    * multi-component images. This could potentially impair performance as
    * the image data must be manually de-interleaved. Calling this with an
    * argument of 0 should be equivalent to calling getRawPixels() for
    * single-component images, except that a copy of the pixels will be made.
    */
   public Object getRawPixelsForComponent(int component);

   /**
    * Generate a copy of this Image, except that its Coords object is at a
    * different location, as specified.
    */
   public Image copyAtCoords(Coords coords);

   /**
    * Generate a copy of this Image, except that its Metadata object uses
    * the provided Metadata.
    */
   public Image copyWithMetadata(Metadata metadata);

   /**
    * Generate a copy of this Image, except that its Coords object is at the
    * provided location, and it uses the provided Metadata.
    */
   public Image copyWith(Coords coords, Metadata metadata);

   /**
    * Retrieve the intensity of the pixel at the specified position. Not
    * guaranteed to work well for all image types (e.g. RGB images will still
    * get only a single value, which may be an odd summation of the values
    * of the different components).
    */
   public long getIntensityAt(int x, int y);

   /**
    * For multi-component (e.g. RGB) images, extract the value of the specified
    * component at the given pixel location. Not guaranteed to make any kind of
    * sense if called on single-component images with a nonzero "component"
    * value.
    */
   public long getComponentIntensityAt(int x, int y, int component);

   /**
    * Generate a string describing the value(s) of the pixel at the specified
    * location. The string will be a plain number for single-component images,
    * and an "[A/B/C]"-formatted string for multi-component images.
    */
   public String getIntensityStringAt(int x, int y);

   /**
    * Retrieve the Metadata for this Image.
    */
   public Metadata getMetadata();

   /**
    * Retrieve the Coords for this Image.
    */
   public Coords getCoords();

   /**
    * Get the width of the image in pixels.
    */
   public int getWidth();

   /**
    * Get the height of the image in pixels.
    */
   public int getHeight();

   /**
    * Get the number of bytes used to represent each pixel in the raw pixel
    * data.
    */
   public int getBytesPerPixel();

   /**
    * Get the number of components (e.g. for RGB images) in each pixel in the
    * raw pixel data.
    */
   public int getNumComponents();

   /**
    * For legacy support only: convert to TaggedImage;
    */
   public TaggedImage legacyToTaggedImage();
}
