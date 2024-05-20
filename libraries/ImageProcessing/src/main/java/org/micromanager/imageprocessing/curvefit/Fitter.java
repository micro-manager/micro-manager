package org.micromanager.imageprocessing.curvefit;

import org.apache.commons.math3.analysis.function.Gaussian;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.jfree.data.xy.XYSeries;

public class Fitter {
   static final String NOFIT = "No fit (take max)";
   static final String GAUSSIAN = "Gaussian";

   public enum FunctionType {
      NoFit, Gaussian
   }

   /**
    * Utility to facilitate fitting data plotted in JFreeChart.
    * Provide data in JFReeChart format (XYSeries), and retrieve univariate
    * function parameters that best fit (using least squares) the data. All data
    * points will be weighted equally.
    * TODO: investigate whether weighting (possibly automatic weighting) can
    * improve accuracy
    *
    * @param data xy series in JFReeChart format
    * @param type one of the Fitter.FunctionType predefined functions
    * @param guess initial guess for the fit.  The number and meaning of these
    *              parameters depends on the FunctionType.  Implemented:
    *              Gaussian: 0: Normalization, 1: Mean 2: Sigma
    * @return array with parameters, whose meaning depends on the FunctionType.
    *          Use the function getXYSeries to retrieve the XYDataset predicted
    *          by this fit
    */
   public static double[] fit(XYSeries data, Fitter.FunctionType type, double[] guess) {

      if (type == Fitter.FunctionType.NoFit) {
         return null;
      }
      // create the commons math data object from the JFreeChart data object
      final WeightedObservedPoints obs = new WeightedObservedPoints();
      for (int i = 0; i < data.getItemCount(); i++) {
         obs.add(1.0, data.getX(i).doubleValue(), data.getY(i).doubleValue());
      }

      double[] result = null;
      switch (type) {
         case Gaussian:
            final GaussianWithOffsetCurveFitter gf = GaussianWithOffsetCurveFitter.create();
            if (guess != null) {
               gf.withStartPoint(guess);
            }
            result = gf.fit(obs.toList());
            break;
         default:
            break;
      }

      return result;
   }

   /**
    * Given a JFreeChart dataset and a commons math function, return a JFreeChart
    * dataset in which the original x values are now accompanied by the y values
    * predicted by the function.
    *
    * @param data input JFreeChart data set
    * @param type one of the Fitter.FunctionType predefined functions
    * @param parms parameters describing the function.  These need to match the
    *             selected function or an IllegalArgumentEception will be thrown
    *
    * @return JFreeChart dataset with original x values and fitted y values.
    */
   public static XYSeries getFittedSeries(XYSeries data, Fitter.FunctionType type,
                                          double[] parms) {
      XYSeries result = new XYSeries(data.getItemCount() * 10);
      double minRange = data.getMinX();
      double maxRange = data.getMaxX();
      double xStep = (maxRange - minRange) / (data.getItemCount() * 10);
      switch (type) {
         case NoFit:
            try {
               result = data.createCopy(0, data.getItemCount() - 1);
               result.setKey(data.getItemCount() * 10);
            } catch (CloneNotSupportedException ex) {
               return null;
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
         default:
            throw new RuntimeException("Unknown function type");
      }

      return result;
   }

   /**
    * Finds the x value corresponding to the maximum function value within the
    * range of the provided data set.
    *
    * @param type one of the Fitter.FunctionType predefined functions
    * @param parms parameters describing the function.  These need to match the
    *             selected function or an IllegalArgumentException will be thrown
    * @param data JFreeChart series, used to bracket the range in which the
    *             maximum will be found
    *
    * @return x value corresponding to the maximum function value
    */
   public static double getXofMaxY(XYSeries data, Fitter.FunctionType type, double[] parms) {
      double xAtMax = 0.0;
      double minX = data.getMinX();
      double maxX = data.getMaxX();
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
         case Gaussian:
            // for a Gaussian we can take the mean and be sure it is the maximum
            // note that this may be outside our range of X values, but
            // this will be caught by our sanity checks below
            xAtMax = parms[1];
            break;
         default:
            throw new RuntimeException("Unknown function type");
      }

      // sanity checks
      if (xAtMax > maxX) {
         xAtMax = maxX;
      }
      if (xAtMax < minX) {
         xAtMax = minX;
      }

      return xAtMax;
   }

   /**
    * Find the index in the data series with an x value closest to the given
    * searchValue.
    *
    * @param data data in XYSeries format
    * @param searchValue x value that we try to get close to
    * @return index into data with x value closest to searchValue
    */
   public static int getIndex(XYSeries data, double searchValue) {
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
    * Helper function for getIndex.
    *
    * @param num number to compare
    * @param val value to compare to
    * @return difference between the number and the value
    */
   static double dataDiff(Number num, double val) {
      double diff = num.doubleValue() - val;
      return Math.sqrt(diff * diff);
   }

   /**
    * Calculates a measure for the goodness of fit as defined here:
    * http://en.wikipedia.org/wiki/Coefficient_of_determination
    * R^2 = 1 - (SSres/SStot)
    * where
    *    SSres = SUM(i) (yi - fi)^2
    * end
    *    SStot = SUM(i) (yi - yavg)^2
    *
    * @param data input data (raw data that were fitted
    * @param type function type used for fitting
    * @param parms function parameters derived in the fit
    * @return R^2 value
    */
   public static double getRSquare(XYSeries data, Fitter.FunctionType type,
                                   double[] parms) {

      // calculate SStot
      double yAvg = getYAvg(data);
      double ssTot = 0.0;
      for (int i = 0; i < data.getItemCount(); i++) {
         double y = data.getY(i).doubleValue();
         ssTot += (y - yAvg) * (y - yAvg);
      }

      // calculate SSres
      double ssRes = 0.0;
      for (int i = 0; i < data.getItemCount(); i++) {
         double y = data.getY(i).doubleValue();
         double f = getFunctionValue(data.getX(i).doubleValue(), type, parms);
         ssRes += (y - f) * (y - f);

      }

      return 1.0 - (ssRes / ssTot);
   }

   /**
    * Returns the average of the ys in a XYSeries.
    *
    * @param data input data
    * @return y average
    */
   public static double getYAvg(XYSeries data) {
      double avg = 0;
      for (int i = 0; i < data.getItemCount(); i++) {
         avg += data.getY(i).doubleValue();
      }
      avg = avg / data.getItemCount();
      return avg;
   }

   /**
    * Calculate the y value for a given function and x value.
    * Throws an IllegalArgumentException if the pars do not match the function
    *
    * @param xValue xValue to be used in the function
    * @param type function type
    * @param parms function parameters (for instance, as return from the fit function
    * @return y value
    */
   public static double getFunctionValue(double xValue, Fitter.FunctionType type,
                                         double[] parms) {
      switch (type) {
         case NoFit: {
            return xValue;
         }
         case Gaussian:
            checkParms(type, parms);
            Gaussian.Parametric gf = new Gaussian.Parametric();
            double[] parms2 = new double[3];
            System.arraycopy(parms, 0, parms2, 0, 3);
            return gf.value(xValue, parms2) + parms[3];
         default:
            throw new RuntimeException("Unknown function type");
      }
   }

   static void checkParms(Fitter.FunctionType type, double[] parms) {
      switch (type) {
         case Gaussian:
            if (parms.length != 4) {
               throw new IllegalArgumentException("Needs a double[] of size 4");
            }
            break;
         case NoFit:
         default:
            break;
      }
   }

   public static String getFunctionTypeAsString(Fitter.FunctionType key) {
      switch (key) {
         case NoFit: return NOFIT;
         case Gaussian: return GAUSSIAN;
         default: throw new RuntimeException("Unknown function type");
      }
   }

   public static Fitter.FunctionType getFunctionTypeAsType(String key) {
      if (key.equals(NOFIT)) {
         return Fitter.FunctionType.NoFit;
      } else if (key.equals(GAUSSIAN)) {
         return Fitter.FunctionType.Gaussian;
      }
      return Fitter.FunctionType.NoFit;
   }

   public static String[] getFunctionTypes() {
      return new String[] {NOFIT, GAUSSIAN};
   }

}