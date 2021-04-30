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

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

/**
 * This is a modified version of ImageJ FHT class which is in the public domain
 * (http://rsb.info.nih.gov/ij/developer/source/ij/process/FHT.java.html). This modified version is
 * also released into the public domain. Principal changes are in the method getPowerSpectrum()
 * which has been changed to remove scaling and renamed getPowerSpectrum_NoScaling. The pad method
 * (renamed to padImage) was also copied from separate ImageJ code in the public domain at
 * http://rsb.info.nih.gov/ij/developer/source/ij/plugin/FFT.java.html All other changes are
 * incidental like tweaking imports, renaming constructors, deleting unused code, making methods
 * private, and declaring as a static nested class. This code created by Jon Daniels (Applied
 * Scientific Instrumentation) based on code from William (Bill) Mohler (University of Connecticut)
 * which in turn was based on the public-domain ImageJ code.
 *
 * @author Jon
 */
public class FHT_NoScaling extends FloatProcessor {

   private boolean isFrequencyDomain_;
   private int maxN_;
   private float[] C_;
   private float[] S_;
   private int[] bitrev_;
   private float[] tempArr_;

   /**
    * Constructs a FHT object from an ImageProcessor. Byte, short and RGB images are converted to
    * float. Float images are duplicated.
    */
   public FHT_NoScaling(ImageProcessor ip) {
      this(padImage(ip), false);
   }

   private FHT_NoScaling(ImageProcessor ip, boolean isFrequencyDomain) {
      super(ip.getWidth(), ip.getHeight(),
            (float[]) ((ip instanceof FloatProcessor) ? ip.duplicate().getPixels()
                  : ip.convertToFloat().getPixels()), null);
      this.isFrequencyDomain_ = isFrequencyDomain;
      maxN_ = getWidth();
      resetRoi();
   }

   /**
    * Returns true of this FHT contains a square image with a width that is a power of two.
    */
   private boolean powerOf2Size() {
      int i = 2;
      while (i < width) {
         i *= 2;
      }
      return i == width && width == height;
   }

   /**
    * Performs a forward transform, converting this image into the frequency domain. The image
    * contained in this FHT must be square and its width must be a power of 2.
    */
   public void transform() {
      transform(false);
   }

   private void transform(boolean inverse) throws IllegalArgumentException {
      if (!powerOf2Size()) {
         throw new IllegalArgumentException(
               "Image not power of 2 size or not square: " + width + "x" + height);
      }
      maxN_ = width;
      if (S_ == null) {
         initializeTables(maxN_);
      }
      float[] fht = (float[]) getPixels();
      rc2DFHT(fht, inverse, maxN_);
      isFrequencyDomain_ = !inverse;
   }

   private void initializeTables(int maxN) throws IllegalArgumentException {
      if (maxN > 0x40000000) {
         throw new IllegalArgumentException("Too large for FHT:  " + maxN + " >2^30");
      }
      makeSinCosTables(maxN);
      makeBitReverseTable(maxN);
      tempArr_ = new float[maxN];
   }

   private void makeSinCosTables(int maxN) {
      int n = maxN / 4;
      C_ = new float[n];
      S_ = new float[n];
      double theta = 0.0;
      double dTheta = 2.0 * Math.PI / maxN;
      for (int i = 0; i < n; i++) {
         C_[i] = (float) Math.cos(theta);
         S_[i] = (float) Math.sin(theta);
         theta += dTheta;
      }
   }

   private void makeBitReverseTable(int maxN) {
      bitrev_ = new int[maxN];
      int nLog2 = log2(maxN);
      for (int i = 0; i < maxN; i++) {
         bitrev_[i] = bitRevX(i, nLog2);
      }
   }

   /**
    * Performs a 2D FHT (Fast Hartley Transform).
    */
   private void rc2DFHT(float[] x, boolean inverse, int maxN) {
      if (S_ == null) {
         initializeTables(maxN);
      }
      for (int row = 0; row < maxN; row++) {
         dfht3(x, row * maxN, inverse, maxN);
      }
      transposeR(x, maxN);
      for (int row = 0; row < maxN; row++) {
         dfht3(x, row * maxN, inverse, maxN);
      }
      transposeR(x, maxN);

      int mRow, mCol;
      float A, B, C, D, E;
      for (int row = 0; row <= maxN / 2; row++) { // Now calculate actual Hartley transform
         for (int col = 0; col <= maxN / 2; col++) {
            mRow = (maxN - row) % maxN;
            mCol = (maxN - col) % maxN;
            A = x[row * maxN
                  + col];    //  see Bracewell, 'Fast 2D Hartley Transf.' IEEE Procs. 9/86
            B = x[mRow * maxN + col];
            C = x[row * maxN + mCol];
            D = x[mRow * maxN + mCol];
            E = ((A + D) - (B + C)) / 2;
            x[row * maxN + col] = A - E;
            x[mRow * maxN + col] = B + E;
            x[row * maxN + mCol] = C + E;
            x[mRow * maxN + mCol] = D - E;
         }
      }
   }

   /**
    * Performs an optimized 1D FHT of an array or part of an array.
    *
    * @param x       Input array; will be overwritten by the output in the range given by base and
    *                maxN.
    * @param base    First index from where data of the input array should be read.
    * @param inverse True for inverse transform.
    * @param maxN    Length of data that should be transformed; this must be always the same for a
    *                given FHT object. Note that all amplitudes in the output 'x' are multiplied by
    *                maxN.
    */
   private void dfht3(float[] x, int base, boolean inverse, int maxN) {
      int i, stage, gpNum, gpSize, numGps, Nlog2;
      int bfNum, numBfs;
      int Ad0, Ad1, Ad2, Ad3, Ad4, CSAd;
      float rt1, rt2, rt3, rt4;

      if (S_ == null) {
         initializeTables(maxN);
      }
      Nlog2 = log2(maxN);
      BitRevRArr(x, base, Nlog2, maxN);   //bitReverse the input array
      gpSize = 2;     //first & second stages - do radix 4 butterflies once thru
      numGps = maxN / 4;
      for (gpNum = 0; gpNum < numGps; gpNum++) {
         Ad1 = gpNum * 4;
         Ad2 = Ad1 + 1;
         Ad3 = Ad1 + gpSize;
         Ad4 = Ad2 + gpSize;
         rt1 = x[base + Ad1] + x[base + Ad2];   // a + b
         rt2 = x[base + Ad1] - x[base + Ad2];   // a - b
         rt3 = x[base + Ad3] + x[base + Ad4];   // c + d
         rt4 = x[base + Ad3] - x[base + Ad4];   // c - d
         x[base + Ad1] = rt1 + rt3;      // a + b + (c + d)
         x[base + Ad2] = rt2 + rt4;      // a - b + (c - d)
         x[base + Ad3] = rt1 - rt3;      // a + b - (c + d)
         x[base + Ad4] = rt2 - rt4;      // a - b - (c - d)
      }

      if (Nlog2 > 2) {
         // third + stages computed here
         gpSize = 4;
         numBfs = 2;
         numGps = numGps / 2;
         for (stage = 2; stage < Nlog2; stage++) {
            for (gpNum = 0; gpNum < numGps; gpNum++) {
               Ad0 = gpNum * gpSize * 2;
               Ad1 = Ad0;     // 1st butterfly is different from others - no mults needed
               Ad2 = Ad1 + gpSize;
               Ad3 = Ad1 + gpSize / 2;
               Ad4 = Ad3 + gpSize;
               rt1 = x[base + Ad1];
               x[base + Ad1] = x[base + Ad1] + x[base + Ad2];
               x[base + Ad2] = rt1 - x[base + Ad2];
               rt1 = x[base + Ad3];
               x[base + Ad3] = x[base + Ad3] + x[base + Ad4];
               x[base + Ad4] = rt1 - x[base + Ad4];
               for (bfNum = 1; bfNum < numBfs; bfNum++) {
                  // subsequent BF's dealt with together
                  Ad1 = bfNum + Ad0;
                  Ad2 = Ad1 + gpSize;
                  Ad3 = gpSize - bfNum + Ad0;
                  Ad4 = Ad3 + gpSize;

                  CSAd = bfNum * numGps;
                  rt1 = x[base + Ad2] * C_[CSAd] + x[base + Ad4] * S_[CSAd];
                  rt2 = x[base + Ad4] * C_[CSAd] - x[base + Ad2] * S_[CSAd];

                  x[base + Ad2] = x[base + Ad1] - rt1;
                  x[base + Ad1] = x[base + Ad1] + rt1;
                  x[base + Ad4] = x[base + Ad3] + rt2;
                  x[base + Ad3] = x[base + Ad3] - rt2;

               } /* end bfNum loop */
            } /* end gpNum loop */
            gpSize *= 2;
            numBfs *= 2;
            numGps = numGps / 2;
         } /* end for all stages */
      } /* end if Nlog2 > 2 */

      if (inverse) {
         for (i = 0; i < maxN; i++) {
            x[base + i] = x[base + i] / maxN;
         }
      }
   }

   void transposeR(float[] x, int maxN) {
      int r, c;
      float rTemp;

      for (r = 0; r < maxN; r++) {
         for (c = r; c < maxN; c++) {
            if (r != c) {
               rTemp = x[r * maxN + c];
               x[r * maxN + c] = x[c * maxN + r];
               x[c * maxN + r] = rTemp;
            }
         }
      }
   }

   int log2(int x) {
      int count = 31;
      while (!btst(x, count)) {
         count--;
      }
      return count;
   }


   private boolean btst(int x, int bit) {
      return ((x & (1 << bit)) != 0);
   }

   private void BitRevRArr(float[] x, int base, int bitlen, int maxN) {
      for (int i = 0; i < maxN; i++) {
         tempArr_[i] = x[base + bitrev_[i]];
      }
      System.arraycopy(tempArr_, 0, x, base, maxN);
   }

   private int bitRevX(int x, int bitlen) {
      int temp = 0;
      for (int i = 0; i <= bitlen; i++) {
         if ((x & (1 << i)) != 0) {
            temp |= (1 << (bitlen - i - 1));
         }
      }
      return temp;
   }

   /**
    * Returns an 8-bit power spectrum, log-scaled to 1-254. The image in this FHT is assumed to be
    * in the frequency domain. Modified to remove scaling per William Mohler's tweaks.
    */
   public ImageProcessor getPowerSpectrum_noScaling() throws IllegalArgumentException {
      if (!isFrequencyDomain_) {
         throw new IllegalArgumentException("Frequency domain image required");
      }
      int base;
      float r;
      float[] fps = new float[maxN_ * maxN_];
      byte[] ps = new byte[maxN_ * maxN_];
      float[] fht = (float[]) getPixels();

      for (int row = 0; row < maxN_; row++) {
         FHTps(row, maxN_, fht, fps);
      }

      // no longer use min (=0), max, or scale (=1)

      for (int row = 0; row < maxN_; row++) {
         base = row * maxN_;
         for (int col = 0; col < maxN_; col++) {
            r = fps[base + col];
            if (Float.isNaN(r) || r < 1f)  // modified for no scaling
            {
               r = 0f;
            } else {
               r = (float) Math.log(r);  // modified for no scaling
            }
            ps[base + col] = (byte) (r + 1f); // 1 is min value
         }
      }
      ImageProcessor ip = new ByteProcessor(maxN_, maxN_, ps, null);
      swapQuadrants(ip);
      return ip;
   }

   /**
    * Power Spectrum of one row from 2D Hartley Transform.
    */
   private void FHTps(int row, int maxN, float[] fht, float[] ps) {
      int base = row * maxN;
      int l;
      for (int c = 0; c < maxN; c++) {
         l = ((maxN - row) % maxN) * maxN + (maxN - c) % maxN;
         ps[base + c] = (sqr(fht[base + c]) + sqr(fht[l])) / 2f;
      }
   }

   private static float sqr(float x) {
      return x * x;
   }

   /**
    * Pad the image to be square and have dimensions being a power of 2 Copied from
    * http://rsb.info.nih.gov/ij/developer/source/ij/plugin/FFT.java.html (in public domain),
    * changed name from pad() to padImage(), and tweaked to remove unused variables
    */
   private static ImageProcessor padImage(ImageProcessor ip) {
      final int originalWidth = ip.getWidth();
      final int originalHeight = ip.getHeight();
      int maxN = Math.max(originalWidth, originalHeight);
      int i = 2;
      while (i < maxN) {
         i *= 2;
      }
      if (i == maxN && originalWidth == originalHeight) {
         return ip;
      }
      maxN = i;
      ImageStatistics stats = ImageStatistics.getStatistics(ip, ImageStatistics.MEAN, null);
      ImageProcessor ip2 = ip.createProcessor(maxN, maxN);
      ip2.setValue(stats.mean);
      ip2.fill();
      ip2.insert(ip, 0, 0);
      return ip2;
   }

   /**
    * Swap quadrants 1 and 3 and 2 and 4 of the specified ImageProcessor so the power spectrum
    * origin is at the center of the image.
    * <pre>
    * 2 1
    * 3 4
    * </pre>
    */
   private void swapQuadrants(ImageProcessor ip) {
      ImageProcessor t1, t2;
      int size = ip.getWidth() / 2;
      ip.setRoi(size, 0, size, size);
      t1 = ip.crop();
      ip.setRoi(0, size, size, size);
      t2 = ip.crop();
      ip.insert(t1, 0, size);
      ip.insert(t2, size, 0);
      ip.setRoi(0, 0, size, size);
      t1 = ip.crop();
      ip.setRoi(size, size, size, size);
      t2 = ip.crop();
      ip.insert(t1, size, size);
      ip.insert(t2, 0, 0);
      ip.resetRoi();
   }

}
