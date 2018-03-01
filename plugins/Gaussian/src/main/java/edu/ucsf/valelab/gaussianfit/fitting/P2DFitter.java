/*
 * Copyright (c) 2015-2017, Regents the University of California
 * Author: Nico Stuurman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package edu.ucsf.valelab.gaussianfit.fitting;

import edu.ucsf.valelab.gaussianfit.utils.Besseli;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.function.Exp;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionMappingAdapter;

/**
 * Implements fitting of pairwise distribution function as described in
 *         http://dx.doi.org/10.1529/biophysj.105.065599
 * 
 * @author nico
 */
class P2DFunc implements MultivariateFunction {
   private final double[] points_;
   private final double sigma_;
   private final boolean fitSigma_;
   
   /**
    * 
    * @param points array with measurements
    * @param fitSigma whether sigma value should be fitted
    * @param sigma  Fixed sigma, only needed when fitSigma is true
    */
   public P2DFunc(double[] points, final boolean fitSigma, final double sigma) {
      points_ = points;
      fitSigma_ = fitSigma;
      sigma_ = sigma;
   }
   
   /**
    * Calculate the sum of the likelihood function
    * @param doubles array with parameters, here doubles[0] == mu, 
    * doubles [1] == sigma
    * @return -sum(logP2D(points, mu, sigma));
    */
   @Override
   public double value(double[] doubles) {
      double sum = 0.0;
      double sigma = sigma_;
      if (fitSigma_) {
         sigma = doubles[1];
      }
      for (double point : points_) {
         double predictedValue = P2DFitter.p2d(point, doubles[0], sigma);
         sum += Math.log(predictedValue);
      }
      return sum;
   }
   
   public double nonLogValue(double[] doubles) {
      double sum = 0;
      double sigma = sigma_;
      if (fitSigma_) {
         sigma = doubles[1];
      }
      for (double point : points_) {
         double predictedValue = P2DFitter.p2d(point, doubles[0], sigma);
         sum *= predictedValue;
      }
      return sum;
   }
     
}


class P2D50 implements MultivariateFunction {
   private final double target_;
   private final double mu_;
   private final double sigma_;
   
   /**
    * 
    * @param points array with measurements
    * @param fitSigma whether sigma value should be fitted
    * @param sigma  Fixed sigma, only needed when fitSigma is true
    */
   public P2D50(double target, double mu, double sigma) {
      target_ = target;
      mu_ = mu;
      sigma_ = sigma;
   }
   
   /**
    * Calculates the difference of the likelihood with the target
    * @param doubles array of size 1 containing r (i.e. we are looking 
    * for the r that gives a value closest to target_)
    * @return target - P2D(r, mu, sigma));
    */
   @Override
   public double value(double[] doubles) {
      double result = target_ - P2DFitter.p2d(doubles[0], mu_, sigma_);
      return result * result;
   }
    

   
}

/**
 * Implements fitting of pairwise distribution function as described in
 *         http://dx.doi.org/10.1529/biophysj.105.065599
 * 
 * This version uses the measured sigmas for each individual spot (pair)
 * 
 * @author nico
 */
class P2DIndividualSigmasFunc implements MultivariateFunction {
   private final double[] points_;
   private final double[] sigmas_;
   private final boolean useApproximation_;
   
   /**
    * @param useApproximation Use approximation to p2d if true. This is 
    *                         needed to accurately solve instances where 
    *                         mu >> sigma
    * @param points array with measurements
    * @param fitSigma whether sigma value should be fitted
    * @param sigma  Fixed sigma, only needed when fitSigma is true
    */
   public P2DIndividualSigmasFunc(final boolean useApproximation, 
           final double[] points, final double[] sigmas) {
      useApproximation_ = useApproximation;
      points_ = points;
      sigmas_ = sigmas;
      
      // TODO: bail out if these two arrays are not identical in size
   }
   
   /**
    * Calculate the sum of the likelihood function
    * @param doubles array with parameters, here doubles[0] == mu, 
    * doubles [1] == sigma
    * @return -sum(logP2D(points, mu, sigma));
    */
   @Override
   public double value(double[] doubles) {
      double sum = 0.0;
      if (useApproximation_) {
         for (int i = 0; i < points_.length; i++) {
            double predictedValue = P2DFitter.p2dApproximation(points_[i], doubles[0], sigmas_[i]);
            sum += Math.log(predictedValue);
         }
      } else {
         for (int i = 0; i < points_.length; i++) {
            double predictedValue = P2DFitter.p2d(points_[i], doubles[0], sigmas_[i]);
            sum += Math.log(predictedValue);
         }
      }
      return sum;
   }
   
   public double nonLogValue(double[] doubles) {
      double sum = 0;
      if (useApproximation_) {
         for (int i = 0; i < points_.length; i++) {
            double predictedValue = P2DFitter.p2dApproximation(points_[i], doubles[0], sigmas_[i]);
            sum *= predictedValue;
         }
      } else {
         for (int i = 0; i < points_.length; i++) {
            double predictedValue = P2DFitter.p2d(points_[i], doubles[0], sigmas_[i]);
            sum *= predictedValue;
         }
      }
      return sum;
   }
     
}



/**
 * Class that uses the apache commons math3 library to fit a maximum likelihood
 * function for the probability density function of the distribution of measured
 * distances.
 * @author nico
 */

public class P2DFitter {
   
   private final static Exp EXP = new Exp();
   private final double[] points_;
   private final double[] sigmas_;
   private final double upperBound_;
   private final boolean fitSigma_;
   private final boolean useIndividualSigmas_;
   private double muGuess_ = 0.0;
   private double sigmaGuess_ = 10.0;
   
   
    /**
    * Calculates the probability density function:
    * p2D(r) = (r / sigma2) exp(-(mu2 + r2)/2sigma2) I0(rmu/sigma2)
    * where I0 is the modified Bessel function of integer order zero
    * @param r
    * @param mu
    * @param sigma
    * @return 
    */
   public static double p2d (double r, double mu, double sigma) {
      double first = r / (sigma * sigma);
      double second = EXP.value(- (mu * mu + r * r)/ (2 * sigma * sigma));
      double third = Besseli.bessi(0, (r * mu) / (sigma * sigma) );
      
      if (second < 1e-300) {
         second = 1e-300;
      }
      if (Double.isInfinite(third)) {
         third = Double.MAX_VALUE;
      }
      
      return first * second * third;
   }
   
   public static double p2dApproximation(double r, double mu, double sigma) {
      
      double result = Math.sqrt(r / (2 * Math.PI * sigma * mu)) * 
              EXP.value(- (r - mu) * (r - mu) / (2 * sigma * sigma) );
      if (result < Double.MIN_NORMAL) {
         result = Double.MIN_NORMAL;
      }
      return result; 
   }
   
   
   /**
    * 
    * @param points array with data points to be fitted
    * @param sigmas array with sigmas forpoints above.  Only used when useIndvidualSigmas is true
    * @param fitSigma whether or not sigma should be fitted.  When false, the
    *                sigmaEstimate given in setStartParams will be used as 
    *                a fixed parameter in the P2D function
    * @param upperBound Upper bound for average and sigma
    * @param useIndividualSigmas Use the sigmas for individual points in the fit function
    */
   public P2DFitter(final double[] points, 
           final double[] sigmas, 
           final boolean fitSigma, 
           final double upperBound, 
           final boolean useIndividualSigmas) {
      points_ = points;
      sigmas_ = sigmas;
      fitSigma_ = fitSigma;
      upperBound_ = upperBound;
      useIndividualSigmas_ = useIndividualSigmas;
   }
   
   /**
    * Lets caller provide start parameters for fit of mu and sigma
    * @param mu
    * @param sigma 
    */
   public void setStartParams(double mu, double sigma) {
      muGuess_ = mu;
      sigmaGuess_ = sigma;
   }
   
   /**
    * Given a stepsize, generate an array with distances between 0 and upperBound
    * @param stepSize distance between values in output array
    * @return array with distances between 0 and upperbound, stepsize apart
    */
   public double[] getDistances(double stepSize) {
      return getDistances(0.0, stepSize, upperBound_);
   }
   
   /**
    * Given a stepsize, generate an array with distances between 0 and upperBound
    * @param start first distance in the array
    * @param stepSize distance between values in output array
    * @param end last distance in the array
    * @return array with distances between start and end, stepsize apart
    */
   public double[] getDistances(double start, double stepSize, double end) {
      // instead of being smart, studpidly calculate the size of the output array
      int count = 0; 
      for (double val = start; val <= end; val += stepSize) {
         count++;
      }
      double[] output = new double[count];
      double r = start;
      for (int i = 0; i < output.length; i++) {
         output[i] = r;
         r += stepSize;
      }
      return output;
   }
   
   
   
   /**
    * Given estimators for mu and sigma, what is the log likelihood for this
    * distribution of data?
    * @param estimators - array containing mu, and - if fitSigma is true sigma
    *       (i.e. the array returned from the solve function can be used here).
    * @param distances distances for which we want to know the likelihood
     * @return likelihood for the input distances
    */
   public double[] logLikelihood(double[] estimators, double[] distances) {
      double[] localEstimators = estimators.clone();
      P2DFunc myP2DFunc = new P2DFunc(points_, fitSigma_, sigmaGuess_);
      double[] output = new double[distances.length];
      double r = 0.0;
      for (int i = 0; i < output.length; i++) {
         localEstimators[0] = distances[i];
         output[i] = myP2DFunc.value(localEstimators); 
      }
      return output;
   }
      
   public double[] solve() throws FittingException {
      SimplexOptimizer optimizer = new SimplexOptimizer(1e-9, 1e-12);

      if (useIndividualSigmas_) {
         boolean useApproximation = sigmaGuess_ < muGuess_ / 2;
         P2DIndividualSigmasFunc myP2DFunc = new P2DIndividualSigmasFunc(
                 useApproximation, points_, sigmas_);
         double[] lowerBounds = {0.0};
         double[] upperBounds = {upperBound_};
         MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
                 myP2DFunc, lowerBounds, upperBounds);

         try {
            PointValuePair solution = optimizer.optimize(
                    new ObjectiveFunction(mfma),
                    new MaxEval(10000),
                    GoalType.MAXIMIZE,
                    new InitialGuess(mfma.boundedToUnbounded(new double[]{muGuess_})),
                    new NelderMeadSimplex(new double[]{0.2})//,
            );
            return mfma.unboundedToBounded(solution.getPoint());
         } catch (TooManyEvaluationsException tmee) {
            throw new FittingException("P2D fit failed due to too many Evaluation Exceptions");
         }
      } else if (fitSigma_) {
         P2DFunc myP2DFunc = new P2DFunc(points_, fitSigma_, sigmaGuess_);
         double[] lowerBounds = {0.0, 0.0};
         double[] upperBounds = {upperBound_, upperBound_};
         MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
                 myP2DFunc, lowerBounds, upperBounds);
         try {
            PointValuePair solution = optimizer.optimize(
                    new ObjectiveFunction(mfma),
                    new MaxEval(10000),
                    GoalType.MAXIMIZE,
                    new InitialGuess(mfma.boundedToUnbounded(new double[]{muGuess_, sigmaGuess_})),
                    new NelderMeadSimplex(new double[]{0.2, 0.2})//,
            );

            return mfma.unboundedToBounded(solution.getPoint());
         } catch (TooManyEvaluationsException tmee) {
            throw new FittingException("P2D fit failed due to too many Evaluation Exceptions");
         }
      } else {
         P2DFunc myP2DFunc = new P2DFunc(points_, fitSigma_, sigmaGuess_);
         double[] lowerBounds = {0.0};
         double[] upperBounds = {upperBound_};
         MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
                 myP2DFunc, lowerBounds, upperBounds);

         try {
            PointValuePair solution = optimizer.optimize(
                    new ObjectiveFunction(mfma),
                    new MaxEval(10000),
                    GoalType.MAXIMIZE,
                    new InitialGuess(mfma.boundedToUnbounded(new double[]{muGuess_})),
                    new NelderMeadSimplex(new double[]{0.2})//,
            );

            return mfma.unboundedToBounded(solution.getPoint());
         } catch (TooManyEvaluationsException tmee) {
            throw new FittingException("P2D fit failed due to too many Evaluation Exceptions");
         }
      }
   }

            
}