package org.micromanager.data;

import org.micromanager.api.data.SummaryMetadata;

/**
 * This class signifies that new summary metadata has been set for a 
 * Datastore.
 */
public class NewSummaryMetadataEvent implements org.micromanager.api.data.NewSummaryMetadataEvent {
   private SummaryMetadata metadata_;
   public NewSummaryMetadataEvent(SummaryMetadata metadata) {
      metadata_ = metadata;
   }

   public SummaryMetadata getSummaryMetadata() {
      return metadata_;
   }
}
