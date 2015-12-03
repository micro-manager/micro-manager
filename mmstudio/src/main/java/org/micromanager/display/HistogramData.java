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

package org.micromanager.display;

/**
 * This is a simple container class containing a histogram for one component
 * of an int-based image, and some related statistics. You can generate new
 * HistogramData objects yourself, or use DisplayManager.calculateHistogram()
 * to generate a HistogramData based off of an Image.
 */
public class HistogramData {
   private final int[] histogram_;
   private final int numSamples_;
   private final int minVal_;
   private final int maxVal_;
   private final int minIgnoringOutliers_;
   private final int maxIgnoringOutliers_;
   private final int mean_;
   private final int bitDepth_;
   private final int binSize_;

   /**
    * @param histogram An array of integers indicating how many pixels had
    *        intensities falling into the "bin" at the given index. The first
    *        bin always starts from an intensity of 0; the last bin ends with
    *        an intensity depending on the bitDepth parameter.
    * @param numSamples The number of pixels used to generate the histogram;
    *        should be equal to the sum of all values in the histogram.
    * @param minVal The lowest intensity found when generating the histogram.
    * @param maxVal The brightest intensity found when generating the
    *        histogram.
    * @param minIgnoringOutliers The lowest intensity when a fraction of the
    *        lowest samples are ignored. See the extremaPercentage parameter
    *        of DisplayManager.calculateHistogram().
    * @param maxIgnoringOutliers The highest intensity when a fraction of the
    *        lowest samples are ignored. See the extremaPercentage parameter
    *        of DisplayManager.calculateHistogram().
    * @param mean The average intensity of all samples.
    * @param bitDepth The dynamic range of the histogram. The histogram will
    *        cover values from 0 to (2^bitDepth - 1), inclusive.
    * @param binSize How many distinct intensities are combined into a single
    *        bin in the histogram. For example, if the bit depth is 10 and
    *        there are 2^8 bins, then there are (2^10 / 2^8 = 4) intensities
    *        per bin.
    */
   public HistogramData(int[] histogram, int numSamples, int minVal,
         int maxVal, int minIgnoringOutliers, int maxIgnoringOutliers,
         int mean, int bitDepth, int binSize) {
      histogram_ = histogram;
      numSamples_ = numSamples;
      minVal_ = minVal;
      maxVal_ = maxVal;
      minIgnoringOutliers_ = minIgnoringOutliers;
      maxIgnoringOutliers_ = maxIgnoringOutliers;
      mean_ = mean;
      bitDepth_ = bitDepth;
      binSize_ = binSize;
   }

   /**
    * Retrieve the histogram, an array of ints counting the number of samples
    * whose intensities fall into each bin.
    * @return Array with histogram data
    */
   public int[] getHistogram() {
      return histogram_;
   }

   /**
    * Retrieve the intensity of the dimmest sample in the dataset used to
    * generate the histogram.
    * @return lowest intensity value in the dataset
    */
   public int getMinVal() {
      return minVal_;
   }

   /**
    * Retrieve the intensity of the brightest sample in the dataset used to
    * generate the histogram.
    * @return highest intensity value in the dataset
    */
   public int getMaxVal() {
      return maxVal_;
   }

   /**
    * Retrieve the intensity of the dimmest sample in the dataset once a
    * fraction of all pixels have been discarded. See the extremaPercentage
    * parameter to DisplayManager.calculateHistogram() for more information.
    * @return lowest intensity value in the dataset ignoring given fraction of outliers
    */
   public int getMinIgnoringOutliers() {
      return minIgnoringOutliers_;
   }

   /**
    * Retrieve the intensity of the brightest sample in the dataset once a
    * fraction of all pixels have been discarded. See the extremaPercentage
    * parameter to DisplayManager.calculateHistogram() for more information.
    * @return highest intensity value in the dataset ignoring given fraction of outliers
    */
   public int getMaxIgnoringOutliers() {
      return maxIgnoringOutliers_;
   }

   /**
    * Retrieve the mean value of all samples in the dataset.
    * @return mean intensity value in this dataset
    */
   public int getMean() {
      return mean_;
   }

   /**
    * Retrieve the bit depth of the dataset. The highest pixel intensity in the 
    * dataset can not be higher than 2^bitDepth.
    * @return bitdepth of the dataset
    */
   public int getBitDepth() {
      return bitDepth_;
   }

   /**
    * Retrieve the number of distinct intensities that fit within each bin
    * of the histogram. For example, if this is 4, then the first bin will
    * hold the number of samples whose intensities were 0, 1, 2, or 3.
    * @return Number of divers intensity values that can be represented in a histogram bin
    */
   public int getBinSize() {
      return binSize_;
   }

   /**
    * Retrieve the number of samples used to construct the histogram.
    * @return Total number of bins in the histogram
    */
   public int getNumSamples() {
      return numSamples_;
   }

   @Override
   public String toString() {
      String result = String.format("<HistogramData mins %d/%d maxes %d/%d mean %d bitdepth %d binsize %d>",
            minVal_, minIgnoringOutliers_, maxVal_, maxIgnoringOutliers_,
            mean_, bitDepth_, binSize_);
      return result;
   }
}
