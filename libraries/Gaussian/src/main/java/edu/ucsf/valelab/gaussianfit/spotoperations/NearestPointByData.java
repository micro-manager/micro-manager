/**
 * Utility class to find the nearest point given a List of PointData
 *
 * @copyright UCSF, Dec. 2012
 * @author - Nico Stuurman, Dec. 2012
 * <p>
 * <p>
 * Copyright (c) 2012-2017, Regents of the University of California All rights reserved.
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
package edu.ucsf.valelab.gaussianfit.spotoperations;

import ags.utils.KdTree;
import edu.ucsf.valelab.gaussianfit.data.PointData;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Class that finds the closest by point in a point collection given a single point
 * <p>
 * The method findKDWSE uses a kd tree approach based written by Rednaxela
 *
 * @author nico
 */
public class NearestPointByData {

   private final List<? extends PointData> theList_;
   private final double maxDistance_;
   private final double maxDistanceSquared_;
   private KdTree<Integer> we_;

   public NearestPointByData(List<? extends PointData> unsorted, double maxDistance) {
      theList_ = unsorted;
      maxDistance_ = maxDistance;
      maxDistanceSquared_ = maxDistance * maxDistance;
   }

   /**
    * method to find the nearest point in the collection of Points Uses Squared Euclidian distance
    * method from Rednaxela TODO: evaluate if the copy of the spot is actually needed.
    *
    * @param input - point for which we want to find the nearest neighbor
    * @return copy of the point found or null when it was farther away than the cutoff set in the
    * constructor
    */
   public PointData findKDWSE(Point2D.Double input) {
      // construct a new KD tree if needed
      if (we_ == null) {
         we_ = new KdTree.SqrEuclid<Integer>(2, 50 * theList_.size());
         for (int i = 0; i < theList_.size(); i++) {
            Point2D.Double p = theList_.get(i).getPoint();
            double[] point = {p.x, p.y};
            we_.addPoint(point, i);
         }
      }
      double[] testPoint = {input.x, input.y};
      List<KdTree.Entry<Integer>> result = we_.nearestNeighbor(testPoint, 1, false);

      if (result != null && !result.isEmpty()) {
         Integer index = result.get(0).value;
         double distance = result.get(0).distance;

         PointData ret = theList_.get(index);

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
    * @return point found or null when it was farther away than the cutoff set in the constructor
    */
   public PointData findBF(Point2D.Double input) {
      PointData closestPoint = theList_.get(0);
      double minDist2 = Double.MAX_VALUE;
      for (PointData p : theList_) {
         double dist2 = NearestPoint2D.distance2(input, p.getPoint());
         if (dist2 < minDist2) {
            minDist2 = dist2;
            closestPoint = p;
         }
      }

      double dist = Math.sqrt(NearestPoint2D.distance2(input,
            closestPoint.getPoint()));
      if (dist < maxDistance_) {
         return closestPoint;
      }
      return null;
   }


}
