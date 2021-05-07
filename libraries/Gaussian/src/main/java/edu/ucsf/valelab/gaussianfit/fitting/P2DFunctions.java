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
import org.apache.commons.math3.analysis.function.Exp;

/**
 * @author nico
 */
public class P2DFunctions {

   private final static Exp EXP = new Exp();

   /**
    * Calculates the probability density function: p2D(r) = (r / sigma2) exp(-(mu2 + r2)/2sigma2)
    * I0(rmu/sigma2) where I0 is the modified Bessel function of integer order zero
    *
    * @param r
    * @param mu
    * @param sigma
    * @return
    */
   public static double p2d(double r, double mu, double sigma) {
      double first = r / (sigma * sigma);
      double second = EXP.value(-(mu * mu + r * r) / (2 * sigma * sigma));
      double third = Besseli.bessi(0, (r * mu) / (sigma * sigma));

      if (second < 1e-300) {
         second = 1e-300;
      }
      if (Double.isInfinite(third)) {
         third = Double.MAX_VALUE;
      }

      return first * second * third;
   }

   /**
    * Used when r > sigma
    * <p>
    * Sqrt( r / (2Pi * sigma * mu)) * e pow(- (r - mu)^2 / (2 * sigma^2) )
    *
    * @param r
    * @param mu
    * @param sigma
    * @return
    */
   public static double p2dApproximation(double r, double mu, double sigma) {

      double result = Math.sqrt(r / (2 * Math.PI * sigma * mu)) *
            EXP.value(-(r - mu) * (r - mu) / (2 * sigma * sigma));
      if (result < Double.MIN_NORMAL) {
         result = Double.MIN_NORMAL;
      }
      return result;
   }


}
