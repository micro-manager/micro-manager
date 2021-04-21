package org.micromanager.pointandshootanalysis.data;

import java.awt.geom.Point2D;
import java.util.List;
import org.micromanager.pointandshootanalysis.DataExporter;

/**
 * Simple class to hold data describing the fit applied to a recover data set, as well as the
 * parameters calculated from the fit
 *
 * <p>TODO: add builder
 *
 * @author nico
 */
public class FitData {
  private final List<Point2D> data_; // actual XY points that were fitted
  private final Class fitType_; // Class that performed the fitting
  private final DataExporter.Type subjectType_;
  private final double[] parms_; // parameters found in fit
  private final double rSquared_; // estimted of the goodness of fit
  private final double tHalf_; // x at which y is halfway between min and max

  public FitData(
      List<Point2D> data,
      Class fitType,
      DataExporter.Type subjectType,
      double[] parms,
      double rSquared,
      double tHalf) {
    data_ = data;
    fitType_ = fitType;
    subjectType_ = subjectType;
    parms_ = parms;
    rSquared_ = rSquared;
    tHalf_ = tHalf;
  }

  public List<Point2D> data() {
    return data_;
  }

  public Class fitType() {
    return fitType_;
  }

  public DataExporter.Type subjectType() {
    return subjectType_;
  }

  public double[] parms() {
    return parms_;
  }

  public double rSquared() {
    return rSquared_;
  }

  public double tHalf() {
    return tHalf_;
  }
}
