/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.pointandshootanalysis.algorithm;

import boofcv.alg.misc.GImageStatistics;
import boofcv.struct.image.ImageGray;

/**
 *
 * @author nico
 */
public class ThresholdImageOps {
   
   /**
	 * <p>
	 * Computes Li's Minimum Cross Entropy thresholding from an input image. Internally it uses
	 * {@link #computeLi(int[], int, int)} and {@link boofcv.alg.misc.GImageStatistics#histogram(ImageGray, double, int[])}
	 * </p>
	 *
	 * @param input Input gray-scale image
	 * @param minValue The minimum value of a pixel in the image.  (inclusive)
	 * @param maxValue The maximum value of a pixel in the image.  (inclusive)
	 * @return Selected threshold.
	 */
	public static double computeLi(ImageGray input , double minValue , double maxValue ) {

		int range = (int)(1+maxValue - minValue);
		int histogram[] = new int[ range ];

		GImageStatistics.histogram(input,minValue,histogram);

		// Total number of pixels
		int total = input.width*input.height;

		return computeLi(histogram,range,total)+minValue;
	}
   
   /**
    * Implements Li's Minimum Cross Entropy thresholding method This
    * implementation is based on the iterative version (Ref. 2) of the
    * algorithm. 1) Li C.H. and Lee C.K. (1993) "Minimum Cross Entropy
    * Thresholding" Pattern Recognition, 26(4): 617-625 2) Li C.H. and Tam
    * P.K.S. (1998) "An Iterative Algorithm for Minimum Cross Entropy
    * Thresholding"Pattern Recognition Letters, 18(8): 771-776 3) Sezgin M. and
    * Sankur B. (2004) "Survey over Image Thresholding Techniques and
    * Quantitative Performance Evaluation" Journal of Electronic Imaging, 13(1):
    * 146-165 http://citeseer.ist.psu.edu/sezgin04survey.html 
    * 
    * Ported to ImageJ plugin by G.Landini from E Celebi's fourier_0.8 routines 
    * Ported from Imagej code (https://imagej.nih.gov/ij/developer/source/) to 
    * BoofCV by Nico Stuurman
    *
    * @param histogram Histogram of pixel intensities.
    * @param length Number of elements in the histogram.
    * @param totalPixels Total pixels in the image
    * @return Selected threshold
    */
   public static int computeLi(int histogram[], int length, int totalPixels) {
      int threshold;
      double sum_back; // sum of the background pixels at a given threshold 
      double sum_obj;  // sum of the object pixels at a given threshold 
      double num_back; // number of background pixels at a given threshold 
      double num_obj;  // number of object pixels at a given threshold 
      double old_thresh;
      double new_thresh;
      double mean_back; // mean of the background pixels at a given threshold 
      double mean_obj;  // mean of the object pixels at a given threshold 
      double mean;      // mean gray-level in the image 
      double tolerance; // threshold tolerance 
      double temp;

      tolerance = 0.5;

      // Calculate the mean gray-level 
      mean = 0.0;
      for (int i = 0; i < length; i++) 
      {
         mean += (double) i * histogram[i];
      }
      mean /= totalPixels;
      
      // Initial estimate 
      new_thresh = mean;

      do {
         old_thresh = new_thresh;
         threshold = (int) (old_thresh + 0.5);
         // range 
         // Calculate the means of background and object pixels 
         // Background 
         sum_back = 0;
         num_back = 0;
         for (int ih = 0; ih <= threshold; ih++) {
            sum_back += (double) ih * histogram[ih];
            num_back += histogram[ih];
         }
         mean_back = (num_back == 0 ? 0.0 : (sum_back / (double) num_back));
         // Object 
         sum_obj = 0;
         num_obj = 0;
         for (int ih = threshold + 1; ih < length; ih++) {
            sum_obj += (double) ih * histogram[ih];
            num_obj += histogram[ih];
         }
         mean_obj = (num_obj == 0 ? 0.0 : (sum_obj / (double) num_obj));

         /* Calculate the new threshold: Equation (7) in Ref. 2 */
         //new_thresh = simple_round ( ( mean_back - mean_obj ) / ( Math.log ( mean_back ) - Math.log ( mean_obj ) ) );
         //simple_round ( double x ) {
         // return ( int ) ( IS_NEG ( x ) ? x - .5 : x + .5 );
         //}
         //
         //#define IS_NEG( x ) ( ( x ) < -DBL_EPSILON ) 
         //DBL_EPSILON = 2.220446049250313E-16
         temp = (mean_back - mean_obj) / (Math.log(mean_back) - Math.log(mean_obj));

         if (temp < -2.220446049250313E-16) {
            new_thresh = (int) (temp - 0.5);
         } else {
            new_thresh = (int) (temp + 0.5);
         }
         //  Stop the iterations when the difference between the
         // new and old threshold values is less than the tolerance
      } while (Math.abs(new_thresh - old_thresh) > tolerance);

      return threshold;
   }

}
