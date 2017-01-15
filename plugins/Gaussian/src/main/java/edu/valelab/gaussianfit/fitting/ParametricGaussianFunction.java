package edu.valelab.gaussianfit.fitting;

import edu.valelab.gaussianfit.utils.GaussianUtils;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.fitting.ParametricRealFunction;

/**
 *
 * @author nico
 */
public class ParametricGaussianFunction implements ParametricRealFunction{
   private final int imageWidth_;
   private final int shape_;
   private final double s_;
   private final boolean fitWidth_;

   /**
    * 
    * @param shape 1=circle, 2=width varies in x and y, 3=ellipse
    * @param imageWidth - with f image in pixels
    * @param s  // width of Gaussian in pixels, negative if it should be fitted
    */
   public ParametricGaussianFunction(final int shape, final int imageWidth, final double s) {
      imageWidth_ = imageWidth;
      shape_ = shape;
      s_ = s;
      fitWidth_ = s <= 0.0; 
   }

   /**
    * 
    * @param d - index into the pixel array, used to calculate x,y coordinates
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
               value =  GaussianUtils.gaussian(doubles, ((int) d) % imageWidth_,
                       ((int) d) / imageWidth_);
            } else {
               value =  GaussianUtils.gaussianFixS(doubles, s_, ((int) d) % imageWidth_,
                       ((int) d) / imageWidth_);
            }  break;
         case 2:
            value =  GaussianUtils.gaussian2DXY(doubles, ((int) d) % imageWidth_, ((int) d) / imageWidth_);
            break;
         case 3:
            value =  GaussianUtils.gaussian2DEllips(doubles, ((int) d) % imageWidth_, ((int) d) / imageWidth_);
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
               value =  GaussianUtils.gaussianJ(doubles, ((int) d) % imageWidth_, 
                       ((int) d) / imageWidth_);
            } else {
               value =  GaussianUtils.gaussianJFixS(doubles, s_, ((int) d) % imageWidth_, 
                       ((int) d) / imageWidth_);
            }
            break;
         case 2:
            value =  GaussianUtils.gaussianJ2DXY(doubles, ((int) d) % imageWidth_, ((int) d) / imageWidth_);
            break;
         case 3:
            value =  GaussianUtils.gaussianJ2DEllips(doubles, ((int) d) % imageWidth_, ((int) d) / imageWidth_);
            break;
         default:
            break;
      }
      return value;
   }

}
