package org.micromanager.data.internal;

/**
 * This class signifies that a Datastore has become locked and cannot be 
 * written to any more (but read actions can still occur).
 */
public class DefaultDatastoreFrozenEvent implements org.micromanager.data.DatastoreFrozenEvent {}
