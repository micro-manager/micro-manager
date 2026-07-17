package org.micromanager.data.internal;

import org.junit.Assert;
import org.junit.Test;
import org.micromanager.PropertyMap;
import org.micromanager.data.MultiWellPlate;

/**
 * Tests round-trip serialization/deserialization of MultiWellPlate via PropertyMap.
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
      MultiWellPlate.WellNamingConvention plateRowNamingConvention =
            MultiWellPlate.WellNamingConvention.LETTER;
      MultiWellPlate.WellNamingConvention plateColumnNamingConvention =
            MultiWellPlate.WellNamingConvention.NUMBER;
      double plateWellOriginX = 8.0;
      double plateWellOriginY = -28.0;
      String plateStatus = "plateStatus";

      MultiWellPlate plate = new DefaultMultiWellPlate.Builder()
            .plateID(plateID)
            .plateName(plateName)
            .plateDescription(plateDescription)
            .plateExternalIdentifier(plateExternalIdentifier)
            .plateRows(plateRows)
            .plateColumns(plateColumns)
            .plateRowNamingConvention(plateRowNamingConvention)
            .plateColumnNamingConvention(plateColumnNamingConvention)
            .plateWellOriginXUm(plateWellOriginX)
            .plateWellOriginYUm(plateWellOriginY)
            .plateStatus(plateStatus)
            .build();

      PropertyMap plateAsMap = plate.toPropertyMap();
      MultiWellPlate plateFromMap = new DefaultMultiWellPlate.FromPropertyMapBuilder()
            .build(plateAsMap);

      Assert.assertEquals(plateID, plateFromMap.getPlateID());
      Assert.assertEquals(plateName, plateFromMap.getPlateName());
      Assert.assertEquals(plateDescription, plateFromMap.getPlateDescription());
      Assert.assertEquals(plateExternalIdentifier, plateFromMap.getPlateExternalIdentifier());
      Assert.assertEquals(plateRows, (int) plateFromMap.getPlateRows());
      Assert.assertEquals(plateColumns, (int) plateFromMap.getPlateColumns());
      Assert.assertEquals(plateRowNamingConvention, plateFromMap.getPlateRowNamingConvention());
      Assert.assertEquals(plateColumnNamingConvention,
            plateFromMap.getPlateColumnNamingConvention());
      Assert.assertEquals(plateWellOriginX, plateFromMap.getPlateWellOriginXUm(), 0.0001);
      Assert.assertEquals(plateWellOriginY, plateFromMap.getPlateWellOriginYUm(), 0.0001);
      Assert.assertEquals(plateStatus, plateFromMap.getPlateStatus());
   }
}
