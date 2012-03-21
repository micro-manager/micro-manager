package org.micromanager.lwm;

import ags.utils.KdTree;
import java.awt.geom.Point2D;
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
   final private KdTree kdTree_;
   final private int order_;
   final private Map<Point2D.Double, Point2D.Double> pointMap_;

   public static class ExponentPair
   {
      public int xExponent;
      public int yExponent;
   }

   public static interface ExponentPairs extends List<ExponentPair> {}

   public static class PolynomialCoefficients {
      public double[] polyX;
      public double[] polyY;
   }

   public static double weightFunction(double R) {
      return (1 + (-3 * R * R) + (2 * R * R * R));
   }

   public static ExponentPairs polynomialExponents(int order) {
      final ExponentPairs exponents = (ExponentPairs) new ArrayList<ExponentPair>();
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

   public static class ControlPoint {
      final public Point2D.Double point;
      final public double Rnormalized;
      final public PolynomialCoefficients polynomialCoefficients;

      public ControlPoint(KdTree kdTree, Point2D.Double srcPoint,
              int order, Map<Point2D.Double, Point2D.Double> pointMap) {
         point = srcPoint;
         ExponentPairs exponentPairs = polynomialExponents(order);
         List<Point2D.Double> neighbors = kdTree.nearestNeighbor(new double[] {srcPoint.x, srcPoint.y},
                 exponentPairs.size(), true);
         Rnormalized = neighbors.get(0).distance(srcPoint);
         polynomialCoefficients = fitPolynomial(exponentPairs, pointMap);
      }
   }

   public static interface ControlPoints extends Map<Point2D.Double, ControlPoint> {}

   public static double[] powerTerms(double x, double y,
           final ExponentPairs exponentPairs) {
      final double[] powerTerms = new double[exponentPairs.size()];
      int i = 0;
      for (ExponentPair exponentPair:exponentPairs) {
         ++i;
         powerTerms[i] = Math.pow(x, exponentPair.xExponent)*Math.pow(y, exponentPair.yExponent);
      }
      return powerTerms;
   }

   public static PolynomialCoefficients fitPolynomial(ExponentPairs exponentPairs,
           Map<Point2D.Double, Point2D.Double> pointPairs) {
      final List<Point2D.Double> srcPoints = new ArrayList(pointPairs.keySet());
      final RealMatrix matrix = new Array2DRowRealMatrix();
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

   public static ControlPoints createControlPoints(KdTree kdTree, int order,
           Map<Point2D.Double, Point2D.Double> pointMap) {
      final ControlPoints controlPointMap = (ControlPoints) new HashMap<Point2D.Double, ControlPoint>();
      for (Point2D.Double srcPoint:pointMap.keySet()) {
         controlPointMap.put(srcPoint, new ControlPoint(kdTree, srcPoint, order, pointMap));
      }
      return controlPointMap;
   }

   public static Point2D.Double computeTransformation(KdTree kdTree, Point2D.Double testPoint, ControlPoints controlPoints, ExponentPairs exponentPairs) {
      final List<Point2D.Double> neighbors = kdTree.nearestNeighbor(new double [] {testPoint.x, testPoint.y}, 20, false);
      double sumWeights = 0;
      double sumWeightedPolyX = 0;
      double sumWeightedPolyY = 0;
      for (Point2D.Double srcPoint:neighbors) {
         final ControlPoint controlPoint = controlPoints.get(srcPoint);
         final double r = testPoint.distance(controlPoint.point) / controlPoint.Rnormalized;
         final double weight = weightFunction(r);
         sumWeights += weight;
         sumWeightedPolyX += evaluatePolynomial(testPoint.x, testPoint.y,
                 controlPoint.polynomialCoefficients.polyX, exponentPairs);
         sumWeightedPolyY += evaluatePolynomial(testPoint.x, testPoint.y,
                 controlPoint.polynomialCoefficients.polyY, exponentPairs);
      }
      return new Point2D.Double(sumWeightedPolyX / sumWeights,
                                sumWeightedPolyY / sumWeights);
   }

   public static KdTree setupKdTree(Point2D.Double[] points) {
      final KdTree kdTree = new KdTree.SqrEuclid<Double>(2, Integer.MAX_VALUE);
      for (int i = 0; i<points.length; ++i) {
         final Point2D.Double point = points[i];
         kdTree.addPoint(new double[] {point.x, point.y}, i);
      }
      return kdTree;
   }

   public LocalWeightedMean(int order, Map<Point2D.Double, Point2D.Double> pointMap) {
      pointMap_ = pointMap;
      order_ = order;
      kdTree_ = setupKdTree((Point2D.Double []) pointMap.keySet().toArray());
      exponentPairs_ = polynomialExponents(order);
      controlPoints_ = createControlPoints(kdTree_, order_, pointMap_);
   }

   public Point2D.Double transform(Point2D.Double srcTestPoint) {
      return computeTransformation(kdTree_, srcTestPoint, controlPoints_, exponentPairs_);
   }
         
}