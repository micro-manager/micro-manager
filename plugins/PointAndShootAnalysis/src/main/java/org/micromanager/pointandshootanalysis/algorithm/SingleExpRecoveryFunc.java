package org.micromanager.pointandshootanalysis.algorithm;

import java.awt.geom.Point2D;
import java.util.List;
import org.ddogleg.optimization.functions.FunctionNtoM;

/**
 *
 * @author nico
 */
public class SingleExpRecoveryFunc implements FunctionNtoM  {

   List<Point2D> data_;

   /**
    *
    * @param data actual observations. here a list of x-y values
    */
   public SingleExpRecoveryFunc(List<Point2D> data) {
      this.data_ = data;
   }

   /**
    *
    * Process function returns the error between the data point and the function
    * value for that point
    *
    * @param input: function parameters to be fitted (here: A and k)
    * @param output: residual error, here: the difference
    * between function value and measured value
    */
   @Override
   public void process(double[] input, double[] output) {
      double a = input[0];
      double b = input[1];
      double k = input[2];

      for (int i = 0; i < data_.size(); i++) {
         Point2D p = data_.get(i);

         double y = a * (1 - Math.exp(-k * (p.getX() + b)));
         output[i] = p.getY() - y;
      }
   }

   @Override
   public int getNumOfInputsN() {
      return 3;
   }

   @Override
   public int getNumOfOutputsM() {
      return data_.size();
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
   

}
