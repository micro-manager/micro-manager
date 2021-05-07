///////////////////////////////////////////////////////////////////////////////
//FILE:           OughtaFocus.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Autofocusing plug-in for micro-manager and ImageJ
//-----------------------------------------------------------------------------
//
//AUTHOR:         Arthur Edelstein, October 2010
//                Based on SimpleAutofocus by Karl Hoover
//                and the Autofocus "H&P" plugin
//                by Pakpoom Subsoontorn & Hernan Garcia
//                Contributions by Jon Daniels (ASI): FFTBandpass, MedianEdges 
//                      and Tenengrad
//                Chris Weisiger: 2.0 port
//                Nico Stuurman: 2.0 port and Math3 port
//                Nick Anthony: Refactoring
//
//COPYRIGHT:      University of California San Francisco
//                
//LICENSE:        This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//CVS:            $Id: MetadataDlg.java 1275 2008-06-03 21:31:24Z nenad $
package org.micromanager.imageprocessing;

import ij.gui.OvalRoi;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import java.util.Arrays;

/**
 * @author Nick Anthony
 */
public class ImgSharpnessAnalysis {

   private double fftLowerCutoff_ = 2.5;
   private double fftUpperCutoff_ = 14;
   private Method method_ = Method.Edges;

   public enum Method {
      Edges, StdDev, Mean,
      NormalizedVariance, SharpEdges, Redondo, Volath, Volath5,
      MedianEdges, Tenengrad, FFTBandpass;

      public static String[] getNames() {
         return Arrays.stream(Method.class.getEnumConstants()).map(Enum::name)
               .toArray(String[]::new);
      }
   }

   /**
    * These parameters are only used for method: FFTBandpass
    *
    * @param fftLowerCutoff Frequencies below this will be filtered out
    * @param fftUpperCutoff Frequencies above this will be filtered out
    */
   public void setFFTCutoff(double fftLowerCutoff, double fftUpperCutoff) {
      fftLowerCutoff_ = fftLowerCutoff;
      fftUpperCutoff_ = fftUpperCutoff;
   }

   public double getFFTLowerCutoff() {
      return fftLowerCutoff_;
   }

   public double getFFTUpperCutoff() {
      return fftUpperCutoff_;
   }

   public void setComputationMethod(Method method) {
      method_ = method;
   }

   public Method getComputationMethod() {
      return method_;
   }

   /**
    * Compute the sharpness of `proc` using the current `Method` set with `setComputationMethod`.
    *
    * @param proc
    * @return
    */
   public double compute(ImageProcessor proc) {
      switch (method_) {
         case Edges:
            return computeEdges(proc);
         case StdDev:
            return computeNormalizedStdDev(proc);
         case Mean:
            return computeMean(proc);
         case NormalizedVariance:
            return computeNormalizedVariance(proc);
         case SharpEdges:
            return computeSharpEdges(proc);
         case Redondo:
            return computeRedondo(proc);
         case Volath:
            return computeVolath(proc);
         case Volath5:
            return computeVolath5(proc);
         case MedianEdges:
            return computeMedianEdges(proc);
         case Tenengrad:
            return computeTenengrad(proc);
         case FFTBandpass:
            return computeFFTBandpass(proc, fftLowerCutoff_, fftUpperCutoff_);
         default:
            throw new AssertionError(method_.name());
      }
   }

   public static double computeEdges(ImageProcessor proc) {
      // mean intensity for the original image
      double meanIntensity = proc.getStatistics().mean;
      ImageProcessor proc1 = proc.duplicate();
      // mean intensity of the edge map
      proc1.findEdges();
      double meanEdge = proc1.getStatistics().mean;

      return meanEdge / meanIntensity;
   }

   public static double computeSharpEdges(ImageProcessor proc) {
      // mean intensity for the original image
      double meanIntensity = proc.getStatistics().mean;
      ImageProcessor proc1 = proc.duplicate();
      // mean intensity of the edge map
      proc1.sharpen();
      proc1.findEdges();
      double meanEdge = proc1.getStatistics().mean;

      return meanEdge / meanIntensity;
   }

   public static double computeMean(ImageProcessor proc) {
      return proc.getStatistics().mean;
   }

   public static double computeNormalizedStdDev(ImageProcessor proc) {
      ImageStatistics stats = proc.getStatistics();
      return stats.stdDev / stats.mean;
   }

   public static double computeNormalizedVariance(ImageProcessor proc) {
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
   public static double computeRedondo(ImageProcessor proc) {
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
    * From "Autofocusing Algorithm Selection in Computer Microscopy" (doi:
    * 10.1109/IROS.2005.1545017). 2016 paper (doi:10.1038/nbt.3708) concludes this is best  most
    * non-spectral metric for their light sheet microscopy application
    *
    * @author Jon
    */
   public static double computeTenengrad(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;
      int[] ken1 = {-1, 0, 1, -2, 0, 2, -1, 0, 1};
      int[] ken2 = {1, 2, 1, 0, 0, 0, -1, -2, -1};

      ImageProcessor proc2 = proc.duplicate();
      proc.convolve3x3(ken1);
      proc2.convolve3x3(ken2);
      for (int i = 0; i < w; i++) {
         for (int j = 0; j < h; j++) {
            sum += Math.pow(proc.getPixel(i, j), 2) + Math.pow(proc2.getPixel(i, j), 2);
         }
      }
      return sum;
   }

   // Volath's 1D autocorrelation
   // Volath  D., "The influence of the scene parameters and of noise on
   // the behavior of automatic focusing algorithms,"
   // J. Microsc. 151, (2), 133-146 (1988).
   public static double computeVolath(ImageProcessor proc) {
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
   public static double computeVolath5(ImageProcessor proc) {
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
    * Modified version of the algorithm used by the AutoFocus JAF(H&P) code in Micro-Manager's
    * Autofocus.java by Pakpoom Subsoontorn & Hernan Garcia. Looks for diagonal edges in both
    * directions, then combines them (RMS). (Original algorithm only looked for edges in one
    * diagonal direction). Similar to Edges algorithm except it does no normalization by original
    * intensity and adds a median filter before edge detection.
    *
    * @author Jon
    */
   public static double computeMedianEdges(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;
      int[] ken1 = {2, 1, 0, 1, 0, -1, 0, -1, -2};
      int[] ken2 = {0, 1, 2, -1, 0, 1, -2, -1, 0};

      proc.medianFilter();    // 3x3 median filter
      ImageProcessor proc2 = proc.duplicate();
      proc.convolve3x3(ken1);
      proc2.convolve3x3(ken2);
      for (int i = 0; i < w; i++) {
         for (int j = 0; j < h; j++) {
            sum += Math.sqrt(Math.pow(proc.getPixel(i, j), 2) + Math.pow(proc2.getPixel(i, j), 2));
         }
      }
      return sum;
   }

   /**
    * Per suggestion of William "Bill" Mohler @ UConn.  Returns the power in a specified band of
    * spatial frequencies via the FFT.  Key according to Bill is to use an unscaled FFT, so this is
    * provided using a modified ImageJ class.
    *
    * @author Jon
    */
   public static double computeFFTBandpass(ImageProcessor proc, double fftLowerCutoff,
         double fftUpperCutoff) {
      // gets power spectrum (FFT) without scaling result
      FHT_NoScaling myFHT = new FHT_NoScaling(proc);
      myFHT.transform();
      ImageProcessor ps = myFHT.getPowerSpectrum_noScaling();
      int midpoint = ps.getHeight() / 2;
      final int scaled_lower = (int) Math.round(fftLowerCutoff / 100 * midpoint);
      final int start_lower = Math.round(midpoint - scaled_lower);
      final int scaled_upper = (int) Math.round(fftUpperCutoff / 100 * midpoint);
      final int start_upper = Math.round(midpoint - scaled_upper);
      OvalRoi innerCutoff = new OvalRoi(start_lower, start_lower,
            2 * scaled_lower + 1, 2 * scaled_lower + 1);
      OvalRoi outerCutoff = new OvalRoi(start_upper, start_upper,
            2 * scaled_upper + 1, 2 * scaled_upper + 1);
      ps.setColor(0);
      ps.fillOutside(outerCutoff);
      ps.fill(innerCutoff);
      ps.setRoi(outerCutoff);
      return ps.getStatistics().mean;
   }
}
