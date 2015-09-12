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

import java.awt.Window;
import java.io.Closeable;
import java.util.List;

/**
 * Datastores provide access to image data and metadata. You are not expected to
 * implement this interface; it is here to describe how you can interact with
 * Datastores created by Micro-Manager itself.
 */
public interface Datastore extends Closeable {

   /**
    * Sets the source for data for this Datastore.
    * 
    * @param storage source of data to be used henceforth
    */
   public void setStorage(Storage storage);

   /**
    * Subscribe the provided object to the Datastore's event bus.
    * 
    * @param obj Object that will receive updates through this Datastore's 
    * event bus
    */
   public void registerForEvents(Object obj);

   /**
    * Unsubscribe the provided object from the Datastore's event bus.
    * 
    * @param obj Object that no longer will receive updates through this 
    * Datastore's event bus
    */
   public void unregisterForEvents(Object obj);

   /**
    * Publish the given event on the Datastore's event bus.
    * 
    * @param obj Event that will be published on this Datastore's event bus
    */
   public void publishEvent(Object obj);

   /**
    * Retrieve the image at the specified coordinates. Will be null if no
    * Storage has been provided yet.
    * @param coords Object specifying the location of the image in this dataset
    * @return Micro-Manager Image object
    */
   public Image getImage(Coords coords);

   /**
    * Retrieve an image of arbitrary coordinates, or null if there are no images
    * (or if no Storage has been provided yet). No guarantees are made about the
    * position of the provided image.
    * 
    * @return Micro-Manager Image object
    */
   public Image getAnyImage();

   /**
    * Retrieve a list of all images whose Coords match the given incomplete
    * Coords instance. For example, providing a Coords of {@code <"z" = 9>}
    * would return all Images whose position along the "z" axis is 9. May be
    * empty. Will be null if no Storage has been provided yet.
    * 
    * @param coords Object specifying the location of the image in this dataset
    * @return List with Micro-Manager Image objects
    */
   public List<Image> getImagesMatching(Coords coords);

   /**
    * Provide an object that you can iterate over to get the Coords of all
    * images in the Datastore, and which you can then use with getImage() to get
    * the specific Images. The Coords are not guaranteed to be in any specific
    * order.
    * 
    * @return object that you can iterate over to get the Coords of all
    * images in the Datastore. 
    */
   public Iterable<Coords> getUnorderedImageCoords();

   /**
    * Insert an image into the Datastore. Posts a NewImageEvent to the event
    * bus.
    *
    * @param image Micro-Manager Image object
    * @throws DatastoreFrozenException if the freeze() method has been called.
    * @throws IllegalArgumentException if the image's axes do not match the
    *         axes of images previously added to the Datastore. All images
    *         in a Datastore are required to have the same set of axes in
    *         their Coords objects.
    */
   public void putImage(Image image) throws DatastoreFrozenException, IllegalArgumentException;

   /**
    * Return the maximum Image position along the specified access that this
    * Datastore has seen so far. Will be null if no Storage has been provided
    * yet.
    * 
    * @param axis name of the Axis (e.g. Coords.Z)
    * @return Maximum Image position along the given axis or null
    */
   public Integer getMaxIndex(String axis);

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
   public Integer getAxisLength(String axis);

   /**
    * Return a List of all axis names for Images in the store. Will be null if
    * no Storage has been provided yet.
    * 
    * @return List with all axis names used in this data store
    */
   public List<String> getAxes();

   /**
    * Return a Coord that represents the maximum possible index along all
    * available axes. Will be null if no Storage has been provided yet.
    * 
    * @return Coords object that represents the maximum possible index along
    * all available axis
    */
   public Coords getMaxIndices();

   /**
    * Retrieve the summary metadata for the datastore. Will be null if no
    * Storage has been provided yet. If the Storage has a null SummaryMetadata,
    * then an empty SummaryMetadata (which is not null but returns null for
    * all of its fields) will be provided instead.
    * 
    * @return Object giving access to the summary metadata
    */
   public SummaryMetadata getSummaryMetadata();

   /**
    * Set the SummaryMetadata. Posts a NewSummaryMetadataEvent to the event bus.
    *
    * @param metadata Object representing the summary metadata
    * @throws DatastoreFrozenException if the freeze() method has been called.
    */
   public void setSummaryMetadata(SummaryMetadata metadata) 
           throws DatastoreFrozenException;

   /**
    * Freeze the Datastore. Methods that modify its contents will throw
    * DatastoreFrozenExceptions, and a DatastoreFrozenEvent() will be posted to
    * any subscribers.
    */
   public void freeze();

   /**
    * Returns whether or not the Datastore has been frozen.
    * 
    * @return true if the data store is currently frozen
    */
   public boolean getIsFrozen();

   /**
    * Close the Datastore, removing all references to it from MicroManager's
    * code. This will in turn cause the resources used by the Datastore (e.g.
    * RAM storage) to be released, assuming that there are no references to the
    * Datastore in other parts of the program (e.g. in plugins or Beanshell
    * scripts). Displays attached to the Datastore will automatically be closed,
    * with no prompt for data to be saved.
    */
   @Override
   public void close();

   /**
    * Tell the Datastore where on disk it is being saved to. This will post a
    * DatastoreSavePathEvent to the datastore's EventBus.
    * 
    * @param path The location on disk at which the data has been stored.
    */
   public void setSavePath(String path);

   /**
    * Return the path for where the data has been saved to disk. Will be null
    * if the data has not been saved yet.
    * 
    * @return The directory path to the saved data.
    */
   public String getSavePath();

   /**
    * These are the valid inputs to the save() methods. 
    * SINGLEPLANE_TIFF_SERIES saves each individual 2D image plane as a 
    * separate file; 
    * MULTIPAGE_TIFF saves all images together in a single file (up to a 
    * limit of 4GB/file, after which point the images will be split into 
    * a second file).
    * 
    * This enum will likely be expanded in the future
    */
   public enum SaveMode {
      SINGLEPLANE_TIFF_SERIES,
      MULTIPAGE_TIFF
   }

   /**
    * Prompts the user for a location to save data to, then saves data there
    * according to the mode parameter. After this method, getSavePath() will
    * return the selected save path, unless the user cancels when prompted for
    * directory/filename or there is an error while saving. Includes a file
    * format selector in the save dialog, which defaults to the last format
    * the user used.
    *
    * @param window Window  on top of which to display the dialog prompt; 
    *        may be null.
    * @return TODO WHAT DOES THE RETURN BOOLEAN MEAN?
    */
   public boolean save(Window window);

   /**
    * As above, except uses the provided path (the last element of which is
    * assumed to be a filename), instead of prompting the user. After this
    * method, getSavePath() will return the input save path, unless there is an
    * error while saving.
    *
    * @param mode
    * @param path
    * @return true if saving succeeded, false otherwise.
    */
   public boolean save(SaveMode mode, String path);

   /**
    * Returns the total number of Images in the Datastore. Returns -1 if no
    * Storage has been provided yet.
    * 
    * @return total number of Images in the Datastore
    */
   public int getNumImages();
}
