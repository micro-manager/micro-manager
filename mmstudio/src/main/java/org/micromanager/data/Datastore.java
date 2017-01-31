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
import java.io.IOException;

/**
 * Datastores provide access to image data and metadata. You are not expected to
 * implement this interface; it is here to describe how you can interact with
 * Datastores created by Micro-Manager itself.
 */
public interface Datastore extends DataProvider {

   /**
    * Sets the source for data for this Datastore.
    * 
    * @param storage source of data to be used henceforth
    */
   public void setStorage(Storage storage);


   /**
    * Publish the given event on the Datastore's event bus.
    * 
    * @param obj Event that will be published on this Datastore's event bus
    */
   public void publishEvent(Object obj);


   /**
    * Insert an image into the Datastore. Posts a NewImageEvent to the event
    * bus.
    *
    * @param image Micro-Manager Image object
    * @throws DatastoreFrozenException if the freeze() method has been called.
    * @throws DatastoreRewriteException if an Image with the same coordinates
    *         already exists in the Datastore.
    * @throws IllegalArgumentException if the image's axes do not match the
    *         axes of images previously added to the Datastore. All images
    *         in a Datastore are required to have the same set of axes in
    *         their Coords objects.
    */
   public void putImage(Image image) throws DatastoreFrozenException, DatastoreRewriteException, IllegalArgumentException;


   /**
    * Set the SummaryMetadata. Posts a NewSummaryMetadataEvent to the event
    * bus. This method may only be called once for a given Datastore.
    *
    * @param metadata Object representing the summary metadata
    * @throws DatastoreFrozenException if the freeze() method has been called.
    * @throws DatastoreRewriteException if the Datastore already has
    *         SummaryMetadata.
    */
   public void setSummaryMetadata(SummaryMetadata metadata)
           throws DatastoreFrozenException, DatastoreRewriteException;

   /**
    * Create a Annotation, whose data is stored in the specified file alongside
    * the Datastore's own data. If a Annotation already exists for the
    * Datastore, using that filename, then it will be returned instead.
    * Alternatively, if the Datastore has been saved to disk, and the filename
    * parameter indicates a file that already exists, then it will be loaded
    * and a new Annotation with the loaded data will be returned.
    * This method will always succeed: either it creates a new "empty"
    * Annotation, returns an existing already-loaded Annotation, or it loads a
    * Annotation's data from disk and returns the result.
    * @param filename Filename to use to save and load the Annotation's data.
    * @throws IOException if there is a file for this Annotation already, but
    *         we are unable to load its contents.
    */
   public Annotation loadAnnotation(String filename) throws IOException;

   /**
    * Return true if there is an existing Annotation using the specified
    * filename. This does not necessarily indicate that a file using the
    * provided filename exists; the Annotation may not yet have saved data to
    * the file, but it has still "reserved" the file for use.
    * @param filename Filename the Annotation would use if it exists.
    * @return true if the Annotation exists.
    */
   public boolean hasAnnotation(String filename);

   /**
    * Create a new, empty Annotation, whose data is to be saved to disk at the
    * specified filename alongside the Datastore's own data.
    * @param filename Filename to use to save the Annotation's data.
    * @throws IllegalArgumentException if the filename is already claimed
    *         (either a file of that name exists on disk where the Datastore
    *         is saved, or we already have a Annotation in memory using that
    *         filename).
    */
   public Annotation createNewAnnotation(String filename) throws IllegalArgumentException;

   /**
    * Freeze the Datastore. Methods that modify its contents will throw
    * DatastoreFrozenExceptions, and a DatastoreFrozenEvent() will be posted to
    * any subscribers.
    */
   public void freeze();


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

}
