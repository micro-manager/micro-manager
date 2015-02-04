/*
 * Copyright (c) 2015, nico
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
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionMappingAdapter;


class P2DFunc implements MultivariateFunction {
   private final double[] points_;
   
   public P2DFunc(double[] points) {
      points_ = points;
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
      for (double point : points_) {
         double predictedValue = p2d(point, doubles[0], doubles[1]);
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
 * function for the probability density function of the distrubtion of measured
 * distances.
 * @author nico
 */

public class P2DFitter {
   private final double[] points_;
   private double muGuess_ = 0.0;
   private double sigmaGuess_ = 10.0;
   public P2DFitter(double[] points) {
      points_ = points;
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
      
   public double[] solve() {
      SimplexOptimizer optimizer = new SimplexOptimizer(1e-9, 1e-12);
      P2DFunc myP2DFunc = new P2DFunc(points_);
      double[] lowerBounds = {0.0, 0.0};
      double[] upperBounds = {50.0, 50.0};
      MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
              myP2DFunc, lowerBounds, upperBounds);
      
      PointValuePair solution = optimizer.optimize(
              new ObjectiveFunction(mfma),
              new MaxEval(500),
              GoalType.MINIMIZE,
              new InitialGuess(new double[]{muGuess_, sigmaGuess_}),
              new NelderMeadSimplex(new double[]{0.2, 0.2})//,
      );
      
      return mfma.unboundedToBounded( solution.getPoint() );
   }
    
        
}


/*




/**
 *
 * @author nico
 *
public class P2DFitter extends AbstractCurveFitter{

   @Override
   protected LeastSquaresProblem getProblem(Collection<WeightedObservedPoint> points) {
      final int len = points.size();
      final double[] target  = new double[len];
      final double[] weights = new double[len];
      final double[] initialGuess = { 1.0, 1.0, 1.0 };

      int i = 0;
      for(WeightedObservedPoint point : points) {
         target[i]  = point.getY();
         weights[i] = point.getWeight();
         i += 1;
      }

      final AbstractCurveFitter.TheoreticalValuesFunction model = new
            AbstractCurveFitter.TheoreticalValuesFunction(new MyFunc(), points);

      return new LeastSquaresBuilder().
            maxEvaluations(Integer.MAX_VALUE).
            maxIterations(Integer.MAX_VALUE).
            start(initialGuess).
            target(target).
            weight(new DiagonalMatrix(weights)).
            model(model.getModelFunction(), model.getModelFunctionJacobian()).
            build();
   }
   
}

*/
