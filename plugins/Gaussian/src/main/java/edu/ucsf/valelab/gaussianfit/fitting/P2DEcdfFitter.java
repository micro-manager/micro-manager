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
import java.awt.geom.Point2D;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

/**
 *
 * @author nico
 */
public class P2DEcdfFitter {
   private final Vector2D[] ecd_; // empirical cumulative distribution function
   private final double muGuess_;
   private final double sigmaGuess_;
   
   /**
    * Univariate function wrapping the p2d Set mu and sigma in advance, and used
    * to calculate the current p2d
    *
    */
   class p2dUniVariate implements UnivariateFunction {

      private double mu_;
      private double sigma_;

      public p2dUniVariate(double mu, double sigma) {
         mu_ = mu;
         sigma_ = sigma;
      }

      public void setParameters(double mu, double sigma) {
         mu_ = mu;
         sigma_ = sigma;
      }

      @Override
      public double value(double d) {
         if (d > sigma_) { // check if this is the correct condition
            return P2DFunctions.p2dApproximation(d, mu_, sigma_);
         }
         return P2DFunctions.p2d(d, mu_, sigma_);
      }
   }
   
   class P2DIntegralFunc implements MultivariateFunction {
      private final Vector2D[] data_;
      
      public P2DIntegralFunc(Vector2D[] data) {
         data_ = data;
      }
      
      @Override
      public double value(double[] doubles) {
         // TODO: use P2D estimate if sigma << mu?
         UnivariateFunction function = v ->  P2DFunctions.p2d(v, doubles[0], doubles[1]);
         UnivariateIntegrator in = new SimpsonIntegrator();
         double maxIntegral = in.integrate(100, function, 0.0, 
                 data_[data_.length - 1].getX());
         double lsqErrorSum = 0.0d;
         for (Vector2D d : data_) {
            // TODO: evalute if it is faster to remeber the prevous integration result
            double estimate = (in.integrate(100, function, 0.0, d.getX())) / maxIntegral;
            lsqErrorSum += (estimate - d.getY()) * (estimate - d.getY());
         }
         return lsqErrorSum;
      }
   }
   
   public P2DEcdfFitter (double[]d, double muGuess, double sigmaGuess) {
      ecd_ = EmpiricalCumulativeDistribution.calculate(d);
      muGuess_ = muGuess;
      sigmaGuess_ = sigmaGuess;
   }
   
   public double[] solve() throws FittingException {
      SimplexOptimizer optimizer = new SimplexOptimizer(1e-9, 1e-12);
      
      
      // approach: calculate cdf.  Use a Simplexoptimizer to optimize the 
      // least square error function of the numerical calculation of the PDF
      // against the experimental cdf
      double[] result = new double[2];
      
      return result;
      
      
   }
   
}
