package edu.valelab.gaussianfit.fitting;

import edu.valelab.gaussianfit.utils.GaussianUtils;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.fitting.ParametricRealFunction;

/**
 *
 * @author nico
 */
public class ParametricGaussianMLE implements ParametricRealFunction{
   private int width_;
   private int height_;
   private int mode_;

   public ParametricGaussianMLE(int mode, int width, int height) {
      width_ = width;
      height_ = height;
      mode_ = mode;
   }

   public double value(double d, double[] doubles) throws FunctionEvaluationException {
      double value = 0;
      if (mode_ == 1)
         value =  GaussianUtils.gaussian(doubles, ((int) d) % width_, ((int) d) / width_);
      if (mode_ == 2)
          value =  GaussianUtils.gaussian2DXY(doubles, ((int) d) % width_, ((int) d) / width_);
      if (mode_ == 3)
          value =  GaussianUtils.gaussian2DEllips(doubles, ((int) d) % width_, ((int) d) / width_);
      return value;
   }

   public double[] gradient(double d, double[] doubles) throws FunctionEvaluationException {
      double[] value = {0.0};
      if (mode_ == 1)
         value =  GaussianUtils.gaussianJ(doubles, ((int) d) % width_, ((int) d) / width_);
      if (mode_ == 2)
          value =  GaussianUtils.gaussianJ2DXY(doubles, ((int) d) % width_, ((int) d) / width_);
      if (mode_ == 3)
          value =  GaussianUtils.gaussianJ2DEllips(doubles, ((int) d) % width_, ((int) d) / width_);
      return value;
   }

}
