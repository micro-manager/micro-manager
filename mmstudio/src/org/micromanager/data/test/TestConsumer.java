package org.micromanager.data.test;

import com.google.common.eventbus.Subscribe;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.NewDisplaySettingsEvent;
import org.micromanager.api.data.NewImageEvent;
import org.micromanager.api.data.NewSummaryMetadataEvent;
import org.micromanager.api.data.SummaryMetadata;

import org.micromanager.utils.ReportingUtils;

/**
 * Dummy class that consumes events from a Datastore.
 */
public class TestConsumer {
   Datastore store_;
   public TestConsumer(Datastore store) {
      store_ = store;
      store_.registerForEvents(this);
   }

   @Subscribe
   public void onNewImage(NewImageEvent event) {
      ReportingUtils.logError("TestConsumer received new image");
   }

   @Subscribe
   public void onNewSummaryMetadata(NewSummaryMetadataEvent event) {
      ReportingUtils.logError("TestConsumer received new summary metadata");
   }

   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      ReportingUtils.logError("TestConsumer received new display settings");
   }

   public void unregister() {
      store_.unregisterForEvents(this);
   }
}
