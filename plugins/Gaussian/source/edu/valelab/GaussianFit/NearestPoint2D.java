/*
 * Utility class to find the nearest point given an ArrayList of Points
 *
 * 
 * @author Nico Stuurman
 * @copyright UCSF, febr. 2012
 */
package edu.valelab.GaussianFit;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Class that finds the closest by point in a point collection given a single point
 * 
 * Currently, only the method findBF (Brute Force) is implemented
 * This can surely be optimized
 * 
 * 
 * @author nico
 */
public class NearestPoint2D {
   private ArrayList<Point2D.Double> theList_;
   private final double maxDistance_;
   //private final ArrayList<Point2D.Double> sortedByX_;
   //private final ArrayList<Point2D.Double> sortedByY_;
   
   public NearestPoint2D(ArrayList<Point2D.Double> unsorted, double maxDistance) {
      theList_ = unsorted;
      maxDistance_ = maxDistance;
      
      // Code below 
      /*
      sortedByX_ = new ArrayList<Point2D.Double>(unsorted);
      sortedByY_ = new ArrayList<Point2D.Double>(unsorted);
      Collections.sort (sortedByX_, 
         new Comparator<Point2D.Double>(){
            public int compare(Point2D.Double a, Point2D.Double b) {
               double aa = a.getX();
               double bb = b.getX();
               if ( (aa - bb) > 0.0)
                  return 1;
               if ( (aa - bb) < 0.0)
                  return -1;
               return 0;
            }
         }
      );
      Collections.sort (sortedByY_, 
         new Comparator<Point2D.Double>(){
            public int compare(Point2D.Double a, Point2D.Double b) {
               double aa = a.getY();
               double bb = b.getY();
               if ( (aa - bb) > 0.0)
                  return 1;
               if ( (aa - bb) < 0.0)
                  return -1;
               return 0;
            }
         }
      );
      */
   }
   
   
   
   /**
    * Brute force method to find the nearest point in the collection of Points
    * 
    * @param input - point for which we want to find the nearest neighbor
    * @return point found or null when it was farther away than the cutoff set 
    * in the constructor
    */
   public Point2D.Double findBF(Point2D.Double input) {
      Point2D.Double closestPoint = input;
      double minDist2 = Double.MAX_VALUE;
      Iterator it = theList_.iterator();
      while (it.hasNext()) {
         Point2D.Double p = (Point2D.Double) it.next();
         double dist2 = distance2(input, p);
         if (dist2 < minDist2) {
            minDist2 = dist2;
            closestPoint = p;
         }
      }
      
      double dist = Math.sqrt(distance2(input, closestPoint));
      if (dist < maxDistance_)
         return closestPoint;
      return null;
   }
   
   public static double distance2(Point2D.Double p1, Point2D.Double p2) {
      double x = p1.getX() - p2.getX();
      double y = p1.getY() - p2.getY();
      return ( (x * x) + (y * y) );  
   }
   

           
   
}
