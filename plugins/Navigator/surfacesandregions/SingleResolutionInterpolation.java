/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class SingleResolutionInterpolation {
   
   private final int pixPerInterpPoint_;
   private final Float[][] interpolation_;
   private final double boundXMin_, boundXMax_, boundYMin_, boundYMax_;
   private Region<Euclidean2D> convexHullRegion_;
   
   public SingleResolutionInterpolation(int pixPerPoint, Float[][] interp, double boundXMin, double boundXMax, double boundYMin, double boundYMax, Region<Euclidean2D> ch) {
      pixPerInterpPoint_ = pixPerPoint;
      interpolation_ = interp;      
      boundXMax_ = boundXMax;
      boundYMax_ = boundYMax;
      boundXMin_ = boundXMin;
      boundYMin_ = boundYMin;
      convexHullRegion_ = ch;
   }
   
   public int getPixelsPerInterpPoint() {
      return pixPerInterpPoint_;
   }
   
   /**
    *
    * @param x
    * @param y
    * @return null if not inside
    */
   public Float getInterpolatedValue(double x, double y) {
      try {
         //check if theres anyhting to interpolate
         if (!isInsideConvexHull(x, y)) {
            return null;
         }

         int numInterpPointsX = interpolation_[0].length;
         int numInterpPointsY = interpolation_.length;

         int xIndex = (int) Math.round(((x - boundXMin_) / (boundXMax_ - boundXMin_)) * (numInterpPointsX - 1));
         int yIndex = (int) Math.round(((y - boundYMin_) / (boundYMax_ - boundYMin_)) * (numInterpPointsY - 1));
         return interpolation_[yIndex][xIndex];
      } catch (Exception e) {
         ReportingUtils.showError("Problem interpolating");
         return null;
      }
   }

   private boolean isInsideConvexHull(double x, double y) {
      if (convexHullRegion_ == null) {
         return false;
      }
      return convexHullRegion_.checkPoint(new Vector2D(x, y)) != Region.Location.OUTSIDE;
   }
}
