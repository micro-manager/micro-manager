package org.micromanager.internal;

import org.micromanager.MultiWellPlate;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.internal.PropertyKey;

/**
 * Metadata describing a multi-well plate.
 */
public class DefaultMultiWellPlate implements MultiWellPlate {
   WellNamingConvention plateColumnNamingConvention_;
   Integer plateColumns_ = 0;
   String plateDescription_ = "";
   String plateExternalIdentifier_ = "";
   String plateID_;
   String plateName_ = "";
   WellNamingConvention plateRowNamingConvention_;
   Integer plateRows_ = 0;
   String plateStatus_ = "";
   Double plateWellOriginX_;
   Double plateWellOriginY_;

   /**
    * Utility class to restore MultiWellPlate from a PropertyMap.
    */
   public static class PropertyMapBuilder implements MultiWellPlate.PropertyMapBuilder {

      @Override
      public MultiWellPlate build(PropertyMap map) {
         if (map == null) {
            return null;
         }
         return new DefaultMultiWellPlate(
                 MultiWellPlate.WellNamingConvention.valueOf((map.getString(
                         PropertyKey.WELL_PLATE_COLUMN_NAMING_CONVENTION.key(),
                         MultiWellPlate.WellNamingConvention.NUMBER.name()))),
                 map.getInteger(PropertyKey.WELL_PLATE_COLUMNS.key(), 0),
                 map.getString(PropertyKey.WELL_PLATE_DESCRIPTION.key(), ""),
                 map.getString(PropertyKey.WELL_PLATE_EXTERNAL_IDENTIFIER.key(), ""),
                 map.getString(PropertyKey.WELL_PLATE_ID.key(), ""),
                 map.getString(PropertyKey.WELL_PLATE_NAME.key(), ""),
                 MultiWellPlate.WellNamingConvention.valueOf(map.getString(
                          PropertyKey.WELL_PLATE_ROW_NAMING_CONVENTION.key(),
                          MultiWellPlate.WellNamingConvention.LETTER.name())),
                 map.getInteger(PropertyKey.WELL_PLATE_ROWS.key(), 0),
                 map.getString(PropertyKey.WELL_PLATE_STATUS.key(), ""),
                 map.getDouble(PropertyKey.WELL_PLATE_WELL_ORIGIN_X.key(), 0.0),
                 map.getDouble(PropertyKey.WELL_PLATE_WELL_ORIGIN_Y.key(), 0.0) );
      }
   }

   public static class Builder implements MultiWellPlate.Builder {
      WellNamingConvention plateColumnNamingConvention_;
      Integer plateColumns_ = 0;
      String plateDescription_ = "";
      String plateExternalIdentifier_ = "";
      String plateID_;
      String plateName_ = "";
      WellNamingConvention plateRowNamingConvention_;
      Integer plateRows_ = 0;
      String plateStatus_ = "";
      Double plateWellOriginX_;
      Double plateWellOriginY_;

      @Override
      public MultiWellPlate build() {
         return new DefaultMultiWellPlate(
                 plateColumnNamingConvention_,
                 plateColumns_,
                 plateDescription_,
                 plateExternalIdentifier_,
                 plateID_,
                 plateName_,
                 plateRowNamingConvention_,
                 plateRows_,
                 plateStatus_,
                 plateWellOriginX_,
                 plateWellOriginY_);
      }

      @Override
      public MultiWellPlate.Builder plateColumnNamingConvention(
              WellNamingConvention plateColumnNamingConvention) {
         plateColumnNamingConvention_ = plateColumnNamingConvention;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateColumns(int plateColumns) {
         plateColumns_ = plateColumns;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateDescription(String plateDescription) {
         plateDescription_ = plateDescription;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateExternalIdentifier(String plateExternalIdentifier) {
         plateExternalIdentifier_ = plateExternalIdentifier;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateID(String plateID) {
         plateID_ = plateID;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateName(String plateName) {
         plateName_ = plateName;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateRowNamingConvention(
              WellNamingConvention plateRowNamingConvention) {
         plateRowNamingConvention_ = plateRowNamingConvention;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateRows(int plateRows) {
         plateRows_ = plateRows;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateStatus(String plateStatus) {
         plateStatus_ = plateStatus;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateWellOriginX(double plateWellOriginX) {
         plateWellOriginX_ = plateWellOriginX;
         return this;
      }

      @Override
      public MultiWellPlate.Builder plateWellOriginY(double plateWellOriginY) {
         plateWellOriginY_ = plateWellOriginY;
         return this;
      }
   }

   private DefaultMultiWellPlate(
           WellNamingConvention plateColumnNamingConvention,
           Integer plateColumns,
           String plateDescription,
           String plateExternalIdentifier,
           String plateID,
           String plateName,
           WellNamingConvention plateRowNamingConvention,
           Integer plateRows,
           String plateStatus,
           Double plateWellOriginX,
           Double plateWellOriginY) {
      plateColumnNamingConvention_ = plateColumnNamingConvention;
      plateColumns_ = plateColumns;
      plateDescription_ = plateDescription;
      plateExternalIdentifier_ = plateExternalIdentifier;
      plateID_ = plateID;
      plateName_ = plateName;
      plateRowNamingConvention_ = plateRowNamingConvention;
      plateRows_ = plateRows;
      plateStatus_ = plateStatus;
      plateWellOriginX_ = plateWellOriginX;
      plateWellOriginY_ = plateWellOriginY;
   }


   /**
    * Returns the ColumnNamingConvention property of Plate, either LETTER or NUMBER.
    *
    * @return the ColumnNamingConvention property of Plate
    */
   @Override
   public WellNamingConvention getPlateColumnNamingConvention() {
      return plateColumnNamingConvention_;
   }

   /**
    * Get the number of Columns of the Plate.
    *
    * @return the number of Columns of the Plate
    */
   @Override
   public Integer getPlateColumns() {
      return plateColumns_;
   }

   /**
    * Returns a description of the Plate.
    *
    * @return a description of the Plate
    */
   @Override
   public String getPlateDescription() {
      return plateDescription_;
   }

   /**
    * Returns an optional ExternalIdentifier of the Plate.
    *
    * @return an optional ExternalIdentifier of the Plate
    */
   @Override
   public String getPlateExternalIdentifier() {
      return plateExternalIdentifier_;
   }

   /**
    * Returns an ID of the plate.  This can be any String that uniquely identifies the plate.
    *
    * @return an ID of the plate
    */
   @Override
   public String getPlateID() {
      return plateID_;
   }

   /**
    * Returns a human-readable name of the plate.
    *
    * @return a human-readable name of the plate
    */
   @Override
   public String getPlateName() {
      return plateName_;
   }

   /**
    * Returns the RowNamingConvention property of Plate, either LETTER or NUMBER.
    *
    * @return the RowNamingConvention property of Plate
    */
   @Override
   public WellNamingConvention getPlateRowNamingConvention() {
      return plateRowNamingConvention_;
   }

   /**
    * Returns the number of Rows of the Plate.
    *
    * @return the number of Rows of the Plate
    */
   @Override
   public Integer getPlateRows() {
      return plateRows_;
   }

   /**
    * Returns the status of the plate.
    *
    * @return the status of the plate
    */
   @Override
   public String getPlateStatus() {
      return plateStatus_;
   }

   /**
    * Returns the origin for the well sample positions in the well.  It is unclear to me
    * how this number is defined.  We will returns this is in microns, but relative to what?
    *
    * @return the origin for the well sample positions in the well
    */
   @Override
   public Double getPlateWellOriginX() {
      return plateWellOriginX_;
   }

   /**
    * Returns the origin for the well sample positions in the well.  It is unclear to me
    * how this number is defined.  We will returns this is in microns, but relative to what?
    *
    * @return the origin for the well sample positions in the well
    */
   @Override
   public Double getPlateWellOriginY() {
      return plateWellOriginY_;
   }

   @Override
   public PropertyMap toPropertyMap() {
      return PropertyMaps.builder()
              .putString(PropertyKey.WELL_PLATE_COLUMN_NAMING_CONVENTION.key(),
                      plateColumnNamingConvention_.name())
              .putInteger(PropertyKey.WELL_PLATE_COLUMNS.key(), plateColumns_)
              .putString(PropertyKey.WELL_PLATE_DESCRIPTION.key(), plateDescription_)
              .putString(PropertyKey.WELL_PLATE_EXTERNAL_IDENTIFIER.key(), plateExternalIdentifier_)
              .putString(PropertyKey.WELL_PLATE_ID.key(), plateID_)
              .putString(PropertyKey.WELL_PLATE_NAME.key(), plateName_)
              .putString(PropertyKey.WELL_PLATE_ROW_NAMING_CONVENTION.key(),
                      plateRowNamingConvention_.name())
              .putInteger(PropertyKey.WELL_PLATE_ROWS.key(), plateRows_)
              .putString(PropertyKey.WELL_PLATE_STATUS.key(), plateStatus_)
              .putDouble(PropertyKey.WELL_PLATE_WELL_ORIGIN_X.key(), plateWellOriginX_)
              .putDouble(PropertyKey.WELL_PLATE_WELL_ORIGIN_Y.key(), plateWellOriginY_)
              .build();
   }

}