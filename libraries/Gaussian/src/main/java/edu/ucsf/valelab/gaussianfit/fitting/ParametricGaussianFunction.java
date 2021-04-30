/* @author - Nico Stuurman, 2010
 * 
 * 
Copyright (c) 2010-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */


package edu.ucsf.valelab.gaussianfit.fitting;

import edu.ucsf.valelab.gaussianfit.utils.GaussianUtils;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.fitting.ParametricRealFunction;

/**
 * @author nico
 */
public class ParametricGaussianFunction implements ParametricRealFunction {

   private final int imageWidth_;
   private final int shape_;
   private final double s_;
   private final boolean fitWidth_;

   /**
    * @param shape      1=circle, 2=width varies in x and y, 3=ellipse
    * @param imageWidth - with f image in pixels
    * @param s          // width of Gaussian in pixels, negative if it should be fitted
    */
   public ParametricGaussianFunction(final int shape, final int imageWidth, final double s) {
      imageWidth_ = imageWidth;
      shape_ = shape;
      s_ = s;
      fitWidth_ = s <= 0.0;
   }

   /**
    * @param d       - index into the pixel array, used to calculate x,y coordinates
    * @param doubles
    * @return - residual (error) with respect to Gaussian based on parameters 'doubles'
    * @throws FunctionEvaluationException
    */
   @Override
   public double value(double d, double[] doubles) throws FunctionEvaluationException {
      double value = 0;
      switch (shape_) {
         case 1:
            if (fitWidth_) {
               value = GaussianUtils.gaussian(doubles, ((int) d) % imageWidth_,
                     ((int) d) / imageWidth_);
            } else {
               value = GaussianUtils.gaussianFixS(doubles, s_, ((int) d) % imageWidth_,
                     ((int) d) / imageWidth_);
            }
            break;
         case 2:
            value = GaussianUtils
                  .gaussian2DXY(doubles, ((int) d) % imageWidth_, ((int) d) / imageWidth_);
            break;
         case 3:
            value = GaussianUtils
                  .gaussian2DEllips(doubles, ((int) d) % imageWidth_, ((int) d) / imageWidth_);
            break;
         default:
            break;
      }
      return value;
   }

   @Override
   public double[] gradient(double d, double[] doubles) throws FunctionEvaluationException {
      double[] value = {0.0};
      switch (shape_) {
         case 1:
            if (fitWidth_) {
               value = GaussianUtils.gaussianJ(doubles, ((int) d) % imageWidth_,
                     ((int) d) / imageWidth_);
            } else {
               value = GaussianUtils.gaussianJFixS(doubles, s_, ((int) d) % imageWidth_,
                     ((int) d) / imageWidth_);
            }
            break;
         case 2:
            value = GaussianUtils
                  .gaussianJ2DXY(doubles, ((int) d) % imageWidth_, ((int) d) / imageWidth_);
            break;
         case 3:
            value = GaussianUtils
                  .gaussianJ2DEllips(doubles, ((int) d) % imageWidth_, ((int) d) / imageWidth_);
            break;
         default:
            break;
      }
      return value;
   }

}
