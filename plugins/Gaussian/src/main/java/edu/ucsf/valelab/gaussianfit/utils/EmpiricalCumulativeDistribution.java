
package edu.ucsf.valelab.gaussianfit.utils;

import java.awt.geom.Point2D;
import java.util.Arrays;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

/**
 *
 * @author nico
 */
public class EmpiricalCumulativeDistribution {
   
   public static Vector2D[] calculate(double[] values) {
      Arrays.sort(values);
      Vector2D[] result = new Vector2D[values.length];
      //final double p = 1d / values.length;
      //final double halfp = p / 0.5d;
      final double valuesd = (double) values.length;
      for (int i = 0; i < values.length; i++) {
         result[i] = new Vector2D(values[i], ((double)i + 1d) / valuesd);
      }
      
      return result;
      
   }
   
}
