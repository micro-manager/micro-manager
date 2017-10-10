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
    * @throws java.io.IOException
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
    * bus. This method may only be called once for a given Datastore.
    *
    * @param metadata Object representing the summary metadata
    * @throws DatastoreFrozenException if the freeze() method has been called.
    * @throws DatastoreRewriteException if the Datastore already has
    *         SummaryMetadata.
    */
   void setSummaryMetadata(SummaryMetadata metadata) throws IOException;

   /**
    * Return whether an annotation with the given tag exists.
    *
    * @param tag the tag for the annotation
    * @return true if the annotation exists; false otherwise
    */
   boolean hasAnnotation(String tag) throws IOException;

   /**
    * Get an annotation, creating it if it doesn't exist
    * @param tag
    * @return
    */
   Annotation getAnnotation(String tag) throws IOException;

   /**
    * Freeze this Datastore so it cannot be further modified.
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
    */
   boolean save(Component parent) throws IOException;

   /**
    * @param mode
    * @param path
    */
   void save(SaveMode mode, String path) throws IOException;
}