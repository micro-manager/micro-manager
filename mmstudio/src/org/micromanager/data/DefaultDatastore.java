package org.micromanager.data;

import java.util.ArrayList;
import java.util.List;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Storage;
import org.micromanager.api.data.SummaryMetadata;
import org.micromanager.api.display.DisplayWindow;

import org.micromanager.utils.PrioritizedEventBus;

public class DefaultDatastore implements Datastore {
   private Storage storage_ = null;
   private PrioritizedEventBus bus_;
   private boolean isLocked_ = false;
   private ArrayList<DisplayWindow> displays_;

   public DefaultDatastore() {
      bus_ = new PrioritizedEventBus();
      displays_ = new ArrayList<DisplayWindow>();
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
   public List<Image> getImagesMatching(Coords coords) {
      if (storage_ != null) {
         return storage_.getImagesMatching(coords);
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
