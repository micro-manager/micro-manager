package org.micromanager.data;

import java.lang.Iterable;
import java.util.List;

/**
 * Storages are responsible for providing image data to Datastores when
 * requested. Different Storages may have different mechanisms -- for example,
 * by storing images in a file, or in RAM.
 * In practice you are unlikely to need to implement your own Storage class,
 * and most of its methods are simply "backings" for similar methods in
 * Datastore.
 *
 * Note that the Storage interface does not expose any "setter" methods
 * (e.g. putImage(), setSummaryMetadata(), etc.). It is expected that any
 * read/write Storage listen for the relevant events published by the
 * Datastore instead.
 */
public interface Storage {
   /**
    * Retrieve the Image located at the specified coordinates.
    */
   public Image getImage(Coords coords);

   /**
    * Return any Image, or null if there are no images. Only really useful if
    * you need a representative image to work with. No guarantees are made
    * about which image will be provided.
    */
   public Image getAnyImage();

   /**
    * Return an Iterable that provides access to all image coordinates in the
    * Storage, in arbitrary order.
    */
   public Iterable<Coords> getUnorderedImageCoords();

   /**
    * Retrieve a list of all images whose Coords match the given incomplete
    * Coords instance. For example, providing a Coords of <"z" = 9> would
    * return all Images whose position along the "z" axis is 9. May be empty.
    */
   public List<Image> getImagesMatching(Coords coords);

   /**
    * Return the largest stored position along the specified axis.
    */
   public Integer getMaxIndex(String axis);

   /**
    * Return a List of all axis names for Images we know about.
    */
   public List<String> getAxes();

   /**
    * Return a Coords that provides the maximum index along all available axes.
    */
   public Coords getMaxIndices();

   /**
    * Retrieve the SummaryMetadata associated with this dataset.
    */
   public SummaryMetadata getSummaryMetadata();

   /**
    * Return the number of images in this dataset.
    */
   public int getNumImages();
}
