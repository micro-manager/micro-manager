///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
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

package org.micromanager.display.internal;

/**
 * This is a simple container class containing a histogram for one component
 * of an image, and some related statistics.
 */
public class HistogramData {
   private int[] histogram_;
   private int numPixels_;
   private int minVal_;
   private int maxVal_;
   private int minIgnoringOutliers_;
   private int maxIgnoringOutliers_;
   private int mean_;
   private int bitDepth_;
   private int binSize_;

   public HistogramData(int[] histogram, int numPixels, int minVal, int maxVal,
         int minIgnoringOutliers, int maxIgnoringOutliers, int mean,
         int bitDepth, int binSize) {
      histogram_ = histogram;
      numPixels_ = numPixels;
      minVal_ = minVal;
      maxVal_ = maxVal;
      minIgnoringOutliers_ = minIgnoringOutliers;
      maxIgnoringOutliers_ = maxIgnoringOutliers;
      mean_ = mean;
      bitDepth_ = bitDepth;
      binSize_ = binSize;
   }

   public int[] getHistogram() {
      return histogram_;
   }

   public int getMinVal() {
      return minVal_;
   }

   public int getMaxVal() {
      return maxVal_;
   }

   public int getMinIgnoringOutliers() {
      return minIgnoringOutliers_;
   }

   public int getMaxIgnoringOutliers() {
      return maxIgnoringOutliers_;
   }

   public int getMean() {
      return mean_;
   }

   public int getBitDepth() {
      return bitDepth_;
   }

   public int getBinSize() {
      return binSize_;
   }

   public int getNumPixels() {
      return numPixels_;
   }

   @Override
   public String toString() {
      String result = String.format("<HistogramData mins %d/%d maxes %d/%d mean %d bitdepth %d binsize %d>",
            minVal_, minIgnoringOutliers_, maxVal_, maxIgnoringOutliers_,
            mean_, bitDepth_, binSize_);
      return result;
   }
}
