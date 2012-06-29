/* 
 * Data Structures and functions used to relate assymetry of a spot to Z position
 * 
 * Based on Bo Huang et al., DOI: 10.1126/science.1153529
 * 
 * Create 6/28/2012
 * 
 * Nico Stuurman, nico@cmp.ucsf.edu
 * 
 * Copyright UCSF 2012
 *
 * Licensed under BSD version 2.0 
 * 
 */
package edu.valelab.GaussianFit;

import java.util.ArrayList;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.commons.math.optimization.SimpleScalarValueChecker;
import org.apache.commons.math.optimization.direct.NelderMead;
import org.jfree.data.xy.XYSeries;

/**
 *
 * @author Nico Stuurman
 */
public class ZCalibrator {
   
   public static class FitFunctionParameters {
      public double c_; // center z position (in microns)
      public double w0_; // PSF width at center position
      public double d_; // focus depth of the microscope (not sure what this means)
      public double A_; // higher order factor
      public double B_; // second higher order factor
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
   
   public void addDataPoint (double wx, double wy, double z) {
      if (data_ == null)
         data_ = new ArrayList<DataPoint>();
      data_.add(new DataPoint(wx, wy, z));
   }
   
   public void clearDataPoints() {
      if (data_ != null)
         data_.clear();
   }
   
   public int nrDataPoints() {
      return data_.size();
   }
   
   
   /**
    * returns a 2D array representation of the data:
    * either z - wx ( dim == 0)
    * or z - wy (dim == 1)
    * 
    * @param dim 0-returns array z-wx, 1 retunr array w-wy
    * @return 2D array, z values in D0, w value in D2
    */
   public double[][] getDataAsArray (int dim) {
      double[][] output = new double[2][data_.size()];
      for (int i=0; i < data_.size(); i++) {
         DataPoint dp = data_.get(i);
         output[0][i] = dp.z_;
         if (dim == 0)
            output[1][i] = dp.wx_;
         if (dim == 1)
            output[1][i] = dp.wy_;
      }
      
      return output;
   }
   
   public void plotDataPoints() {
      
      String xAxis = "Z (um)";
      
      XYSeries[] plotData = new XYSeries[2];
      plotData[0] = new XYSeries("wx");
      plotData[1] = new XYSeries("wy");
            
      for (int i = 0; i < data_.size(); i++) {
         DataPoint d = data_.get(i);
         plotData[0].add(d.z_, d.wx_);
         plotData[1].add(d.z_, d.wy_);
      }
      
      GaussianUtils.plotDataN("", plotData, xAxis, "Width(nm)", 0, 400, true, false);
           
   }
   
   public void fitFunction() {
      final int maxIterations = 10000;
      
      NelderMead nm = new NelderMead();
      SimpleScalarValueChecker convergedChecker_ = new SimpleScalarValueChecker(1e-6,-1);
      
      double[][] wxData = getDataAsArray(0);
      MultiVariateZCalibrationFunction mvcx = new MultiVariateZCalibrationFunction(wxData);
      
      double[] steps = new double[5];
      double[] params0_ = new double[5]; // initial estimates:
      params0_[0] = 25; // TODO: better estimate for c
      params0_[1] = 300; // Estimate for w0
      params0_[2] = 1000; // TODO: better estimate for d
      params0_[3] = 1; // TODO: better estimate for A
      params0_[4] = 1; // TODO: better estimate for B
      
      nm.setStartConfiguration(steps);
      nm.setConvergenceChecker(convergedChecker_);
      nm.setMaxIterations(maxIterations);
      
      double[] paramsOut = {0.0};
      
      try {
         RealPointValuePair result = nm.optimize(mvcx, GoalType.MINIMIZE, params0_);
         paramsOut = result.getPoint();
      } catch (java.lang.OutOfMemoryError e) {
         throw (e);
      } catch (Exception e) {
         ij.IJ.log(" " + e.toString());
      }
      
      for (int i = 0; i < paramsOut.length; i++) {
         System.out.append("Result " + i + " value: " + paramsOut[i]);
      }

   }
   
   
}
