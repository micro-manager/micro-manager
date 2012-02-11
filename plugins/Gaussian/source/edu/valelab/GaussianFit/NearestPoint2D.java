/*
 * Utility class to find the nearest point given an ArrayList of Points
 * 
 * Inspired by http://algs4.cs.princeton.edu/99hull/ClosestPair.java.html
 * 
 * @author Nico Stuurman
 * @copyright UCSF 2012
 */
package edu.valelab.GaussianFit;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 *
 * @author nico
 */
public class NearestPoint2D {
   private final ArrayList<Point2D.Double> sortedByX_;
   private final ArrayList<Point2D.Double> sortedByY_;
   
   public NearestPoint2D(ArrayList<Point2D.Double> unsorted) {
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
      
   }
   
   public Point2D.Double find(Point2D.Double input) {
      return input;
   }
   
   
}
