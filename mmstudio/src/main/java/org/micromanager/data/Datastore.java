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
import java.io.IOException;

/**
 * Read/write access to multi-dimensional image data.
 *
 * <strong>Important</strong>: Custom Datastore implementations are not
 * supported (consider creating a custom storage instead).
 */
public interface Datastore extends DataProvider {
   /**
    * Sets the data storage implementation for this Datastore.
    * 
    * @param storage data storage to be used henceforth
    */
   void setStorage(Storage storage);

   /**
    * Insert an image into the Datastore. Posts a NewImageEvent to the event
    * bus.
    *
    * @param image Micro-Manager Image object
    * @throws java.io.IOException if an IO error occurs
    * @throws DatastoreFrozenException if the freeze() method has been called.
    * @throws DatastoreRewriteException if an Image with the same coordinates
    *         already exists in the Datastore.
    * @throws IllegalArgumentException if the image's axes do not match the
    *         axes of images previously added to the Datastore. All images
    *         in a Datastore are required to have the same set of axes in
    *         their Coords objects.
    */
   void putImage(Image image) throws IOException;

   /**
    * Set the SummaryMetadata. Posts a NewSummaryMetadataEvent to the event
    * bus. This method may only be called once for a given Datastore, and
    * must be called before adding any images.
    *
    * @param metadata Object representing the summary metadata
    * @throws java.io.IOException if an IO error occurs
    * @throws DatastoreFrozenException if the freeze() method has been called.
    * @throws DatastoreRewriteException if the Datastore already has
    *         SummaryMetadata or already has images.
    */
   void setSummaryMetadata(SummaryMetadata metadata) throws IOException;

   /**
    * Return whether an annotation with the given tag exists.
    *
    * @param tag the tag for the annotation
    * @return true if the annotation exists; false otherwise
    * @throws java.io.IOException if an IO error occurs
    */
   boolean hasAnnotation(String tag) throws IOException;

   /**
    * Get an annotation, creating it if it doesn't exist
    * @param tag tag for the annotation
    * @return Annotation for th given tag
    * @throws java.io.IOException if an IO error occurs
    */
   Annotation getAnnotation(String tag) throws IOException;

   /**
    * Freeze this Datastore so it cannot be further modified.
    * 
    * @throws java.io.IOException if an IO error occurs
    */
   void freeze() throws IOException;

   /**
    * Set the intended path where the data will be stored.
    * <p>
    * In the current Micro-Manager file format, this path is used as the
    * enclosing directory.
    * 
    * @param path the file path (without extension)
    * @throws UnsupportedOperationException if this is an on-disk datastore
    */
   void setSavePath(String path);

   /**
    * Get the intended or actual path where the data will be stored.
    * <p>
    * If this is an in-memory datastore, returns whatever was set by {@link
    * #setSavePath}. If this is an on-disk datastore, returns the actual path.
    * 
    * @return the file path where the data is (to be) saved
    */
   String getSavePath();

   /**
    * The file format to save to.
    */
   enum SaveMode {
      SINGLEPLANE_TIFF_SERIES,
      MULTIPAGE_TIFF,
   }
   
   /**
    * @param parent Window  on top of which to display the dialog prompt;
    *        may be null.
    * @return true if data was saved; false if user canceled
    * @throws java.io.IOException if an IO error occurs
    * @deprecated Use {@link #save(Component, boolean)} instead
    */
   @Deprecated
   boolean save(Component parent) throws IOException;

   /**
    * Saves the datastore to an interactively determined path.
    * Opens a file dialog prompting user for a storage location
    * 
    * @param parent Window  on top of which to display a dialog prompting
    *        the user for a location to save.  After displaying 
    * @param blocking if true will return after saving, otherwise will return quickly
    *       and continue saving on another thread
    * @return Path chosen by user to save the data, null if dialog was canceled
    * @throws java.io.IOException if an IO error occurs
    */
   String save(Component parent, boolean blocking) throws IOException;

   
   /**
    * Saves the datastore to the given path using the given format (SaveMode)
    * Will save synchronously (i.e. this function will block)
    * 
    * @param mode File format to save to
    * @param path File path used to save the data
    * 
    * @throws java.io.IOException if an IO error occurs
    */
   void save(SaveMode mode, String path) throws IOException;
   
   /**
    * Saves the datastore to the given path using the given format (SaveMode)
    * 
    * @param mode File format to save to
    * @param path File path used to save the data
    * @param blocking when true, will block while saving data, otherwise will return
    *                   immediately
    * @throws java.io.IOException if an IO error occurs
    */
   void save(SaveMode mode, String path, boolean blocking) throws IOException;
   
   /**
    * Sets the name of the Datastore.  Posts a DatastoreNewNameEvent
    * @param name new name of the Datastore
    */
   void setName(String name);
}