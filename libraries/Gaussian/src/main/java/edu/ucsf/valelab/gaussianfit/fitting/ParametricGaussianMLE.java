/**
 * @author - Nico Stuurman, 2013
 * <p>
 * <p>
 * Copyright (c) 2013-2017, Regents of the University of California All rights reserved.
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


package edu.ucsf.valelab.gaussianfit.fitting;

import edu.ucsf.valelab.gaussianfit.utils.GaussianUtils;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.fitting.ParametricRealFunction;

/**
 * @author nico
 */
public class ParametricGaussianMLE implements ParametricRealFunction {

   private final int width_;
   private final int height_;
   private final int mode_;

   public ParametricGaussianMLE(int mode, int width, int height) {
      width_ = width;
      height_ = height;
      mode_ = mode;
   }

   @Override
   public double value(double d, double[] doubles) throws FunctionEvaluationException {
      double value = 0;
      if (mode_ == 1) {
         value = GaussianUtils.gaussian(doubles, ((int) d) % width_, ((int) d) / width_);
      }
      if (mode_ == 2) {
         value = GaussianUtils.gaussian2DXY(doubles, ((int) d) % width_, ((int) d) / width_);
      }
      if (mode_ == 3) {
         value = GaussianUtils.gaussian2DEllips(doubles, ((int) d) % width_, ((int) d) / width_);
      }
      return value;
   }

   @Override
   public double[] gradient(double d, double[] doubles) throws FunctionEvaluationException {
      double[] value = {0.0};
      if (mode_ == 1) {
         value = GaussianUtils.gaussianJ(doubles, ((int) d) % width_, ((int) d) / width_);
      }
      if (mode_ == 2) {
         value = GaussianUtils.gaussianJ2DXY(doubles, ((int) d) % width_, ((int) d) / width_);
      }
      if (mode_ == 3) {
         value = GaussianUtils.gaussianJ2DEllips(doubles, ((int) d) % width_, ((int) d) / width_);
      }
      return value;
   }

}
