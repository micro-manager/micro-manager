package org.micromanager.events;

import org.json.JSONObject;

/**
 * This class provides access to new summary metadata that is about to be used
 * for an acquisition, so that code that needs to modify it has a chance to
 * do so.
 */
public class SummaryMetadataEvent {
   private JSONObject summary_;

   public SummaryMetadataEvent(JSONObject summary) {
      summary_ = summary;
   }

   public JSONObject getSummaryMetadata() {
      return summary_;
   }
}
