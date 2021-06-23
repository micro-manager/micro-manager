/* 
 * @author - Nico Stuurman, September 2010
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
import org.apache.commons.math.analysis.MultivariateRealFunction;

/**
 * @author Nico Stuurman
 * <p>
 * Function as defined in Bo Huang Science paper
 * <p>
 * f = (z-c) / d;
 * <p>
 * w(x or y) (z) = w0 * Sqrt (1 + f*f + A * f*f*f + B * f*f*f*f )
 * <p>
 * Where z = z position of the stage c = offset from average focal plane w0 = PSF width where z==c d
 * = focus depth of the microscope A, B = higher order coeficients
 */
public class MultiVariateZCalibrationFunction implements MultivariateRealFunction {

   // 2D array with data points to be fitted
   // data_[0] has x-axis values (z), data_[1] y-axis (width of peaks)
   double[][] data_;


   public MultiVariateZCalibrationFunction(double[][] data) {
      data_ = new double[2][];
      data_ = data;
   }


   /**
    * @param params array of double with function parameters where: 0: c 1: w0 2: d 3: A 4: B
    * @return
    * @throws FunctionEvaluationException
    */
   public double value(double[] params) throws FunctionEvaluationException {
      if (params.length < 5) {
         throw new FunctionEvaluationException(0);
      }

      double residual = 0.0;

      for (int i = 0; i < data_[0].length; i++) {
         residual += GaussianUtils.sqr(funcval(params, data_[0][i]) - data_[1][i]);
      }

      return residual;
   }

   /**
    * actual function evaluation
    */
   public static double funcval(double[] params, double z) {

      double f = (z - params[0]) / params[2];

      return params[1] * Math.sqrt(1 + f * f +
            params[3] * f * f * f +
            params[4] * f * f * f * f);
   }


}
