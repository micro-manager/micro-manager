/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import ij.IJ;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;
import org.apache.commons.math3.geometry.Point;
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
   private final Double[][] interpolation_;
   private final double[][] normals_; //stored in degrees
   private final double boundXMin_, boundXMax_, boundYMin_, boundYMax_;
   private Region<Euclidean2D> convexHullRegion_;
   //for extrapolation
   private TreeSet<Vector2D> convexHullVertices_;
   private Point3d[] allPoints_;
   
   public SingleResolutionInterpolation(int pixPerPoint, Double[][] interp, double[][] normals, double boundXMin, double boundXMax, double boundYMin, double boundYMax, 
           Region<Euclidean2D> ch, Vector2D[] convexHullVertices, Point3d[] allPoints ) {
      pixPerInterpPoint_ = pixPerPoint;
      interpolation_ = interp;      
      normals_ = normals;
      boundXMax_ = boundXMax;
      boundYMax_ = boundYMax;
      boundXMin_ = boundXMin;
      boundYMin_ = boundYMin;
      convexHullRegion_ = ch;
      //keep them sorted for fast searching
      convexHullVertices_ = new TreeSet<Vector2D>(new Comparator<Vector2D>() {
         @Override
         public int compare(Vector2D o1, Vector2D o2) {
            if (o1.getX() != o2.getX()) {
               return o1.getX() < o2.getX() ? -1 : 1;
            }
            if (o1.getY() == o2.getY()) {
               return 0;
            }
            return o1.getY() < o2.getY() ? -1 : 1;
         }
      });
      convexHullVertices_.addAll(Arrays.asList(convexHullVertices));
      allPoints_ = allPoints;
   }
   
   public int getPixelsPerInterpPoint() {
      return pixPerInterpPoint_;
   }
   
 
   /**
    * Get the angle of the surface normal at this point to vertical
    * If extrpolated value, return 0
    * @param x
    * @param y
    * @return 
    */
   public double getNormalAngleToVertical(double x, double y) {
      if (!isInsideConvexHull(x, y)) {
         return 0;
      }
      int numInterpPointsX = normals_[0].length;
      int numInterpPointsY = normals_.length;
      int xIndex = (int) Math.round(((x - boundXMin_) / (boundXMax_ - boundXMin_)) * (numInterpPointsX - 1));
      int yIndex = (int) Math.round(((y - boundYMin_) / (boundYMax_ - boundYMin_)) * (numInterpPointsY - 1));
      if (xIndex >= 0 && yIndex >= 0 && xIndex < normals_[0].length && yIndex < normals_.length) {
         return normals_[yIndex][xIndex];
      }
      return 0;
   }

   /**
    *
    * @param x
    * @param y
    * @return null if not inside
    */
   public Double getInterpolatedValue(double x, double y, boolean extrapolate) {
      try {
         if (!isInsideConvexHull(x, y) && !extrapolate) {
            return null; //if not extrapolating, everything outside convex hull is null
         }
         //try to get the value from the calulated interpolation
         int numInterpPointsX = interpolation_[0].length;
         int numInterpPointsY = interpolation_.length;
         int xIndex = (int) Math.round(((x - boundXMin_) / (boundXMax_ - boundXMin_)) * (numInterpPointsX - 1));
         int yIndex = (int) Math.round(((y - boundYMin_) / (boundYMax_ - boundYMin_)) * (numInterpPointsY - 1));
         if (xIndex >= 0 && yIndex >= 0 && xIndex < interpolation_[0].length && yIndex < interpolation_.length &&
                 interpolation_[yIndex][xIndex] != null) {
            return interpolation_[yIndex][xIndex];
         }
         //if interpolation is null, its either outside convex hull, or just inside and not calculated due
         //to sampling artifacts. Either way, setting value equal to closest convex hull point should do just fine   
         //find closest convex hull vertex
         Vector2D closest = null;
         double minDistance = Integer.MAX_VALUE;
         for (Vector2D vertex : convexHullVertices_) {
            double distance = vertex.distance(new Vector2D(x, y));
            if (distance < minDistance) {
               minDistance = distance;
               closest = vertex;
            }
         }
         //find 3d point with same xy as convex hull vertex and use its z coordinate
         for (Point3d p : allPoints_) {
            if (closest.equals(new Vector2D(p.x, p.y))) {
               return (double) p.z;
            }
         }
         //if I ever get this error, either the two lists are out of sync or creating vecotrs causes some loss of precision
         IJ.log("Couldn't find 3d point with same XY as convex hull");   
      } catch (Exception e) {
         e.printStackTrace();
         ReportingUtils.showError("Problem interpolating");
      }
      return null;
   }

   private boolean isInsideConvexHull(double x, double y) {
      if (convexHullRegion_ == null) {
         return false;
      }
      return convexHullRegion_.checkPoint(new Vector2D(x, y)) != Region.Location.OUTSIDE;
   }
}
