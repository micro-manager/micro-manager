///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    Open Imaging, Inc 2016
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

/**
 * RewritableDatastores are Datastores that allow images and SummaryMetadata
 * to be deleted or overwritten after they have been inserted. They also
 * publish additional events when those actions are performed. You can create
 * an RewritableDatastore using the DataManager. Note that not all types of
 * Datastores support rewriting (e.g. file-based Datastores).
 */
public interface RewritableDatastore extends Datastore {
   /**
    * Insert an image into the Datastore. Posts a NewImageEvent to the event
    * bus. Unlike the base Datastore method, this method will allow images to
    * be overwritten (i.e. will not cause a DatastoreRewriteException), if an
    * image with the same coordinates is already in the Datastore. An
    * ImageOverwrittenEvent will be published on the Datastore's EventBus if
    * this occurs.
    * This method will also update the axisOrder property of the Datastore's
    * SummaryMetadata, if appropriate.
    *
    * @param image Micro-Manager Image object
    * @throws DatastoreFrozenException if the freeze() method has been called.
    * @throws IllegalArgumentException if the image's axes do not match the
    *         axes of images previously added to the Datastore. All images
    *         in a Datastore are required to have the same set of axes in
    *         their Coords objects.
    */
   @Override
   void putImage(Image image) throws IOException;

   /**
    * Set the SummaryMetadata. Posts a NewSummaryMetadataEvent to the event
    * bus. Unlike with standard Datastores, this method may be called
    * multiple times.
    *
    * @param metadata Object representing the summary metadata
    * @throws DatastoreFrozenException if the freeze() method has been called.
    */
   @Override
   void setSummaryMetadata(SummaryMetadata metadata)
         throws IOException;

   /**
    * Delete an image from the Datastore. Posts an ImageDeletedEvent to the
    * event bus. Throws an IllegalArgumentException if the provided coordinates
    * do not correspond to any image in the Datastore.
    * @param coords Coordinates of the image to remove.
    * @throws java.io.IOException
    * @throws IllegalArgumentException if the coords do not match any image.
    */
   void deleteImage(Coords coords) throws IOException;

   /**
    * Delete all images from the Datastore whose coordinates match the provided
    * Coords object, which may be underspecified. All images whose coordinate
    * positions match all positions in the Coords will be deleted (with a
    * corresponding ImageDeletedEvent posted on the Datastore's event bus).
    * For example, calling this method with a Coords of {@code <"z" = 9>}
    * would delete all Images whose Z coordinate is 9. Calling this method
    * with an empty Coords object will delete every image in the Datastore,
    * as per deleteAllImages() except without posting a DatastoreClearedEvent.
    * This method may potentially remove no images.
    * @param coords Potentially-underspecified coordinates of the image(s) to
    *        remove.
    * @throws java.io.IOException
    */
   void deleteImagesMatching(Coords coords) throws IOException;

   /**
    * Delete all images from the Datastore. An ImageDeletedEvent will be
    * published on the Datastore's event bus for each image, as will a
    * DatastoreClearedEvent.
    * @throws java.io.IOException
    */
   void deleteAllImages() throws IOException;
}
