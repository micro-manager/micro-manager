///////////////////////////////////////////////////////////////////////////////
//FILE:          Fitter.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.asidispim.fit;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.solvers.BracketingNthOrderBrentSolver;
import org.apache.commons.math3.analysis.solvers.UnivariateSolver;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.jfree.data.xy.XYSeries;

/**
 *
 * @author nico
 */
public class Fitter {
   public static enum FunctionType {NoFit, Pol1, Pol2, Pol3, Gaussian};
   
   /**
    * Utility to facilitate fitting data plotted in JFreeChart
    * Provide data in JFReeChart format (XYSeries), and retrieve univariate
    * function parameters that best fit (using least squares) the data. All data
    * points will be weighted equally.
    * 
    * TODO: investigate whether weighting (possibly automatic weighting) can
    * improve accuracy
    * 
    * @param data xy series in JFReeChart format
    * @param type one of the Fitter.FunctionType predefined functions
    * @param guess initial guess for the fit.  The number and meaning of these
             parameters depends on the FunctionType.  Implemented:
             Gaussian: 0: Normalization, 1: Mean 2: Sigma

    * @return array with parameters, whose meaning depends on the FunctionType.
    *          Use the function getXYSeries to retrieve the XYDataset predicted 
    *          by this fit
    */
   public static double[] fit(XYSeries data, FunctionType type, double[] guess) {
      
      if (type == FunctionType.NoFit) {
         return null;
      }
      // create the commons math data object from the JFreeChart data object
      final WeightedObservedPoints obs = new WeightedObservedPoints();
      for (int i = 0; i < data.getItemCount(); i++) {
         obs.add(1.0, data.getX(i).doubleValue(), data.getY(i).doubleValue());
      }
      
      double[] result = null;
      switch (type) {
         case Pol1:
            final PolynomialCurveFitter fitter1 = PolynomialCurveFitter.create(1);
            result = fitter1.fit(obs.toList());
            break;
         case Pol2:
            final PolynomialCurveFitter fitter2 = PolynomialCurveFitter.create(2);
            result = fitter2.fit(obs.toList());
            break;
         case Pol3:
            final PolynomialCurveFitter fitter3 = PolynomialCurveFitter.create(3);
            result = fitter3.fit(obs.toList());
            break;
         case Gaussian:
            final GaussianWithOffsetCurveFitter gf = GaussianWithOffsetCurveFitter.create();
            if (guess != null) {
               gf.withStartPoint(guess);
            }
            result = gf.fit(obs.toList());
      }
      
      return result;
   }
   
   /**
    * Given a JFreeChart dataset and a commons math function, return a JFreeChart
    * dataset in which the original x values are now accompanied by the y values
    * predicted by the function
    * 
    * @param data input JFreeChart data set
    * @param type one of the Fitter.FunctionType predefined functions
    * @param parms parameters describing the function.  These need to match the
    *             selected function or an IllegalArgumentEception will be thrown
    * 
    * @return JFreeChart dataset with original x values and fitted y values.
    */
   public static XYSeries getFittedSeries(XYSeries data, FunctionType type, 
           double[] parms) {
      XYSeries result = new XYSeries(data.getItemCount() * 10);
      double minRange = data.getMinX();
      double maxRange = data.getMaxX();
      double xStep = (maxRange - minRange) / (data.getItemCount() * 10);
      switch (type) {
         case NoFit: {
            try {
               XYSeries resCopy = data.createCopy(0, data.getItemCount() - 1);
               return resCopy;
            } catch (CloneNotSupportedException ex) {
               return null;
            }
         }
         case Pol1:
         case Pol2:
         case Pol3:
            checkParms(type, parms);
            PolynomialFunction polFunction = new PolynomialFunction(parms);
            for (int i = 0; i < data.getItemCount() * 10; i++) {
               double x = minRange + i * xStep;
               double y = polFunction.value(x);
               result.add(x, y);
            }
            break;
         case Gaussian:
            checkParms(type, parms);
            Gaussian.Parametric gf = new Gaussian.Parametric();
            for (int i = 0; i < data.getItemCount() * 10; i++) {
               double x = minRange + i * xStep;
               double[] gparms = new double[3];
               System.arraycopy(parms, 0, gparms, 0, 3);
               double y = gf.value(x, gparms) + parms[3];
               result.add(x, y);
            }
            break;
      }
      
      return result;
   }
   
   /**
    * Finds the x value corresponding to the maximum function value within the 
    * range of the provided data set
    * 
    * @param type one of the Fitter.FunctionType predefined functions
    * @param parms parameters describing the function.  These need to match the
    *             selected function or an IllegalArgumentEception will be thrown
    * @param data JFreeChart series, used to bracket the range in which the 
    *             maximum will be found
    * 
    * @return x value corresponding to the maximum function value
    */
   public static double getMaxX(XYSeries data, FunctionType type, double[] parms) {
      double xMax = 0.0;
      double minRange = data.getMinX();
      double maxRange = data.getMaxX();
      switch (type) {
         case NoFit:
            //  find the position in data with the highest y value
            double highestScore = data.getY(0).doubleValue();
            int highestIndex = 0;
            for (int i = 1; i < data.getItemCount(); i++) {
               double newVal = data.getY(i).doubleValue();
               if (newVal > highestScore) {
                  highestScore = newVal;
                  highestIndex = i;
               }
            }
            return data.getX(highestIndex).doubleValue();
         case Pol1:
         case Pol2:
         case Pol3:
            checkParms(type, parms);
            PolynomialFunction derivativePolFunction =
                    (new PolynomialFunction(parms)).polynomialDerivative();

            final double relativeAccuracy = 1.0e-12;
            final double absoluteAccuracy = 1.0e-8;
            final int maxOrder = 5;
            UnivariateSolver solver = 
                    new BracketingNthOrderBrentSolver(relativeAccuracy, 
                            absoluteAccuracy, maxOrder);
            xMax = solver.solve(100, derivativePolFunction, minRange, maxRange);
            break;
         case Gaussian:
            // for a Gaussian we can take the mean and be sure it is the maximum
            xMax = parms[1];
      }
              
      return xMax;
   }
   
   /**
    * Find the index in the data series with an x value closest to the given
    * searchValue
    * 
    * @param data data in XYSeries format
    * @param searchValue x value that we try to get close to
    * @return index into data with x value closest to searhValue
    */
   public static int getIndex (XYSeries data, double searchValue) {
      int index = 0;
      double diff = dataDiff(data.getX(0), searchValue);
      for (int i = 1; i < data.getItemCount(); i++) {
         double newVal = dataDiff(data.getX(i), searchValue);
         if (newVal < diff) {
            diff = newVal;
            index = i;
         } 
      }
      return index;
   }
   /**
    * helper function for getIndex
    * 
    * @param num
    * @param val
    * @return 
    */
   private static double dataDiff(Number num, double val) {
      double diff = num.doubleValue() - val;
      return Math.sqrt(diff * diff);
   }
   
   private static void checkParms(FunctionType type, double[] parms) {
      switch (type) {
         case Pol1:
            if (parms.length != 2) {
               throw new IllegalArgumentException("Needs a double[] of size 2");
            }
            break;
         case Pol2:
            if (parms.length != 3) {
               throw new IllegalArgumentException("Needs a double[] of size 3");
            }
            break;
         case Pol3:
            if (parms.length != 4) {
               throw new IllegalArgumentException("Needs a double[] of size 4");
            }
            break;
         case Gaussian:
            if (parms.length != 4) {
               throw new IllegalArgumentException("Needs a double[] of size 4");
            }
            break;
      }
   }
   
}