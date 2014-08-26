package org.micromanager.data;

import com.google.common.eventbus.EventBus;

import java.util.List;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Reader;
import org.micromanager.api.data.SummaryMetadata;


public class DefaultDatastore implements Datastore {
   private Reader reader_ = null;
   private EventBus bus_;
   private boolean isLocked_ = false;

   public DefaultDatastore() {
      bus_ = new EventBus();
   }

   @Override
   public void setReader(Reader reader) {
      reader_ = reader;
   }

   @Override
   public void registerForEvents(Object obj) {
      bus_.register(obj);
   }

   @Override
   public void unregisterForEvents(Object obj) {
      bus_.unregister(obj);
   }

   @Override
   public Image getImage(Coords coords) {
      if (reader_ != null) {
         return reader_.getImage(coords);
      }
      return null;
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) {
      if (reader_ != null) {
         return reader_.getImagesMatching(coords);
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
      if (reader_ != null) {
         return reader_.getMaxIndex(axis);
      }
      return null;
   }

   @Override
   public List<String> getAxes() {
      if (reader_ != null) {
         return reader_.getAxes();
      }
      return null;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      if (reader_ != null) {
         return reader_.getSummaryMetadata();
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
      if (reader_ != null) {
         return reader_.getDisplaySettings();
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
}
