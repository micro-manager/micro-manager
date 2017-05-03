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

import java.awt.Component;
import java.beans.ExceptionListener;
import java.io.IOException;

/**
 * Datastores provide read/write access to images and metadata.
 *
 * <strong>Important</strong>: Custom Datastore implementations are not
 * supported.
 */
public interface Datastore extends DataProvider {
   /**
    * Registers an object that will be notified if there is an error during
    * file input and output.
    * <p>
    * The listener is notified of IOException and other errors encountered
    * while loading and saving images. It is not called for parameter errors,
    * which are reported as unchecked exceptions to the method caller.
    * <p>
    * When an error occurs while storing an image (usually a file system error,
    * such as insufficient disk space), the storage may be in an inconsistent
    * state. Therefore it is advisable for image saving code to register an
    * exception listener to handle the error and stop storing.
    * <p>
    * When an error occurs while retrieving an image (usually either a file
    * system error, or a file in an unrecognized format), the Datastore will
    * behave as if the requested data doesn't exist (e.g. by returning null).
    * But it is advisable for image reading code to register an exception to
    * notify the user.
    * <p>
    * If the exception listener throws an (unchecked) exception, it will be
    * propagated back to the caller. This can be used to handle input/output
    * errors in a traditional way by catching exceptions at the call site.
    * However, this method should be avoided when the Datastore is shared with
    * other objects.
    *
    * @param listener an exception listener
    * @see #removeExceptionListener
    */
   void addExceptionListener(ExceptionListener listener);

   /**
    * Unregister an exception listener.
    * @param listener an exception listener
    * @see #addExceptionListener
    */
   void removeExceptionListener(ExceptionListener listener);

   /**
    * Sets the data storage implementation for this Datastore.
    * 
    * @param storage source of data to be used henceforth
    */
   void setStorage(Storage storage);


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
   void putImage(Image image)
         throws DatastoreFrozenException, DatastoreRewriteException;


   /**
    * Set the SummaryMetadata. Posts a NewSummaryMetadataEvent to the event
    * bus. This method may only be called once for a given Datastore.
    *
    * @param metadata Object representing the summary metadata
    * @throws DatastoreFrozenException if the freeze() method has been called.
    * @throws DatastoreRewriteException if the Datastore already has
    *         SummaryMetadata.
    */
   void setSummaryMetadata(SummaryMetadata metadata)
           throws DatastoreFrozenException, DatastoreRewriteException;

   /**
    * Return whether an annotation with the given tag exists.
    *
    * @param tag the tag for the annotation
    * @return true if the annotation exists; false otherwise
    */
   boolean hasAnnotation(String tag);

   /**
    * Get an annotation, creating it if it doesn't exist
    * @param tag
    * @return
    */
   Annotation getAnnotation(String tag);

   /**
    * Get an annotation, creating it if it doesn't exist.
    *
    * @param tag the tag for the annotation
    * @return the annotation
    * @deprecated use {@link #createAnnotation} instead
    */
   @Deprecated
   Annotation loadAnnotation(String tag);

   /**
    * Create a new annotation.
    *
    * @param tag the tag for the annotation
    * @return the created annotation
    * @throws IllegalArgumentException if an annotation with {@code tag}
    * already exists
    * @deprecated use {@link #hasAnnotation} and {@link #getAnnotation}
    */
   @Deprecated
   Annotation createNewAnnotation(String tag);

   /**
    * Freeze the Datastore. Methods that modify its contents will throw
    * DatastoreFrozenExceptions, and a DatastoreFrozenEvent() will be posted to
    * any subscribers.
    */
   void freeze();


   /**
    * Tell the Datastore where on disk it is being saved to.
    * 
    * @param path The location on disk at which the data has been stored.
    */
   void setSavePath(String path);

   /**
    * Return the path for where the data has been saved to disk. Will be null
    * if the data has not been saved yet.
    * 
    * @return The directory path to the saved data.
    */
   String getSavePath();

   /**
    * These are the valid inputs to the save() methods. 
    * SINGLEPLANE_TIFF_SERIES saves each individual 2D image plane as a 
    * separate file; 
    * MULTIPAGE_TIFF saves all images together in a single file (up to a 
    * limit of 4GB/file, after which point the images will be split into 
    * a second file).
    * 
    * @deprecated because it doesn't make sense to define this for general
    * Datastores
    */
   @Deprecated
   enum SaveMode {
      SINGLEPLANE_TIFF_SERIES,
      MULTIPAGE_TIFF,
   }

   /**
    * Prompts the user for a location to save data to, then saves data there
    * according to the mode parameter. After this method, getSavePath() will
    * return the selected save path, unless the user cancels when prompted for
    * directory/filename or there is an error while saving. Includes a file
    * format selector in the save dialog, which defaults to the last format
    * the user used.
    *
    * @param parent Window  on top of which to display the dialog prompt;
    *        may be null.
    * @return TODO WHAT DOES THE RETURN BOOLEAN MEAN?
    */
   boolean save(Component parent);

   /**
    * As above, except uses the provided path (the last element of which is
    * assumed to be a filename), instead of prompting the user. After this
    * method, getSavePath() will return the input save path, unless there is an
    * error while saving.
    *
    * @param mode
    * @param path
    * @return true if saving succeeded, false otherwise.
    * @deprecated because it doesn't make sense to define this for general
    * Datastores
    */
   @Deprecated
   boolean save(SaveMode mode, String path);
}