
package org.micromanager.pointandshootanalysis.data;

import java.awt.geom.Point2D;
import java.util.List;
import org.micromanager.pointandshootanalysis.DataExporter;

/**
 * Simple class to hold data describing the fit applied to a recover
 * data set, as well as the parameters calculated from the fit
 * 
 * TODO: add builder
 * 
 * @author nico
 */
public class FitData {
   final private List<Point2D> data_; // actual XY points that were fitted
   final private Class fitType_; // Class that performed the fitting
   final private DataExporter.Type subjectType_;
   final private double[] parms_; // parameters found in fit
   final private double rSquared_; // estimted of the goodness of fit
   final private double tHalf_; // x at which y is halfway between min and max
   
   public FitData(List<Point2D> data, Class fitType, DataExporter.Type subjectType, 
           double[] parms, double rSquared, double tHalf) {
      data_ = data;
      fitType_ = fitType;
      subjectType_ = subjectType;
      parms_ = parms;
      rSquared_ = rSquared;
      tHalf_ = tHalf;
   }
   
   public List<Point2D> data() { return data_; }
   public Class fitType() {return fitType_; }
   public DataExporter.Type subjectType() { return subjectType_; }
   public double[] parms() { return parms_; }
   public double rSquared() { return rSquared_; }
   public double tHalf() { return tHalf_; }
}
