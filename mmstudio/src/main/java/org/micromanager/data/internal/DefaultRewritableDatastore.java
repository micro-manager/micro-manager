///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Data API implementation
// -----------------------------------------------------------------------------
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

import java.io.IOException;
import java.util.List;
import org.micromanager.data.Coords;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.RewritableDatastore;
import org.micromanager.data.RewritableStorage;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

public final class DefaultRewritableDatastore extends DefaultDatastore
    implements RewritableDatastore {

  public DefaultRewritableDatastore(MMStudio mmStudio) {
    super(mmStudio);
  }

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
    bus_.post(new DefaultNewSummaryMetadataEvent(metadata));
  }

  @Override
  public void putImage(Image image) throws IOException {
    try {
      super.putImage(image);
    } catch (DatastoreRewriteException e) {
      Image oldImage;
      oldImage = storage_.getImage(image.getCoords());
      // We call the storage's method directly instead of using our
      // deleteImage() method, to avoid posting an ImageDeletedEvent.
      ((RewritableStorage) storage_).deleteImage(image.getCoords());
      try {
        super.putImage(image);
        bus_.post(new DefaultImageOverwrittenEvent(image, oldImage, this));
      } catch (DatastoreRewriteException e2) {
        // This should never happen.
        ReportingUtils.logError(e2, "Unable to insert image after having cleared space for it.");
      }
    }

    // Track changes to our axes so we can note the axis order.
    Coords coords = image.getCoords();
    SummaryMetadata summary = getSummaryMetadata();
    if (summary == null) {
      return;
    }
    List<String> axisOrderList = summary.getOrderedAxes();
    boolean didAdd = false;
    for (String axis : coords.getAxes()) {
      if (!axisOrderList.contains(axis) && coords.getIndex(axis) > 0) {
        // This axis is newly nonzero.
        axisOrderList.add(axis);
        didAdd = true;
      }
    }
    if (didAdd) {
      // Update summary metadata with the new axis order.
      summary = summary.copyBuilder().axisOrder(axisOrderList.toArray(new String[] {})).build();
      setSummaryMetadata(summary);
    }
  }

  @Override
  public void deleteImage(Coords coords) throws IOException {
    Image image = getImage(coords);
    ((RewritableStorage) storage_).deleteImage(coords);
    bus_.post(new DefaultImageDeletedEvent(image, this));
  }

  @Override
  public void deleteImagesMatching(Coords coords) throws IOException {
    for (Image image : getImagesMatching(coords)) {
      deleteImage(image.getCoords());
    }
  }

  @Override
  public void deleteAllImages() throws IOException {
    Coords blank = new DefaultCoords.Builder().build();
    deleteImagesMatching(blank);
  }
}
