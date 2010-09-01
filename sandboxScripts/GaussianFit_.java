import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.plugin.frame.*;

import org.apache.commons.math.analysis.*;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.optimization.direct.NelderMead;
import org.apache.commons.math.optimization.direct.MultiDirectional;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.SimpleScalarValueChecker;


import java.lang.Math;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.IJ;

public class GaussianFit_ implements PlugIn {
	double[] params0_ = {16000.0, 5.0, 5.0, 1.0, 850.0};
	double[] steps_ = new double[5];
	String [] paramNames_ = {"A", "x_c", "y_c", "sigma", "b"};

	private void print(String myText) {
		ij.IJ.log(myText);
	}

   public class GaussianResidual implements MultivariateRealFunction {
       short[] data_;
       int nx_;
       int ny_;
       int count_ = 0;


       public void setImage(short[] data, int width, int height) {
          data_ = data;
          nx_ = width;
          ny_ = height;
       }

       public double value(double[] params) {
   //               print("Count: " + count_);
   //               count_++;
          double residual = 0.0;
          for (int i = 0; i < nx_; i++) {
             for (int j = 0; j < ny_; j++) {
                residual += sqr(gaussian(params, i, j) - data_[(i*nx_) + j]);
             }
          }
   /*
         for (int i=0; i< params.length; i++)
                  print(" " + paramNames_[i] + ": " + params[i]);

         print("Residual: " + residual);
   */
         return residual;
      }

      public double sqr(double val) {
         return val*val;
      }

      public double gaussian(double[] params, int x, int y) {

                  /* Gaussian function of the form:
                   * A *  exp(-((x-xc)^2+(y-yc)^2)/(2 sigy^2))+b
                   * A = params[0]  (total intensity)
                   * xc = params[1]
                   * yc = params[2]
                   * sig = params[3]
                   * b = params[4]  (background)
                   */

         if (params.length < 5) {
                          // Problem, what do we do???
                          //MMScriptException e;
                          //e.message = "Params for Gaussian function has too few values";
                          //throw (e);
         }

         double exponent = (sqr(x - params[1])  + sqr(y - params[2])) / (2 * sqr(params[3]));
         double res = params[0] * Math.exp(-exponent) + params[4];
         return res;
      }
   }



	public void run(String arg) {

		long startTime = System.currentTimeMillis();

		GaussianResidual gs = new GaussianResidual();
		NelderMead nm = new NelderMead();
		SimpleScalarValueChecker convergedChecker = new SimpleScalarValueChecker(1e-6,-1);

		ImagePlus siPlus = IJ.getImage();
		ImageProcessor siProc = siPlus.getProcessor();
		gs.setImage((short[])siProc.getPixels(), siProc.getWidth(), siProc.getHeight());

		for (int i=0;i<params0_.length;++i)
			steps_[i] = params0_[i]*0.3;

		nm.setStartConfiguration(steps_);
		nm.setConvergenceChecker(convergedChecker);

		nm.setMaxIterations(200);
		double[] paramsOut = {0.0};
		try {
			RealPointValuePair result = nm.optimize(gs, GoalType.MINIMIZE, params0_);
			paramsOut = result.getPoint();
		} catch (Exception e) {}



		print("\n\nFinal result:");
		for (int i=0; i<paramsOut.length; i++)
       		print(" " + paramNames_[i] + ": " + paramsOut[i]);

		double anormalized = paramsOut[0] * (2 * Math.PI * paramsOut[3] * paramsOut[3]);
		print("Amplitude normalized: " + anormalized);

		long endTime = System.currentTimeMillis(); 
		long took = endTime - startTime;

		print("Calculation took: " + took + " milli seconds"); 

	}

}
