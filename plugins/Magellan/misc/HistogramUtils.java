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
package misc;

/**
 *
 * @author Karl Hoover
 */
public class HistogramUtils {

   public HistogramUtils(int[] histogram, int totalPoints) {
      histogram_ = histogram;
      totalPoints_ = totalPoints;
      fractionToReject_ = 0.0027; // corresponds to 3 sigma
   }

   public HistogramUtils(int[] histogram) {
      histogram_ = histogram;
      totalPoints_ = 0;
      fractionToReject_ = 0.0027; // corresponds to 3 sigma
   }

      public HistogramUtils(int[] histogram, int totalPoints, double f) {
      histogram_ = histogram;
      totalPoints_ = totalPoints;
      fractionToReject_ = f;
   }

   public HistogramUtils(int[] histogram, double f) {
      histogram_ = histogram;
      totalPoints_ = 0;
      fractionToReject_ = f;
   }


   public int getMin() {
      int ret = 0;
      if (null != histogram_) {
         for (int i = 0; i < histogram_.length; ++i) {
            if (0 < histogram_[i]) {
               ret = i;
               break;
            }
         }
      }
      return ret;
   }

   public int getMax() {
      int ret = 0;
      if (null!=histogram_ ) {
         for (int i = histogram_.length - 1; 0 <= i; --i) {
            if (0 < histogram_[i]) {
               ret = i;
               break;
            }
         }
      }
      return ret;
   }

   int sumTotalPoints() {
      int t = 0;
      for (int i = 0; i < histogram_.length; ++i) {
         t += histogram_[i];
      }
      return t;
   }

   //ignore a fraction of low pixels
   public int getMinAfterRejectingOutliers() {
      int ret = 0;
      if (totalPoints_ < 1) {
         totalPoints_ = sumTotalPoints();
      }
      int maxOutliers = (int) (0.5 + totalPoints_ * fractionToReject_);

      // march through raw histogram until we see fractionToReject_*totalPoints pixels
      int iterator;
      int outliers;
      outliers = 0;
      for (iterator = 0; iterator < histogram_.length; ++iterator) {
         outliers += histogram_[iterator];
         if (outliers > maxOutliers) {
            ret = Math.max(0, iterator);
            break;
         }
      }
      return ret;
   }

   //ignore a fraction of high pixels
   public int getMaxAfterRejectingOutliers() {
      int ret = 0;
      if (totalPoints_ < 1) {
         totalPoints_ = sumTotalPoints();
      }
      int maxOutliers = (int) (0.5 + totalPoints_ * fractionToReject_);
      int outliers = 0;
      for (int iterator = histogram_.length - 1; iterator >= 0; --iterator) {
         outliers += histogram_[iterator];
         if (outliers > maxOutliers) {
            ret = Math.min(iterator, histogram_.length - 1);
            break;
         }
      }
      return ret;
   }

   public double getFractionToReject(){
      return fractionToReject_;
   }

   public int getTotalPoints(){
      if( null!= histogram_){
         if (totalPoints_ < 1) {
            totalPoints_ = sumTotalPoints();
         }
      }
      return totalPoints_;
   }

   public void setFractionToReject(double f){
      fractionToReject_ = f;
   }

   int[] histogram_;
   int totalPoints_;
   double fractionToReject_;

}
