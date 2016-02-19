///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
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

package org.micromanager.data.internal;

import org.micromanager.data.Coords;
import org.micromanager.data.RewritableDatastore;
import org.micromanager.data.RewritableStorage;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.utils.ReportingUtils;


public class DefaultRewritableDatastore extends DefaultDatastore implements RewritableDatastore {

   @Override
   public void setStorage(Storage storage) {
      if (!(storage instanceof RewritableStorage)) {
         throw new IllegalArgumentException("RewritableDatastore must use RewritableStorage");
      }
      super.setStorage(storage);
   }

   @Override
   public void setSummaryMetadata(SummaryMetadata metadata) throws DatastoreFrozenException {
      if (isFrozen_) {
         throw new DatastoreFrozenException();
      }
      bus_.post(new NewSummaryMetadataEvent(metadata));
   }

   @Override
   public void putImage(Image image) throws DatastoreFrozenException, IllegalArgumentException {
      try {
         super.putImage(image);
      }
      catch (DatastoreRewriteException e) {
         Image oldImage = storage_.getImage(image.getCoords());
         // We call the storage's method directly instead of using our
         // deleteImage() method, to avoid posting an ImageDeletedEvent.
         ((RewritableStorage) storage_).deleteImage(image.getCoords());
         try {
            super.putImage(image);
            bus_.post(new DefaultImageOverwrittenEvent(image, oldImage, this));
         }
         catch (DatastoreRewriteException e2) {
            // This should never happen.
            ReportingUtils.logError(e2, "Unable to insert image after having cleared space for it.");
         }
      }
   }

   @Override
   public void deleteImage(Coords coords) {
      Image image = getImage(coords);
      ((RewritableStorage) storage_).deleteImage(coords);
      bus_.post(new DefaultImageDeletedEvent(image, this));
   }

   @Override
   public void deleteImagesMatching(Coords coords) {
      for (Image image : getImagesMatching(coords)) {
         deleteImage(image.getCoords());
      }
   }

   @Override
   public void deleteAllImages() {
      Coords blank = new DefaultCoords.Builder().build();
      deleteImagesMatching(blank);
      bus_.post(new DefaultDatastoreClearedEvent(this));
   }
}
