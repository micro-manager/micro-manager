package org.micromanager.data;

import org.micromanager.api.events.DatastoreClosingEvent;
import org.micromanager.api.data.Datastore;

public class DefaultDatastoreClosingEvent implements DatastoreClosingEvent {
   private Datastore store_;

   public DefaultDatastoreClosingEvent(Datastore store) {
      store_ = store;
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }
}
