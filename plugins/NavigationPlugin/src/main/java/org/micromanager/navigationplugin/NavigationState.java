/**
 * NavigationState - Manages calibration points and coordinate transformations
 *
 * Stores the reference image, calibration points, and affine transformations
 * between image and stage coordinate systems.
 *
 * LICENSE:      This file is distributed under the BSD license.
 */

package org.micromanager.navigationplugin;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

public class NavigationState {

   public enum Mode {
      NO_IMAGE,
      CALIBRATING,
      CALIBRATED
   }

   private BufferedImage referenceImage;
   private final List<CalibrationPoint> calibrationPoints;
   private AffineTransform imageToStageTransform;
   private AffineTransform stageToImageTransform;
   private Mode currentMode;

   public NavigationState() {
      this.calibrationPoints = new ArrayList<>();
      this.currentMode = Mode.NO_IMAGE;
      this.imageToStageTransform = null;
      this.stageToImageTransform = null;
   }

   /**
    * Set the reference image
    */
   public void setReferenceImage(BufferedImage image) {
      this.referenceImage = image;
      if (image != null) {
         this.currentMode = Mode.CALIBRATING;
      } else {
         this.currentMode = Mode.NO_IMAGE;
      }
   }

   public BufferedImage getReferenceImage() {
      return referenceImage;
   }

   /**
    * Add a new calibration point
    *
    * @param imageCoord Pixel coordinates in the reference image
    * @param stageCoord Stage coordinates in micrometers
    */
   public void addCalibrationPoint(Point2D.Double imageCoord, Point2D.Double stageCoord) {
      int index = calibrationPoints.size() + 1;
      calibrationPoints.add(new CalibrationPoint(imageCoord, stageCoord, index));

      // Try to recalculate transform if we have enough points
      if (calibrationPoints.size() >= 3) {
         recalculateTransform();
      }
   }

   /**
    * Removes the calibration point whose image coordinate is closest to the
    * given image-pixel location and recalculates the affine transform.
    * @return true if a point was removed, false if there were no points
    */
   public boolean removeClosestCalibrationPoint(Point2D.Double imageCoord) {
      if (calibrationPoints.isEmpty()) return false;
      int closestIdx = 0;
      double minDist = Double.MAX_VALUE;
      for (int i = 0; i < calibrationPoints.size(); i++) {
         Point2D.Double pt = calibrationPoints.get(i).getImageCoord();
         double dx = pt.x - imageCoord.x;
         double dy = pt.y - imageCoord.y;
         double dist = dx * dx + dy * dy;
         if (dist < minDist) { minDist = dist; closestIdx = i; }
      }
      calibrationPoints.remove(closestIdx);
      // Re-index remaining points so indices stay consecutive
      for (int i = 0; i < calibrationPoints.size(); i++) {
         CalibrationPoint old = calibrationPoints.get(i);
         calibrationPoints.set(i, new CalibrationPoint(old.getImageCoord(), old.getStageCoord(), i + 1));
      }
      recalculateTransform();
      return true;
   }

   /**
    * Remove all calibration points
    */
   public void clearAllPoints() {
      calibrationPoints.clear();
      imageToStageTransform = null;
      stageToImageTransform = null;
      if (referenceImage != null) {
         currentMode = Mode.CALIBRATING;
      } else {
         currentMode = Mode.NO_IMAGE;
      }
   }

   /**
    * Recalculate the affine transform from current calibration points
    *
    * @return true if transform was successfully calculated, false otherwise
    */
   public boolean recalculateTransform() {
      if (calibrationPoints.size() < 3) {
         imageToStageTransform = null;
         stageToImageTransform = null;
         currentMode = Mode.CALIBRATING;
         return false;
      }

      // Build point pair map: imageCoord → stageCoord
      Map<Point2D.Double, Point2D.Double> pointPairs = new HashMap<>();
      for (CalibrationPoint cp : calibrationPoints) {
         pointPairs.put(cp.getImageCoord(), cp.getStageCoord());
      }

      try {
         // Generate affine transform from point pairs
         imageToStageTransform = generateAffineTransformFromPointPairs(pointPairs);

         // Create inverse for stage→image mapping
         stageToImageTransform = imageToStageTransform.createInverse();

         currentMode = Mode.CALIBRATED;
         return true;

      } catch (NoninvertibleTransformException e) {
         // Points are likely collinear
         imageToStageTransform = null;
         stageToImageTransform = null;
         currentMode = Mode.CALIBRATING;
         return false;
      } catch (Exception e) {
         // Other calculation error
         imageToStageTransform = null;
         stageToImageTransform = null;
         currentMode = Mode.CALIBRATING;
         return false;
      }
   }

   /**
    * Generate an AffineTransform from point pairs using QR decomposition.
    * Based on MathFunctions.generateAffineTransformFromPointPairs()
    *
    * @param pointPairs Map of source points to destination points
    * @return AffineTransform that maps source to destination
    */
   private static AffineTransform generateAffineTransformFromPointPairs(
         Map<Point2D.Double, Point2D.Double> pointPairs) {

      RealMatrix u = new Array2DRowRealMatrix(pointPairs.size(), 3);
      RealMatrix v = new Array2DRowRealMatrix(pointPairs.size(), 3);

      // Create u (source) and v (dest) matrices whose row vectors
      // are [x,y,1] for each Point2D.Double:
      int i = 0;
      for (Map.Entry<Point2D.Double, Point2D.Double> pair : pointPairs.entrySet()) {
         Point2D.Double uPt = pair.getKey();
         Point2D.Double vPt = pair.getValue();

         // Set row to [x,y,1]:
         u.setEntry(i, 0, uPt.x);
         u.setEntry(i, 1, uPt.y);
         u.setEntry(i, 2, 1);

         v.setEntry(i, 0, vPt.x);
         v.setEntry(i, 1, vPt.y);
         v.setEntry(i, 2, 1);

         i++;
      }

      // Find the 3x3 linear least squares solution to u*m'=v
      // (the last row should be [0,0,1]):
      DecompositionSolver solver = (new QRDecomposition(u)).getSolver();
      double[][] m = solver.solve(v).transpose().getData();

      // Create an AffineTransform object from the elements of m
      // (the last row is omitted as specified in AffineTransform class):
      return new AffineTransform(m[0][0], m[1][0], m[0][1], m[1][1], m[0][2], m[1][2]);
   }

   /**
    * Transform image coordinates to stage coordinates
    *
    * @param imageCoord Coordinates in image pixel space
    * @return Coordinates in stage space (micrometers), or null if not calibrated
    */
   public Point2D.Double imageToStage(Point2D.Double imageCoord) {
      if (imageToStageTransform == null) {
         return null;
      }
      Point2D.Double result = new Point2D.Double();
      imageToStageTransform.transform(imageCoord, result);
      return result;
   }

   /**
    * Transform stage coordinates to image coordinates
    *
    * @param stageCoord Coordinates in stage space (micrometers)
    * @return Coordinates in image pixel space, or null if not calibrated
    */
   public Point2D.Double stageToImage(Point2D.Double stageCoord) {
      if (stageToImageTransform == null) {
         return null;
      }
      Point2D.Double result = new Point2D.Double();
      stageToImageTransform.transform(stageCoord, result);
      return result;
   }

   /**
    * Check if the system is calibrated and ready for navigation
    */
   public boolean isCalibrated() {
      return currentMode == Mode.CALIBRATED && imageToStageTransform != null;
   }

   public Mode getCurrentMode() {
      return currentMode;
   }

   public List<CalibrationPoint> getCalibrationPoints() {
      return new ArrayList<>(calibrationPoints);
   }

   public int getPointCount() {
      return calibrationPoints.size();
   }
}
