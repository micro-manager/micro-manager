package org.micromanager.api.data;

import java.util.List;

/**
 * Datastores provide access to image data and metadata.
 */
public interface Datastore {
   /**
    * Sets the source for data for this Datastore.
    */
   public void setReader(Reader reader);

   /**
    * Subscribe the provided object to the Datastore's event bus.
    */
   public void registerForEvents(Object obj);

   /**
    * Unsubscribe the provided object from the Datastore's event bus.
    */
   public void unregisterForEvents(Object obj);

   /**
    * Retrieve the image at the specified coordinates. Will be null if no
    * Reader has been provided yet.
    */
   public Image getImage(Coords coords);

   /**
    * Retrieve a list of all images whose Coords match the given incomplete
    * Coords instance. For example, providing a Coords of <"z" = 9> would
    * return all Images whose position along the "z" axis is 9. May be empty.
    * Will be null if no Reader has been provided yet.
    */
   public List<Image> getImagesMatching(Coords coords);

   /**
    * Insert an image into the Datastore. Posts a NewImageEvent to the event
    * bus.
    * @throws DatastoreLockedException if the lock() method has been called.
    */
   public void putImage(Image image) throws DatastoreLockedException;

   /**
    * Return the maximum Image position along the specified access that this
    * Datastore has seen so far. Will be null if no Reader has been provided
    * yet.
    */
   public Integer getMaxIndex(String axis);

   /**
    * Return a List of all axis names for Images in the store. Will be null if
    * no Reader has been provided yet.
    */
   public List<String> getAxes();

   /**
    * Retrieve the summary metadata for the datastore. Will be null if no
    * Reader has been provided yet.
    */
   public SummaryMetadata getSummaryMetadata();

   /**
    * Set the SummaryMetadata. Posts a NewSummaryMetadataEvent to the event
    * bus.
    * @throws DatastoreLockedException if the lock() method has been called.
    */
   public void setSummaryMetadata(SummaryMetadata metadata) throws DatastoreLockedException;

   /**
    * Retrieve the DisplaySettings for the datastore. Will be null if no
    * Reader has been provided yet.
    */
   public DisplaySettings getDisplaySettings();

   /**
    * Set the DisplaySettings. Posts a NewDisplaySettingsEvent to the event
    * bus.
    * @throws DatastoreLockedException if the lock() method has been called.
    */
   public void setDisplaySettings(DisplaySettings settings) throws DatastoreLockedException;

   /**
    * Lock the Datastore. Methods that modify its contents will throw
    * DatastoreLockedExceptions, and a DatastoreLockedEvent() will be posted
    * to any subscribers.
    */
   public void lock();

   /**
    * Returns whether or not the Datastore has been locked.
    */
   public boolean getIsLocked();
}
