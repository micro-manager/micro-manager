package org.micromanager.data;

/**
 * This exception is raised when an attempt is made to modify a Datastore after
 * its lock() method has been called.
 */
public class DatastoreLockedException extends Exception {}
