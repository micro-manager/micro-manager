package org.micromanager.api.data;

/**
 * This class signifies that new summary metadata has been set for a 
 * Datastore.
 */
public interface NewSummaryMetadataEvent {
   public SummaryMetadata getSummaryMetadata();
}
