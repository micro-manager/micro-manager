/**
 * CalibrationPoint - Data class for storing correspondence pairs
 *
 * <p>Represents a single correspondence between a point in the reference image
 * and a point in the microscope stage coordinate system.
 *
 * <p>LICENSE:      This file is distributed under the BSD license.
 */

package org.micromanager.navigationplugin;

import java.awt.geom.Point2D;

public class CalibrationPoint {
   private final Point2D.Double imageCoord;
   private final Point2D.Double stageCoord;
   private final int index;

   /**
    * Create a new calibration point
    *
    * @param imageCoord Pixel coordinates in the reference image
    * @param stageCoord Stage coordinates in micrometers
    * @param index Display index (1-based for user display)
    */
   public CalibrationPoint(Point2D.Double imageCoord, Point2D.Double stageCoord, int index) {
      this.imageCoord = imageCoord;
      this.stageCoord = stageCoord;
      this.index = index;
   }

   public Point2D.Double getImageCoord() {
      return imageCoord;
   }

   public Point2D.Double getStageCoord() {
      return stageCoord;
   }

   public int getIndex() {
      return index;
   }

   @Override
   public String toString() {
      return String.format("Point %d: Image(%.1f, %.1f) -> Stage(%.1f, %.1f)",
            index, imageCoord.x, imageCoord.y, stageCoord.x, stageCoord.y);
   }
}
