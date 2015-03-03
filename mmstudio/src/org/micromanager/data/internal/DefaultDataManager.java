package org.micromanager.data.internal;

import com.google.common.eventbus.Subscribe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;

import mmcorej.TaggedImage;

import org.json.JSONException;

import org.micromanager.data.Coords;
import org.micromanager.data.DataManager;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreLockedException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.RequestToCloseEvent;
import org.micromanager.events.DatastoreClosingEvent;

import org.micromanager.data.Coords;
import org.micromanager.data.internal.multipagetiff.MultipageTiffReader;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.data.internal.StorageRAM;
import org.micromanager.data.internal.StorageSinglePlaneTiffSeries;

import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.internal.MMStudio;
// TODO: this should be moved into the API.
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.PropertyMap;

/**
 * This implementation of the DataManager interface provides general utility
 * access to Micro-Manager's data objects.
 */
public class DefaultDataManager implements DataManager {
   private Datastore albumDatastore_;
   private MMStudio studio_;

   public DefaultDataManager(MMStudio studio) {
      studio_ = studio;
      studio_.registerForEvents(this);
   }

   @Override
   public Coords.CoordsBuilder getCoordsBuilder() {
      return new DefaultCoords.Builder();
   }

   @Override
   public Datastore createRAMDatastore() {
      Datastore result = new DefaultDatastore();
      result.setStorage(new StorageRAM(result));
      return result;
   }

   @Override
   public Datastore createMultipageTIFFDatastore(String directory,
         boolean shouldGenerateSeparateMetadata, boolean shouldSplitPositions)
         throws IOException {
      DefaultDatastore result = new DefaultDatastore();
      result.setStorage(new StorageMultipageTiff(result, directory, true,
               shouldGenerateSeparateMetadata, shouldSplitPositions));
      return result;
   }

   @Override
   public Datastore createSinglePlaneTIFFSeriesDatastore(String directory) {
      DefaultDatastore result = new DefaultDatastore();
      result.setStorage(new StorageSinglePlaneTiffSeries(result, directory,
            true));
      return result;
   }

   @Override
   public Datastore loadData(String directory, boolean isVirtual) throws IOException {
      DefaultDatastore result = new DefaultDatastore();
      // TODO: future additional file formats will need to be handled here.
      // For now we just choose between StorageMultipageTiff and
      // StorageSinglePlaneTiffSeries.
      boolean isMultipageTiff = MultipageTiffReader.isMMMultipageTiff(directory);
      if (isMultipageTiff) {
         result.setStorage(new StorageMultipageTiff(result, directory, false));
      }
      else {
         result.setStorage(new StorageSinglePlaneTiffSeries(result, directory,
                  false));
      }
      if (!isVirtual) {
         // Copy into a StorageRAM.
         DefaultDatastore tmp = (DefaultDatastore) createRAMDatastore();
         tmp.copyFrom(result);
         result = tmp;
      }
      return result;
   }

   @Override
   public Datastore getAlbumDatastore() {
      return albumDatastore_;
   }

   @Override
   public Image createImage(Object pixels, int width, int height,
         int bytesPerPixel, int numComponents, Coords coords,
         Metadata metadata) {
      return new DefaultImage(pixels, width, height, bytesPerPixel,
            numComponents, coords, metadata);
   }

   @Override
   public void addToAlbum(Image image) {
      if (albumDatastore_ == null || albumDatastore_.getIsLocked()) {
         // Need to create a new album.
         albumDatastore_ = new DefaultDatastore();
         studio_.displays().track(albumDatastore_);
         albumDatastore_.setStorage(new StorageRAM(albumDatastore_));
         new DefaultDisplayWindow(albumDatastore_, null);
      }
      // Adjust image coordinates to be at the N+1th timepoint.
      Coords newCoords = image.getCoords().copy()
         .time(albumDatastore_.getAxisLength(Coords.TIME))
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
   public Image convertTaggedImage(TaggedImage tagged) throws JSONException, MMScriptException {
      return new DefaultImage(tagged);
   }

   @Override
   public Metadata.MetadataBuilder getMetadataBuilder() {
      return new DefaultMetadata.Builder();
   }

   @Override
   public SummaryMetadata.SummaryMetadataBuilder getSummaryMetadataBuilder() {
      return new DefaultSummaryMetadata.Builder();
   }

   @Override
   public PropertyMap.PropertyMapBuilder getPropertyMapBuilder() {
      return new DefaultPropertyMap.Builder();
   }
}
