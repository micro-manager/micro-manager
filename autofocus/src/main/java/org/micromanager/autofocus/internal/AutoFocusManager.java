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
package org.micromanager.autofocus.internal;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.micromanager.Studio;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.TextUtils;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;

/**
 *
 * @author Nick Anthony
 */

public class AutoFocusManager {
   // Note on the tolerance settings for the Brent optimizer:
   //
   // The reason BrentOptimizer needs both a relative and absolute tolerance
   // (the _rel_ and _abs_ arguments to the constructor) is that it is
   // designed for use with arbitrary floating-point numbers. A given
   // floating point type (e.g. double) always has a certain relative
   // precision, but larger values have less absolute precision (e.g.
   // 1.0e100 + 1.0 == 1.0e100).
   //
   // So if the result of the optimization is a large FP number, having just
   // an absolute tolerance (say, 0.01) would result in the algorithm
   // never terminating (or failing when it reaches the max iterations
   // constraint, if any). Using a reasonable relative tolerance can prevent
   // this and allow the optimization to finish when it reaches the
   // nearly-best-achievable optimum given the FP type.
   //
   // Z stage positions, of course, don't have this property and behave like
   // a fixed-point data type, so only the absolute tolerance is important.
   // As long as we have a value of _abs_ that is greater than the stage's
   // minimum step size, the optimizer should always converge (barring other
   // issues, such as a pathological target function (=focus score)), as
   // long as we don't run into the FP data type limitations.
   //
   // So here we need to select _rel_ to be large enough for
   // the `double` type and small enough to be negligible in terms of stage
   // position values. Since we don't expect huge values for the stage
   // position, we can be relatively conservative and use a value that is
   // much larger than the recommended minimum (2 x epsilon).
   //
   // For the user, it remains important to set a reasonable absolute
   // tolerance.
   //
   // 1.0e-9 is a reasonable relative tolerance to use here, since it
   // translates to 1 nm when the stage position is 1 m (1e6 µm). Thinking
   // of piezo stages, a generous position of 1000 µm would give relative
   // tolerance of 1 pm, again small enough to be negligible.
   //
   // The machine epsilon for double is 2e-53, so we could use a much
   // smaller value (down to 2e-27 or so) if we wanted to, but OughtaFocus
   // has been tested for quite some time with _rel_ = 1e-9 (the default
   // relative tolerance in commons-math 2) and appeared to function
   // correctly.
   private static final double BRENT_RELATIVE_TOLERANCE = 1e-9;
   private int imageCount_;
   private final Studio studio_;
   private long startTimeMs_;
   private boolean displayImages_ = false;
   private double searchRange_ = 10;
   private double absoluteTolerance_ = 1.0;
   private double cropFactor_ = 1;
   
   public AutoFocusManager(Studio studio) {
      studio_ = studio;
   }
   
   public void setDisplayImages(boolean display) {
      displayImages_ = display;
   }
   
   public boolean getDisplayImages() {
      return displayImages_;
   }
   
   public int getImageCount() {
      return imageCount_;
   }
   
   public void setSearchRange(double searchRange) {
      searchRange_ = searchRange;
   }
   
   public double getSearchRange() {
      return searchRange_;
   }
   
   public void setAbsoluteTolerance(double tolerance) {
      absoluteTolerance_ = tolerance;
   }
   
   public double getAbsoluteTolerance() {
      return absoluteTolerance_;
   }
   
   private double runAutofocusAlgorithm() throws Exception {
      startTimeMs_ = System.currentTimeMillis();

      UnivariateFunction scoreFun = (double d) -> {
         try {
            return measureFocusScore(d);
         } catch (Exception e) {
            throw new LocalException(d, e);
         }
      };
      
      UnivariateObjectiveFunction uof = new UnivariateObjectiveFunction(scoreFun);

      BrentOptimizer brentOptimizer =
         new BrentOptimizer(BRENT_RELATIVE_TOLERANCE, absoluteTolerance_);

      imageCount_ = 0;

      CMMCore core = studio_.getCMMCore();
      double z = core.getPosition(core.getFocusDevice());
      double startZUm = z;
      
      UnivariatePointValuePair result = brentOptimizer.optimize(uof, 
              GoalType.MAXIMIZE,
              new MaxEval(100),
              new SearchInterval(z - searchRange_ / 2, z + searchRange_ / 2));
      studio_.logs().logMessage("OughtaFocus Iterations: " + brentOptimizer.getIterations()
              + ", z=" + TextUtils.FMT2.format(result.getPoint())
              + ", dz=" + TextUtils.FMT2.format(result.getPoint() - startZUm)
              + ", t=" + (System.currentTimeMillis() - startTimeMs_));
      return result.getPoint();
   }

   private void setZPosition(double z) throws Exception {
      CMMCore core = studio_.getCMMCore();
      String focusDevice = core.getFocusDevice();
      core.setPosition(focusDevice, z);
      core.waitForDevice(focusDevice);
   }
   
   public double measureFocusScore(double z) throws Exception {
      CMMCore core = studio_.getCMMCore();
      long start = System.currentTimeMillis();
      try {
         setZPosition(z);
         long tZ = System.currentTimeMillis() - start;

         TaggedImage img;
         core.waitForDevice(core.getCameraDevice());
         core.snapImage();
         final TaggedImage img1 = core.getTaggedImage();
         img = img1;
         if (displayImages_) {
            SwingUtilities.invokeLater(() -> {
               try {
                  studio_.live().displayImage(studio_.data().convertTaggedImage(img1));
               }
               catch (JSONException | IllegalArgumentException e) {
                  studio_.logs().showError(e);
               }
            });
         }

         long tI = System.currentTimeMillis() - start - tZ;
         ImageProcessor proc = makeMonochromeProcessor(core, getMonochromePixels(img));
         double score = computeScore(proc);
         long tC = System.currentTimeMillis() - start - tZ - tI;
         studio_.logs().logMessage("OughtaFocus: image=" + imageCount_++
                 + ", t=" + (System.currentTimeMillis() - startTimeMs_)
                 + ", z=" + TextUtils.FMT2.format(z)
                 + ", score=" + TextUtils.FMT2.format(score)
                 + ", Tz=" + tZ + ", Ti=" + tI + ", Tc=" + tC);
         return score;
      } catch (Exception e) {
         studio_.logs().logError(e);
         throw e;
      }
   }
      
   private static class LocalException extends RuntimeException {
      // The x value that caused the problem.
      private final double x_;
      private final Exception ex_;

      public LocalException(double x, Exception ex) {
         x_ = x;
         ex_ = ex;
      }

      public double getX() {
         return x_;
      }

      public Exception getException() {
         return ex_;
      } 
   }
   
   public static ImageProcessor makeMonochromeProcessor(CMMCore core, Object pixels) {
      int w = (int) core.getImageWidth();
      int h = (int) core.getImageHeight();
      if (pixels instanceof byte[]) {
         return new ByteProcessor(w, h, (byte[]) pixels, null);
      } else if (pixels instanceof short[]) {
         return new ShortProcessor(w, h, (short[]) pixels, null);
      } else {
         return null;
      }
   }

   public static Object getMonochromePixels(TaggedImage image) throws JSONException, Exception {
      if (MDUtils.isRGB32(image)) {
         final byte[][] planes = ImageUtils.getColorPlanesFromRGB32((byte[]) image.pix);
         final int numPixels = planes[0].length;
         byte[] monochrome = new byte[numPixels];
         for (int j=0;j<numPixels;++j) {
            monochrome[j] = (byte) ((planes[0][j] + planes[1][j] + planes[2][j]) / 3);
         }
         return monochrome;
      } else if (MDUtils.isRGB64(image)) {
         final short[][] planes = ImageUtils.getColorPlanesFromRGB64((short[]) image.pix);
         final int numPixels = planes[0].length;
         short[] monochrome = new short[numPixels];
         for (int j=0;j<numPixels;++j) {
            monochrome[j] = (short) ((planes[0][j] + planes[1][j] + planes[2][j]) / 3);
         }
         return monochrome;
      } else {
         return image.pix;  // Presumably already a gray image.
      }
   }
}

