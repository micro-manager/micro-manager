
package org.micromanager.assembledata;

import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.display.DataViewer;

/**
 *
 * @author Nico
 */
public class AssembleDataUtils {
   
   public static double getSmallestZoom(DataViewer dv1, DataViewer dv2) {
      double zoom = dv1.getDisplaySettings().getZoomRatio();
      if (dv2.getDisplaySettings().getZoomRatio() < zoom) { 
         zoom = dv2.getDisplaySettings().getZoomRatio(); 
      }
      return zoom;
   }
   
   public static DataProvider singlePositionData(DataProvider dp1, DataProvider dp2) {
      Coords maxCoords1 = dp1.getMaxIndices();
      Coords maxCoords2 = dp2.getMaxIndices();
      
      if (maxCoords1.getP() <= 1 && maxCoords2.getP() > 1) {
         return dp1;
      } else if (maxCoords2.getP() <= 1 && maxCoords1.getP() > 1) {
         return dp2;
      }
      return null;
   }
   
   public static DataProvider multiPositionData(DataProvider dp1, DataProvider dp2) {
      Coords maxCoords1 = dp1.getMaxIndices();
      Coords maxCoords2 = dp2.getMaxIndices();
      
      if (maxCoords1.getP() <= 1 && maxCoords2.getP() > 1) {
         return dp2;
      } else if (maxCoords2.getP() <= 1 && maxCoords1.getP() > 1) {
         return dp1;
      }
      return null;
   }
}
