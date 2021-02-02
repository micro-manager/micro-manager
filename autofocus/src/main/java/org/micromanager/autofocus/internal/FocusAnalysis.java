/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.autofocus.internal;

import ij.gui.OvalRoi;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

/**
 *
 * @author nick
 */
public class FocusAnalysis {
   public enum Method {
      Edges, StdDev, Mean, 
      NormalizedVariance, SharpEdges, Redondo, Volath, Volath5, 
      MedianEdges, Tenengrad, FFTBandpas;
   }
    
   private static double compute(Method method, ImageProcessor proc) {
      double score;
      switch (method) {
         case Edges:
            score = computeEdges(proc);
            break;
         case StdDev:
            score = computeNormalizedStdDev(proc);
            break;
         case Mean:
            score = computeMean(proc);
            break;
         case NormalizedVariance:
            score = computeNormalizedVariance(proc);
            break;
         case SharpEdges:
            score = computeSharpEdges(proc);
            break;
         case Redondo:
            score = computeRedondo(proc);
            break;
         case Volath:
            score = computeVolath(proc);
            break;
         case Volath5:
            score = computeVolath5(proc);
            break;
         case MedianEdges:
            score = computeMedianEdges(proc);
            break;
         case Tenengrad:
            score = computeTenengrad(proc);
            break;
         case FFTBandpas:
            score = computeFFTBandpass(proc);
            break;
         default:
            throw new AssertionError(method.name());
      }
      return score;
   }
    
   private static double computeEdges(ImageProcessor proc) {
      // mean intensity for the original image
      double meanIntensity = proc.getStatistics().mean;
      ImageProcessor proc1 = proc.duplicate();
      // mean intensity of the edge map
      proc1.findEdges();
      double meanEdge = proc1.getStatistics().mean;

      return meanEdge / meanIntensity;
   }

   private static double computeSharpEdges(ImageProcessor proc) {
      // mean intensity for the original image
      double meanIntensity = proc.getStatistics().mean;
      ImageProcessor proc1 = proc.duplicate();
      // mean intensity of the edge map
      proc1.sharpen();
      proc1.findEdges();
      double meanEdge = proc1.getStatistics().mean;

      return meanEdge / meanIntensity;
   }

   private static double computeMean(ImageProcessor proc) {
      return proc.getStatistics().mean;
   }

   private static double computeNormalizedStdDev(ImageProcessor proc) {
      ImageStatistics stats = proc.getStatistics();
      return stats.stdDev / stats.mean;
   }

   private static double computeNormalizedVariance(ImageProcessor proc) {
      ImageStatistics stats = proc.getStatistics();
      return (stats.stdDev * stats.stdDev) / stats.mean;
   }

   
   // this is NOT a traditional Laplace filter; the "center" weight is
   // actually the bottom-center cell of the 3x3 matrix.  AFAICT it's a
   // typo in the source paper, but works better than the traditional
   // Laplace filter.
   //
   // Redondo R, Bueno G, Valdiviezo J et al.  "Autofocus evaluation for
   // brightfield microscopy pathology", J Biomed Opt 17(3) 036008 (2012)
   //
   // from
   //
   // Russel M, Douglas T.  "Evaluation of autofocus algorithms for
   // tuberculosis microscopy". Proc 29th International Conference of the
   // IEEE EMBS, Lyon, 3489-3492 (22-26 Aug 2007)
   private static double computeRedondo(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;

      for (int i = 1; i < w - 1; ++i) {
         for (int j = 1; j < h - 1; ++j) {
            double p = proc.getPixel(i - 1, j)
                    + proc.getPixel(i + 1, j)
                    + proc.getPixel(i, j - 1)
                    + proc.getPixel(i, j + 1)
                    - 4 * (proc.getPixel(i - 1, j));
            sum += (p * p);
         }
      }

      return sum;
   }

     
   /**
    * From "Autofocusing Algorithm Selection in Computer Microscopy" 
    * (doi: 10.1109/IROS.2005.1545017). 
    * 2016 paper (doi:10.1038/nbt.3708) concludes this is best  most 
    * non-spectral metric for their light sheet microscopy application
    * @author Jon
    */
   private static double computeTenengrad(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;
      int[] ken1 = {-1, 0, 1, -2, 0, 2, -1, 0, 1};
      int[] ken2 = {1, 2, 1, 0, 0, 0, -1, -2, -1};

      ImageProcessor proc2 = proc.duplicate();
      proc.convolve3x3(ken1);
      proc2.convolve3x3(ken2);
      for (int i=0; i<w; i++){
         for (int j=0; j<h; j++){
            sum += Math.pow(proc.getPixel(i,j),2) + Math.pow(proc2.getPixel(i, j), 2);
         }
      }
      return sum;
   }
   
   // Volath's 1D autocorrelation
   // Volath  D., "The influence of the scene parameters and of noise on
   // the behavior of automatic focusing algorithms,"
   // J. Microsc. 151, (2), 133-146 (1988).
   private static double computeVolath(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum1 = 0.0;
      double sum2 = 0.0;

      for (int i = 1; i < w - 1; ++i) {
         for (int j = 0; j < h; ++j) {
            sum1 += proc.getPixel(i, j) * proc.getPixel(i + 1, j);
         }
      }

      for (int i = 0; i < w - 2; ++i) {
         for (int j = 0; j < h; ++j) {
            sum2 += proc.getPixel(i, j) * proc.getPixel(i + 2, j);
         }
      }

      return (sum1 - sum2);
   }

   // Volath 5 - smooths out high-frequency (suppresses noise)
   // Volath  D., "The influence of the scene parameters and of noise on
   // the behavior of automatic focusing algorithms,"
   // J. Microsc. 151, (2), 133-146 (1988).
   private static double computeVolath5(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;

      for (int i = 0; i < w - 1; ++i) {
         for (int j = 0; j < h; ++j) {
            sum += proc.getPixel(i, j) * proc.getPixel(i + 1, j);
         }
      }

      ImageStatistics stats = proc.getStatistics();

      sum -= ((w - 1) * h * stats.mean * stats.mean);
      return sum;
   }
   
   
 /**
    * Modified version of the algorithm used by the AutoFocus JAF(H&P) code
    * in Micro-Manager's Autofocus.java by Pakpoom Subsoontorn & Hernan Garcia.
    * Looks for diagonal edges in both directions, then combines them (RMS).
    * (Original algorithm only looked for edges in one diagonal direction).
    * Similar to Edges algorithm except it does no normalization by original
    * intensity and adds a median filter before edge detection.
    * @author Jon
    */
   private static double computeMedianEdges(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;
      int[] ken1 = {2, 1, 0, 1, 0, -1, 0, -1, -2};
      int[] ken2 = {0, 1, 2, -1, 0, 1, -2, -1, 0};

      proc.medianFilter();    // 3x3 median filter
      ImageProcessor proc2 = proc.duplicate();
      proc.convolve3x3(ken1);
      proc2.convolve3x3(ken2);
      for (int i=0; i<w; i++){
         for (int j=0; j<h; j++){
            sum += Math.sqrt(Math.pow(proc.getPixel(i,j),2) + Math.pow(proc2.getPixel(i, j), 2));
         }
      }
      return sum;
   }

   /**
    * Per suggestion of William "Bill" Mohler @ UConn.  Returns the power in a
    * specified band of spatial frequencies via the FFT.  Key according to Bill is
    * to use an unscaled FFT, so this is provided using a modified ImageJ class.
    * @author Jon
    */
   private static double computeFFTBandpass(ImageProcessor proc) {
      try {
         // gets power spectrum (FFT) without scaling result
         FHT_NoScaling myFHT = new FHT_NoScaling(proc);
         myFHT.transform();
         ImageProcessor ps = myFHT.getPowerSpectrum_noScaling();
         int midpoint = ps.getHeight()/2;
         final int scaled_lower = (int) Math.round(fftLowerCutoff/100*midpoint);
         final int start_lower = Math.round(midpoint-scaled_lower);
         final int scaled_upper = (int) Math.round(fftUpperCutoff/100*midpoint);
         final int start_upper = Math.round(midpoint-scaled_upper);
         OvalRoi innerCutoff = new OvalRoi(start_lower, start_lower,
               2*scaled_lower+1, 2*scaled_lower+1);
         OvalRoi outerCutoff = new OvalRoi(start_upper, start_upper,
               2*scaled_upper+1, 2*scaled_upper+1);
         ps.setColor(0);
         ps.fillOutside(outerCutoff);
         ps.fill(innerCutoff);
         ps.setRoi(outerCutoff);
         return ps.getStatistics().mean;
      } catch (Exception e) {
         studio_.logs().logError(e);
         return 0;
      }
   }
}
