package org.micromanager.api.data;

/**
 * This class signifies that new summary metadata has been set for a 
 * Datastore.
 */
public class NewSummaryMetadataEvent {
   private SummaryMetadata metadata_;
   public NewSummaryMetadataEvent(SummaryMetadata metadata) {
      metadata_ = metadata;
   }

   public SummaryMetadata getSummaryMetadata() {
      return metadata_;
   }
}
