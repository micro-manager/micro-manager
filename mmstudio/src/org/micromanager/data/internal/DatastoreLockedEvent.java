package org.micromanager.data.internal;

/**
 * This class signifies that a Datastore has become locked and cannot be 
 * written to any more (but read actions can still occur).
 * TODO: should be renamed to DefaultDatastoreLockedEvent.
 */
public class DatastoreLockedEvent implements org.micromanager.data.DatastoreLockedEvent {}
