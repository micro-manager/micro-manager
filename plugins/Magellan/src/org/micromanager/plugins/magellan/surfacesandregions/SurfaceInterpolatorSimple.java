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

import delaunay_triangulation.Delaunay_Triangulation;
import delaunay_triangulation.Point_dt;
import delaunay_triangulation.Triangle_dt;
import java.util.LinkedList;
import org.micromanager.plugins.magellan.main.Magellan;
import org.apache.commons.math3.geometry.euclidean.threed.Line;
import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.partitioning.Region;

/**
 * Subclass that implements a particular interpolation method This one creates a
 * plane based on the 3 closest points in XY space using the delaunay
 * triangulation and calculates specific z values in that plane
 */
public class SurfaceInterpolatorSimple extends SurfaceInterpolator {

   private static final double TOLERANCE = 0.01;
   
   public SurfaceInterpolatorSimple(String xyName, String zName) {
      super(xyName, zName);
   }

   protected void interpolateSurface(LinkedList<Point3d> points) throws InterruptedException {

      double pixSize = Magellan.getCore().getPixelSizeUm();
      //provide interpolator with current list of data points
      Point_dt triangulationPoints[] = new Point_dt[points.size()];
      for (int i = 0; i < points.size(); i++) {
         triangulationPoints[i] = new Point_dt(points.get(i).x, points.get(i).y, points.get(i).z);
      }
      Delaunay_Triangulation dTri = new Delaunay_Triangulation(triangulationPoints);

      int maxPixelDimension = (int) (Math.max(boundXMax_ - boundXMin_, boundYMax_ - boundYMin_) / pixSize);
      //Start with at least 20 interp points and go smaller and smaller until every pixel interped?
      int pixelsPerInterpPoint = 1;
      while (maxPixelDimension / (pixelsPerInterpPoint + 1) > 20) {
         pixelsPerInterpPoint *= 2;
      }
      if (Thread.interrupted()) {
         throw new InterruptedException();
      }

      while (pixelsPerInterpPoint >= MIN_PIXELS_PER_INTERP_POINT) {
         int numInterpPointsX = (int) (((boundXMax_ - boundXMin_) / pixSize) / pixelsPerInterpPoint);
         int numInterpPointsY = (int) (((boundYMax_ - boundYMin_) / pixSize) / pixelsPerInterpPoint);
         double dx = (boundXMax_ - boundXMin_) / (numInterpPointsX - 1);
         double dy = (boundYMax_ - boundYMin_) / (numInterpPointsY - 1);

         float[][] interpVals = new float[numInterpPointsY][numInterpPointsX];
         float[][] interpNormals = new float[numInterpPointsY][numInterpPointsX];
         boolean[][] interpDefined = new boolean[numInterpPointsY][numInterpPointsX];
         for (int yInd = 0; yInd < interpVals.length; yInd++) {
            for (int xInd = 0; xInd < interpVals[0].length; xInd++) {
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
               double xVal = boundXMin_ + dx * xInd;
               double yVal = boundYMin_ + dy * yInd;
               boolean inHull = convexHullRegion_.checkPoint(new Vector2D(xVal, yVal)) == Region.Location.INSIDE;
               if (inHull) {
                  Triangle_dt tri = dTri.find(new Point_dt(xVal, yVal));
                  //convert to apache commons coordinates to make a plane
                  Vector3D v1 = new Vector3D(tri.p1().x(), tri.p1().y(), tri.p1().z());
                  Vector3D v2 = new Vector3D(tri.p2().x(), tri.p2().y(), tri.p2().z());
                  Vector3D v3 = new Vector3D(tri.p3().x(), tri.p3().y(), tri.p3().z());
                  Plane plane = new Plane(v1, v2, v3, TOLERANCE);
                  //intersetion of vertical line at these x+y values with plane gives point in plane
                  Vector3D pointInPlane = plane.intersection(new Line(new Vector3D(xVal, yVal, 0), new Vector3D(xVal, yVal, 1),TOLERANCE));
                  float zVal =  (float) pointInPlane.getZ();                  
                  interpVals[yInd][xInd] = zVal;
                  float angle = (float) (Vector3D.angle(plane.getNormal(),new Vector3D(0, 0, 1)) / Math.PI * 180.0);
                  interpNormals[yInd][xInd] = angle;
                  interpDefined[yInd][xInd] = true;
               } else {
                  interpDefined[yInd][xInd] = false;
               }
            }
         }
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
         synchronized (interpolationLock_) {
            currentInterpolation_ = new SingleResolutionInterpolation(pixelsPerInterpPoint, interpDefined, interpVals, interpNormals, 
            boundXMin_, boundXMax_, boundYMin_, boundYMax_,
                    convexHullRegion_, convexHullVertices_, getPoints());
            interpolationLock_.notifyAll();
         }
//         System.gc();
         pixelsPerInterpPoint /= 2;
      }
   }


}
