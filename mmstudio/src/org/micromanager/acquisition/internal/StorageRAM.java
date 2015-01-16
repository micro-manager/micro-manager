package org.micromanager.acquisition.internal;

import com.google.common.eventbus.Subscribe;

import java.awt.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import mmcorej.TaggedImage;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.NewImageEvent;
import org.micromanager.data.NewSummaryMetadataEvent;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;

import org.micromanager.internal.MMStudio;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * Simple RAM-based storage for Datastores. Methods that interact with the
 * HashMap that is our image storage are synchronized.
 */
public class StorageRAM implements Storage {
   private HashMap<Coords, Image> coordsToImage_;
   private Coords maxIndex_;
   private SummaryMetadata summaryMetadata_;
   private boolean amInDebugMode_ = false;

   public StorageRAM(Datastore store) {
      coordsToImage_ = new HashMap<Coords, Image>();
      maxIndex_ = new DefaultCoords.Builder().build();
      summaryMetadata_ = (new DefaultSummaryMetadata.Builder()).build();
      // It is imperative that we be notified of new images before anyone who
      // wants to retrieve the images from the store is notified.
      store.registerForEvents(this, 0);
   }

   /**
    * Toggle debug mode on/off. In debug mode, any request for an image will
    * be satisfied, by snapping new images if necessary.
    */
   public void setDebugMode(boolean amInDebugMode) {
      amInDebugMode_ = amInDebugMode;
   }

   /**
    * Add a new image to our storage, and update maxIndex_.
    */
   private synchronized void addImage(Image image) {
      Coords coords = image.getCoords();
      coordsToImage_.put(coords, image);
      for (String axis : coords.getAxes()) {
         if (maxIndex_.getPositionAt(axis) < coords.getPositionAt(axis)) {
            // Either this image is further along on this axis, or we have
            // no position for this axis yet.
            maxIndex_ = maxIndex_.copy()
                  .position(axis, coords.getPositionAt(axis))
                  .build();
         }
      }
   }

   @Override
   public synchronized Image getImage(Coords coords) {
      if (coordsToImage_.containsKey(coords)) {
         return coordsToImage_.get(coords);
      }
      if (!amInDebugMode_) {
         // No image available, and can't make a new one.
         return null;
      }
      ReportingUtils.logError("Snapping new image for " + coords);

      MMStudio studio = MMStudio.getInstance();
      try {
         studio.snapSingleImage();
         TaggedImage tagged = studio.getMMCore().getTaggedImage();
         Image result = new DefaultImage(tagged).copyAtCoords(coords);
         addImage(result);
         return result;
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to generate a new image");
         return null;
      }
   }

   @Override
   public synchronized Image getAnyImage() {
      if (coordsToImage_.size() > 0) {
         Coords coords = new ArrayList<Coords>(coordsToImage_.keySet()).get(0);
         return coordsToImage_.get(coords);
      }
      return null;
   }

   @Override
   public synchronized List<Image> getImagesMatching(Coords coords) {
      ArrayList<Image> results = new ArrayList<Image>();
      for (Image image : coordsToImage_.values()) {
         if (image.getCoords().matches(coords)) {
            results.add(image);
         }
      }
      return results;
   }

   @Override
   public synchronized Iterable<Coords> getUnorderedImageCoords() {
      return coordsToImage_.keySet();
   }

   @Override
   public Integer getMaxIndex(String axis) {
      return maxIndex_.getPositionAt(axis);
   }

   @Override
   public List<String> getAxes() {
      return maxIndex_.getAxes();
   }

   @Override
   public Coords getMaxIndices() {
      return maxIndex_;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Subscribe
   public void onNewSummary(NewSummaryMetadataEvent event) {
      summaryMetadata_ = event.getSummaryMetadata();
   }

   @Subscribe
   public void onNewImage(NewImageEvent event) {
      addImage(event.getImage());
   }

   @Override
   public int getNumImages() {
      return coordsToImage_.size();
   }
}
