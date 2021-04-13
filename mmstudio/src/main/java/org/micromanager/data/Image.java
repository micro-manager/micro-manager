///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
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

package org.micromanager.data;

/**
 * A 2-dimensional pixel plane with associated multi-dimensional coordinates
 * and metadata.
 * <p>
 * Image objects are immutable (cannot be modified once created). This makes it
 * easier to pass around images between multple threads or data structures,
 * since you don't need to worry about another process modifying it.
 * <p>
 * In order to "modify" an Image, you will need to create a "copy", but
 * creating a copy is very efficient because the actual pixels and metadata
 * (which don't change) can be shared and do not need to be copied.
 * <p>
 * To create an Image, (TODO document).
 * <p>
 * To access the pixel data of an Image, (TODO document).
 */
public interface Image {
   /**
    * Generate a copy of this Image, except with different coordinates.
    * <p>
    * Because Image objects are immutable, the "copy" can safely share the
    * pixel data with the original. So this is an efficient operation.
    *
    * @param coords Coordinates at which to place the new image.
    * @return The copied image
    * @see #copyWith
    */
   Image copyAtCoords(Coords coords);

   /**
    * Generate a copy of this Image, except with different metadata.
    * <p>
    * Because Image objects are immutable, the "copy" can safely share the
    * pixel data with the original. So this is an efficient operation.
    *
    * @param metadata The new metadata to use for the copy
    * @return The copied image
    * @see #copyWith
    */
   Image copyWithMetadata(Metadata metadata);

   /**
    * Generate a copy of this Image, except with different coordinates and
    * metadata.
    * <p>
    * Because Image objects are immutable, the "copy" can safely share the
    * pixel data with the original. So this is an efficient operation.
    *
    * @param coords Coordinates at which to place the new image.
    * @param metadata The new metadata to use for the copy
    * @return The copied image
    * @see #copyAtCoords
    * @see #copyWithMetadata
    */
   Image copyWith(Coords coords, Metadata metadata);

   /**
    * Retrieve the intensity of the pixel at the specified position.
    * <p>
    * This method is intended for getting a single pixel value (e.g. to see the
    * value at the mouse cursor).
    * <p>
    * Equivalent to calling {@code getComponentIntensityAt(x, y, 0)}. Use this
    * method only in contexts where you are sure you will never handle
    * non-grayscale images.
    *
    * @param x X coordinate at which to retrieve image data
    * @param y Y coordinate at which to retrieve image data
    * @return intensity of the image at the specified coordinates
    * @throws IndexOutOfBoundsException
    * @see #getComponentIntensityAt
    * @see #getComponentIntensitiesAt
    */
   long getIntensityAt(int x, int y);

   /**
    * Get the intensity of a component of the pixel at the specified position.
    *
    * @param x X coordinate at which to retrieve image data
    * @param y Y coordinate at which to retrieve image data
    * @param component The component number to retrieve intensity for, starting
    * from 0.
    * @return intensity of the image at the specified coordinates, for the
    * given component
    * @throws IndexOutOfBoundsException
    * @see #getComponentIntensitiesAt
    */
   long getComponentIntensityAt(int x, int y, int component);

   /**
    *
    * @param x Pixel location along x axis.
    * @param y Pixel location along y axis.
    * @return Intensity of the component.
    * @throws IndexOutOfBoundsException
    * @see #getComponentIntensityAt
    */
   long[] getComponentIntensitiesAt(int x, int y);

   /**
    * Return the ImageJ pixel type enum constant.
    *
    * The constants are defined in {@link ij.ImagePlus}: {@code GRAY8},
    * {@code GRAY16}, {@code COLOR_RGB}.
    * @return ImageJ pixel type.
    * @throws UnsupportedOperationException if the image format is not
    * supported by ImageJ1
    * @deprecated Unclear what should be used instead. Do not delete until this is figure out.
    */
   @Deprecated
   int getImageJPixelType();

   /**
    * Generate a string describing the value(s) of the pixel at the specified
    * location.
    * <p>
    * The string will be a plain number for single-component images, and an
    * "(A, B, C)"-formatted string for multi-component images. (The string
    * format may change in the future.)
    *
    * @param x X coordinate at which to retrieve image data
    * @param y Y coordinate at which to retrieve image data
    * @return A string describing the pixel intensity/intensities at the given
    * coordinates.
    */
   String getIntensityStringAt(int x, int y);

   /**
    * Retrieve the Metadata for this Image.
    * @return The image metadata.
    */
   Metadata getMetadata();

   /**
    * Retrieve the Coords of this Image.
    * @return The image coordinates.
    */
   Coords getCoords();

   /**
    * Get the width of this image in pixels.
    * @return The width of the image in pixels.
    */
   int getWidth();

   /**
    * Get the height of this image in pixels.
    * @return The height of the image in pixels.
    */
   int getHeight();

   /**
    * Return the number of bytes used to represent each pixel of this image.
    * <p>
    * Note that this does not necessarily match 8 times the bit depth of the
    * image, since images may have lower bit depth than the samples used.
    * <p>
    * For multi-component images, the bytes per pixel may be greater than the
    * number of components times the bytes per component, if padding is used.
    *
    * @return size of pixel in bytes
    */
   int getBytesPerPixel();

   /**
    * Return the number of bytes used to represent each pixel component of this
    * image.
    *
    * @return size of component in bytes
    */
   int getBytesPerComponent();

   /**
    * Get the number of components per pixel in this image.
    * <p>
    * The return value is 1 for grayscale images and 3 for RGB images.
    *
    * @return number of sample components
    */
   int getNumComponents();

   /**
    * Returns the internal pixel data of this image.
    *
    * The returned value may or may not be a copy of the internal pixel data.
    * <p>
    * <strong>Warning</strong>: Do not depend on the type of the object
    * returned. It may change in the future. Also, <strong>never modify the
    * returned object!</strong>
    *
    * @return An array of pixel values for the image data
    * @see #getRawPixelsCopy
    */
   Object getRawPixels();

   /**
    * Returns a copy of the raw pixel data that {@code getRawPixels} returns.
    * <p>
    * Use if you need to modify the pixel data.
    * <p>
    * <strong>Warning</strong>: Do not depend on the type of the object
    * returned. It may change in the future.
    *
    * @return An array of pixel values for the image data, copied from the
    * original data.
    */
   Object getRawPixelsCopy();

   /**
    * Return a copy of the raw pixel data for the specified component.
    * <p>
    * As {@code getRawPixelsCopy}, but will split out the specified component
    * for multi-component images. Use of this method could potentially impair
    * performance as the image data must be de-interleaved. Calling this on a
    * single-component image with an argument of 0 is equivalent to calling
    * {@code getRawPixelsCopy}.
    *
    * @param component The component number, starting from 0
    * @return An array of pixel values for the specified component
    * @see #getRawPixelsCopy
    */
   Object getRawPixelsForComponent(int component);

}