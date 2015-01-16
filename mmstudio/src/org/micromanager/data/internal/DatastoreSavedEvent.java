package org.micromanager.data.internal;

/**
 * This class signifies that a Datastore has just finished saving data.
 */
public class DatastoreSavedEvent {
   private String path_;

   public DatastoreSavedEvent(String path) {
      path_ = path;
   }

   /** The path to which data was saved */
   public String getPath() {
      return path_;
   }
}
