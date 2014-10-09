package org.micromanager.acquisition;

import com.google.common.eventbus.Subscribe;

import java.awt.Color;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

import mmcorej.TaggedImage;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Metadata;
import org.micromanager.api.data.NewDisplaySettingsEvent;
import org.micromanager.api.data.NewImageEvent;
import org.micromanager.api.data.NewSummaryMetadataEvent;
import org.micromanager.api.data.Storage;
import org.micromanager.api.data.SummaryMetadata;

import org.micromanager.data.DefaultCoords;
import org.micromanager.data.DefaultDisplaySettings;
import org.micromanager.data.DefaultImage;
import org.micromanager.data.DefaultMetadata;
import org.micromanager.data.DefaultSummaryMetadata;

import org.micromanager.MMStudio;

import org.micromanager.utils.ReportingUtils;

/**
 * Simple RAM-based storage for Datastores. Methods that interact with the
 * HashMap that is our image storage are synchronized.
 */
public class StorageRAM implements Storage {
   private HashMap<Coords, Image> coordsToImage_;
   private Coords maxIndex_;
   private SummaryMetadata summaryMetadata_;
   private DisplaySettings displaySettings_;
   private boolean amInDebugMode_ = false;

   public StorageRAM(Datastore store) {
      coordsToImage_ = new HashMap<Coords, Image>();
      maxIndex_ = new DefaultCoords.Builder().build();
      summaryMetadata_ = (new DefaultSummaryMetadata.Builder()).build();
      // TODO: arbitrarily deciding color settings.
      displaySettings_ = (new DefaultDisplaySettings.Builder())
            .channelColors(new Color[] {Color.RED, Color.GREEN})
            .build();
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
         ReportingUtils.logError("No image at " + coords + " compare keys " + coordsToImage_.keySet());
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

   @Override
   public DisplaySettings getDisplaySettings() {
      return displaySettings_;
   }

   @Subscribe
   public void onNewSummary(NewSummaryMetadataEvent event) {
      summaryMetadata_ = event.getSummaryMetadata();
   }

   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      displaySettings_ = event.getDisplaySettings();
   }

   @Subscribe
   public void onNewImage(NewImageEvent event) {
      ReportingUtils.logError("Storage " + hashCode() + " adding image at " + event.getCoords() + " with corner pixel " + event.getImage().getIntensityStringAt(0, 0));
      addImage(event.getImage());
   }

   @Override
   public int getNumImages() {
      return coordsToImage_.size();
   }
}
