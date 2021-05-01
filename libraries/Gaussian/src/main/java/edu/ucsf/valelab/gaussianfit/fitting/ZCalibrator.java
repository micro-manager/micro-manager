/* 
 * Data Structures and functions used to relate assymetry of a spot to Z position
 * 
 * Based on Bo Huang et al., DOI: 10.1126/science.1153529
 * 
 * Created 6/28/2012
 * 
 * Nico Stuurman, nico@cmp.ucsf.edu

* @author - Nico Stuurman, September 2012
 * 
 * 
Copyright (c) 2012-2017, Regents of the University of California
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
import ij.measure.ResultsTable;
import java.util.ArrayList;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.commons.math.optimization.SimpleScalarValueChecker;
import org.apache.commons.math.optimization.direct.NelderMead;
import org.jfree.data.xy.XYSeries;

/**
 * @author Nico Stuurman
 */
public class ZCalibrator {
   
   /*
    * 
    Structure of arrays "fitFunction";
    * 0: c_;  // center z position 
    * 1: w0_; // PSF width at center position
    * 2: d_;  // focus depth of the microscope (not sure what this means)
    * 3: A    // higher order factor
    * 4: B_;  // second higher order factor   
   */

   private double[] fitFunctionWx_;
   private double[] fitFunctionWy_;

   final int maxIterations_ = 10000;

   public boolean hasFitFunctions() {
      return fitFunctionWx_ != null && fitFunctionWy_ != null;
   }

   public class DataPoint {

      public double wx_; // width in x
      public double wy_; // width in y
      public double z_; //z position

      public DataPoint(double wx, double wy, double z) {
         wx_ = wx;
         wy_ = wy;
         z_ = z;
      }
   }

   private ArrayList<DataPoint> data_;

   public void addDataPoint(double wx, double wy, double z) {
      if (data_ == null) {
         data_ = new ArrayList<DataPoint>();
      }
      data_.add(new DataPoint(wx, wy, z));
   }

   public void clearDataPoints() {
      if (data_ != null) {
         data_.clear();
      }
   }

   public int nrDataPoints() {
      return data_.size();
   }


   /**
    * returns a 2D array representation of the data: either z - wx ( dim == 0) or z - wy (dim == 1)
    *
    * @param dim 0-returns array z-wx, 1 return array w-wy
    * @return 2D array, z values in D0, w value in D2
    */
   public double[][] getDataAsArray(int dim) {
      double[][] output = new double[2][data_.size()];
      for (int i = 0; i < data_.size(); i++) {
         DataPoint dp = data_.get(i);
         output[0][i] = dp.z_;
         if (dim == 0) {
            output[1][i] = dp.wx_;
         }
         if (dim == 1) {
            output[1][i] = dp.wy_;
         }
      }

      return output;
   }

   public void plotDataPoints() {

      String xAxis = "Z (frame nr)";

      XYSeries[] plotData = new XYSeries[2];
      plotData[0] = new XYSeries("wx");
      plotData[1] = new XYSeries("wy");

      for (DataPoint d : data_) {
         plotData[0].add(d.z_, d.wx_);
         plotData[1].add(d.z_, d.wy_);
      }

      GaussianUtils
            .plotDataN("Z-calibration Data Points", plotData, xAxis, "Width(nm)", 0, 400, true,
                  false);

   }

   private void plotFitFunctions() {
      String xAxis = "Z (frame nr)";

      XYSeries[] plotData = new XYSeries[2];
      plotData[0] = new XYSeries("wx");
      plotData[1] = new XYSeries("wy");

      for (DataPoint d : data_) {
         plotData[0].add(d.z_, MultiVariateZCalibrationFunction.funcval(fitFunctionWx_, d.z_));
         plotData[1].add(d.z_, MultiVariateZCalibrationFunction.funcval(fitFunctionWy_, d.z_));
      }

      GaussianUtils
            .plotDataN("Z- calibraton fitted functions", plotData, xAxis, "Width(nm)", 0, 400, true,
                  false);
   }

   /**
    * Creates fitFunctionWx_ and fitFunctionWy_ based on data in data_
    *
    * @throws org.apache.commons.math.FunctionEvaluationException
    * @throws org.apache.commons.math.optimization.OptimizationException
    */
   public void fitFunction() throws FunctionEvaluationException, OptimizationException {

      NelderMead nmx = new NelderMead();
      SimpleScalarValueChecker convergedChecker_ = new SimpleScalarValueChecker(1e-6, -1);

      double[][] wxData = getDataAsArray(0);
      MultiVariateZCalibrationFunction mvcx = new MultiVariateZCalibrationFunction(wxData);

      double[] params0_ = new double[5]; // initial estimates:
      params0_[0] = 37;  // TODO: better estimate for c
      params0_[1] = 200; // Estimate for w0
      params0_[2] = 10;  // TODO: better estimate for d
      params0_[3] = 1;   // TODO: better estimate for A
      params0_[4] = 1;   // TODO: better estimate for B

      nmx.setStartConfiguration(params0_);
      nmx.setConvergenceChecker(convergedChecker_);
      nmx.setMaxIterations(maxIterations_);

      double[] paramsOut;

      RealPointValuePair result = nmx.optimize(mvcx, GoalType.MINIMIZE, params0_);
      paramsOut = result.getPoint();

      // write fit result to Results Table:
      ResultsTable res = new ResultsTable();
      res.incrementCounter();
      res.addValue("c", paramsOut[0]);
      res.addValue("w0", paramsOut[1]);
      res.addValue("d", paramsOut[2]);
      res.addValue("A", paramsOut[3]);
      res.addValue("B", paramsOut[4]);

      fitFunctionWx_ = paramsOut;

      double[][] yxData = getDataAsArray(1);
      MultiVariateZCalibrationFunction yvcx = new MultiVariateZCalibrationFunction(yxData);

      nmx.setStartConfiguration(params0_);

      result = nmx.optimize(yvcx, GoalType.MINIMIZE, params0_);
      paramsOut = result.getPoint();

      res.incrementCounter();
      res.addValue("c", paramsOut[0]);
      res.addValue("w0", paramsOut[1]);
      res.addValue("d", paramsOut[2]);
      res.addValue("A", paramsOut[3]);
      res.addValue("B", paramsOut[4]);

      res.show("Fit Parameters");

      fitFunctionWy_ = paramsOut;

      plotFitFunctions();


   }


   /**
    * Use the fitfunction to estimate the z position given width in x and y
    * <p>
    * minimize the distance D in sqrt wx and sqrt wy space D = sqrt (  square (sqrt wx - sqrt wx,
    * calib) + sqr(sqrt wy - sqrt w, calib) )
    *
    * @param wx - width in x
    * @param wy - width in y
    * @return - calculated z position
    */

   public double getZ(double wx, double wy) {
      if (!hasFitFunctions()) {
         return 0.0;
      }

      NelderMead nmx = new NelderMead();
      SimpleScalarValueChecker convergedChecker_ = new SimpleScalarValueChecker(1e-6, -1);

      MultiVariateZFunction mz = new MultiVariateZFunction(fitFunctionWx_, fitFunctionWy_,
            wx, wy);

      double[] params0_ = new double[1]; // initial estimates:
      params0_[0] = 15;  // TODO: Need the middle z value of the stack here!!!

      nmx.setStartConfiguration(params0_);
      nmx.setConvergenceChecker(convergedChecker_);
      nmx.setMaxIterations(maxIterations_);

      double[] paramsOut = {0.0};

      try {
         RealPointValuePair result = nmx.optimize(mz, GoalType.MINIMIZE, params0_);
         paramsOut = result.getPoint();
      } catch (java.lang.OutOfMemoryError e) {
         throw (e);
      } catch (FunctionEvaluationException e) {
         ij.IJ.log(" " + e.toString());
      } catch (OptimizationException e) {
         ij.IJ.log(" " + e.toString());
      } catch (IllegalArgumentException e) {
         ij.IJ.log(" " + e.toString());
      }

      return paramsOut[0];
   }


}
