package org.micromanager.projector.internal;

import java.awt.geom.Point2D;

/**
 * The raw calibration measurements: projector positions slm[i][j] and the camera positions
 * cam[i][j] where those spots were found (null when not found). Kept so that the mapping can be
 * rebuilt, e.g. with different smoothing, without measuring again.
 */
final class CalibrationGrid {
   final Point2D.Double[][] slm;
   final Point2D.Double[][] cam;

   CalibrationGrid(Point2D.Double[][] slm, Point2D.Double[][] cam) {
      this.slm = slm;
      this.cam = cam;
   }

   int size() {
      return slm.length;
   }
}
