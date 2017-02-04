/**
 * Utility class to find the nearest point given an ArrayList GsSpotPairs
 *
 * 
 * @author Nico Stuurman
 * @copyright UCSF, Dec. 2012
 */
package edu.valelab.gaussianfit.spotoperations;
import edu.valelab.gaussianfit.data.GsSpotPair;
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
public class NearestPointGsSpotPair {
   private final ArrayList<GsSpotPair> theList_;
   private final double maxDistance_;
   private final double maxDistanceSquared_;
   private KdTree<Integer> we_;
   
   public NearestPointGsSpotPair(ArrayList<GsSpotPair> unsorted, double maxDistance) {
      theList_ = unsorted;
      maxDistance_ = maxDistance;
      maxDistanceSquared_ = maxDistance * maxDistance;
   }
   
   /**
    * method to find the nearest point in the collection of Points
    * Uses Squared Euclidian distance method from Rednaxela
    * 
    * @param input - point for which we want to find the nearest neighbor
    * @return point found or null when it was farther away than the cutoff set 
    * in the constructor
    */
   public GsSpotPair findKDWSE(Point2D.Double input) {
      // construct a new KD tree if needed
      if (we_ == null) {
         we_ = new SqrEuclid<Integer>(2, 50 * theList_.size());
         for (int i = 0; i < theList_.size(); i++) {
            Point2D.Double p = theList_.get(i).getfp();
            double[] point = {p.x, p.y};
            we_.addPoint(point, i);
         }
      }
      double[] testPoint = {input.x, input.y};
      List<Entry<Integer>> result = we_.nearestNeighbor(testPoint, 1, false);
      
      if (result != null && !result.isEmpty()) {
         Integer index = result.get(0).value;
         double distance = result.get(0).distance;

         GsSpotPair ret = theList_.get(index).copy();

         if (distance < maxDistanceSquared_) {
            return ret;
         }
      }
      
      return null;
   }
   
   /**
    * Brute force method to find the nearest point in the collection of Points
    * 
    * @param input - point for which we want to find the nearest neighbor
    * @return point found or null when it was farther away than the cutoff set 
    * in the constructor
    */
   public GsSpotPair findBF(Point2D.Double input) {
      GsSpotPair closestPoint = theList_.get(0);
      double minDist2 = Double.MAX_VALUE;
      for (GsSpotPair p : theList_) {
         double dist2 = NearestPoint2D.distance2(input, p.getfp());
         if (dist2 < minDist2) {
            minDist2 = dist2;
            closestPoint = p;
         }
      }
      
      double dist = Math.sqrt(NearestPoint2D.distance2(input, 
              closestPoint.getfp()));
      if (dist < maxDistance_)
         return closestPoint;
      return null;
   }
   

   

       
}

