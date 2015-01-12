package org.micromanager.events;

import org.micromanager.data.Datastore;

/**
 * This class signifies that a Datastore's close() method has been called, and
 * thus that all resources associated with that Datastore, and references to
 * the Datastore, should be removed so that it can be garbage collected.
 */
public interface DatastoreClosingEvent {
   public Datastore getDatastore();
}
