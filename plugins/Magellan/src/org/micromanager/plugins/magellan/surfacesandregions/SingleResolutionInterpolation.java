///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.plugins.magellan.surfacesandregions;

import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;
import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.partitioning.Region;

/**
 *
 * @author Henry
 */
public class SingleResolutionInterpolation {
   
   private final int pixPerInterpPoint_;
   private final boolean[][] interpDefined_;
   private final float[][] interpolation_;
   private final float[][] normals_; //stored in degrees
   private final double boundXMin_, boundXMax_, boundYMin_, boundYMax_;
   private Region<Euclidean2D> convexHullRegion_;
   //for extrapolation
   private TreeSet<Vector2D> convexHullVertices_;
   private Point3d[] allPoints_;
   
   public SingleResolutionInterpolation(int pixPerPoint, boolean[][] defined, float[][] interp, float[][] normals, double boundXMin, double boundXMax, double boundYMin, double boundYMax, 
           Region<Euclidean2D> ch, Vector2D[] convexHullVertices, Point3d[] allPoints ) {
      pixPerInterpPoint_ = pixPerPoint;
      interpDefined_ = defined;
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
   public float getNormalAngleToVertical(double x, double y) {
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

      public boolean isInterpDefined(double x, double y) {
      if (!isInsideConvexHull(x, y)) {
         return false;
      }
      //try to get the value from the calulated interpolation
      int numInterpPointsX = interpolation_[0].length;
      int numInterpPointsY = interpolation_.length;
      int xIndex = (int) Math.round(((x - boundXMin_) / (boundXMax_ - boundXMin_)) * (numInterpPointsX - 1));
      int yIndex = (int) Math.round(((y - boundYMin_) / (boundYMax_ - boundYMin_)) * (numInterpPointsY - 1));
      if (xIndex >= 0 && yIndex >= 0 && xIndex < interpolation_[0].length && yIndex < interpolation_.length) {
         return interpDefined_[yIndex][xIndex];

      }
      return false;
   }
   
   public float getInterpolatedValue(double x, double y) {
         //try to get the value from the calulated interpolation
         int numInterpPointsX = interpolation_[0].length;
         int numInterpPointsY = interpolation_.length;
         int xIndex = (int) Math.round(((x - boundXMin_) / (boundXMax_ - boundXMin_)) * (numInterpPointsX - 1));
         int yIndex = (int) Math.round(((y - boundYMin_) / (boundYMax_ - boundYMin_)) * (numInterpPointsY - 1));
//         if (xIndex >= 0 && yIndex >= 0 && xIndex < interpolation_[0].length && yIndex < interpolation_.length ){
            return interpolation_[yIndex][xIndex];               
//         }
   }
      

   private boolean isInsideConvexHull(double x, double y) {
      if (convexHullRegion_ == null) {
         return false;
      }
      return convexHullRegion_.checkPoint(new Vector2D(x, y)) != Region.Location.OUTSIDE;
   }
}
