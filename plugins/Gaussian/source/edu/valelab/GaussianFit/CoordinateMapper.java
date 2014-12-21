///////////////////////////////////////////////////////////////////////////////
//FILE:           CoordinateMapper.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Provides facilities for mapping one coordinate system into another
//                Currently implements LWM and affine transforms
//                Implementation of Local Weighted Mean algorithm,
//                as first described by
//                Ardeshir Goshtasby. (1988). Image registration by local approximation methods.
//                and used in Matlab by cp2tform
//-----------------------------------------------------------------------------
//
//AUTHOR:         Arthur Edelstein, arthuredelstein@gmail.com, 2012
//
//COPYRIGHT:      University of California San Francisco
//
//LICENSE:        This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
 
package edu.valelab.GaussianFit;

import ags.utils.KdTree;
import ags.utils.KdTree.Entry;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.DecompositionSolver;
import org.apache.commons.math.linear.LUDecompositionImpl;
import org.apache.commons.math.linear.QRDecompositionImpl;
import org.apache.commons.math.linear.RealMatrix;



public class CoordinateMapper {
   final private ExponentPairs exponentPairs_;
   final private ControlPoints controlPoints_;
   final private EnhancedKDTree kdTree_;
   final private int order_;
   final private PointMap pointMap_;
   final private AffineTransform af_;
   final private AffineTransform rbAf_;
   final public static int LWM = 1;
   final public static int AFFINE = 2;
   final public static int NONRFEFLECTIVESIMILARITY = 3;
   
   private int method_ = LWM;

   public static class PointMap extends HashMap<Point2D.Double, Point2D.Double> {}

   public static class ExponentPair
   {
      public int xExponent;
      public int yExponent;
   }

   public static class ExponentPairs extends ArrayList<ExponentPair> {}

   public static class PolynomialCoefficients {
      public double[] polyX;
      public double[] polyY;
   }

   public static double weightFunction(double R) {
      return (R < 1) ? (1 + (-3 * R * R) + (2 * R * R * R)) : 0;
   }

   public static ExponentPairs polynomialExponents(int order) {
      final ExponentPairs exponents = new ExponentPairs();
      for (int j=0; j<=order; ++j) {
         for (int k = j; k>=0; k--) {
            final ExponentPair pair = new ExponentPair();
            pair.xExponent = k;
            pair.yExponent = j - k;
            exponents.add(pair);
         }
      }
      return exponents;
   }

   public static class EnhancedKDTree extends KdTree.SqrEuclid {
      final Point2D.Double[] points_;

      
      @SuppressWarnings("unchecked")
      EnhancedKDTree(Point2D.Double[] points) {
         super(2, Integer.MAX_VALUE);
         points_ = points;
         for (int i = 0; i < points.length; ++i) {
            final Point2D.Double point = points[i];
            addPoint(new double[]{point.x, point.y}, i);
         }
      }

      public List<Point2D.Double> nearestNeighbor(Point2D.Double testPoint,
              int size, boolean ordered) {
         @SuppressWarnings("unchecked")
         List<Entry<Integer>> neighbors = super.nearestNeighbor(
                 new double[] {testPoint.x, testPoint.y}, size, ordered);
         List<Point2D.Double> neighborList = new ArrayList<Point2D.Double>();
         for (int i=0; i<neighbors.size(); ++i) {
            neighborList.add(points_[neighbors.get(i).value]);
         }
         return neighborList;
      }
      
   }

   public static PointMap selectPoints(PointMap points, List<Point2D.Double> srcPoints) {
      PointMap selectPointMap = new PointMap();
      for (Point2D.Double srcPoint:srcPoints) {
         selectPointMap.put(srcPoint, points.get(srcPoint));
      }
      return selectPointMap;
   }
   

   public static class ControlPoint {
      final public Point2D.Double point;
      final public double Rnormalized;
      final public PolynomialCoefficients polynomialCoefficients;

      public ControlPoint(EnhancedKDTree kdTree, Point2D.Double srcPoint,
              int order, PointMap pointMap) {
         point = srcPoint;
         ExponentPairs exponentPairs = polynomialExponents(order);
         List<Point2D.Double> neighbors = kdTree.nearestNeighbor(srcPoint,
                 exponentPairs.size(), true);
         Rnormalized = neighbors.get(0).distance(srcPoint);
         polynomialCoefficients = fitPolynomial(exponentPairs, selectPoints(pointMap, neighbors));
      }
   }

   public static class ControlPoints extends HashMap<Point2D.Double, ControlPoint> {}

   public static double[] powerTerms(double x, double y,
           final ExponentPairs exponentPairs) {
      final double[] powerTerms = new double[exponentPairs.size()];
      int i = 0;
      for (ExponentPair exponentPair:exponentPairs) {
         powerTerms[i] = Math.pow(x, exponentPair.xExponent)*Math.pow(y, exponentPair.yExponent);
         ++i;
      }
      return powerTerms;
   }

   public static PolynomialCoefficients fitPolynomial(ExponentPairs exponentPairs,
           Map<Point2D.Double, Point2D.Double> pointPairs) {
      final List<Point2D.Double> srcPoints = new ArrayList<Point2D.Double>(pointPairs.keySet());
      final RealMatrix matrix = new Array2DRowRealMatrix(srcPoints.size(), exponentPairs.size());
      for (int i=0; i<srcPoints.size(); ++i) {
         matrix.setRow(i, powerTerms(srcPoints.get(i).x, srcPoints.get(i).y, exponentPairs));
      }
      final DecompositionSolver solver = new LUDecompositionImpl(matrix).getSolver();
      final double [] destX = new double[srcPoints.size()];
      final double [] destY = new double[srcPoints.size()];
      for (int i=0; i<srcPoints.size(); ++i) {
         final Point2D.Double destPoint = pointPairs.get(srcPoints.get(i));
         destX[i] = destPoint.x;
         destY[i] = destPoint.y;
      }
      final PolynomialCoefficients polys = new PolynomialCoefficients();
      polys.polyX = solver.solve(destX);
      polys.polyY = solver.solve(destY);
      return polys;
   }

   public static double evaluatePolynomial(double x, double y, double[] coeffs,
           ExponentPairs exponentPairs) {
      double result = 0;
      for (int i=0;i<coeffs.length;++i) {
           result += coeffs[i] * powerTerms(x, y, exponentPairs)[i];
      }
      return result;
   }

   public static ControlPoints createControlPoints(EnhancedKDTree kdTree, int order,
           PointMap pointMap) {
      final ControlPoints controlPointMap = new ControlPoints();
      for (Point2D.Double srcPoint:pointMap.keySet()) {
         controlPointMap.put(srcPoint, new ControlPoint(kdTree, srcPoint, order, pointMap));
      }
      return controlPointMap;
   }

   public static Point2D.Double computeTransformation(EnhancedKDTree kdTree, Point2D.Double testPoint, ControlPoints controlPoints, ExponentPairs exponentPairs) {
      final List<Point2D.Double> neighbors = kdTree.nearestNeighbor(testPoint, 20, false);
      double sumWeights = 0;
      double sumWeightedPolyX = 0;
      double sumWeightedPolyY = 0;
      for (Point2D.Double srcPoint:neighbors) {
         final ControlPoint controlPoint = controlPoints.get(srcPoint);
         final double r = testPoint.distance(controlPoint.point) / controlPoint.Rnormalized;
         final double weight = weightFunction(r);
         if (weight > 0) {
            sumWeights += weight;
            sumWeightedPolyX += weight * evaluatePolynomial(testPoint.x, testPoint.y,
                    controlPoint.polynomialCoefficients.polyX, exponentPairs);
            sumWeightedPolyY += weight * evaluatePolynomial(testPoint.x, testPoint.y,
                    controlPoint.polynomialCoefficients.polyY, exponentPairs);
         }
      }
      return new Point2D.Double(sumWeightedPolyX / sumWeights,
                                sumWeightedPolyY / sumWeights);
   }
   
   
   /***  Affine Transform (from Micro-Manager Math utils) ***/
   
   
   /**
    * Helper function for generateAffineTransformFromPointPairs
    * @param m
    * @param pt
    * @param row 
    */ 
  private static void insertPoint2DInMatrix(RealMatrix m, Point2D.Double pt, int row) {
      // Set row to [x,y,1]:
      m.setEntry(row, 0, pt.x);
      m.setEntry(row, 1, pt.y);
      m.setEntry(row, 2, 1);
   }
   
    /**
    * Creates an AffineTransform object that maps a source planar coordinate system to
    * a destination planar coordinate system. At least three point pairs are needed.
    * 
    * @param pointPairs - a Map of points measured in the two coordinates systems (srcPt->destPt)
    * @return 
    */
   public static AffineTransform generateAffineTransformFromPointPairs
        (Map<Point2D.Double, Point2D.Double> pointPairs) {
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
      
      AffineTransform tmp = new AffineTransform(m[0][0], m[1][0], m[0][1], m[1][1], m[0][2], m[1][2]);
      try {
         AffineTransform inv = tmp.createInverse();
         ij.IJ.log (inv.toString());
      } catch (NoninvertibleTransformException ex) {
         Logger.getLogger(CoordinateMapper.class.getName()).log(Level.SEVERE, null, ex);
      }
      /*
      double pxSize = 1.0;
      ij.IJ.log("Affine matrix: " + "\n\r" +
              m[0][0]/pxSize + "\t" + m[0][1] + "\t0.0\t" + m[0][2] + "\n" + 
              m[1][0] + "\t" + m[1][1]/pxSize + "\t0.0\t" + m[1][2] + "\n" +
              0.0 + "\t" + 0.0 + "\t" + 1.0 + "\t" + 0.0 + "\n" +
              0.0 + "\t" + 0.0 + "\t" + 0.0 + "\t" + 1.0);
       */

      // Create an AffineTransform object from the elements of m
      // (the last row is omitted as specified in AffineTransform class):
      return new AffineTransform(m[0][0], m[1][0], m[0][1], m[1][1], m[0][2], m[1][2]);
   }
   

   /*** Rigid body transform (rotation and translation only)
   
   
    /**
    * Creates an AffineTransform object that uses only rotation and translation
    * 
    * @param pointPairs - a Map of points measured in the two coordinates systems (srcPt->destPt)
    * @return Affine transform object
    */
   public static AffineTransform generateRigidBodyTransform
        (Map<Point2D.Double, Point2D.Double> pointPairs) {
      int number = pointPairs.size();
      
     
      RealMatrix X = new Array2DRowRealMatrix(2 * number, 4);
      RealMatrix U = new Array2DRowRealMatrix(2 * number, 1);
      
      int i= 0;
      for (Map.Entry<Point2D.Double, Point2D.Double> pair : pointPairs.entrySet()) {
         double[] thisRow = {pair.getKey().x, pair.getKey().y, 1.0, 0.0};
         X.setRow(i, thisRow);
         double[] otherRow = {pair.getKey().y, -pair.getKey().x, 0.0, 1.0};
         X.setRow(i + number, otherRow);
            
         U.setEntry(i, 0, pair.getValue().x);
         U.setEntry(i + number, 0, pair.getValue().y); 
         i++;
      }
      
      DecompositionSolver solver = (new QRDecompositionImpl(X)).getSolver();
      double[][] m = solver.solve(U).getData();
      
      
      return new AffineTransform(m[0][0], m[1][0], - m[1][0], m[0][0], m[2][0], m[3][0]);
   }
   
   
   // General methods 
   

   /**
    * @param srcTestPoint
    * @return
    */
   public Point2D.Double transform(Point2D.Double srcTestPoint) {
      if (method_ == LWM) {
         return computeTransformation(kdTree_, srcTestPoint, controlPoints_, exponentPairs_);
      }
      if (method_ == AFFINE) {
         try {
            return (Point2D.Double) af_.transform(srcTestPoint, null);
         } catch (Exception ex) {
            return null;
         }
      }
      if (method_ == NONRFEFLECTIVESIMILARITY) {
         try {
            return (Point2D.Double) rbAf_.transform(srcTestPoint, null);
         } catch (Exception ex) {
            return null;
         }
      }
      return null;
   }
   
   public void setMethod(int method) {
      method_ = method;
   }
   


   /**
    * Feeds control points into this class
    * Performs initial calculations for lwm and affine transforms
    * @param pointMap pairs of reference points
    * @param order polynomial order for Kd tree 
    * @param method Affine, LWM, non-reflective similarity
    * 
    */
   public CoordinateMapper(PointMap pointMap, int order, int method) {
      pointMap_ = pointMap;
      order_ = order;
      method_ = method;
      
      // Set up LWM
      exponentPairs_ = polynomialExponents(order);
      final ArrayList<Point2D.Double> keys = new ArrayList<Point2D.Double>();
      keys.addAll(pointMap.keySet());
      final Point2D.Double[] keyArray = keys.toArray(new Point2D.Double[]{});
      kdTree_ = new EnhancedKDTree(keyArray);
      controlPoints_ = createControlPoints(kdTree_, order_, pointMap_);
      
      // Set up Affine transform
      af_ = generateAffineTransformFromPointPairs(pointMap);
      
      // set up Rigid Body
      rbAf_ = generateRigidBodyTransform(pointMap);      
   }   
   
   public AffineTransform getAffineTransform() {
      return af_;
   }
   
}