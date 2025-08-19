///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
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

package org.micromanager.data.internal;

import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProviderHasNewSummaryMetadataEvent;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.RewritableStorage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * Simple RAM-based storage for Datastores. Methods that interact with the
 * HashMap that is our image storage are synchronized.
 * TODO: coordsToImage_ can be set to null in the close function
 * if any of the member functions are called after "close", a null pointer exception
 * will follow.  We can either check for null whenever coordsToImage is used,
 * or make sure that no member is ever called after the close function
 * (which may be very difficult to guarantee).
 */
public final class StorageRAM implements RewritableStorage {
   private HashMap<Coords, Image> coordsToImage_;
   private Map<Coords, List<Coords>> coordsIndexedMissingC_;
   private Coords maxIndex_;
   private SummaryMetadata summaryMetadata_;
   private final Set<String> axesInUse_;

   /**
    * Image Data Storage located in RAM.
    *
    * @param store Datastore that "owns" this storage.
    */
   public StorageRAM(Datastore store) {
      coordsToImage_ = new HashMap<>();
      maxIndex_ = new DefaultCoords.Builder().build();
      axesInUse_ = new TreeSet<>();
      summaryMetadata_ = (new DefaultSummaryMetadata.Builder()).build();
      coordsIndexedMissingC_ = new HashMap<>();
      // It is imperative that we be notified of new images before anyone who
      // wants to retrieve the images from the store is notified.
      ((DefaultDatastore) store).registerForEvents(this, 0);
   }

   /**
    * Add a new image to our storage, and update maxIndex_.
    */
   @Override
   public synchronized void putImage(Image image) {
      Image imageExisting = getAnyImage();
      if (imageExisting != null) {
         ImageSizeChecker.checkImageSizes(image, imageExisting);
      } else {
         ImageSizeChecker.checkImageSizeInSummary(summaryMetadata_, image);
      }
      // index the coords
      Coords coords = image.getCoords();
      coordsToImage_.put(coords, image);
      Coords coordsNoC = image.getCoords().copyRemovingAxes(Coords.C);
      if (!coordsIndexedMissingC_.containsKey(coordsNoC)) {
         coordsIndexedMissingC_.put(coordsNoC, new ArrayList<>(4));
      }
      // since we can insert the same coords multiple times in a rewriteable RAMStore
      // we need to check if this coord was already present
      if (!coordsIndexedMissingC_.get(coordsNoC).contains(image.getCoords())) {
         coordsIndexedMissingC_.get(coordsNoC).add(image.getCoords());
      }

      for (String axis : coords.getAxes()) {
         axesInUse_.add(axis);
         if (maxIndex_.getIndex(axis) < coords.getIndex(axis)) {
            // Either this image is further along on this axis, or we have
            // no index for this axis yet.
            maxIndex_ = maxIndex_.copyBuilder()
                  .index(axis, coords.getIndex(axis))
                  .build();
         }
      }
   }

   @Override
   public void freeze() {
      // The Datastore handles making certain writes don't occur, and we don't
      // do anything special to "finish" storing data, so this is a no-op.
   }

   @Override
   public synchronized Image getImage(Coords coords) {
      if (coordsToImage_ != null && coordsToImage_.containsKey(coords)) {
         return coordsToImage_.get(coords);
      }
      return null;
   }

   @Override
   public Image getAnyImage() {
      synchronized (this) {
         if (coordsToImage_ != null && !coordsToImage_.isEmpty()) {
            Iterator<Image> valueIterator = coordsToImage_.values().iterator();
            if (valueIterator.hasNext()) {
               return valueIterator.next();
            }
         }
      }
      return null;
   }

   @Override
   public synchronized List<Image> getImagesMatching(Coords coords) {
      List<String> ignoredAxes = new ArrayList<>();
      for (String axis : axesInUse_) {
         if (!coords.getAxes().contains(axis)) {
            ignoredAxes.add(axis);
         }
      }
      try {
         return getImagesIgnoringAxes(coords, ignoredAxes.toArray(new String[0]));
      } catch (IOException ex) {
         ReportingUtils.logError(ex, "Failed to read image at " + coords);
         return null;
      }
   }

   /**
    * Finds images in this storage that match the given coord, but ignore
    * the provided axes (i.e., remove those axes from our images, and
    * then check if the Coord is identical to the one given).
    *
    * @param coords          coord looking for matching images
    * @param ignoreTheseAxes Axes to be ignored in the images collection when
    *                        looking for matches
    * @return List with Images that have the same coord as the one given
    *     (except for the axes to be ignored).
    * @throws IOException Not sure why this is here, should never be thrown.
    */
   public synchronized List<Image> getImagesIgnoringAxes(Coords coords, String... ignoreTheseAxes)
         throws IOException {
      if (coordsToImage_ == null) {
         return null;
      }
      // Optimization: traversing large HashMaps is costly, so avoid that when there is no need
      // without this, there is noticeable slowdown for one axis data > ~10,000 images.
      // An alternative optimization approach is to keep collections of coords
      // for all possible ignoredAxes.  There is more upfront work involved but may be
      // needed for fast multi-camera imaging.
      List<Image> result = new ArrayList<>();
      boolean haveIgnoredAxes = false;
      for (String axis : ignoreTheseAxes) {
         if (axesInUse_.contains(axis)) {
            haveIgnoredAxes = true;
            break;
         }
      }
      if (!haveIgnoredAxes) {
         result.add(coordsToImage_.get(coords));
      } else {
         // special case: if the ignored axis is C, use a special index to find the Coords
         // otherwise, the search will be very expensive (which will  be the case for other
         // axes) and result in noticaeble slowodwns with large datasetsz
         if (ignoreTheseAxes[0].equals(Coords.CHANNEL)) {
            if (coordsIndexedMissingC_.get(coords) != null) {
               for (Coords tmpCoords : coordsIndexedMissingC_.get(coords)) {
                  result.add(coordsToImage_.get(tmpCoords));
               }
            }
         } else {
            // Brute force it.  This will be slow with large data sets
            // Note that coordsToReader_ can be modified at the same time,
            // catch ConcurrentModificationException rather than incur the cost
            // of a lock that could slow down insertions
            try {
               for (Image image : coordsToImage_.values()) {
                  Coords imCoord = image.getCoords().copyRemovingAxes(ignoreTheseAxes);
                  if (imCoord.equals(coords)) {
                     result.add(image);
                  }
               }
            } catch (ConcurrentModificationException cme) {
               ReportingUtils.logError(cme, "coordsToReader_ was modified while iterating");
            }
         }
      }
      return result;
   }

   @Override
   public synchronized Iterable<Coords> getUnorderedImageCoords() {
      return coordsToImage_.keySet();
   }

   @Override
   public boolean hasImage(Coords coords) {
      return coordsToImage_.containsKey(coords);
   }

   @Override
   public int getMaxIndex(String axis) {
      return maxIndex_.getIndex(axis);
   }

   // TODO: check that metadata axis are a reliable source of information
   @Override
   public List<String> getAxes() {
      return summaryMetadata_.getOrderedAxes();
   }

   @Override
   public Coords getMaxIndices() {
      return maxIndex_;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return summaryMetadata_;
   }

   /**
    * Recieve the new summary through an event.  This is guaranteed to happen before
    * putImage is called.
    *
    * @param event this gives use the summary metadata
    */
   @Subscribe
   public void onNewSummary(DataProviderHasNewSummaryMetadataEvent event) {
      summaryMetadata_ = event.getSummaryMetadata();

      // setSummaryMetadata must be called before adding images to the store, so use this moment
      // to smartly initialize several HashMaps
      Coords dims = summaryMetadata_.getIntendedDimensions();
      int nrImagesNoC = 1;
      for (String axis : dims.getAxes()) {
         if (!axis.equals(Coords.CHANNEL)) {
            nrImagesNoC *= dims.getIndex(axis);
         }
      }
      coordsIndexedMissingC_ = new HashMap<>(nrImagesNoC);
   }

   @Override
   public int getNumImages() {
      return coordsToImage_.size();
   }

   @Override
   public synchronized void deleteImage(Coords coords) throws IllegalArgumentException {
      if (!coordsToImage_.containsKey(coords)) {
         throw new IllegalArgumentException("Storage does not contain image at " + coords);
      }
      coordsToImage_.remove(coords);
   }

   @Override
   public void close() {
      coordsToImage_ = null;
      coordsIndexedMissingC_ = null;
   }
}
