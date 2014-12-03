package org.micromanager.data;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.events.NewDatastoreEvent;

public class DefaultNewDatastoreEvent implements NewDatastoreEvent {
   private Datastore store_;

   public DefaultNewDatastoreEvent(Datastore store) {
      store_ = store;
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }
}
