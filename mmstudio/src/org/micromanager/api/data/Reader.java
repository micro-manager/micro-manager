package org.micromanager.api.data;


/**
 * Readers are responsible for providing image data to Datastores when
 * requested. Different Readers may have different mechanisms -- for example,
 * by storing images in a file, or in RAM.
 */
public interface Reader {
   /**
    * Retrieve the Image located at the specified coordinates.
    */
   public Image getImage(Coords coords);
   
   /**
    * Return the largest stored position along the specified axis.
    */
   public Integer getMaxExtent(String axis);

   /**
    * Retrieve the SummaryMetadata associated with this dataset.
    */
   public SummaryMetadata getSummaryMetadata();

   /**
    * Retrieve the DisplaySettings associated with this dataset.
    */
   public DisplaySettings getDisplaySettings();
}
