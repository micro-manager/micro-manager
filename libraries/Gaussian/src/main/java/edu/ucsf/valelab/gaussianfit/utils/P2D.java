/*
 * @author Nico Stuurman
 * Copyright (c) 2015, Regents of the University of California
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
package edu.ucsf.valelab.gaussianfit.utils;

import edu.ucsf.valelab.gaussianfit.fitting.P2DFunctions;
import org.jfree.data.function.Function2D;

/**
 * Utility functions to calculate and fit the Probability density function to estimate the distance
 * between two single molecules. Based on: http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1367071/
 * <p>
 * A Non-Gaussian Distribution Quantifies Distances Measured with Fluorescence Localization
 * Techniques L. Stirling Churchman, Henrik Flyvbjerg, and James A. Spudich Biophys J. Jan 15, 2006;
 * 90(2): 668-671.
 * <p>
 * To avoid running into problems with positive and negative infinity, we use the approximation
 * function when sigma < mu/2
 *
 * @author nico
 */
public class P2D implements Function2D {

   private final double mu_;
   private final double sigma_;
   private final boolean useApproximation_;

   public P2D(double mu, double sigma) {
      mu_ = mu;
      sigma_ = sigma;
      useApproximation_ = sigma_ < mu_ / 2;
   }

   @Override
   public double getValue(double d) {
      if (useApproximation_) {
         return P2DFunctions.p2dApproximation(d, mu_, sigma_);
      }
      return P2DFunctions.p2d(d, mu_, sigma_);
   }

}
