/*
 * Copyright (c) 2018, Regents the University of California
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

import edu.ucsf.valelab.gaussianfit.utils.EmpiricalCumulativeDistribution;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

/**
 * @author nico
 */
public class P2DEcdfFitter {

   private final Vector2D[] ecd_; // empirical cumulative distribution function
   private final double muGuess_;
   private final double sigmaGuess_;
   private final double maxMu_;

   /**
    * Helper class to calculate the least square error between the numerically calculated P2D for a
    * given mu and sigma, and the experimentally determined cumulative distribution function
    */
   private class P2DIntegralFunc implements MultivariateFunction {

      private final Vector2D[] data_;

      public P2DIntegralFunc(Vector2D[] data) {
         data_ = data;
      }

      /**
       * @param input = double[] {mu, sigma}
       * @return least square error with the data
       */
      @Override
      public double value(double[] input) {
         // use real P2d unless mu > 5 * sigma
         UnivariateFunction function = v -> P2DFunctions.p2d(v, input[0],
               input[1]);
         if (input[0] > 5 * input[1]) {
            function = v -> P2DFunctions.p2dApproximation(v, input[0],
                  input[1]);
         }
         UnivariateIntegrator in = new SimpsonIntegrator();
         double maxIntegral = in.integrate(100000000, function, 0.0,
               data_[data_.length - 1].getX());
         double lsqErrorSum = 0.0d;
         Vector2D previousIntegral = new Vector2D(0.0, 0.0);
         for (Vector2D d : data_) {
            if (d.getX() <= previousIntegral.getX()) {
               // will happen when same value occurs twice as in bootstrapping
               lsqErrorSum += (previousIntegral.getY() - d.getY()) *
                     (previousIntegral.getY() - d.getY());
            } else {
               double incrementalIntegral = in.integrate(100000000, function,
                     previousIntegral.getX(), d.getX());
               double currentIntegral = previousIntegral.getY() + incrementalIntegral;
               previousIntegral = new Vector2D(d.getX(), currentIntegral);
               double fractionalIntegral = currentIntegral / maxIntegral;
               lsqErrorSum += ((fractionalIntegral - d.getY()) * (fractionalIntegral - d.getY()));
            }
         }
         return lsqErrorSum;
      }
   }


   /**
    * Calculates the empirical cumulative distribution function from the data Then fits mu and sigma
    * in the P2D function by calculating the least square error between the numerically calculated
    * integral of the P2D and the experimental data
    *
    * @param d          Input data: array of measured distances
    * @param muGuess    Initial estimate of mu
    * @param sigmaGuess Initial estimate for sigma
    * @param maxMu      Maximum bound for mu.  Currently not used. Need to figure out bounding
    */
   public P2DEcdfFitter(double[] d, double muGuess, double sigmaGuess, double maxMu) {
      ecd_ = EmpiricalCumulativeDistribution.calculate(d);
      muGuess_ = muGuess;
      sigmaGuess_ = sigmaGuess;
      maxMu_ = maxMu;  // TODO: figure out how to properly use bounds
   }

   public double[] solve() throws FittingException {
      SimplexOptimizer optimizer = new SimplexOptimizer(1e-9, 1e-12);
      P2DIntegralFunc integralFunction = new P2DIntegralFunc(ecd_);

      // approach: calculate cdf.  Use a Simplexoptimizer to optimize the 
      // least square error function of the numerical calculation of the PDF
      // against the experimental cdf
      PointValuePair result = optimizer.optimize(new MaxEval(5000),
            new ObjectiveFunction(integralFunction),
            GoalType.MINIMIZE,
            new InitialGuess(new double[]{muGuess_, sigmaGuess_}),
            new NelderMeadSimplex(new double[]{0.2, 0.2})
            //, this may work in math4:
            // new SimpleBounds(new double[] {0.0, 0.0}, new double[] {100.0, 50.0}
      );

      return result.getPoint();

   }

}
