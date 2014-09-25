package org.micromanager.api.data;

/**
 * This event indicates that whatever process is feeding data into the
 * Datastore ought to be stopped. The Datastore itself does not perform any
 * action when this event is received.
 */
public interface AbortEvent {}
