/**
 * Gaussian Fitting package
 * Implements MultiVariateRealFunction using the Gaussian functions defined in
 * GaussianUtils
 *
 * @author - Nico Stuurman, September 2010
 */

package edu.valelab.gaussianfit.fitting;


import edu.valelab.gaussianfit.utils.GaussianUtils;
import org.apache.commons.math.analysis.*;

/**
 *
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
    * Gaussian fit can be run by estimating parameter c (width of Gaussian)
    * as 1 (circle), 2 (width varies in x and y), or 3 (ellipse) parameters
    *
    * @param shape 1=circle, 2=width varies in x and y, 3=ellipse
    * @param s  // width of Gaussian in pixels, negative if it should be fitted
    */
   public MultiVariateGaussianFunction(int shape, double s) {
      super();
      shape_ = shape;
      s_ = s;
      fitWidth_ = s_ <= 0.0;
   }

   public void setImage(short[] data, int width, int height) {
      data_ = new int[data.length];
      for (int i=0; i < data.length; i++) {
         data_[i] = (int) data [i] & 0xffff;
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
                          GaussianUtils.gaussian2DXY(params, i, j) - data_[(j*nx_) + i]);
               }
            } break;
         case 3:
            for (int i = 0; i < nx_; i++) {
               for (int j = 0; j < ny_; j++) {
                  residual += GaussianUtils.sqr(
                          GaussianUtils.gaussian2DEllips(params, i, j) - data_[(j*nx_) + i]);
               }
            } break;
         default:
            break;
      }
      return residual;
   }
}
