///////////////////////////////////////////////////////////////////////////////
//FILE:          MathUtils.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, 2/26/2010
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          
package org.micromanager.utils;

import java.awt.geom.NoninvertibleTransformException;
import org.apache.commons.math.linear.DecompositionSolver;
import org.apache.commons.math.linear.RealMatrix;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.QRDecompositionImpl;

public class MathFunctions {

   private static void insertPoint2DInMatrix(RealMatrix m, Point2D.Double pt, int row) {
      // Set row to [x,y,1]:
      m.setEntry(row, 0, pt.x);
      m.setEntry(row, 1, pt.y);
      m.setEntry(row, 2, 1);
   }

   /*
    * Creates an AffineTransform object that maps a source planar coordinate system to
    * a destination planar coordinate system. At least three point pairs are needed.
    * 
    * @pointPairs is a Map of points measured in the two coordinates systems (srcPt->destPt)
    */
   public static AffineTransform generateAffineTransformFromPointPairs(Map<Point2D.Double, Point2D.Double> pointPairs) {
      RealMatrix u = new Array2DRowRealMatrix(pointPairs.size(), 3);
      RealMatrix v = new Array2DRowRealMatrix(pointPairs.size(), 3);

      // Create u (source) and v (dest) matrices whose row vectors
      // are [x,y,1] for each Point2D.Double:

      int i = 0;
      for (Map.Entry pair : pointPairs.entrySet()) {
         Point2D.Double uPt = (Point2D.Double) pair.getKey();
         Point2D.Double vPt = (Point2D.Double) pair.getValue();

         insertPoint2DInMatrix(u, uPt, i);
         insertPoint2DInMatrix(v, vPt, i);

         i++;
      }
      // Find the 3x3 linear least squares solution to u*m'=v
      // (the last row should be [0,0,1]):
      DecompositionSolver solver = (new QRDecompositionImpl(u)).getSolver();
      double[][] m = solver.solve(v).transpose().getData();

      // Create an AffineTransform object from the elements of m
      // (the last row is omitted as specified in AffineTransform class):
      return new AffineTransform(m[0][0], m[1][0], m[0][1], m[1][1], m[0][2], m[1][2]);
   }

   /*
    * Creates an AffineTransform object that maps a source planar coordinate system to
    * a destination planar coordinate system. At least three point pairs are needed.
    *
    * Throws an Exception if the mean square deviation of transformed
    * points exceeds the specified tolerances.
    *
    * @pointPairs is a Map of points measured in the two coordinates systems (srcPt->destPt)
    */
   public static AffineTransform generateAffineTransformFromPointPairs(Map<Point2D.Double, Point2D.Double> pointPairs, double srcTol, double destTol) throws Exception {
      AffineTransform transform = generateAffineTransformFromPointPairs(pointPairs);
      double srcDevSqSum = 0;
      double destDevSqSum = 0;
      for (Map.Entry pair : pointPairs.entrySet()) {
         try {
            Point2D.Double srcPt = (Point2D.Double) pair.getKey();
            Point2D.Double destPt = (Point2D.Double) pair.getValue();

            Point2D.Double srcPt2 = (Point2D.Double) transform.inverseTransform(destPt, null);
            Point2D.Double destPt2 = (Point2D.Double) transform.transform(srcPt, null);

            srcDevSqSum += srcPt.distanceSq(srcPt2);
            destDevSqSum += destPt.distanceSq(destPt2);

         } catch (NoninvertibleTransformException ex) {
            throw new Exception("Singular matrix encountered.");
         }
      }

      int n = pointPairs.size();
      double srcRMS = Math.sqrt(srcDevSqSum / n);
      double destRMS = Math.sqrt(destDevSqSum / n);

      if (srcRMS > srcTol || destRMS > destTol) {
         throw new Exception("Point mapping scatter exceeds tolerance.");
      }

      return transform;
   }

   public static double getScalingFactor(AffineTransform transform) {
      return Math.sqrt(Math.abs(transform.getDeterminant()));
   }

   public static double clip(double min, double val, double max) {
      return Math.min(Math.max(min, val), max);
   }

   public static int clip(int min, int val, int max) {
      return Math.min(Math.max(min, val), max);
   }

   public static void runAffineTest() {

      Map<Point2D.Double, Point2D.Double> pointPairs = new HashMap<Point2D.Double, Point2D.Double>();


      // Create sample src and dest points:
      pointPairs.put(new Point2D.Double(1, 1), new Point2D.Double(18, 2));
      pointPairs.put(new Point2D.Double(1, 9), new Point2D.Double(2, 2));
      pointPairs.put(new Point2D.Double(9, 9), new Point2D.Double(2, 18));
      pointPairs.put(new Point2D.Double(9, 1), new Point2D.Double(18, 18));

      // Run the computation to be tested:
      AffineTransform affineTransform = generateAffineTransformFromPointPairs(pointPairs);

      // Print input and output:
      System.out.println(pointPairs);
      System.out.println(affineTransform);

      int i = 0;
      // Check that affineTransform works correctly:
      for (Map.Entry pair : pointPairs.entrySet()) {
         Point2D.Double uPt = (Point2D.Double) pair.getKey();
         Point2D.Double vPt = (Point2D.Double) pair.getValue();
         Point2D.Double result = new Point2D.Double();
         affineTransform.transform(uPt, result);
         System.out.println(uPt + "->" + result + " residual: " + vPt.distance(result));
         i++;
      }
   }
}
