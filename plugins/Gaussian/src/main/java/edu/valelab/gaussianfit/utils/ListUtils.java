/**
 * ListUtils
 * 
 * Static functions providing niceties for Lists
 * 
 * Copyright UCSF, 2013
 * 
 * Licensed under BSD version 2.0 
 *
 * 
 */
package edu.valelab.gaussianfit.utils;

import edu.valelab.gaussianfit.data.SpotData;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author nico
 */
public class ListUtils {
   
   public static ArrayList<Point2D.Double> spotListToPointList(List<SpotData> spotList){
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
   
   public static Point2D.Double stdDevXYList(ArrayList<Point2D.Double> xyPoints, 
           Point2D.Double avg) {
      Point2D.Double myStdDev = new Point2D.Double(0.0, 0.0);
      for (Point2D.Double point : xyPoints) {
         myStdDev.x += (point.x - avg.x) * (point.x - avg.x);
         myStdDev.y += (point.y - avg.y) * (point.y - avg.y);
      }
      
      myStdDev.x = Math.sqrt(myStdDev.x / (xyPoints.size() - 1) ) ;
      myStdDev.y = Math.sqrt(myStdDev.y / (xyPoints.size() - 1) ) ;
      
      return myStdDev;
   }
   
    /**
    * Calculates the average of a list of doubles
    * 
    * @param vals
    * @return average
    */
   public static double listAvg(List<Double> vals) {
      double result = 0;
      for (Double val : vals) {
         result += val;
      }
      
      return result / vals.size();
   }
   
   /**
    * Returns the Standard Deviation as sqrt( 1/(n-1) sum( square(value - avg)) )
    * Feeding in parameter avg is just increase performance
    * 
    * @param vals - List of doubles
    * @param avg - Pre-calculated average of this list
    * @return stddev
    */
   public static double listStdDev(List<Double> vals, double avg) {
      double result = 0;
      for (Double val: vals) {
         result += (val - avg) * (val - avg);
      }
      if (vals.size() < 2)
         return 0.0;
      
      result = result / (vals.size() - 1);
      
      return Math.sqrt(result);
   }

   /**
    * Utility function to calculate Standard Deviation
    * @param list - List of doubles 
    * @return stdev
    */
   private static double listStdDev (List<Double> list) {
      double avg = listAvg(list);
      
      return listStdDev(list, avg);
   }
}
