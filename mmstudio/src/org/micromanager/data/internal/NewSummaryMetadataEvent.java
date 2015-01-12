package org.micromanager.data.internal;

import org.micromanager.data.SummaryMetadata;

/**
 * This class signifies that new summary metadata has been set for a 
 * Datastore.
 */
public class NewSummaryMetadataEvent implements org.micromanager.data.NewSummaryMetadataEvent {
   private SummaryMetadata metadata_;
   public NewSummaryMetadataEvent(SummaryMetadata metadata) {
      metadata_ = metadata;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return metadata_;
   }
}
