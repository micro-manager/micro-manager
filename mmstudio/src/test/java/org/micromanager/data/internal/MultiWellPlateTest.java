package org.micromanager.data.internal;

import org.junit.Assert;
import org.junit.Test;
import org.micromanager.PropertyMap;
import org.micromanager.data.MultiWellPlate;
import org.micromanager.data.MultiWellPlateWell;

/**
 * Tests for MultiWellPlate.
 * Tests round-trip serialization/deserialization of MultiWellPlate.
 */
public class MultiWellPlateTest {

   @Test
   public void testPlate() {
      String plateID = "plateID";
      String plateName = "plateName";
      String plateDescription = "plateDescription";
      String plateExternalIdentifier = "plateExternalIdentifier";
      int plateRows = 8;
      int plateColumns = 12;
      MultiWellPlate.WellNamingConvention plateRowNamingConvention = MultiWellPlate.WellNamingConvention.LETTER;
      MultiWellPlate.WellNamingConvention plateColumnNamingConvention = MultiWellPlate.WellNamingConvention.NUMBER;
      double plateWellOriginX = 0.0;
      double plateWellOriginY = 0.0;
      String plateStatus = "plateStatus";

      MultiWellPlate.Builder builder = new DefaultMultiWellPlate.Builder();
      builder.plateID(plateID);
      builder.plateName(plateName);
      builder.plateDescription(plateDescription);
      builder.plateExternalIdentifier(plateExternalIdentifier);
      builder.plateRows(plateRows);
      builder.plateColumns(plateColumns);
      builder.plateRowNamingConvention(plateRowNamingConvention);
      builder.plateColumnNamingConvention(plateColumnNamingConvention);
      builder.plateWellOriginX(plateWellOriginX);
      builder.plateWellOriginY(plateWellOriginY);
      builder.plateStatus(plateStatus);
      MultiWellPlate plate = builder.build();

      PropertyMap plateAsMap = plate.toPropertyMap();
      MultiWellPlate plateFromMap = new DefaultMultiWellPlate.FromPropertyMapBuilder().build(plateAsMap);

      Assert.assertEquals(plateID, plateFromMap.getPlateID());
      Assert.assertEquals(plateName, plateFromMap.getPlateName());
      Assert.assertEquals(plateDescription, plateFromMap.getPlateDescription());
      Assert.assertEquals(plateExternalIdentifier, plateFromMap.getPlateExternalIdentifier());
      Assert.assertEquals(plateRows, plateFromMap.getPlateRows(), 0.01);
      Assert.assertEquals(plateColumns, plateFromMap.getPlateColumns(), 0.01);
      Assert.assertEquals(plateRowNamingConvention, plateFromMap.getPlateRowNamingConvention());
      Assert.assertEquals(plateColumnNamingConvention, plateFromMap.getPlateColumnNamingConvention());
      Assert.assertEquals(plateWellOriginX, plateFromMap.getPlateWellOriginX(), 0.0);
      Assert.assertEquals(plateWellOriginY, plateFromMap.getPlateWellOriginY(), 0.0);
      Assert.assertEquals(plateStatus, plateFromMap.getPlateStatus());
   }


   /**
    * Tests for MultiWellPlateWell.
    * Tests round-trip serialization/deserialization of MultiWellPlateWell.
    */
   @Test
   public void testWell() {
      String wellID = "wellID";
      int row = 3;
      int column = 4;

      MultiWellPlateWell.Builder builder = new DefaultMultiWellPlateWell.Builder();
      builder.wellID(wellID);
      builder.row(row);
      builder.column(column);
      MultiWellPlateWell well = builder.build();

      PropertyMap wellAsMap = well.toPropertyMap();
      MultiWellPlateWell wellFromMap = new DefaultMultiWellPlateWell
              .FromPropertyMapBuilder().build(wellAsMap);

      Assert.assertEquals(wellID, wellFromMap.getWellID());
      Assert.assertEquals(row, wellFromMap.getRow(), 0.01);
      Assert.assertEquals(column, wellFromMap.getColumn(), 0.01);
   }
}
