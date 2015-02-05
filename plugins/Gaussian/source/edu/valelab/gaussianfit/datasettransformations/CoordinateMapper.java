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
 
package edu.valelab.gaussianfit.datasettransformations;

import ags.utils.KdTree;
import ags.utils.KdTree.Entry;
import edu.valelab.gaussianfit.spotoperations.NearestPoint2D;
import edu.valelab.gaussianfit.utils.ListUtils;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.DecompositionSolver;
import org.apache.commons.math.linear.LUDecompositionImpl;
import org.apache.commons.math.linear.QRDecompositionImpl;
import org.apache.commons.math.linear.RealMatrix;
import org.micromanager.utils.ReportingUtils;

/**
 * Provides facilities for mapping one coordinate system into another
 * Currently implements LWM and affine transforms implementation of 
 * Local Weighted Mean algorithm,as first described by Ardeshir Goshtasby. 
 * (1988). Image registration by local approximation methods, 
 * and used in Matlab by cp2tform
 * @author Arthur
 */

public class CoordinateMapper {
   final private ExponentPairs exponentPairs_;
   final private ControlPoints controlPoints_;
   final private EnhancedKDTree kdTree_;
   final private int order_;
   final private PointMap pointMap_;
   private PointMap cleanedPointMap_ = null;
   private AffineTransform af_;
   final private AffineTransform rbAf_;
   final public static int LWM = 1;
   final public static int AFFINE = 2;
   final public static int NONRFEFLECTIVESIMILARITY = 3;
   final public static int PIECEWISEAFFINE = 4;
   
   private int method_ = LWM;
   private int pieceWiseAffineMaxControlPoints_ = 100;
   private double pieceWiseAffineMaxDistance_ = 500.0;

   /**
    * Shorthand name
    */
   public static class PointMap extends 
           HashMap<Point2D.Double, Point2D.Double> {
   
      public PointMap copy() {
         PointMap myCopy = new PointMap();
         for (Point2D.Double key : this.keySet()) {
            myCopy.put(key, this.get(key));
         }
         return myCopy;
      }
      
   }

   /**
    * Utility class
    */
   public static class ExponentPair
   {
      public int xExponent;
      public int yExponent;
   }

   /**
    * Shorthand name
    */
   public static class ExponentPairs extends ArrayList<ExponentPair> {}

   /**
    * Utility class
    */
   public static class PolynomialCoefficients {
      public double[] polyX;
      public double[] polyY;
   }

   /**
    * Weightfunction as defined in Ardeshir Goshtasby. (1988).  
    * Used for LWM only
    * @param r distance of given control point to experimental point
    * @return weight for this control point
    */
   public static double weightFunction(double r) {
      return (r < 1) ? (1 + (-3 * r * r) + (2 * r * r * r)) : 0;
   }

   /**
    * Generates exponents for given order, see Ardeshir Goshtasby (1988).
    * Only used in LWM
    * @param order
    * @return ArrayList of double[], double[]
    */
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

   /**
    * Utility class that functions as a bridge between KdTree implementation
    * and the data structures used in our code
    */
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
      
      /**
       * Variant of nearestNeighbor that returns up to size points closest to
       * testPoint at a maximum distance of maxDistance
       * @param testPoint - point for which we want nearest neighbors
       * @param size - max number of neighbors to be returned
       * @param maxDistance - maximum distance of the neighbors to the testpoint
       * @return list with found neighbors that fulfill the criteria
       */
      public List<Point2D.Double> nearestNeighbor(Point2D.Double testPoint,
              int size, double maxDistance) {
         List<Point2D.Double> nearestNeighbors = nearestNeighbor(testPoint, size, true);
         // reverse thought the list and remove items until distance is smaller than 
         // maxDistance
         final double maxDistanceSquare = maxDistance * maxDistance;
         ListIterator li = nearestNeighbors.listIterator(nearestNeighbors.size());
         boolean goOn = true;
         while (li.hasPrevious() && goOn) {
            Point2D.Double controlPoint = (Point2D.Double) li.previous();
            if (NearestPoint2D.distance2(testPoint, controlPoint) > maxDistanceSquare) {
               li.remove();
            } else {
               goOn = false;
            }
         }
         return nearestNeighbors;
      }

   }

   /**
    * Selects a subset of points in the input PointMap, which is a:
    *    HashMap<Point2D.Double, Point2D.Double> 
    * @param points - Input pointMap
    * @param srcPoints - List with keys that we want to select in the input
    * @return PointMap (selected subset of the input)
    */
   public static PointMap selectPoints(PointMap points, List<Point2D.Double> srcPoints) {
      PointMap selectPointMap = new PointMap();
      for (Point2D.Double srcPoint:srcPoints) {
         selectPointMap.put(srcPoint, points.get(srcPoint));
      }
      return selectPointMap;
   }
   

   /**
    * ControlPoint as used in LWM 
    */
   public static class ControlPoint {
      final public Point2D.Double point;
      final public double Rnormalized;
      final public PolynomialCoefficients polynomialCoefficients;

      /**
       * Generates a Controlpoint for the given srcPoint.
       * First finds n (order!) control points around scrPoint
       * Then fits a polynomial to minimize the error between points in the 
       * two channels
       * @param kdTree - kdTree used to find the nearest neighbor in the input
       *                   channel
       * @param srcPoint - point in the input channel used to generate a control 
       *                   point.
       * @param order - desired order of the polynomial
       * @param pointMap - input map with channel 1 points as keys and channel 2 
       *                   points as values
       */
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

   /**
    * Shorthand notation
    */
   public static class ControlPoints extends 
           HashMap<Point2D.Double, ControlPoint> {
   
      public ControlPoints copy() {
         ControlPoints clone = new ControlPoints();
         for (Point2D.Double key : this.keySet()) {
            clone.put(key, this.get(key));
         }
         return clone;
      }
   }

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

   /**
    * For each point in the input pointMap, calculates a control-point, which
    * contains polynomial coefficients in x and y, as well as a normalization factor
    * the polynomial coefficients are calculated by fitting a polynomial to minimize
    * the error in the nearest neighbor pairs
    * @param kdTree - 
    * @param order
    * @param pointMap
    * @return 
    */
   public static ControlPoints createControlPoints(EnhancedKDTree kdTree, int order,
           PointMap pointMap) {
      final ControlPoints controlPointMap = new ControlPoints();
      for (Point2D.Double srcPoint:pointMap.keySet()) {
         controlPointMap.put(srcPoint, new ControlPoint(kdTree, srcPoint, order, pointMap));
      }
      return controlPointMap;
   }

   /**
    * Computes the transform of Ch1 into Ch2 coordinates using the LWM
    * @param kdTree
    * @param testPoint
    * @param controlPoints
    * @param exponentPairs
    * @return 
    */
   public static Point2D.Double computeTransformation(EnhancedKDTree kdTree, 
           Point2D.Double testPoint, ControlPoints controlPoints, ExponentPairs exponentPairs) {
      final List<Point2D.Double> neighbors = kdTree.nearestNeighbor(testPoint, 20, false);
      double sumWeights = 0;
      double sumWeightedPolyX = 0;
      double sumWeightedPolyY = 0;
      int count = 0;
      for (Point2D.Double srcPoint:neighbors) {
         final ControlPoint controlPoint = controlPoints.get(srcPoint);
         final double r = testPoint.distance(controlPoint.point) / controlPoint.Rnormalized;
         final double weight = weightFunction(r);
         if (weight > 0) {
            count++;
            sumWeights += weight;
            sumWeightedPolyX += weight * evaluatePolynomial(testPoint.x, testPoint.y,
                    controlPoint.polynomialCoefficients.polyX, exponentPairs);
            sumWeightedPolyY += weight * evaluatePolynomial(testPoint.x, testPoint.y,
                    controlPoint.polynomialCoefficients.polyY, exponentPairs);
         }
      }
      System.out.println("Used " + count + "  controlpoints in computeTransform");
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
     
      // Create an AffineTransform object from the elements of m
      // (the last row is omitted as specified in AffineTransform class):
      return new AffineTransform(m[0][0], m[1][0], m[0][1], m[1][1], m[0][2], m[1][2]);
   }
   
        
   public static void logAffineTransform(AffineTransform af) {
      AffineTransform tmp = new AffineTransform(af);
      try {
         AffineTransform inv = tmp.createInverse();
         ij.IJ.log(inv.toString());
      } catch (NoninvertibleTransformException ex) {
         ReportingUtils.logError(ex, "Problem while printing affine transform");
      }
   }

 
    /**
    * Rigid body transform (rotation and translation only)
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
   
   /**
    * Generates an affine transform using only control points that are close by
    * The code finds the closest by maxNrControlPoints and then removes any points
    * that are more than maxDinstance away from the testPoint
    * It uses the resulting set of control points to generate an affine transform
    * @param srcTestPoint Input test point
    * @param maxNrControlPoints - Number of desired neighboring control points
    * @param maxDistance - Distance above which a control point will be rejected
    * @return Affine transform calculated from the neighboring control points
    */     
   public AffineTransform generateLocalAffineTransform(
           Point2D.Double srcTestPoint, int maxNrControlPoints, double maxDistance) {

      List<Point2D.Double> nearestNeighbors
              = kdTree_.nearestNeighbor(srcTestPoint, maxNrControlPoints, maxDistance);
      if (nearestNeighbors.size() > 10) {
         PointMap localMap = selectPoints(pointMap_, nearestNeighbors);
         return generateAffineTransformFromPointPairs(localMap);
      }
      return null;
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
            if (cleanedPointMap_ == null) {
               cleanedPointMap_ = makeCleanedPointMap();
               af_ = generateAffineTransformFromPointPairs(cleanedPointMap_);
               logAffineTransform(af_);
               ij.IJ.log("Used " + cleanedPointMap_.size() + 
                       " spot pairs to calculate 2C reference");
            }
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
      if (method_ == PIECEWISEAFFINE) {
         try {
            AffineTransform piecewiseAf = generateLocalAffineTransform(srcTestPoint,
                    pieceWiseAffineMaxControlPoints_, pieceWiseAffineMaxDistance_);
            if (piecewiseAf != null) {
               Point2D result = piecewiseAf.transform(srcTestPoint, null);
               return (Point2D.Double) result;
            }
         } catch (Exception ex) {
            return null;
         }
      }
      return null;
   }
   
   public void setMethod(int method) {
      method_ = method;
   }
   
   public void setPieceWiseAffineMaxControlPoints(int max) {
      pieceWiseAffineMaxControlPoints_ = max;
   }
   
   public void setPieceWiseAffineMaxDistance(double max) {
      pieceWiseAffineMaxDistance_ = max;
   }
   
   private PointMap makeCleanedPointMap() {
      PointMap cleanedPointMap = pointMap_.copy();
      // TODO: copy controlPOints to cleanedControlPoints
      boolean continueQualityCheck = true;
      int nrOfRemovedSpots = 0;

      while (continueQualityCheck && cleanedPointMap.size() > 4) {
         // quality control on our new coordinate mapper.  
         // Apply an affine transform on our data and check distribution 
        
         CoordinateMapper.PointMap corPoints = new CoordinateMapper.PointMap();
         List<Double> distances = new ArrayList<Double>();
         double maxDistance = 0.0;
         AffineTransform af = generateAffineTransformFromPointPairs(cleanedPointMap);
         Point2D.Double maxPairKey = null;
         for (Map.Entry pair : cleanedPointMap.entrySet()) {
            Point2D.Double uPt = (Point2D.Double) pair.getValue();
            Point2D.Double otherPt = (Point2D.Double) pair.getKey();
            Point2D.Double corPt = (Point2D.Double) af.transform(otherPt, null);
            corPoints.put(uPt, corPt);
            double distance = Math.sqrt(NearestPoint2D.distance2(uPt, corPt));
            if (distance > maxDistance) {
               maxDistance = distance;
               maxPairKey = otherPt;
            }
            distances.add(distance);
         }
         Double avg = ListUtils.listAvg(distances);
         Double stdDev = ListUtils.listStdDev(distances, avg);

         // Quality control check
         if (2 * stdDev > avg) {
            nrOfRemovedSpots += 1;
            cleanedPointMap.remove(maxPairKey);
         } else {
            continueQualityCheck = false;
            ij.IJ.log("Removed " + nrOfRemovedSpots + " pairs, " + " avg. distance: "
                    + avg + ", std. dev: " + stdDev);
         }
      }
      return cleanedPointMap;
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
      logAffineTransform(af_);
      
      // set up Rigid Body
      rbAf_ = generateRigidBodyTransform(pointMap);      
   }   
   
   public AffineTransform getAffineTransform() {
      return af_;
   }
   
}