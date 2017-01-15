/**
 * Gaussian Fitting package
 * Implements MultiVariaRealFunction using the Gaussian functions defined in
 * GaussianUtils.  Implement MLE
 *
 * @author - Arthur Edelstein, September 2013
 */

package edu.valelab.gaussianfit.fitting;


import edu.valelab.gaussianfit.utils.GaussianUtils;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.*;

/**
 *
 * @author nico
 */
public class MultiVariateGaussianMLE implements DifferentiableMultivariateRealFunction {

   int[] data_;
   int nx_;
   int ny_;
   int count_ = 0;
   int shape_ = 1;
   final double s_;     // radius of Gaussian, negative if it will be estimated
   final boolean fitWidth_;


   /**
    * Gaussian fit can be run by estimating parameter c (width of Gaussian)
    * as 1 (circle), 2 (width varies in x and y), or 3 (ellipse) parameters
    *
    * @param shape
    */
   public MultiVariateGaussianMLE(int shape, double s) {
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
                     double expectation = GaussianUtils.gaussian(params, i, j);
                     residual += expectation - data_[(j * nx_) + i] * Math.log(expectation);
                  }
               }
            } else {
               for (int i = 0; i < nx_; i++) {
                  for (int j = 0; j < ny_; j++) {
                     double expectation = GaussianUtils.gaussianFixS(params, s_, i, j);
                     residual += expectation - data_[(j * nx_) + i] * Math.log(expectation);
                  }
               }
            }
            break;
         case 2:
            for (int i = 0; i < nx_; i++) {
               for (int j = 0; j < ny_; j++) {
                  double expectation = GaussianUtils.gaussian2DXY(params, i, j);
                  residual += expectation - data_[(j*nx_) + i] * Math.log(expectation);
               }
            } break;
         case 3:
            for (int i = 0; i < nx_; i++) {
               for (int j = 0; j < ny_; j++) {
                  double expectation = GaussianUtils.gaussian2DEllips(params, i, j);
                  residual += expectation - data_[(j*nx_) + i] * Math.log(expectation);
               }
            } break;
         default:
            break;
      }
       return residual;
   }

   @Override
   public MultivariateRealFunction partialDerivative(int i) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public MultivariateVectorialFunction gradient() {
      
      MultivariateVectorialFunction mVF = new MultivariateVectorialFunction() {
         
         @Override
         public double[] value(double[] params) throws FunctionEvaluationException, IllegalArgumentException {
            double[] mleGradient = new double[params.length];
            for (int i = 0; i < nx_; i++) {
               for (int j = 0; j < ny_; j++) {
                  if (shape_ == 1) {
                     if (fitWidth_) {
                        double[] jacobian = GaussianUtils.gaussianJ(params, i, j);
                        for (int k = 0; k < mleGradient.length; k++) {
                           mleGradient[k] += jacobian[k] * (1 - data_[(j * nx_) + i] / GaussianUtils.gaussian(params, i, j));
                        }
                     } else {
                        double[] jacobian = GaussianUtils.gaussianJFixS(params, s_, i, j);
                        for (int k = 0; k < mleGradient.length; k++) {
                           mleGradient[k] += jacobian[k] * (1 - data_[(j * nx_) + i] / GaussianUtils.gaussian(params, i, j));
                        }
                     }
                  }
                  if (shape_ == 2) {
                     double[] jacobian = GaussianUtils.gaussianJ2DXY(params, i, j);
                     for (int k = 0; k < mleGradient.length; k++) {
                        mleGradient[k] += jacobian[k] * (1 - data_[(j * nx_) + i] / GaussianUtils.gaussian2DXY(params, i, j));
                     }
                  }
                  if (shape_ == 3) {
                     double[] jacobian = GaussianUtils.gaussianJ2DEllips(params, i, j);
                     for (int k = 0; k < mleGradient.length; k++) {
                        mleGradient[k] += jacobian[k] * (1 - data_[(j * nx_) + i] / GaussianUtils.gaussian2DEllips(params, i, j));
                     }
                  }
               }
            }
            return mleGradient;
         }
      };
      
      return mVF;
   }
   


}
