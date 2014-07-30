package org.micromanager.data;

import com.google.common.eventbus.EventBus;

import java.util.ArrayList;

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
   public void putImage(Image image, Coords coords) throws DatastoreLockedException {
      if (isLocked_) {
         throw new DatastoreLockedException();
      }
      bus_.post(new NewImageEvent(image, coords));
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
}
