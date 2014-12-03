package org.micromanager.data;

import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.micromanager.acquisition.multipagetiff.StorageMultipageTiff;
import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Storage;
import org.micromanager.api.data.SummaryMetadata;
import org.micromanager.api.display.DisplayWindow;
import org.micromanager.events.EventManager;
import org.micromanager.MMStudio;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PrioritizedEventBus;
import org.micromanager.utils.ReportingUtils;


public class DefaultDatastore implements Datastore {
   private Storage storage_ = null;
   private PrioritizedEventBus bus_;
   private boolean isLocked_ = false;
   private boolean isSaved_ = false;
   private ArrayList<DisplayWindow> displays_;

   public DefaultDatastore() {
      bus_ = new PrioritizedEventBus();
      displays_ = new ArrayList<DisplayWindow>();
      EventManager.post(new DefaultNewDatastoreEvent(this));
   }

   /**
    * Copy all data from the provided other Datastore into ourselves.
    */
   public void copyFrom(Datastore alt) {
      try {
         setSummaryMetadata(alt.getSummaryMetadata());
         setDisplaySettings(alt.getDisplaySettings());
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

   @Override
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
   public DisplaySettings getDisplaySettings() {
      if (storage_ != null) {
         return storage_.getDisplaySettings();
      }
      return null;
   }

   @Override
   public void setDisplaySettings(DisplaySettings settings) throws DatastoreLockedException {
      if (isLocked_) {
         throw new DatastoreLockedException();
      }
      bus_.post(new NewDisplaySettingsEvent(settings));
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
   public void setIsSaved(boolean isSaved) {
      isSaved_ = isSaved;
   }

   @Override
   public boolean getIsSaved() {
      return isSaved_;
   }

   @Override
   public void save(Datastore.SaveMode mode) {
      // Find a display to use, or use the MainFrame if none is available.
      Window window = MMStudio.getInstance().getFrame();
      if (displays_.size() > 0) {
         DisplayWindow tmp = displays_.get(0);
         if (tmp instanceof Window) { // This should always be true.
            window = (Window) tmp;
         }
         else {
            ReportingUtils.logError("Couldn't cast DisplayWindow to Window");
         }
      }
      File file = FileDialogs.save(window,
            "Please choose a location for the data set", MMStudio.MM_DATA_SET);
      if (file == null) {
         return;
      }
      save(mode, file.getAbsolutePath());
   }

   @Override
   public void save(Datastore.SaveMode mode, String path) {
      // TODO: we have some casts to Default* here; do we really need them?
      DefaultSummaryMetadata summary = (DefaultSummaryMetadata) getSummaryMetadata();
      // Insert intended dimensions if they aren't already present.
      if (summary.getIntendedDimensions() == null) {
         DefaultCoords.Builder builder = new DefaultCoords.Builder();
         for (String axis : getAxes()) {
            builder.position(axis, getAxisLength(axis));
         }
         summary = (DefaultSummaryMetadata) summary.copy().intendedDimensions(builder.build()).build();
      }
      // Downcast enum to boolean as StorageMultipageTiff only has two modes.
      boolean isMultipage = (mode == Datastore.SaveMode.MULTIPAGE_TIFF);
      try {
         // TODO: "false" is saying to not use separate files for each
         // position.  Should have a better way to handle this.
         // TODO: obey the mode parameter and allow saving across multiple
         // files.
         DefaultDatastore duplicate = new DefaultDatastore();
         StorageMultipageTiff saver = new StorageMultipageTiff(duplicate,
               path, true, isMultipage, false);
         duplicate.setStorage(saver);
         duplicate.setSummaryMetadata(summary);
         for (Coords coords : getUnorderedImageCoords()) {
            duplicate.putImage(getImage(coords));
         }
      }
      catch (java.io.IOException e) {
         ReportingUtils.showError(e, "Failed to save image data");
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.logError("Couldn't modify newly-created datastore; this should never happen!");
      }
   }

   @Override
   public int getNumImages() {
      if (storage_ != null) {
         return storage_.getNumImages();
      }
      return -1;
   }

   @Override
   public void associateDisplay(DisplayWindow display) {
      displays_.add(display);
   }

   @Override
   public void removeDisplay(DisplayWindow display) {
      if (displays_.contains(display)) {
         displays_.remove(display);
      }
   }

   @Override
   public List<DisplayWindow> getDisplays() {
      return displays_;
   }
}
