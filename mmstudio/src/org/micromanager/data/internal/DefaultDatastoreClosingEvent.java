package org.micromanager.data.internal;

import org.micromanager.events.DatastoreClosingEvent;
import org.micromanager.data.Datastore;

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
