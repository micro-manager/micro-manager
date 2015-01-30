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
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;


class P2DFunc implements MultivariateFunction {
   private final WeightedObservedPoints points_;
   
   public P2DFunc(WeightedObservedPoints points) {
      points_ = points;
   }
   
   /**
    * Calculate the sum of the root mean square of the errors
    * @param doubles
    * @return 
    */
   @Override
   public double value(double[] doubles) {
      double sum = 0;
      for (WeightedObservedPoint point : points_.toList()) {
         double predictedValue = p2d(point.getX(), doubles[0], doubles[1]);
         double error = point.getY() - predictedValue;
         sum += Math.sqrt(error * error);
      }
      return sum;
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



public class P2DFitter {
   private final WeightedObservedPoints points_;
   
   public P2DFitter() {
      points_ = new WeightedObservedPoints();
   }
   
   public void addPoint(double x, double y) {
      points_.add(x, y);
   }
   
   public void clearpoints() {
      points_.clear();
   };
   
   public PointValuePair solve() {
      SimplexOptimizer optimizer = new SimplexOptimizer(1e-3, 1e-6);
      P2DFunc myP2DFunc = new P2DFunc(points_);
      
      PointValuePair solution = optimizer.optimize(
              new MaxEval(100),
              new ObjectiveFunction(myP2DFunc),
              GoalType.MINIMIZE,
              new InitialGuess(new double[]{-3, 0}),
              new NelderMeadSimplex(new double[]{0.2, 0.2}),
              new SimpleBounds(new double[]{-5, -1},
              new double[]{5, 1})
      );
      return solution;
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
