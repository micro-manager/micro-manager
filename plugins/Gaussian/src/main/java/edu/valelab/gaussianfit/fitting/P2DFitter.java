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

package edu.valelab.gaussianfit.fitting;

import edu.valelab.gaussianfit.utils.Besseli;
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
      double sum = 0;
      double sigma = sigma_;
      if (fitSigma_) {
         sigma = doubles[1];
      }
      for (double point : points_) {
         double predictedValue = p2d(point, doubles[0], sigma);
         sum += Math.log(predictedValue);
      }
      return -sum;
   }
    
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
      Exp exp = new Exp();
      double second = exp.value(- (mu * mu + r * r)/ (2 * sigma * sigma));
      double third = Besseli.bessi(0, (r * mu) / (sigma * sigma) );
      
      return first * second * third;
   }
   
}

/**
 * Class that uses the apache commons math3 library to fit a maximum likelihood
 * function for the probability density function of the distribution of measured
 * distances.
 * @author nico
 */

public class P2DFitter {
   private final double[] points_;
   private double muGuess_ = 0.0;
   private double sigmaGuess_ = 10.0;
   private final double upperBound_;
   private final boolean fitSigma_;
   
   /**
    * 
    * @param points array with data points to be fitted
    * @param fitSigma whether or not sigma should be fitted.  When false, the
    *                sigmaEstimate given in setStartParams will be used as 
    *                a fixed parameter in the P2D function
    * @param upperBound Upper bound for average and sigma
    */
   public P2DFitter(double[] points, final boolean fitSigma, 
           final double upperBound) {
      points_ = points;
      fitSigma_ = fitSigma;
      upperBound_ = upperBound;
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
      
   public double[] solve() throws FittingException {
      SimplexOptimizer optimizer = new SimplexOptimizer(1e-9, 1e-12);
      P2DFunc myP2DFunc = new P2DFunc(points_, fitSigma_, sigmaGuess_);

      if (fitSigma_) {
         double[] lowerBounds = {0.0, 0.0};
         double[] upperBounds = {upperBound_, upperBound_};
         MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
                 myP2DFunc, lowerBounds, upperBounds);

         PointValuePair solution = optimizer.optimize(
                 new ObjectiveFunction(mfma),
                 new MaxEval(500),
                 GoalType.MINIMIZE,
                 new InitialGuess(mfma.boundedToUnbounded(new double[]{muGuess_, sigmaGuess_})),
                 new NelderMeadSimplex(new double[]{0.2, 0.2})//,
         );

         return mfma.unboundedToBounded(solution.getPoint());
      } else {
         double[] lowerBounds = {0.0};
         double[] upperBounds = {upperBound_};
         MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
                 myP2DFunc, lowerBounds, upperBounds);

         try {
         PointValuePair solution = optimizer.optimize(
                 new ObjectiveFunction(mfma),
                 new MaxEval(500),
                 GoalType.MINIMIZE,
                 new InitialGuess(mfma.boundedToUnbounded(new double[]{muGuess_})),
                 new NelderMeadSimplex(new double[]{0.2})//,
         );

         return mfma.unboundedToBounded(solution.getPoint());
         } catch (TooManyEvaluationsException tmee) {
            throw new FittingException("P2D fit faled due to too many Evaluation Exceptions");
         }
      }
   }
            
}