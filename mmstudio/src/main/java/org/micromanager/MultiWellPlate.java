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

   interface PropertyMapBuilder {
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
    * Returns an optional ExternalIdentifier of the Plate.
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
    * Returns the status of the plate.
    *
    * @return the status of the plate
    */
   String getPlateStatus();

   /**
    * Returns the origin for the well sample positions in the well.  It is unclear to me
    * how this number is defined.  We will returns this is in microns, but relative to what?
    *
    * @return the origin for the well sample positions in the well
    */
   Double getPlateWellOriginX();

   /**
    * Returns the origin for the well sample positions in the well.  It is unclear to me
    * how this number is defined.  We will returns this is in microns, but relative to what?
    *
    * @return the origin for the well sample positions in the well
    */
   Double getPlateWellOriginY();

   PropertyMap toPropertyMap();

}
