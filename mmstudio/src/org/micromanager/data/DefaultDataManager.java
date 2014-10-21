package org.micromanager.data;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.DataManager;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Metadata;
import org.micromanager.api.data.SummaryMetadata;

import org.micromanager.acquisition.StorageRAM;

import org.micromanager.imagedisplay.dev.DefaultDisplayWindow;
import org.micromanager.utils.ReportingUtils;

/**
 * This implementation of the DataManager interface provides general utility
 * access to Micro-Manager's data objects.
 */
public class DefaultDataManager implements DataManager {
   private Datastore albumDatastore_;

   public DefaultDataManager() {
   }

   @Override
   public Coords.CoordsBuilder getCoordsBuilder() {
      return new DefaultCoords.Builder();
   }

   @Override
   public Datastore createNewDatastore() {
      return new DefaultDatastore();
   }

   @Override
   public Datastore getAlbumDatastore() {
      return albumDatastore_;
   }

   @Override
   public void addToAlbum(Image image) {
      if (albumDatastore_ == null || albumDatastore_.getIsLocked()) {
         // Need to create a new album.
         albumDatastore_ = new DefaultDatastore();
         albumDatastore_.setStorage(new StorageRAM(albumDatastore_));
         new DefaultDisplayWindow(albumDatastore_, null);
      }
      // Adjust image coordinates to be at the N+1th timepoint.
      Coords newCoords = image.getCoords().copy()
         .position("time", albumDatastore_.getMaxIndex("time") + 1)
         .build();
      try {
         DefaultImage temp = new DefaultImage(image, newCoords, image.getMetadata());
         temp.splitMultiComponentIntoStore(albumDatastore_);
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.showError(e, "Album datastore is locked.");
      }
   }

   @Override
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder() {
      return new DefaultDisplaySettings.Builder();
   }

   @Override
   public DisplaySettings getStandardDisplaySettings() {
      return DefaultDisplaySettings.getStandardSettings();
   }

   @Override
   public Metadata.MetadataBuilder getMetadataBuilder() {
      return new DefaultMetadata.Builder();
   }

   @Override
   public SummaryMetadata.SummaryMetadataBuilder getSummaryMetadataBuilder() {
      return new DefaultSummaryMetadata.Builder();
   }
}
