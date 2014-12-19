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
package edu.valelab.GaussianFit.utils;

import edu.valelab.GaussianFit.GaussianSpotData;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author nico
 */
public class ListUtils {
   
   public static ArrayList<Point2D.Double> spotListToPointList(List<GaussianSpotData> spotList){
      ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
      for (GaussianSpotData gs : spotList) {
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
   
   public static double avgList(ArrayList<Double> vals) {
      double result = 0;
      for (Double val : vals) {
         result += val;
      }
      
      return result / vals.size();
   }
   
   
   public static double stdDevList(ArrayList<Double> vals, double avg) {
      double result = 0;
      for (Double val: vals) {
         result += (val - avg) * (val - avg);
      }
      if (vals.size() < 2)
         return 0.0;
      
      result = result / (vals.size() - 1);
      
      return Math.sqrt(result);
   }

   
}
