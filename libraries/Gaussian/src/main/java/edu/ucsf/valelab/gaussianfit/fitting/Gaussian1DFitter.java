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

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.function.Exp;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionMappingAdapter;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

/**
 * @author nico
 */
class Gaussian1DFunc implements MultivariateFunction {

   private final double[] points_;

   /**
    * @param points array with measurements
    */
   public Gaussian1DFunc(double[] points) {
      points_ = points;
   }

   /**
    * Calculate the sum of the likelihood function
    *
    * @param doubles array with parameters, here doubles[0] == mu, doubles [1] == sigma
    * @return -sum(logP2D(points, mu, sigma));
    */
   @Override
   public double value(double[] doubles) {
      double sum = 0.0;
      for (double point : points_) {
         double predictedValue = Gaussian1DFitter.gaussian(point, doubles[0], doubles[1]);
         sum += Math.log(predictedValue);
      }
      return sum;
   }


}


/**
 * Class that uses the apache commons math3 library to fit a Gaussian distribution by optimizing the
 * maximum likelihood function distances.
 *
 * @author nico
 */

public class Gaussian1DFitter {

   private final double[] points_;
   private double muGuess_ = 0.0;
   private double sigmaGuess_ = 10.0;
   private final double upperBound_;
   private double lowerBound_ = 0.0;

   static double sqrt2Pi = Math.sqrt(2 * Math.PI);


   public static double gaussian(double r, double mu, double sigma) {
      double first = 1 / (sqrt2Pi * sigma);
      Exp exp = new Exp();
      double second = exp.value(-(r - mu) * (r - mu) / (2 * sigma * sigma));

      return first * second;
   }


   /**
    * @param points     array with data points to be fitted
    * @param upperBound Upper bound for average and sigma
    */
   public Gaussian1DFitter(double[] points, final double upperBound) {
      points_ = points;
      upperBound_ = upperBound;
   }

   /**
    * Lets caller provide start parameters for fit of mu and sigma
    *
    * @param mu
    * @param sigma
    */
   public void setStartParams(double mu, double sigma) {
      muGuess_ = mu;
      sigmaGuess_ = sigma;
   }

   /**
    * In most cases, we work with distances and the lower bound is 0.0. To make this function useful
    * for other data, provide the option to set the lower bound
    *
    * @param lowerBound lowerBound to be used in Gaussian fit
    */
   public void setLowerBound(final double lowerBound) {
      lowerBound_ = lowerBound;
   }

   /**
    * Given a stepsize, generate an array with distances between 0 and upperBound
    *
    * @param stepSize distance between values in output array
    * @return array with distances between 0 and upperbound, stepsize apart
    */
   public double[] getDistances(double stepSize) {
      return getDistances(0.0, stepSize, upperBound_);
   }

   /**
    * Given a stepsize, generate an array with distances between 0 and upperBound
    *
    * @param start    first distance in the array
    * @param stepSize distance between values in output array
    * @param end      last distance in the array
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


   public double[] solve() throws FittingException {
      SimplexOptimizer optimizer = new SimplexOptimizer(1e-9, 1e-12);
      Gaussian1DFunc myGaussianFunc = new Gaussian1DFunc(points_);

      double[] lowerBounds = {lowerBound_, lowerBound_};
      double[] upperBounds = {upperBound_, upperBound_};
      MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
            myGaussianFunc, lowerBounds, upperBounds);

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
         throw new FittingException("Gaussian fit failed due to too many Evaluation Exceptions");
      }

   }


}