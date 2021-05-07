/*
 * Utility functions to calculate Math related stuff, such as standard deviations
 * in complicated scenarios

* Nico Stuurman, nico.stuurman at ucsf.edu
 * 
 * 
 * @author - Nico Stuurman, 2013
 * 
 * 
Copyright (c) 2013-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */


package edu.ucsf.valelab.gaussianfit.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author nico
 */
public class CalcUtils {

   /**
    * Function to calculate the standard deviation in the distance of a line between 2 points given
    * the uncertainty in measuring the two points See: http://casa.colorado.edu/~keeney/classes/astr3510/handouts/stats.pdf
    *
    * @param x1  x position of point 1
    * @param x2  x position of point 2
    * @param y1  y position of point 1
    * @param y2  y positions of point 2
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
      double d = Math.sqrt((p * p) + (q * q));
      double varp = (sx1 * sx1) + (sx2 * sx2);
      double varq = (sy1 * sy1) + (sy2 * sy2);

      double tr = Math.sqrt((p * p * varp) + (q * q * varq));
      return tr / d;
   }

   /**
    * Utility function that calculates the mean from a array of doubles
    *
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
    * Utility function that calculates the standard deviation of an array of doubles
    *
    * @param data - input array of doubles
    * @param mean - mean of the input array (provided for performance reasons)
    * @return stddev (as sqrt of sum of errors squared / (n-1) )
    */
   public static double stdDev(double[] data, double mean) {
      double ersq = 0.0;
      for (double d : data) {
         ersq += (d - mean) * (d - mean);
      }
      return Math.sqrt(ersq / (data.length - 1));
   }

   /**
    * Find the index of the maximum value in an array
    *
    * @param data
    * @return
    */
   public static int maxIndex(double[] data) {
      if (data == null || data.length < 1) {
         return -1;
      }
      int val = 0;
      double max = data[0];
      for (int i = 0; i < data.length; i++) {
         if (data[i] > max) {
            val = i;
            max = data[i];
         }
      }
      return val;
   }

   public static int[] indicesToValuesClosest(double[] data, double target) {
      Map<Double, Integer> unsortedMap = new HashMap<Double, Integer>();
      for (int i = 0; i < data.length; i++) {
         unsortedMap.put(Math.abs(data[i] - target), i);
      }
      Map<Double, Integer> sortedMap = new TreeMap<Double, Integer>(unsortedMap);
      int[] output = new int[sortedMap.size()];
      Iterator<Map.Entry<Double, Integer>> iterator = sortedMap.entrySet().iterator();
      int i = 0;
      for (Map.Entry<Double, Integer> entry : sortedMap.entrySet()) {
         output[i] = entry.getValue();
         i++;
      }
      return output;
   }

}
