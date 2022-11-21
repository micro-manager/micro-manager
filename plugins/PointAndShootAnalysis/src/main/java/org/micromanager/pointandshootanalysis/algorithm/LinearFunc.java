package org.micromanager.pointandshootanalysis.algorithm;


import java.awt.geom.Point2D;
import java.util.List;

/**
 * Function of the form: y = a + bx.
 *
 * @author nico
 */
public class LinearFunc  extends PASFunction {

   /**
    * Sets the data.
    *
    * @param data actual observations. here a list of x-y values
    */
   public LinearFunc(List<Point2D> data) {
      super(data);
   }
   
   @Override
   public Double calculate(double[] input, double x) {
      if (input.length != getNumOfInputsN()) {
         return null; // TODO: throw exception
      }
      double a = input[0];
      double b = input[1];
      
      return a + b * x;
   }
   
   /**
    * x = (y - a) / b.
    *
    * @param input
    * @param y
    * @return 
    */
   @Override
   public Double calculateX(double[] input, double y) {
      if (input.length != getNumOfInputsN()) {
         return null; // TODO: throw exception
      }
      double a = input[0];
      double b = input[1];
     
      return (y - a) / b;
   }
   
   @Override
   public void process(double[] input, double[] output) {
      double a = input[0];
      double b = input[1];

      for (int i = 0; i < data_.size(); i++) {
         Point2D p = data_.get(i);

         double y = a + b * p.getX();
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
