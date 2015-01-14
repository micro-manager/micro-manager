package org.micromanager.data;

import java.awt.Window;
import java.io.Closeable;
import java.util.List;

import org.micromanager.display.DisplayWindow;

/**
 * Datastores provide access to image data and metadata. You are not expected
 * to implement this interface; it is here to describe how you can interact
 * with Datastores created by Micro-Manager itself.
 */
public interface Datastore extends Closeable {
   /**
    * Sets the source for data for this Datastore.
    */
   public void setStorage(Storage storage);

   /**
    * Subscribe the provided object to the Datastore's event bus.
    */
   public void registerForEvents(Object obj);

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
    * Retrieve an image of arbitrary coordinates, or null if there are no
    * images (or if no Storage has been provided yet). No guarantees are made
    * about the position of the provided image.
    */
   public Image getAnyImage();

   /**
    * Retrieve a list of all images whose Coords match the given incomplete
    * Coords instance. For example, providing a Coords of <"z" = 9> would
    * return all Images whose position along the "z" axis is 9. May be empty.
    * Will be null if no Storage has been provided yet.
    */
   public List<Image> getImagesMatching(Coords coords);

   /**
    * Provide an object that you can iterate over to get the Coords of all
    * images in the Datastore, and which you can then use with getImage() to
    * get the specific Images. The Coords are not guaranteed to be in any
    * specific order.
    */
   public Iterable<Coords> getUnorderedImageCoords();

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
    * Return the number of valid positions along the specified axis. There is
    * no guarantee that this is equal to the number of occupied positions
    * along that axis. For example, a "sparse timeseries" could have timepoints
    * 0, 10, 20, and 30; this function would return 31.
    * Is always equal to getMaxIndex(axis) + 1, and thus only exists as a
    * convenience function.
    */
   public Integer getAxisLength(String axis);

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
    * Close the Datastore, removing all references to it from MicroManager's
    * code.  This will in turn cause the resources used by the Datastore (e.g.
    * RAM storage) to be released, assuming that there are no references to the
    * Datastore in other parts of the program (e.g. in plugins or Beanshell
    * scripts). Displays attached to the Datastore will automatically be
    * closed, with no prompt for data to be saved.
    */
   public void close();

   /**
    * Tell the Datastore whether or not its image data has been saved.
    * It's unlikely that most users will ever need to call this; it is set
    * automatically by the save() method, below.
    */
   public void setIsSaved(boolean isSaved);

   /**
    * Retrieve whether or not the image data has been saved.
    */
   public boolean getIsSaved();

   /**
    * These are the valid inputs to the save() methods. SEPARATE_TIFFS
    * saves each individual 2D image plane as a separate file; MULTIPAGE_TIFF
    * saves all images together in a single file (up to a limit of 4GB/file).
    */
   public enum SaveMode {
      SEPARATE_TIFFS,
      MULTIPAGE_TIFF
   }

   /**
    * Prompts the user for a directory and filename, then pulls image data
    * from the Storage and saves it according to the mode. After this method,
    * getIsSaved() will be true, unless the user cancels when prompted for
    * directory/filename or there is an error while saving.
    * @param Window Window over which to display the dialog prompt; may be
    *        null.
    * @return true if saving succeeded, false otherwise.
    */
   public boolean save(SaveMode mode, Window window);

   /**
    * As above, except uses the provided path (the last element of which is
    * assumed to be a filename), instead of prompting the user. After this
    * method, getIsSaved() will be true, unless there is an error while saving.
    * @return true if saving succeeded, false otherwise.
    */
   public boolean save(SaveMode mode, String path);

   /**
    * Returns the total number of Images in the Datastore. Returns -1 if no
    * Storage has been provided yet.
    */
   public int getNumImages();
}
