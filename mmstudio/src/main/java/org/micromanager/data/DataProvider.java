// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

import java.io.Closeable;
import java.util.List;

/**
 * Read-only interface to multi-dimensional image data.
 *
 * @author Chris Weisiger and Mark A. Tsuchida
 */
public interface DataProvider extends Closeable {

   /**
    * Close the Datastore, removing all references to it from MicroManager's
    * code. This will in turn cause the resources used by the Datastore (e.g.
    * RAM storage and file descriptors) to be released, assuming that there are
    * no references to the Datastore in other parts of the program (e.g. in
    * plugins or Beanshell scripts). Displays attached to the Datastore will
    * automatically be closed, with no prompt for data to be saved.
    */
   void close();

   /**
    * Retrieve an image of arbitrary coordinates, or null if there are no images
    * (or if no Storage has been provided yet). No guarantees are made about the
    * position of the provided image.
    *
    * @return Micro-Manager Image object
    */
   Image getAnyImage();

   /**
    * Return a List of all axis names for Images in the store. Will be null if
    * no Storage has been provided yet.
    *
    * @return List with all axis names used in this data store
    */
   List<String> getAxes();

   /**
    * Return the number of valid positions along the specified axis. There is no
    * guarantee that this is equal to the number of occupied positions along
    * that axis. For example, a "sparse timeseries" could have timepoints 0, 10,
    * 20, and 30; this function would return 31. Is always equal to
    * getMaxIndex(axis) + 1, and thus only exists as a convenience function.
    *
    * @param axis name of the axis (e.g. Coords.Z)
    * @return Number of valid positions along the axis
    */
   Integer getAxisLength(String axis);

   /**
    * Retrieve the image at the specified coordinates. Will be null if no
    * Storage has been provided yet.
    * @param coords Object specifying the location of the image in this dataset
    * @return Micro-Manager Image object
    */
   Image getImage(Coords coords);

   /**
    * Retrieve a list of all images whose Coords match the given incomplete
    * Coords instance. For example, providing a Coords of {@code <"z" = 9>}
    * would return all Images whose position along the "z" axis is 9. May be
    * empty. Will be null if no Storage has been provided yet.
    *
    * @param coords Object specifying the location of the image in this dataset
    * @return List with Micro-Manager Image objects
    */
   List<Image> getImagesMatching(Coords coords);

   /**
    * Returns whether or not the Datastore has been frozen.
    *
    * @return true if the data store is currently frozen
    */
   boolean getIsFrozen();

   /**
    * Return the maximum Image position along the specified access that this
    * Datastore has seen so far. Will be null if no Storage has been provided
    * yet.
    *
    * @param axis name of the Axis (e.g. Coords.Z)
    * @return Maximum Image position along the given axis or null
    */
   Integer getMaxIndex(String axis);

   /**
    * Return a Coord that represents the maximum possible index along all
    * available axes. Will be null if no Storage has been provided yet.
    *
    * @return Coords object that represents the maximum possible index along
    * all available axis
    */
   Coords getMaxIndices();

   /**
    * Returns the total number of Images in the Datastore. Returns -1 if no
    * Storage has been provided yet.
    *
    * @return total number of Images in the Datastore
    */
   int getNumImages();

   /**
    * Retrieve the summary metadata for the datastore. Will be null if no
    * Storage has been provided yet. If the Storage has a null SummaryMetadata,
    * then an empty SummaryMetadata (which is not null but returns null for
    * all of its fields) will be provided instead.
    *
    * @return Object giving access to the summary metadata
    */
   SummaryMetadata getSummaryMetadata();

   /**
    * Provide an object that you can iterate over to get the Coords of all
    * images in the Datastore, and which you can then use with getImage() to get
    * the specific Images. The Coords are not guaranteed to be in any specific
    * order.
    *
    * @return object that you can iterate over to get the Coords of all
    * images in the Datastore.
    */
   Iterable<Coords> getUnorderedImageCoords();

   /**
    * Returns whether or not an image exists at the specified coordinates.
    * @param coords Coordinates to test
    * @return True if the Datastore has valid Storage and an image exists at
    *         the coordinates, false otherwise.
    */
   boolean hasImage(Coords coords);

   /**
    * Subscribe the provided object to the Datastore's event bus.
    *
    * @param obj Object that will receive updates through this Datastore's
    * event bus
    */
   void registerForEvents(Object obj);

   /**
    * Unsubscribe the provided object from the Datastore's event bus.
    *
    * @param obj Object that no longer will receive updates through this
    * Datastore's event bus
    */
   void unregisterForEvents(Object obj);

}
