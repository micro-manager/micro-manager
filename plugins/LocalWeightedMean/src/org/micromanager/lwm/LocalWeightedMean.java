package org.micromanager.lwm;

import ags.utils.KdTree;
import ags.utils.KdTree.Entry;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.DecompositionSolver;
import org.apache.commons.math.linear.LUDecompositionImpl;
import org.apache.commons.math.linear.RealMatrix;

public class LocalWeightedMean {
   final private ExponentPairs exponentPairs_;
   final private ControlPoints controlPoints_;
   final private EnhancedKDTree kdTree_;
   final private int order_;
   final private PointMap pointMap_;

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
      return (1 + (-3 * R * R) + (2 * R * R * R));
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
         List<Entry<Integer>> neighbors = super.nearestNeighbor(new double[] {testPoint.x, testPoint.y},
                 size, ordered);
         List<Point2D.Double> neighborList = new ArrayList<Point2D.Double>();
         for (int i=0; i<neighbors.size(); ++i) {
            neighborList.add(points_[neighbors.get(i).value]);
         }
         return neighborList;
      }
      
   }

   public static PointMap selectPoints(PointMap points, List<Point2D.Double> srcPoints) {
      PointMap selectPointMap = new PointMap();
      for (Double srcPoint:srcPoints) {
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
      final List<Point2D.Double> srcPoints = new ArrayList(pointPairs.keySet());
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
         sumWeights += weight;
         sumWeightedPolyX += weight * evaluatePolynomial(testPoint.x, testPoint.y,
                 controlPoint.polynomialCoefficients.polyX, exponentPairs);
         sumWeightedPolyY += weight * evaluatePolynomial(testPoint.x, testPoint.y,
                 controlPoint.polynomialCoefficients.polyY, exponentPairs);
      }
      return new Point2D.Double(sumWeightedPolyX / sumWeights,
                                sumWeightedPolyY / sumWeights);
   }

   public Point2D.Double transform(Point2D.Double srcTestPoint) {
      return computeTransformation(kdTree_, srcTestPoint, controlPoints_, exponentPairs_);
   }

   public LocalWeightedMean(int order, PointMap pointMap) {
      pointMap_ = pointMap;
      order_ = order;
      exponentPairs_ = polynomialExponents(order);
      final ArrayList<Point2D.Double> keys = new ArrayList<Point2D.Double>();
      keys.addAll(pointMap.keySet());
      final Point2D.Double[] keyArray = keys.toArray(new Point2D.Double[]{});
      kdTree_ = new EnhancedKDTree(keyArray);
      controlPoints_ = createControlPoints(kdTree_, order_, pointMap_);
   }   
}