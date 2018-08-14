
package edu.ucsf.valelab.gaussianfit.utils;

import java.awt.geom.Point2D;
import java.util.Arrays;

/**
 *
 * @author nico
 */
public class EmpiricalCumulativeDistribution {
   
   public static Point2D.Double[] calculate(double[] values) {
      Arrays.sort(values);
      Point2D.Double[] result = new Point2D.Double[values.length];
      final double p = 1d / values.length;
      final double halfp = p / 0.5d;
      for (int i = 0; i < values.length; i++) {
         result[i] = new Point2D.Double(values[i], i * p + halfp);
      }
      
      return result;
      
   }
   
}
