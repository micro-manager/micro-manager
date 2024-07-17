package org.micromanager.internal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.micromanager.MultiWellPlate;
import org.micromanager.PropertyMap;

/**
 * Test class for MultiWellPlate.
 */
public class MultiWellPlateTest {
   int nrColumns = 12;
   int nrRows = 8;
   String plateID = "plate1";
   String plateName = "plate1";
   String plateDescription = "plate1";
   String plateExternalIdentifier = "plate1";
   String plateStatus = "plate1";
   MultiWellPlate.WellNamingConvention plateRowNamingConvention =
           MultiWellPlate.WellNamingConvention.LETTER;
   MultiWellPlate.WellNamingConvention plateColumnNamingConvention =
           MultiWellPlate.WellNamingConvention.NUMBER;


   @Test
   public void testSaveRestoreFromPropertyMap() {
      MultiWellPlate.Builder mwpb = new DefaultMultiWellPlate.Builder();
      mwpb.plateColumns(nrColumns);
      mwpb.plateRows(nrRows);
      mwpb.plateID(plateID);
      mwpb.plateName(plateName);
      mwpb.plateDescription(plateDescription);
      mwpb.plateExternalIdentifier(plateExternalIdentifier);
      mwpb.plateStatus(plateStatus);
      mwpb.plateRowNamingConvention(plateRowNamingConvention);
      mwpb.plateColumnNamingConvention(plateColumnNamingConvention);
      MultiWellPlate mwp = mwpb.build();

      PropertyMap mwpPropertyMap = mwp.toPropertyMap();
      MultiWellPlate mwp2 = new DefaultMultiWellPlate.PropertyMapBuilder().build(mwpPropertyMap);

      assertEquals(mwp.getPlateColumns(), mwp2.getPlateColumns());
      assertEquals(mwp.getPlateRows(), mwp2.getPlateRows());
      assertEquals(mwp.getPlateID(), mwp2.getPlateID());
      assertEquals(mwp.getPlateName(), mwp2.getPlateName());
      assertEquals(mwp.getPlateDescription(), mwp2.getPlateDescription());
      assertEquals(mwp.getPlateExternalIdentifier(), mwp2.getPlateExternalIdentifier());
      assertEquals(mwp.getPlateStatus(), mwp2.getPlateStatus());
      assertEquals(mwp.getPlateRowNamingConvention(), mwp2.getPlateRowNamingConvention());
      assertEquals(mwp.getPlateColumnNamingConvention(), mwp2.getPlateColumnNamingConvention());


   }
}
