/**
 * Gaussian Fitting package Implements MultiVariateRealFunction using the Gaussian functions defined
 * in GaussianUtils
 *
 * @author - Nico Stuurman, September 2010
 * <p>
 * <p>
 * Copyright (c) 2010-2017, Regents of the University of California All rights reserved.
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
import org.apache.commons.math.analysis.MultivariateRealFunction;

/**
 * @author nico
 */
public class MultiVariateGaussianFunction implements MultivariateRealFunction {

   int[] data_;
   int nx_;
   int ny_;
   int count_ = 0;
   final int shape_;
   final double s_;     // radius of Gaussian, negative if it will be estimated
   final boolean fitWidth_;


   /**
    * Gaussian fit can be run by estimating parameter c (width of Gaussian) as 1 (circle), 2 (width
    * varies in x and y), or 3 (ellipse) parameters
    *
    * @param shape 1=circle, 2=width varies in x and y, 3=ellipse
    * @param s     // width of Gaussian in pixels, negative if it should be fitted
    */
   public MultiVariateGaussianFunction(int shape, double s) {
      super();
      shape_ = shape;
      s_ = s;
      fitWidth_ = s_ <= 0.0;
   }

   public void setImage(short[] data, int width, int height) {
      data_ = new int[data.length];
      for (int i = 0; i < data.length; i++) {
         data_[i] = (int) data[i] & 0xffff;
      }
      nx_ = width;
      ny_ = height;
   }

   @Override
   public double value(double[] params) {
      double residual = 0.0;
      switch (shape_) {
         case 1:
            if (fitWidth_) {
               for (int i = 0; i < nx_; i++) {
                  for (int j = 0; j < ny_; j++) {
                     residual += GaussianUtils.sqr(
                           GaussianUtils.gaussian(params, i, j) - data_[(j * nx_) + i]);
                  }
               }
            } else {
               for (int i = 0; i < nx_; i++) {
                  for (int j = 0; j < ny_; j++) {
                     residual += GaussianUtils.sqr(
                           GaussianUtils.gaussianFixS(params, s_, i, j) - data_[(j * nx_) + i]);
                  }
               }
            }
            break;
         case 2:
            for (int i = 0; i < nx_; i++) {
               for (int j = 0; j < ny_; j++) {
                  residual += GaussianUtils.sqr(
                        GaussianUtils.gaussian2DXY(params, i, j) - data_[(j * nx_) + i]);
               }
            }
            break;
         case 3:
            for (int i = 0; i < nx_; i++) {
               for (int j = 0; j < ny_; j++) {
                  residual += GaussianUtils.sqr(
                        GaussianUtils.gaussian2DEllips(params, i, j) - data_[(j * nx_) + i]);
               }
            }
            break;
         default:
            break;
      }
      return residual;
   }
}
