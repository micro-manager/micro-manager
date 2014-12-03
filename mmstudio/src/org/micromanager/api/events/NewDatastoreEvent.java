package org.micromanager.api.events;

import org.micromanager.api.data.Datastore;

/**
 * This event signifies that a new Datastore has been created. The event fires
 * before Storage has been set for the Datastore, so you cannot access image
 * data at the time of the event.
 */
public interface NewDatastoreEvent {
   public Datastore getDatastore();
}
