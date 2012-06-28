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
   
   
   
}
