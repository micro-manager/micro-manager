package org.micromanager.pointandshootanalysis.algorithm;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.ddogleg.optimization.functions.FunctionNtoM;

/** @author nico */
public abstract class PASFunction implements FunctionNtoM {
  List<Point2D> data_;
  double[] parms_;

  /**
   * Calculates the function value for the given input parameters and x value
   *
   * @param input function parameters. Size should match getNumOfInputsN
   * @param x value for which we want the function value
   * @return calculated value
   */
  public abstract Double calculate(double[] input, double x);

  public abstract Double calculateX(double[] input, double y);

  public PASFunction(List<Point2D> data) {
    data_ = data;
  }

  public double getAverageOfObserved() {
    double sum = 0.0;
    for (Point2D o : data_) {
      sum += o.getY();
    }
    return sum / data_.size();
  }

  public double getSumOfSquares(Double mean) {
    Double avg = mean;
    if (avg == null) {
      avg = getAverageOfObserved();
    }
    double sum = 0.0;
    for (Point2D o : data_) {
      sum += (o.getY() - avg) * (o.getY() - avg);
    }
    return sum;
  }

  public double getSumOfSquaresOfResidual(double[] input) {
    double[] output = new double[data_.size()];
    process(input, output);
    double sum = 0.0;
    for (double o : output) {
      sum += o * o;
    }
    return sum;
  }

  public double getRSquared(double input[]) {
    return 1.0 - getSumOfSquaresOfResidual(input) / getSumOfSquares(null);
  }

  public List<Point2D> getData() {
    return data_;
  }

  public List<Point2D> getFittedData(double[] input) {
    List<Point2D> result = new ArrayList<>(data_.size());
    for (Point2D d : data_) {
      result.add(new Point2D.Double(d.getX(), calculate(input, d.getX())));
    }
    return result;
  }

  public void setParms(double[] parms) {
    parms_ = parms;
  }

  public double[] getParms() {
    return parms_;
  }
}
