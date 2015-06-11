///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package propsandcovariants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import misc.Log;

/**
 *
 * wrapper class that holds precalculated values of relative power based on
 * vertical distance, surface normal value, and radius of curvature
 */
public class CurvedSurfaceCalculations {

   //mean free paths for which vals have been calculated
   private static Integer[] MEAN_FREE_PATHS = new Integer[]{30, 90};
   private static Integer[] RADII_OF_CURVATURE = new Integer[]{600};
   //ponts at which relative power has been calculated
   private static double[] VERTICAL_DISTANCE_POINTS = {0, 20, 40, 60, 80, 100, 120, 140, 160,  180,  200,  220, 240,  260, 280, 300};
   private static double[] NORMAL_POINTS = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90};
   //[radius of curvature][mean free path][normal][vertical distance]
   private static final double[][][][] RELATIVE_POWERS = new double[][][][]{
      //first radius of curvature
      {
         //first mean free path
         {{1.000000, 1.956162, 3.062142, 4.216545, 5.374378, 6.517948, 7.640522, 8.739686, 9.814809, 10.866024, 11.893796, 12.898725, 13.881446, 14.842592, 15.782769, 15.782769},
            {1.000000, 1.950023, 3.110374, 4.357412, 5.619179, 6.863916, 8.080026, 9.264360, 10.416978, 11.539048, 12.632028, 13.697370, 14.736418, 15.750369, 16.740292, 16.740292},
            {1.000000, 1.966496, 3.180014, 4.513880, 5.876458, 7.221048, 8.529177, 9.795824, 11.021389, 12.208105, 13.358597, 14.475363, 15.560622, 16.616293, 17.644024, 17.644024},
            {1.001054, 2.016392, 3.275711, 4.679904, 6.128983, 7.563390, 8.956015, 10.297996, 11.588862, 12.831369, 14.029150, 15.185780, 16.304454, 17.387918, 18.438499, 18.438499},
            {1.041797, 2.102914, 3.382635, 4.820075, 6.319745, 7.815051, 9.270060, 10.669686, 12.010258, 13.293417, 14.522834, 15.702640, 16.836755, 17.928647, 18.981287, 18.981287},
            {1.129420, 2.205711, 3.453737, 4.854501, 6.330998, 7.820231, 9.281358, 10.692266, 12.043373, 13.332343, 14.560530, 14.600000, 14.800000, 15.000000, 16.000000, 16.000000},
            {1.282357, 2.201728, 3.200110, 4.279121, 5.381698, 6.458291, 7.473654, 8.404912, 9.237535, 9.961323, 10.560946, 12.800000, 12.000000, 13.000000, 14.000000, 14.000000},
            {1.435294, 2.197745, 2.946482, 3.703740, 4.432399, 5.096350, 5.665950, 6.117558, 6.431698, 6.590303, 6.561362, 10.429641, 10.766784, 10.940900, 11.000000, 11.000000},
            {1.588231, 2.193761, 2.692854, 3.128359, 3.483099, 3.734409, 3.858246, 3.830204, 3.625860, 3.219283, 2.561777, 2.446063, 2.812709, 2.964646, 2.929796, 2.929796}},
         ///

         {{1.000000, 1.288286, 1.596022, 1.917581, 2.248076, 2.583494, 2.920679, 3.257234, 3.591394, 3.921893, 4.247850, 4.568672, 4.883973, 5.193521, 5.497186, 5.497186},
            {1.000000, 1.279781, 1.584052, 1.907682, 2.245574, 2.593059, 2.946107, 3.301400, 3.656308, 4.008818, 4.357440, 4.701105, 5.039074, 5.370863, 5.696172, 5.696172},
            {1.000000, 1.280337, 1.584774, 1.910662, 2.253768, 2.609614, 2.973957, 3.343030, 3.713645, 4.083216, 4.449715, 4.811608, 5.167777, 5.517436, 5.860064, 5.860064},
            {1.000391, 1.294774, 1.601857, 1.929096, 2.274046, 2.633004, 3.002048, 3.377422, 3.755769, 4.134217, 4.510402, 4.882443, 5.248897, 5.608691, 5.961061, 5.961061},
            {1.021647, 1.330817, 1.638549, 1.962862, 2.303119, 2.656584, 3.019946, 3.389838, 3.763100, 4.136912, 4.508850, 4.876900, 5.239438, 5.595186, 5.943166, 5.943166},
            {1.075882, 1.390637, 1.692120, 2.004541, 2.328665, 2.662677, 3.004004, 3.349881, 3.697612, 4.044703, 4.388921, 4.728322, 5.061243, 5.386277, 5.702240, 5.702240},
            {1.162047, 1.469396, 1.751611, 2.036511, 2.325841, 2.618510, 2.912571, 3.205796, 3.495909, 3.780686, 4.058005, 4.325845, 4.582277, 4.825421, 5.053405, 5.053405},
            {1.279375, 1.555931, 1.796142, 2.027862, 2.253083, 2.470870, 2.679328, 2.876123, 3.058659, 3.224122, 3.369462, 3.491329, 3.585976, 3.649114, 3.675717, 3.675717},
            {1.428078, 1.630085, 1.788115, 1.922932, 2.034927, 2.121671, 2.179338, 2.202806, 2.185072, 2.115186, 1.967917, 1.928475, 2.036901, 2.112577, 2.150098, 2.150098}
         }
      }};
   private static double distanceIncrement_ = VERTICAL_DISTANCE_POINTS[1] - VERTICAL_DISTANCE_POINTS[0];
   private static double normalIncrement_ = NORMAL_POINTS[1] - NORMAL_POINTS[0];

   public static double getRelativePower(int meanFreePath, double vertDistance, double normal, int radiusOfCurvature) {
       int mfpIndex = Arrays.asList(MEAN_FREE_PATHS).indexOf(meanFreePath);
      int radiusIndex = Arrays.asList(RADII_OF_CURVATURE).indexOf(radiusOfCurvature);
      if (mfpIndex == -1) {
         Log.log("Couldn't find mean free path in precalculated values");
         throw new RuntimeException();
      }
      if (radiusIndex == -1) {
         Log.log("Couldn't find radius of curvature in precalculated values");
         throw new RuntimeException();
      }
      double indexedDistance = vertDistance / distanceIncrement_;
      int normalIndex = (int) Math.round(normal / normalIncrement_); //this one should never exceed 90 degrees so we can round
      double[] distanceVec = RELATIVE_POWERS[radiusIndex][mfpIndex][normalIndex];
      if (indexedDistance > distanceVec.length - 1) {
         return distanceVec[distanceVec.length - 1];
      } else {
         double weight = indexedDistance % 1;
         return (1 - weight) * distanceVec[(int) Math.floor(indexedDistance)] + weight * distanceVec[(int) Math.ceil(indexedDistance)];
      }
   }

   public static String[] getAvailableMeanFreePathLengths() {
      String[] vals = new String[MEAN_FREE_PATHS.length];
      for (int i = 0; i < vals.length; i++) {
         vals[i] = MEAN_FREE_PATHS[i] + "";
      }
      return vals;
   }

   public static String[] getAvailableRadiiOfCurvature() {
      String[] vals = new String[RADII_OF_CURVATURE.length];
      for (int i = 0; i < vals.length; i++) {
         vals[i] = RADII_OF_CURVATURE[i] + "";
      }
      return vals;
   }
}
