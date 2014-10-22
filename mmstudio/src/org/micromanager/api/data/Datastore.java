package org.micromanager.api.data;

import java.util.List;

import org.micromanager.api.display.DisplayWindow;

/**
 * Datastores provide access to image data and metadata. You are not expected
 * to implement this interface; it is here to describe how you can interact
 * with Datastores created by Micro-Manager itself.
 */
public interface Datastore {
   /**
    * Sets the source for data for this Datastore.
    */
   public void setStorage(Storage storage);

   /**
    * Subscribe the provided object to the Datastore's event bus. Lower
    * priority numbers are notified of events before higher priority numbers.
    * Do not set a priority of 0 or lower as this will cause unpredictable
    * behaviors.
    */
   public void registerForEvents(Object obj, int priority);

   /**
    * Unsubscribe the provided object from the Datastore's event bus.
    */
   public void unregisterForEvents(Object obj);

   /**
    * Publish the given event on the Datastore's event bus.
    */
   public void publishEvent(Object obj);

   /**
    * Retrieve the image at the specified coordinates. Will be null if no
    * Storage has been provided yet.
    */
   public Image getImage(Coords coords);

   /**
    * Retrieve a list of all images whose Coords match the given incomplete
    * Coords instance. For example, providing a Coords of <"z" = 9> would
    * return all Images whose position along the "z" axis is 9. May be empty.
    * Will be null if no Storage has been provided yet.
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
    * Datastore has seen so far. Will be null if no Storage has been provided
    * yet.
    */
   public Integer getMaxIndex(String axis);

   /**
    * Return a List of all axis names for Images in the store. Will be null if
    * no Storage has been provided yet.
    */
   public List<String> getAxes();

   /**
    * Return a Coord that represents the maximum possible index along all
    * available axes. Will be null if no Storage has been provided yet.
    */
   public Coords getMaxIndices();

   /**
    * Retrieve the summary metadata for the datastore. Will be null if no
    * Storage has been provided yet.
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
    * Storage has been provided yet.
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

   /**
    * Tell the Datastore whether or not its image data has been saved.
    */
   public void setIsSaved(boolean isSaved);

   /**
    * Retrieve whether or not the image data has been saved.
    */
   public boolean getIsSaved();

   /**
    * Returns the total number of Images in the Datastore. Returns -1 if no
    * Storage has been provided yet.
    */
   public int getNumImages();

   /**
    * Associate the specified DisplayWindow with the Datastore. This does
    * nothing besides ensure that it will be returned by getDisplays().
    */
   public void associateDisplay(DisplayWindow window);

   /**
    * Remove the specified DisplayWindow from the list of associated displays.
    */
   public void removeDisplay(DisplayWindow window);

   /**
    * Return all associated DisplayWindows.
    */
   public List<DisplayWindow> getDisplays();
}
