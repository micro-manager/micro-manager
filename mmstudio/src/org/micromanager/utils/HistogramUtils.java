/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.utils;

/**
 *
 * @author Karl Hoover
 */
public class HistogramUtils {

   public HistogramUtils(int[] histogram, int totalPoints) {
      histogram_ = histogram;
      totalPoints_ = totalPoints;
   }

   public HistogramUtils(int[] histogram) {
      histogram_ = histogram;
      totalPoints_ = 0;
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

   //ignore pixels beyond -3 sigma
   public int getMinAfterRejectingOutliers() {
      int ret = 0;
      if (totalPoints_ < 1) {
         totalPoints_ = sumTotalPoints();
      }
      int maxOutliers = (int) (0.5 + totalPoints_ * 0.0027);

      // march through raw histogram until we see 0.0027*totalPoints pixels
      int iterator;
      int outliers;
      outliers = 0;
      for (iterator = 0; iterator < histogram_.length; ++iterator) {
         outliers += histogram_[iterator];
         if (outliers >= maxOutliers) {
            ret = iterator;
            break;
         }
      }
      return ret;

   }

   //ignore pixels beyond +3 sigma
   public int getMaxAfterRejectingOutliers() {
      int ret = 0;
      if (totalPoints_ < 1) {
         totalPoints_ = sumTotalPoints();
      }
      int maxOutliers = (int) (0.5 + totalPoints_ * 0.0027);
      int outliers = 0;
      for (int iterator = histogram_.length - 1; iterator >= 0; --iterator) {
         outliers += histogram_[iterator];
         if (outliers >= maxOutliers) {
            ret = iterator;
            break;
         }
      }
      return ret;

   }
   int[] histogram_;
   int totalPoints_;
}
