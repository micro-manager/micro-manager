package org.micromanager;



/**
 * Metadata describing a multi-well plate
 * Modelled after the OME plate model.
 */
public interface MultiWellPlate {
   enum WellNamingConvention {
      LETTER,
      NUMBER
   }

   interface FromPropertyMapBuilder {
      MultiWellPlate build(PropertyMap map);
   }

   /**
    * Builder for MultiWellPlate, see {@link MultiWellPlate} for function descriptions.
    */
   interface Builder {
      Builder plateID(String plateID);

      Builder plateName(String plateName);

      Builder plateDescription(String plateDescription);

      Builder plateExternalIdentifier(String plateExternalIdentifier);

      Builder plateRows(int plateRows);

      Builder plateColumns(int plateColumns);

      Builder plateRowNamingConvention(WellNamingConvention plateRowNamingConvention);

      Builder plateColumnNamingConvention(WellNamingConvention plateColumnNamingConvention);

      Builder plateWellOriginX(double plateWellOriginX);

      Builder plateWellOriginY(double plateWellOriginY);

      Builder plateStatus(String plateStatus);

      MultiWellPlate build();
   }

   /**
    * Returns a deep copy of the MultiWellPlate.
    *
    * @param origin the MultiWellPlate to copy
    * @return a deep copy of the MultiWellPlate
    */
   MultiWellPlate copyDeep(MultiWellPlate origin);

   /**
    * Get the number of Columns of the Plate.
    *
    * @return the number of Columns of the Plate
    */
   Integer getPlateColumns();

   /**
    * Returns a description of the Plate.
    *
    * @return a description of the Plate
    */
   String getPlateDescription();

   /**
    * Returns an optional ExternalIdentifier of the Plate. The ExternalIdentifier
    * attribute may contain a reference to an external database.
    *
    * @return an optional ExternalIdentifier of the Plate
    */
   String getPlateExternalIdentifier();

   /**
    * Returns an ID of the plate.  This can be any String that uniquely identifies the plate.
    *
    * @return an ID of the plate
    */
   String getPlateID();

   /**
    * Returns a human-readable name of the plate.
    * The Name identifies the plate to the user.
    *             It is used much like the ID, and so must be
    *             unique within the document.
    *             If a plate name is not available when one is needed
    *             it will be constructed by OME/OMERO in the following order:
    *             1. If name is available use it.
    *             2. If not use "Start time - End time"
    *             (NOTE: Not a subtraction! A string representation
    *             of the two times separated by a dash.)
    *             3. If these times are not available use the Plate ID.
    *
    * @return a human-readable name of the plate
    */
   String getPlateName();

   /**
    * Returns the RowNamingConvention property of Plate, either LETTER or NUMBER.
    *
    * @return the RowNamingConvention property of Plate
    */
   WellNamingConvention getPlateRowNamingConvention();

   /**
    * Returns the ColumnNamingConvention property of Plate, either LETTER or NUMBER.
    *
    * @return the ColumnNamingConvention property of Plate
    */
   WellNamingConvention getPlateColumnNamingConvention();

   /**
    * Returns the number of Rows of the Plate.
    *
    * @return the number of Rows of the Plate
    */
   Integer getPlateRows();

   /**
    * Returns the status of the plate, i.e. a textual annotation of the current
    * state of the plate with respect to the experiment work-flow; e.g.
    * 1. Seed cell: done; 2. Transfection: done;  3. Gel doc: todo.
    *
    * @return the status of the plate
    */
   String getPlateStatus();

   /**
    * This defines the X position to use for the origin of the
    *             fields (individual images) taken in a well. It is used
    *             with the X in the WellSample to display the fields
    *             in the correct position relative to each other. Each Well
    *             in the plate has the same well origin. We use microns as the unit.
    *             In the OMERO clients by convention we display the WellOrigin
    *             in the center of the view.
    *
    * @return the origin for the well sample positions in the well
    */
   Double getPlateWellOriginX();

   /**
    * This defines the Y position to use for the origin of the
    *             fields (individual images) taken in a well. It is used
    *             with the Y in the WellSample to display the fields
    *             in the correct position relative to each other. Each Well
    *             in the plate has the same well origin. We use microns as the unit.
    *             In the OMERO clients by convention we display the WellOrigin
    *             in the center of the view.
    *
    * @return the origin for the well sample positions in the well
    */
   Double getPlateWellOriginY();

   PropertyMap toPropertyMap();

}
