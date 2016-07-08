
package org.micromanager.projector.internal;

import ij.IJ;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;

import java.awt.Checkbox;
import java.awt.event.ItemEvent;
import java.awt.Panel;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author nico
 */
public class Utils {
   // Show the ImageJ Roi Manager and return a reference to it.
   public static RoiManager showRoiManager() {
      IJ.run("ROI Manager...");
      final RoiManager roiManager = RoiManager.getInstance();
      // "Get the "Show All" checkbox and make sure it is checked.
      Checkbox checkbox = (Checkbox) ((Panel) roiManager.getComponent(1)).getComponent(9);
      checkbox.setState(true);
      // Simulated click of the "Show All" checkbox to force ImageJ
      // to show all of the ROIs.
      roiManager.itemStateChanged(new ItemEvent(checkbox, 0, null, ItemEvent.SELECTED));
      return roiManager;
   }

   public static List<Polygon> FloatToNormalPolygon(List<FloatPolygon> floatPolygons) {
      // manually copy from FloatPolygon to Polygon
      List<Polygon> roiPolygons = new ArrayList<Polygon>();
      for (FloatPolygon fp : floatPolygons) {
         Polygon p = new Polygon();
         for (int i = 0; i < fp.npoints; i++) {
            p.addPoint((int) (0.5 + fp.xpoints[i]),
                    (int) (0.5 + fp.ypoints[i]));
         }
         roiPolygons.add(p);
      }
      return roiPolygons;
   }
}
