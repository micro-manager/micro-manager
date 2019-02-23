package org.micromanager.pointandshootanalysis.algorithm;

import java.awt.geom.Point2D;
import java.util.List;
import org.ddogleg.optimization.functions.FunctionNtoM;

/**
 *
 * @author nico
 */
public class SingleExpRecoveryFunc implements FunctionNtoM {

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
    * @param output: residual error, here: root of the square of the difference
    * between function value and measured value
    */
   @Override
   public void process(double[] input, double[] output) {
      double a = input[0];
      double k = input[1];

      for (int i = 0; i < data_.size(); i++) {
         Point2D p = data_.get(i);

         double y = a * (1 - Math.exp(-k * p.getX()));
         output[i] = p.getY() - y;
      }
   }

   @Override
   public int getNumOfInputsN() {
      return 2;
   }

   @Override
   public int getNumOfOutputsM() {
      return data_.size();
   }

}
