/**
 * ListUtils
 * <p>
 * Static functions providing niceties for Lists
 *
 * @author - Nico Stuurman,  2013
 * <p>
 * <p>
 * Copyright (c) 2013-2017, Regents of the University of California All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * The views and conclusions contained in the software and documentation are those of the authors
 * and should not be interpreted as representing official policies, either expressed or implied, of
 * the FreeBSD Project.
 */
package edu.ucsf.valelab.gaussianfit.utils;

import edu.ucsf.valelab.gaussianfit.data.SpotData;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nico
 */
public class ListUtils {

   public static ArrayList<Point2D.Double> spotListToPointList(List<SpotData> spotList) {
      ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
      for (SpotData gs : spotList) {
         Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
         xyPoints.add(point);
      }
      return xyPoints;
   }

   public static Point2D.Double avgXYList(ArrayList<Point2D.Double> xyPoints) {
      Point2D.Double myAvg = new Point2D.Double(0.0, 0.0);
      for (Point2D.Double point : xyPoints) {
         myAvg.x += point.x;
         myAvg.y += point.y;
      }

      myAvg.x = myAvg.x / xyPoints.size();
      myAvg.y = myAvg.y / xyPoints.size();

      return myAvg;
   }

   public static Point2D.Double stdDevsXYList(ArrayList<Point2D.Double> xyPoints,
         Point2D.Double avg) {
      Point2D.Double myStdDev = new Point2D.Double(0.0, 0.0);
      for (Point2D.Double point : xyPoints) {
         myStdDev.x += (point.x - avg.x) * (point.x - avg.x);
         myStdDev.y += (point.y - avg.y) * (point.y - avg.y);
      }

      myStdDev.x = Math.sqrt(myStdDev.x / (xyPoints.size() - 1));
      myStdDev.y = Math.sqrt(myStdDev.y / (xyPoints.size() - 1));

      return myStdDev;
   }

   public static double stdDevXYList(ArrayList<Point2D.Double> xyPoints, Point2D.Double avg) {
      /* method 1
      Point2D.Double xyStdDevs = stdDevsXYList(xyPoints, avg);
      double result = Math.sqrt(xyStdDevs.x * xyStdDevs.x + xyStdDevs.y * xyStdDevs.y);
      return result;
      */
      // method 2: http://stats.stackexchange.com/questions/65640/how-to-calculate-2d-standard-deviation-with-0-mean-bounded-by-limits
      double sum = 0.0;
      for (Point2D.Double point : xyPoints) {
         double xDiff = point.x - avg.x;
         double yDiff = point.y - avg.y;
         sum += xDiff * xDiff + yDiff * yDiff;
      }
      return Math.sqrt(sum / (xyPoints.size() - 1));

   }

   /**
    * Calculates the average of a list of doubles
    *
    * @param <T>
    * @param vals
    * @return average
    */
   public static <T extends Number> double listAvg(List<T> vals) {
      double result = 0;
      T sample = vals.get(0);
      if (sample instanceof Double) {
         for (T val : vals) {
            result += (Double) val;
         }
      } else if (sample instanceof Float) {
         for (T val : vals) {
            result += (Float) val;
         }
      } else if (sample instanceof Integer) {
         for (T val : vals) {
            result += (Integer) val;
         }
      }

      return result / vals.size();
   }

   public static double avg(double[] numbers) {
      double sum = 0.0;
      for (double num : numbers) {
         sum += num;
      }
      return sum / numbers.length;
   }

   public static double stdDev(double[] numbers, double avg) {
      double result = 0.0;
      for (double val : numbers) {
         result += (val - avg) * (val - avg);
      }
      if (numbers.length < 2) {
         return 0.0;
      }
      result = result / (numbers.length - 1);

      return Math.sqrt(result);
   }

   public static double[] toArray(List<Double> list) {
      double[] result = new double[list.size()];
      for (int i = 0; i < list.size(); i++) {
         result[i] = list.get(i);
      }
      return result;
   }

   /**
    * Returns the Standard Deviation as sqrt( 1/(n-1) sum( square(value - avg)) ) Feeding in
    * parameter avg is just increase performance
    *
    * @param vals - List of doubles
    * @param avg  - Pre-calculated average of this list
    * @return stddev
    */
   public static double listStdDev(List<Double> vals, double avg) {
      double result = 0;
      for (Double val : vals) {
         result += (val - avg) * (val - avg);
      }
      if (vals.size() < 2) {
         return 0.0;
      }

      result = result / (vals.size() - 1);

      return Math.sqrt(result);
   }

   /**
    * Utility function to calculate Standard Deviation
    *
    * @param list - List of doubles
    * @return stdev
    */
   public static double listStdDev(List<Double> list) {
      double avg = listAvg(list);

      return listStdDev(list, avg);
   }

   /**
    * Generates a list of the same size as the input list The output list is generated by randomly
    * selecting each item from the input list (always selecting from the complete list, i.e.
    * sampling with replacement). This operation is useful for bootstrap analysis
    *
    * @param <T>  type of list, should be irrelevant
    * @param list input list
    * @return output list, ready for bootstrap analysis
    */
   public static <T> List<T> listToListForBootstrap(List<T> list) {
      List<T> newList = new ArrayList<T>(list.size());
      int length = list.size();
      for (int i = 0; i < length; i++) {
         int index = (int) Math.floor(Math.random() * (double) list.size());
         newList.add(list.get(index));
      }
      return newList;
   }


}
