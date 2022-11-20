package org.micromanager.pointandshootanalysis.algorithm;

import java.awt.geom.Point2D;
import java.util.List;

/**
 * @author nico
 */
public class SingleExpRecoveryFunc extends PASFunction {


   /**
    * @param data actual observations. here a list of x-y values
    */
   public SingleExpRecoveryFunc(List<Point2D> data) {
      super(data);
   }

   @Override
   public Double calculate(double[] input, double x) {
      if (input.length != getNumOfInputsN()) {
         return null; // TODO: throw exception
      }
      double a = input[0];
      double b = input[1];
      double k = input[2];

      return a * (1 - Math.exp(-k * (x + b)));
   }

   @Override
   public Double calculateX(double[] input, double y) {
      if (input.length != getNumOfInputsN()) {
         return null; // TODO: throw exception
      }
      double a = input[0];
      double b = input[1];
      double k = input[2];

      // x = (ln((y/A -1) / k ) -b

      double x = -(Math.log(1 - (y / a)) / k) - b;

      return x;
   }


   /**
    * Process function returns the error between the data point and the function
    * value for that point
    *
    * @param input  function parameters to be fitted (here: A and k)
    * @param output residual error, here: the difference
    *                between function value and measured value
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


}
