package org.micromanager.projector.internal;

import java.awt.geom.Point2D;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;

/**
 * Round trip of the raw calibration grid through a PropertyMap.
 */
public class MappingStorageGridTest {

   @Test
   public void gridRoundTrip() {
      final int n = 4;
      Point2D.Double[][] slm = new Point2D.Double[n][n];
      Point2D.Double[][] cam = new Point2D.Double[n][n];
      for (int i = 0; i < n; i++) {
         for (int j = 0; j < n; j++) {
            slm[i][j] = new Point2D.Double(100 * i + 0.25, 50 * j + 0.5);
            cam[i][j] = new Point2D.Double(3.125 * i + j, 7.5 * j - i);
         }
      }
      cam[2][1] = null;
      // with null core and device the calibration key is ""
      PropertyMap settings = PropertyMaps.builder().putPropertyMap("",
            MappingStorage.gridToPropertyMap(new CalibrationGrid(slm, cam))).build();
      CalibrationGrid back = MappingStorage.loadGrid(null, null, settings);
      Assert.assertNotNull(back);
      Assert.assertEquals(n, back.size());
      for (int i = 0; i < n; i++) {
         for (int j = 0; j < n; j++) {
            Assert.assertEquals(slm[i][j], back.slm[i][j]);
            Assert.assertEquals(cam[i][j], back.cam[i][j]);
         }
      }
   }

   @Test
   public void missingGridGivesNull() {
      PropertyMap settings = PropertyMaps.builder().putPropertyMap("",
            PropertyMaps.builder().putInteger("NrEntries", 0).build()).build();
      Assert.assertNull(MappingStorage.loadGrid(null, null, settings));
      Assert.assertNull(MappingStorage.loadGrid(null, null, PropertyMaps.emptyPropertyMap()));
   }
}
