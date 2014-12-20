/**
 * Utility class to find the nearest point given an ArrayList of Points
 *
 * 
 * @author Nico Stuurman
 * @copyright UCSF, febr. 2012
 */
package edu.valelab.GaussianFit.spotAssociations;

import ags.utils.KdTree;
import ags.utils.KdTree.Entry;
import ags.utils.KdTree.SqrEuclid;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that finds the closest by point in a point collection given a single point
 * 
 * The method findKDWSE uses a kd tree approach based written by Rednaxela
 * 
 * 
 * @author nico
 */
public class NearestPoint2D {
   private final ArrayList<Point2D.Double> theList_;
   private final double maxDistance_;
   private final double maxDistanceSquared_;
   private KdTree<Integer> we_;
   
   //private final ArrayList<Point2D.Double> sortedByX_;
   //private final ArrayList<Point2D.Double> sortedByY_;
   
   public NearestPoint2D(ArrayList<Point2D.Double> unsorted, double maxDistance) {
      theList_ = unsorted;
      maxDistance_ = maxDistance;
      maxDistanceSquared_ = maxDistance * maxDistance;
   }
   
   /**
    *  method to find the nearest point in the collection of Points
    * Uses Squared Euclidian distance method from Rednaxela
    * 
    * @param input - point for which we want to find the nearest neighbor
    * @return point found or null when it was farther away than the cutoff set 
    * in the constructor
    */
   public Point2D.Double findKDWSE(Point2D.Double input) {
      // construct a new KD tree if needed
      if (we_ == null) {
         we_ = new SqrEuclid<Integer>(2, 50 * theList_.size());
         for (int i = 0; i < theList_.size(); i++) {
            Point2D.Double p = theList_.get(i);
            double[] point = {p.x, p.y};
            we_.addPoint(point, i);
         }
      }
      double[] testPoint = {input.x, input.y};
      List<Entry<Integer>> result = we_.nearestNeighbor(testPoint, 1, false);
      
      Integer index = result.get(0).value;
      double distance = result.get(0).distance;
      
      Point2D.Double ret = (Point2D.Double) theList_.get(index).clone();
      
      if (distance < maxDistanceSquared_)
         return ret;
      
      return null;
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
      for (Point2D.Double p : theList_) {
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
   
   
   /**
    * Calculates the square of the distance between two points as
    * square(difference in x) + square (difference in y)
    * 
    * @param p1 first point
    * @param p2 second point
    * @return square of the distance
    */
   public static double distance2(Point2D.Double p1, Point2D.Double p2) {
      double x = p1.getX() - p2.getX();
      double y = p1.getY() - p2.getY();
      return ( (x * x) + (y * y) );  
   }
   
   /**
    * Calculates the orientation of these points with respect to each other.
    * Draw a circle with point 1 at the center, and p2 on the perimeter
    * Return the sine of the line connecting p1 and p2
    * 
    * @param p1
    * @param p2
    * @return sine of the line connecting p1 and p2 in relation to their shared 
    * coordinate system
    */
   public static double orientation(Point2D.Double p1, Point2D.Double p2) {
      double x = p2.getX() - p1.getX();
      double y = p2.getY() - p1.getY();
      double hyp = Math.sqrt( (x * x) + (y * y));
      
      if (hyp > 0.0) {
       return y /hyp;
      }
      
      return 0.0;
   }
   

           
   
}
