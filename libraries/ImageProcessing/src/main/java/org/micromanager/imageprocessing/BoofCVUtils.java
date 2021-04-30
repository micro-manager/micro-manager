package org.micromanager.imageprocessing;

import static boofcv.alg.filter.binary.GThresholdImageOps.computeEntropy;

import boofcv.alg.misc.GImageStatistics;
import boofcv.struct.image.ImageGray;

/**
 * @author nico
 */
public class BoofCVUtils {

   /**
    * compress a histogram.  For instance, given a histogram of 1024 values and factor 4, a
    * similarly shaped histogram of 256 values will be returned
    *
    * @param inputHistogram
    * @param factor
    * @return
    */
   public static int[] compress(int[] inputHistogram, int factor) {
      int[] outputHistogram = new int[inputHistogram.length / factor];

      for (int outIndex = 0; outIndex < outputHistogram.length; outIndex++) {
         for (int i = 0; i < factor; i++) {
            outputHistogram[outIndex] += inputHistogram[outIndex * factor + i];
         }
      }

      // add remaining values to outputHistogram
      for (int i = outputHistogram.length * factor; i < inputHistogram.length; i++) {
         outputHistogram[outputHistogram.length - 1] += inputHistogram[i];
      }

      return outputHistogram;
   }

   /**
    * The MaxEntropy threshold method gives quite different values when using the "full" histogram
    * of U16 images (for instance, a histogram with a length of 4000 gray scale values), rather than
    * a histogram "compressed" to 256 gray scale values.  The latter somehow often works better.
    * This function calculates the threshold based on a user-defines range of the histogram.  Note
    * this can also speed up the calculations quite a bit.
    *
    * @param img
    * @param histogramRange
    * @return
    */
   public static int compressedMaxEntropyThreshold(ImageGray<? extends ImageGray<?>> img,
         int histogramRange) {

      double minValue = GImageStatistics.min(img);
      double maxValue = GImageStatistics.max(img);

      int range = (int) (1 + maxValue - minValue);
      int histogram[] = new int[range];
      GImageStatistics.histogram(img, minValue, histogram);
      final int factor = range / histogramRange;
      int[] compressedHistogram = histogram;
      if (factor >= 2) {
         compressedHistogram = BoofCVUtils.compress(histogram, factor);
      }

      // Total number of pixels
      int total = img.width * img.height;

      int tmpThreshold = computeEntropy(compressedHistogram, compressedHistogram.length, total);

      int threshold = tmpThreshold * factor + (int) minValue;

      return threshold;
   }

}
