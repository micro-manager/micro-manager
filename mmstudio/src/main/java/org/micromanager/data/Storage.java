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

import java.io.IOException;
import java.util.List;

/**
 * Storages are responsible for providing image data to Datastores when
 * requested. Different Storages may have different mechanisms -- for example,
 * by storing images in a file, or in RAM.
 * In practice you are unlikely to need to implement your own Storage class,
 * and most of its methods are simply "backings" for similar methods in
 * Datastore.
 *
 * Note that the Storage interface does not expose any "setter" methods
 * (e.g. putImage(), setSummaryMetadata(), etc.). It is expected that any
 * read/write Storage listen for the relevant events published by the
 * Datastore instead.
 */
public interface Storage {
   /**
    * Freeze the Storage, preventing any changes to its contents.
    */
   void freeze() throws IOException;

   /**
    * Insert an Image into the Storage, so that it may be returned by later
    * getImage() calls.
    * @param image Image to be inserted.
    */
   void putImage(Image image) throws IOException;

   /**
    * Retrieve the Image located at the specified coordinates.
    * @param coords Coordinates specifying which image to retrieve
    * @return desired Image
    */
   Image getImage(Coords coords) throws IOException;

   /**
    * Returns whether or not an image exists at the specified coordinates.
    * @param coords Coordinates to test
    * @return True if an image exists at the coordinates, false otherwise.
    */
   boolean hasImage(Coords coords);

   /**
    * Return any Image, or null if there are no images. Only really useful if
    * you need a representative image to work with. No guarantees are made
    * about which image will be provided.
    * @return any Image or null if there are no images
    */
   Image getAnyImage();

   /**
    * Return an Iterable that provides access to all image coordinates in the
    * Storage, in arbitrary order.
    * @return Iterable that provides access to all image coordinates in the
    * Storage, in arbitrary order
    */
   Iterable<Coords> getUnorderedImageCoords();

   /**
    * Retrieve a list of all images whose Coords match the given incomplete
    * Coords instance. For example, providing a Coords of {@code <"z" = 9>}
    * would return all Images whose position along the "z" axis is 9 (note that
    * this means that Images with no defined position along that axis will
    * not be returned). The result may be empty.
    * @param coords Coordinates specifying images to match
    * @return List with matching Images
    */
   List<Image> getImagesMatching(Coords coords) throws IOException;

   /**
    * Retrieve a list of images with Coords identical to the given Coords instance,
    * except for the axes listed in the exception list.
    * For instance, given Coords {@code t=1,z=3>} and axes "c", the following
    * Coords would match: {@code <t=1, z=3>}, {@code <t=1, z=3, c=1>}, {@code <t=1, z=3, c=2>}.
    * @param coords coord looking for matching images
    * @param ignoreTheseAxes Axes to be ignored in the images collection when
    *                        looking for matches
    * @return matchig images
    * @throws IOException
    */
    List<Image> getImagesIgnoringAxes(Coords coords, String... ignoreTheseAxes) throws IOException;


   /**
    * Return the largest stored position along the specified axis. Will be -1
    * if no images have a position along that axis (or the axis is not present
    * in the Storage)
    *
    * @param axis axis of interest
    * @return Largest stored position along the specified axis or -1 when no images
    * are found on the given axis
    */
   int getMaxIndex(String axis);

   /**
    * Return a List of all axis names for Images we know about.
    * @return List of all axis names for Images we know about
    */
   List<String> getAxes();

   /**
    * Return a Coords that provides the maximum index along all available axes.
    * @return Coords that provides the maximum index along all available axes
    */
   Coords getMaxIndices();

   /**
    * Retrieve the SummaryMetadata associated with this dataset.
    * @return SummaryMetadata associated with this dataset
    */
   SummaryMetadata getSummaryMetadata();

   /**
    * Return the number of images in this dataset.
    * @return number of images in this dataset
    */
   int getNumImages();

   /**
    * Release any resources used by the Storage, for example open file
    * descriptors.
    */
   void close() throws IOException;
}