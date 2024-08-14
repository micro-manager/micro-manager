package org.micromanager.data;

import org.micromanager.PropertyMap;

/**
 * Metadata describing a well in a multi-well plate
 * Modelled after the OME plate model.
 */
public interface MultiWellPlateWell {

   /**
    * Builder for MultiWellPlateWell, see {@link MultiWellPlateWell} for function descriptions.
    */
   interface FromPropertyMapBuilder {
      MultiWellPlateWell build(PropertyMap map);
   }

   /**
    * Builder for MultiWellPlateWell, see {@link MultiWellPlateWell} for function descriptions.
    */
   interface Builder {
      Builder wellID(String wellID);

      Builder row(int row);

      Builder column(int column);

      MultiWellPlateWell build();
   }

   /**
    * Returns an ID of the Well.  This can be any String that uniquely identifies the well.
    *
    * @return an ID of the Well
    */
   String getWellID();


   /**
    * Returns the Row of the Well.
    *
    * @return the Row of the Well
    */
   Integer getRow();

   /**
    * Returns the Column of the Well.
    *
    * @return the Column of the Well
    */
   Integer getColumn();

   PropertyMap toPropertyMap();
}
