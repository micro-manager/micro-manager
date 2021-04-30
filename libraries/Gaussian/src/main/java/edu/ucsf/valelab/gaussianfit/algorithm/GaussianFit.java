/**
 * Class that implements Gaussian fitting using the apache commons math library
 * <p>
 * Copyright (c) 2010-2017, Regents of the University of California All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * The views and conclusions contained in the software and documentation are those of the authors
 * and should not be interpreted as representing official policies, either expressed or implied, of
 * the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.algorithm;

import edu.ucsf.valelab.gaussianfit.fitting.MultiVariateGaussianFunction;
import edu.ucsf.valelab.gaussianfit.fitting.MultiVariateGaussianMLE;
import edu.ucsf.valelab.gaussianfit.fitting.ParametricGaussianFunction;
import edu.ucsf.valelab.gaussianfit.utils.GaussianUtils;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;
import ij.process.ImageProcessor;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.commons.math.optimization.SimpleScalarValueChecker;
import org.apache.commons.math.optimization.VectorialConvergenceChecker;
import org.apache.commons.math.optimization.VectorialPointValuePair;
import org.apache.commons.math.optimization.direct.NelderMead;
import org.apache.commons.math.optimization.fitting.CurveFitter;
import org.apache.commons.math.optimization.general.LevenbergMarquardtOptimizer;
import org.apache.commons.math.optimization.general.NonLinearConjugateGradientOptimizer;

/**
 * @author nico
 */
public class GaussianFit {

   public class Data {

      private final double[] parms_;
      private final double apertureIntensity_; // in raw units
      private final double apertureBackground_; // in raw units

      public Data(double[] parms, double intensity, double background) {
         parms_ = parms;
         apertureIntensity_ = intensity;
         apertureBackground_ = background;
      }

      public double[] getParms() {
         return parms_;
      }

      public double getApertureIntensity() {
         return apertureIntensity_;
      }

      public double getApertureBackground() {
         return apertureBackground_;
      }

   }

   public static final int INT = 0;
   public static final int BGR = 1;
   public static final int XC = 2;
   public static final int YC = 3;
   public static final int S = 4;
   public static final int S1 = 4;
   public static final int S2 = 5;
   public static final int S3 = 6;
   public static final int NELDERMEAD = 1;
   public static final int LEVENBERGMARQUARD = 2;
   public static final int NELDERMEADMLE = 3;
   public static final int LEVENBERGMARQUARDMLE = 4;
   public static final int CIRCLE = 1;
   public static final int ASYMMETRIC = 2;
   public static final int ELLIPSE = 3;


   double[] params0_;
   double[] steps_;
   public String[] paramNames_;

   short[] data_;
   int nx_;
   int ny_;
   int count_ = 0;
   int shape_ = CIRCLE;
   int fitMode_ = NELDERMEAD;
   boolean fixWidth_ = false;
   double fixedWidth_ = 0.9;

   NelderMead nm_;
   SimpleScalarValueChecker convergedChecker_;
   MultiVariateGaussianFunction mGF_;
   MultiVariateGaussianMLE mGFMLE_;
   NonLinearConjugateGradientOptimizer nlcgo_;
   LevenbergMarquardtOptimizer lMO_;


   /**
    * Gaussian fit can be run by estimating parameter c (width of Gaussian) as 1 (circle), 2 (width
    * varies in x and y), or 3 (ellipse) parameters
    *
    * @param shape      - fit circle (1) ellipse(2), or ellipse with varying angle (3)
    * @param fitMode    - algorithm use: NelderMead (1), Levenberg Marquard (2), NelderMean MLE (3),
    *                   LevenberMarquard MLE(4)
    * @param fixWidth   - if true, do not fit the width
    * @param fixedWidth - width of the Gaussian in pixels
    */
   public GaussianFit(int shape, final int fitMode, final boolean fixWidth,
         final double fixedWidth) {
      super();
      fitMode_ = fitMode;
      fixWidth_ = fixWidth;
      fixedWidth_ = fixedWidth;
      // There is no point to fix the width of the Gaussian in modes other than 1:
      if (fixWidth_ && fixedWidth_ > 0.0) {
         shape = CIRCLE;
      } else {
         fixedWidth_ = -1.0;
         fixWidth_ = false;
      }
      if (shape == CIRCLE) {
         shape_ = CIRCLE;
         paramNames_ = new String[]{"A", "b", "x_c", "y_c", "sigma"};
      }
      if (shape == ASYMMETRIC) {
         shape_ = ASYMMETRIC;
         paramNames_ = new String[]{"A", "b", "x_c", "y_c", "sigmaX", "sigmaY"};
      }
      if (shape == ELLIPSE) {
         shape_ = ELLIPSE;
         paramNames_ = new String[]{"A", "b", "x_c", "y_c", "sigmaX", "sigmaY", "theta"};
      }
      int paramSize = shape_ + 3;
      if (!(fixWidth_ && fixedWidth_ > 0.0)) {
         paramSize += 1;
      }
      params0_ = new double[paramSize];
      steps_ = new double[paramSize];

      if (fitMode_ == NELDERMEAD) {
         nm_ = new NelderMead();
         convergedChecker_ = new SimpleScalarValueChecker(1e-9, -1);
         mGF_ = new MultiVariateGaussianFunction(shape_, fixedWidth_);
      }
      // Levenberg-Marquardt and weighted Levenberg-Marquardt
      if (fitMode_ == LEVENBERGMARQUARD || fitMode == LEVENBERGMARQUARDMLE) {
         lMO_ = new LevenbergMarquardtOptimizer();
         LMChecker lmChecker = new LMChecker();
         lMO_.setConvergenceChecker(lmChecker);
      }
      if (fitMode_ == NELDERMEADMLE) {
         nm_ = new NelderMead();
         convergedChecker_ = new SimpleScalarValueChecker(1e-9, -1);
         mGFMLE_ = new MultiVariateGaussianMLE(shape_, fixedWidth_);
      }
      /*
       * Gradient MLE, not working very well
       *
      if (fitMode_ == 4) {
         mGFMLE_ = new MultiVariateGaussianMLE(mode_);
         nlcgo_ = 
            new NonLinearConjugateGradientOptimizer(ConjugateGradientFormula.FLETCHER_REEVES);
         convergedChecker_ = new SimpleScalarValueChecker(1e-6,-1);
         nlcgo_.setConvergenceChecker(convergedChecker_);
         nlcgo_.setInitialStep(0.0000002);
      }
      */
   }


   public GaussianFit(int mode, int fitMode) {
      this(mode, fitMode, false, 0.0);
   }


   /**
    * Performs Gaussian Fit on a given ImageProcessor Estimates initial values for the fit and sends
    * off to Apache fitting code Background is estimated by averaging the outer rows and columns
    * Sigma estimate is hardcoded to 1.115 pixels Signal estimate is derived from total signal -
    * total background estimate Steps sizes for the optimizer are set at 0.3 * the estimate values
    *
    * @param siProc        - ImageJ ImageProcessor containing image to be fit
    * @param maxIterations - maximum number of iterations for the Nelder Mead optimization
    *                      algorithm
    * @return
    */
   public Data dogaussianfit(ImageProcessor siProc, int maxIterations) {
      Data estimate = estimateParameters(siProc);

      double[] paramsOut = {0.0};

      if (fitMode_ == NELDERMEAD) {
         nm_.setStartConfiguration(steps_);
         nm_.setConvergenceChecker(convergedChecker_);
         nm_.setMaxIterations(maxIterations);
         mGF_.setImage((short[]) siProc.getPixels(), siProc.getWidth(), siProc.getHeight());
         try {
            RealPointValuePair result = nm_.optimize(mGF_, GoalType.MINIMIZE,
                  estimate.getParms());
            paramsOut = result.getPoint();
         } catch (java.lang.OutOfMemoryError e) {
            throw (e);
         } catch (FunctionEvaluationException e) {
            ReportingUtils.logError(e);
         } catch (OptimizationException e) {
            ReportingUtils.logError(e);
         } catch (IllegalArgumentException e) {
            ReportingUtils.logError(e);
         }
      }

      if (fitMode_ == LEVENBERGMARQUARD || fitMode_ == LEVENBERGMARQUARDMLE) {

         // lMO_.setMaxIterations(maxIterations);
         CurveFitter cF = new CurveFitter(lMO_);
         short[] pixels = (short[]) siProc.getPixels();
         if (fitMode_ == LEVENBERGMARQUARD) {
            for (int i = 0; i < pixels.length; i++) {
               cF.addObservedPoint(i, (int) pixels[i] & 0xffff);
            }
         }
         if (fitMode_ == LEVENBERGMARQUARDMLE) {
            for (int i = 0; i < pixels.length; i++) {
               double factor = ((int) pixels[i] & 0xffff);
               cF.addObservedPoint(1 / factor, i, (int) pixels[i] & 0xffff);
            }
         }
         try {
            paramsOut = cF.fit(new ParametricGaussianFunction(
                        shape_, siProc.getWidth(), fixedWidth_),
                  estimate.getParms());
         } catch (FunctionEvaluationException ex) {
            ReportingUtils.logError(ex.getMessage());
         } catch (OptimizationException ex) {
            ReportingUtils.logError(ex.getMessage());
         } catch (IllegalArgumentException ex) {
            ReportingUtils.logError(ex.getMessage());
         } catch (Exception miee) {
            ReportingUtils.logError(miee.getMessage());
         }
      }

      // Simplex-MLE
      if (fitMode_ == NELDERMEADMLE) {
         nm_.setStartConfiguration(steps_);
         nm_.setConvergenceChecker(convergedChecker_);
         nm_.setMaxIterations(maxIterations);
         mGFMLE_.setImage((short[]) siProc.getPixels(), siProc.getWidth(), siProc.getHeight());
         try {
            RealPointValuePair result = nm_.optimize(mGFMLE_, GoalType.MINIMIZE,
                  estimate.getParms());
            paramsOut = result.getPoint();
         } catch (java.lang.OutOfMemoryError e) {
            throw (e);
         } catch (FunctionEvaluationException e) {
            ReportingUtils.logError(e);
         } catch (OptimizationException e) {
            ReportingUtils.logError(e);
         } catch (IllegalArgumentException e) {
            ReportingUtils.logError(e);
         }
      }
      
      /*
       * not working very well....
      // gradient-MLE
      if (fitMode_ == LEVENBERGMARQUARDMLE) {
         nlcgo_.setMaxIterations(maxIterations);
         mGFMLE_.setImage((short[]) siProc.getPixels(), siProc.getWidth(), siProc.getHeight());
         try {
            RealPointValuePair result = nlcgo_.optimize(mGFMLE_, GoalType.MINIMIZE, params0_);
            paramsOut = result.getPoint();
         } catch (java.lang.OutOfMemoryError e) {
            throw(e);
         } catch (Exception e) {
            ij.IJ.log(" " + e.toString());
         }
      }
      */

      if (shape_ == ELLIPSE) {
         if (paramsOut.length > S3) {
            double[] prms = GaussianUtils
                  .ellipseParmConversion(paramsOut[S1], paramsOut[S2], paramsOut[S3]);
            paramsOut[S1] = prms[1];
            paramsOut[S2] = prms[2];
            paramsOut[S3] = prms[0];
         }
      }
      return new Data(paramsOut, estimate.getApertureIntensity(),
            estimate.getApertureBackground());

   }


   private Data estimateParameters(ImageProcessor siProc) {
      short[] imagePixels = (short[]) siProc.getPixels();

      // Hard code estimate for sigma (expressed in pixels):
      double s = 0.9;
      if (!(fixWidth_ && fixedWidth_ > 0.0)) {
         params0_[S] = s;
      } else {
         s = fixedWidth_;
      }
      if (shape_ >= 2) {
         params0_[S2] = 0.9;
      }
      if (shape_ == 3) {
         params0_[S1] = 1;
         params0_[S2] = 0;
         params0_[S3] = 1;
      }
      double bg = 0.0;
      int n = 0;
      int lastRowOffset = (siProc.getHeight() - 1) * siProc.getWidth();
      for (int i = 0; i < siProc.getWidth(); i++) {
         bg += (imagePixels[i] & 0xffff);
         bg += (imagePixels[i + lastRowOffset] & 0xffff);
         n += 2;
      }
      for (int i = 1; i < siProc.getHeight() - 1; i++) {
         bg += (imagePixels[i * siProc.getWidth()] & 0xffff);
         bg += (imagePixels[(i + 1) * siProc.getWidth() - 1] & 0xffff);
         n += 2;
      }
      double background = bg / n;
      params0_[BGR] = background;
      // estimate signal by subtracting background from total intensity
      double totalIntensity = 0.0;
      for (int i = 0; i < siProc.getHeight() * siProc.getWidth(); i++) {
         totalIntensity += (imagePixels[i] & 0xffff);
      }
      double signal = totalIntensity - (background * siProc.getHeight() * siProc.getWidth());
      params0_[INT] = signal / (2 * Math.PI * s * s);

      // estimate center of mass
      double mx = 0.0;
      double my = 0.0;
      for (int i = 0; i < siProc.getHeight() * siProc.getWidth(); i++) {
         mx += ((imagePixels[i] & 0xffff)) * (i % siProc.getWidth());
         my += ((imagePixels[i] & 0xffff)) * (Math.floor(i / siProc.getWidth()));
      }
      params0_[XC] = mx / totalIntensity;
      params0_[YC] = my / totalIntensity;
      //ij.IJ.log("Centroid: " + mx/mt + " " + my/mt);
      // set step size during estimate
      for (int i = 0; i < params0_.length; ++i) {
         steps_[i] = params0_[i] * 0.3;
         if (steps_[i] == 0) {
            steps_[i] = 0.1;
         }
      }

      return new Data(params0_, signal, background);
   }


   private class LMChecker implements VectorialConvergenceChecker {

      int iteration_ = 0;
      boolean lastResult_ = false;

      @Override
      public boolean converged(int i, VectorialPointValuePair previous,
            VectorialPointValuePair current) {
         if (i == iteration_) {
            return lastResult_;
         }

         iteration_ = i;
         double[] p = previous.getPoint();
         double[] c = current.getPoint();

         boolean sOK = true;
         if (!fixWidth_) {
            sOK = Math.abs(p[S] - c[S]) < 5;
         }

         if (Math.abs(p[INT] - c[INT]) < 10 &&
               Math.abs(p[BGR] - c[BGR]) < 0.2 &&
               Math.abs(p[XC] - c[XC]) < 0.01 &&
               Math.abs(p[YC] - c[YC]) < 0.01 &&
               sOK) {
            lastResult_ = true;
            return true;
         }

         lastResult_ = false;
         return false;
      }
   }

}
