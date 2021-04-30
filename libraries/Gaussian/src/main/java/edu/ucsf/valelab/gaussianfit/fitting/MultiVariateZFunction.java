/* @author - Nico Stuurman, September 2010
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

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.MultivariateRealFunction;

/**
 * @author Nico Stuurman
 * <p>
 * Function as defined in Bo Huang Science paper
 * <p>
 * minimize the distance D in sqrt wx and sqrt wy space D = sqrt (  square (sqrt wx - sqrt wx,
 * calib) + sqr(sqrt wy - sqrt wx, calib) )
 * <p>
 * where wx and wy calib are functions depended on z using functions defined in zCalibrator
 */

/**
 * @author nico
 */
public class MultiVariateZFunction implements MultivariateRealFunction {

   private double[] fitFunctionWx_;
   private double[] fitFunctionWy_;
   private double wx_;
   private double wy_;


   public MultiVariateZFunction(double[] fitFunctionWx, double[] fitFunctionWy,
         double wx, double wy) {
      fitFunctionWx_ = fitFunctionWx;
      fitFunctionWy_ = fitFunctionWy;
      wx_ = wx;
      wy_ = wy;
   }


   /**
    * @param params array of double with function parameters where: 0: z
    * @return
    * @throws FunctionEvaluationException
    */
   public double value(double[] params) throws FunctionEvaluationException {
      if (params.length < 1) {
         throw new FunctionEvaluationException(0);
      }

      return funcval(params);
   }

   /**
    * actual function evaluation
    * <p>
    * D = sqrt (  square (sqrt wx - sqrt wx, calib) + sqr(sqrt wy - sqrt wx, calib) )
    */
   public double funcval(double[] params) {

      double x = Math.sqrt(wx_) - Math.sqrt(
            MultiVariateZCalibrationFunction.funcval(fitFunctionWx_, params[0]));
      double y = Math.sqrt(wy_) - Math.sqrt(
            MultiVariateZCalibrationFunction.funcval(fitFunctionWy_, params[0]));

      return Math.sqrt(x * x + y * y);
   }


}
