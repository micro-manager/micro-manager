package org.micromanager.data;

/**
 * This exception is raised when an attempt is made to modify a Datastore after
 * its freeze() method has been called.
 */
public class DatastoreFrozenException extends Exception {}
