package edu.valelab.GaussianFit.fitting;

import edu.valelab.GaussianFit.utils.GaussianUtils;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.MultivariateRealFunction;

/**
 *
 * @author Nico Stuurman
 * 
 * Function as defined in Bo Huang Science paper
 * 
 * f = (z-c) / d;
 * 
 * w(x or y) (z) = w0 * Sqrt (1 + f*f + A * f*f*f + B * f*f*f*f )
 * 
 * Where
 *    z = z position of the stage
 *    c = offset from average focal plane
 *    w0 = PSF width where z==c
 *    d = focus depth of the microscope
 *    A, B = higher order coeficients
 * 
 * 
 */
public class MultiVariateZCalibrationFunction implements MultivariateRealFunction  {
   
   // 2D array with data points to be fitted
   // data_[0] has x-axis values (z), data_[1] y-axis (width of peaks)
   double[][] data_;
   
   
   public MultiVariateZCalibrationFunction (double[][] data) {
      data_ = new double[2][];
      data_ = data;
   }
   
   
   /**
    * 
    * @param - array of double with function parameters where:
    *       0: c
    *       1: w0 
    *       2: d
    *       3: A
    *       4: B
    *       
    * @return
    * @throws FunctionEvaluationException 
    */
   public double value(double[] params) throws FunctionEvaluationException {
      if (params.length < 5)
         throw new FunctionEvaluationException(0);
      
      double residual = 0.0;
      
      for (int i=0; i < data_[0].length; i++) {
         residual += GaussianUtils.sqr(funcval(params, data_[0][i]) - data_[1][i]);
      }
      
      return residual;
   }
   
   /**
    * actual function evaluation
    */
   public static double funcval(double[] params, double z) {
      
      double f = (z-params[0]) / params[2];
      
      return params[1] * Math.sqrt(1 + f * f + 
              params[3] * f * f * f + 
              params[4] * f * f * f * f);
   }
   
   
}
