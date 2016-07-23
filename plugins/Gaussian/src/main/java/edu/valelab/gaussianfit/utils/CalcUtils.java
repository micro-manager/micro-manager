/*
 * Utility functions to calculate Math related stuff, such as standard deviations
 * in complicated scenarios
 */
package edu.valelab.gaussianfit.utils;

/**
 *
 * @author nico
 */
public class CalcUtils {
   /**
    * Function to calculate the standard deviation in the distance of a line
    * between 2 points given the uncertainty in measuring the two points
    * See: http://casa.colorado.edu/~keeney/classes/astr3510/handouts/stats.pdf
    * 
    * @param x1 x position of point 1
    * @param x2 x position of point 2
    * @param y1 y position of point 1
    * @param y2 y positions of point 2
    * @param sx1 std dev. of x component of point 1
    * @param sx2 std dev. of x component of point 2
    * @param sy1 std dev. of y component of point 1
    * @param sy2 std dev. of y component of point2
    * @return calculated standard deviation
    */
   public static double stdDev(double x1, double x2, double y1, double y2,
            double sx1, double sx2, double sy1, double sy2) {
      double p = x1 - x2;
      double q = y1 - y2;
      double d = Math.sqrt( (p*p) + (q*q) );
      double varp = (sx1 * sx1) + (sx2 * sx2);
      double varq = (sy1 * sy1) + (sy2 * sy2);
      
      double tr = Math.sqrt( (p*p*varp) + (q*q*varq));
      return tr / d;
   }
   
   /**
    * Utility function that calculates the mean from a array of doubles
    * @param data - input array of doubles
    * @return - mean
    */
   public static double mean(double[] data) {
      double sum = 0.0;
      for (double d : data) {
         sum += d;
      }
      return sum / data.length;
   }
   
   /**
    * Utility function that calculates the standard deviation of an array of 
    * doubles
    * @param data - input array of doubles
    * @param mean - mean of the input array (provided for performance reasons)
    * @return stddev (as sqrt of sum of errors squared / (n-1) )
    */
   public static double stdDev(double[] data, double mean) {
      double ersq = 0.0;
      for (double d : data) {
         ersq += (d - mean) * (d - mean);
      }
      return Math.sqrt( ersq / (data.length - 1));
   }
   
}
