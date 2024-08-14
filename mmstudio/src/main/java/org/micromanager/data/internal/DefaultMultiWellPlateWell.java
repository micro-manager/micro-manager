package org.micromanager.data.internal;

import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.MultiWellPlateWell;


/**
 * Default implementation of MultiWellPlateWell.
 */
public class DefaultMultiWellPlateWell implements MultiWellPlateWell {
   String wellID_;
   Integer row_;
   Integer column_;

   /**
    * Utility class to restore MultiWellPlate from a PropertyMap.
    */
   public static class FromPropertyMapBuilder implements MultiWellPlateWell.FromPropertyMapBuilder {

      @Override
      public MultiWellPlateWell build(PropertyMap map) {
         if (map == null) {
            return null;
         }
         return new DefaultMultiWellPlateWell(
                 map.getString(PropertyKey.WELL_PLATE_WELL_ID.key(), ""),
                 map.getInteger(PropertyKey.WELL_PLATE_WELL_ROW.key(), 0),
                 map.getInteger(PropertyKey.WELL_PLATE_WELL_COLUMN.key(), 0));
      }
   }

   public static class Builder implements MultiWellPlateWell.Builder {
      private String wellID_;
      private Integer row_;
      private Integer column_;

      @Override
      public Builder wellID(String wellID) {
         wellID_ = wellID;
         return this;
      }

      @Override
      public Builder row(int row) {
         row_ = row;
         return this;
      }

      @Override
      public Builder column(int column) {
         column_ = column;
         return this;
      }

      @Override
      public MultiWellPlateWell build() {
         return new DefaultMultiWellPlateWell(wellID_, row_, column_);
      }
   }


   /**
    * Private constructor MultiWellPlateWell.
    */
   private DefaultMultiWellPlateWell(String wellID, Integer row, Integer column) {
      wellID_ = wellID;
      row_ = row;
      column_ = column;
   }

   /**
    * Returns an ID of the Well.  This can be any String that uniquely identifies the well.
    *
    * @return an ID of the Well
    */
   @Override
   public String getWellID() {
      return wellID_;
   }

   /**
    * Returns the Row of the Well.
    *
    * @return the Row of the Well
    */
   @Override
   public Integer getRow() {
      return row_;
   }

   /**
    * Returns the Column of the Well.
    *
    * @return the Column of the Well
    */
   @Override
   public Integer getColumn() {
      return column_;
   }

   /**
    * Created a PropertyMap from this MultiWellPlateWell.
    *
    * @return a PropertyMap from this MultiWellPlateWell
    */
   @Override
   public PropertyMap toPropertyMap() {
      return PropertyMaps.builder()
              .putString(PropertyKey.WELL_PLATE_WELL_ID.key(), wellID_)
              .putInteger(PropertyKey.WELL_PLATE_WELL_ROW.key(), row_)
              .putInteger(PropertyKey.WELL_PLATE_WELL_COLUMN.key(), column_)
              .build();
   }
}
