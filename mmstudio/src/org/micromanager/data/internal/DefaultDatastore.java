package org.micromanager.data.internal;

import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreLockedException;
import org.micromanager.data.Image;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.display.DisplaySettings;
import org.micromanager.events.internal.EventManager;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.PrioritizedEventBus;
import org.micromanager.internal.utils.ReportingUtils;


public class DefaultDatastore implements Datastore {
   private Storage storage_ = null;
   private PrioritizedEventBus bus_;
   private boolean isLocked_ = false;
   private boolean isSaved_ = false;

   public DefaultDatastore() {
      bus_ = new PrioritizedEventBus();
   }

   /**
    * Copy all data from the provided other Datastore into ourselves.
    */
   public void copyFrom(Datastore alt) {
      try {
         setSummaryMetadata(alt.getSummaryMetadata());
         for (Coords coords : alt.getUnorderedImageCoords()) {
            putImage(alt.getImage(coords));
         }
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.logError("Can't copy to datastore: we're locked");
      }
   }

   @Override
   public void setStorage(Storage storage) {
      storage_ = storage;
   }

   /**
    * Registers objects at default priority levels.
    */
   @Override
   public void registerForEvents(Object obj) {
      registerForEvents(obj, 100);
   }

   public void registerForEvents(Object obj, int priority) {
      bus_.register(obj, priority);
   }

   @Override
   public void unregisterForEvents(Object obj) {
      bus_.unregister(obj);
   }

   @Override
   public void publishEvent(Object obj) {
      bus_.post(obj);
   }

   @Override
   public Image getImage(Coords coords) {
      if (storage_ != null) {
         return storage_.getImage(coords);
      }
      return null;
   }

   @Override
   public Image getAnyImage() {
      if (storage_ != null) {
         return storage_.getAnyImage();
      }
      return null;
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) {
      if (storage_ != null) {
         return storage_.getImagesMatching(coords);
      }
      return null;
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      if (storage_ != null) {
         return storage_.getUnorderedImageCoords();
      }
      return null;
   }

   @Override
   public void putImage(Image image) throws DatastoreLockedException {
      if (isLocked_) {
         throw new DatastoreLockedException();
      }
      bus_.post(new NewImageEvent(image));
   }

   @Override
   public Integer getMaxIndex(String axis) {
      if (storage_ != null) {
         return storage_.getMaxIndex(axis);
      }
      return -1;
   }

   @Override
   public Integer getAxisLength(String axis) {
      return getMaxIndex(axis) + 1;
   }

   @Override
   public List<String> getAxes() {
      if (storage_ != null) {
         return storage_.getAxes();
      }
      return null;
   }

   @Override
   public Coords getMaxIndices() {
      if (storage_ != null) {
         return storage_.getMaxIndices();
      }
      return null;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      if (storage_ != null) {
         return storage_.getSummaryMetadata();
      }
      return null;
   }
   
   @Override
   public void setSummaryMetadata(SummaryMetadata metadata) throws DatastoreLockedException {
      if (isLocked_) {
         throw new DatastoreLockedException();
      }
      bus_.post(new NewSummaryMetadataEvent(metadata));
   }

   @Override
   public void lock() {
      bus_.post(new DatastoreLockedEvent());
      isLocked_ = true;
   }

   @Override
   public boolean getIsLocked() {
      return isLocked_;
   }

   @Override
   public void close() {
      EventManager.post(new DefaultDatastoreClosingEvent(this));
   }

   @Override
   public void setIsSaved(boolean isSaved) {
      isSaved_ = isSaved;
   }

   @Override
   public boolean getIsSaved() {
      return isSaved_;
   }

   @Override
   public boolean save(Datastore.SaveMode mode, Window window) {
      File file = FileDialogs.save(window,
            "Please choose a location for the data set", MMStudio.MM_DATA_SET);
      if (file == null) {
         return false;
      }
      return save(mode, file.getAbsolutePath());
   }

   // TODO: re-use existing file-based storage if possible/relevant (i.e.
   // if our current Storage is a file-based Storage).
   @Override
   public boolean save(Datastore.SaveMode mode, String path) {
      SummaryMetadata summary = getSummaryMetadata();
      // Insert intended dimensions if they aren't already present.
      if (summary.getIntendedDimensions() == null) {
         DefaultCoords.Builder builder = new DefaultCoords.Builder();
         for (String axis : getAxes()) {
            builder.index(axis, getAxisLength(axis));
         }
         summary = summary.copy().intendedDimensions(builder.build()).build();
      }
      try {
         DefaultDatastore duplicate = new DefaultDatastore();

         Storage saver;
         if (mode == Datastore.SaveMode.MULTIPAGE_TIFF) {
            // TODO: "false" is saying to not use separate files for each
            // position.  Should have a better way to handle this.
            // Should also respect the user's options here.
            saver = new StorageMultipageTiff(duplicate,
               path, true, true, false);
         }
         else if (mode == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
            saver = new StorageSinglePlaneTiffSeries(duplicate, path, true);
         }
         else {
            throw new IllegalArgumentException("Unrecognized mode parameter " + mode);
         }
         duplicate.setStorage(saver);
         duplicate.setSummaryMetadata(summary);
         for (Coords coords : getUnorderedImageCoords()) {
            duplicate.putImage(getImage(coords));
         }
         bus_.post(new DatastoreSavedEvent(path));
         return true;
      }
      catch (java.io.IOException e) {
         ReportingUtils.showError(e, "Failed to save image data");
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.logError("Couldn't modify newly-created datastore; this should never happen!");
      }
      return false;
   }

   @Override
   public int getNumImages() {
      if (storage_ != null) {
         return storage_.getNumImages();
      }
      return -1;
   }
}
