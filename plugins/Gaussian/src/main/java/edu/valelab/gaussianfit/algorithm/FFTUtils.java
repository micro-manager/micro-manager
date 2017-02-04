/**
 * Utility function used in calculation of Power Spectra
 */
package edu.valelab.gaussianfit.algorithm;

import edu.valelab.gaussianfit.DataCollectionForm;
import edu.valelab.gaussianfit.data.SpotData;
import edu.valelab.gaussianfit.data.RowData;
import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.transform.FastFourierTransformer;
import org.jfree.data.xy.XYSeries;

/**
 *
 * @author nico
 */
public class FFTUtils {
   
   
   /**
    * simple and bad way to calculate next power of 2
    * @param n query number
    * @return first number > n that is a power of 2
    */
   public static int nextPowerOf2(int n) {
      int res = 1;
      while (res < n) {
         res <<= 1;
      }
      return res;
   }

   /**
    * simple and bad way to calculate the highest power of 2 contained in this number
    * @param n query
    * @return highest number < n that is a power of 2
    */
   public static int previousPowerOf2(int n) {
      int res = 1;
      while (res < n) {
         res <<= 1;
      }
      res >>=1;
      return res ;
   }
   
  /**
    * Calculates Power Spectrum density for the given datasets
    * and add result to a XYSeries for graphing using JFreeChart
    * Currently, the dataset is truncated to the highest power of two
    * Need to add pWelch windowing and zero-padding to next highest power of two
    * @param rowDatas
    * @param datas 
    * @param plotMode 
    */
   public static void calculatePSDs(RowData[] rowDatas, 
           XYSeries[] datas,
           DataCollectionForm.PlotMode plotMode) {
      for (int index = 0; index < rowDatas.length; index++) {
         FastFourierTransformer fft = new FastFourierTransformer();
         datas[index] = new XYSeries(rowDatas[index].ID_);
         int length = rowDatas[index].spotList_.size();
         if (!FastFourierTransformer.isPowerOf2(length)) {
            length = FFTUtils.previousPowerOf2(length);
         }
         double[] d = new double[length];

         for (int i = 0; i < length; i++) {
            SpotData spot = rowDatas[index].spotList_.get(i);
            if (plotMode == DataCollectionForm.PlotMode.X)
               d[i] = spot.getXCenter();
            else if (plotMode == DataCollectionForm.PlotMode.Y)
               d[i] = spot.getYCenter();
            else if (plotMode == DataCollectionForm.PlotMode.INT)
               d[i] = spot.getIntensity();
         }
         Complex[] c = fft.transform(d);
         int size = c.length / 2;
         double[] e = new double[size];
         double[] f = new double[size];
         double duration = rowDatas[index].timePoints_.get(length)
                 - rowDatas[index].timePoints_.get(0);
         double frequencyStep = 1000.0 / duration;
         // calculate the conjugate and normalize
         for (int i = 1; i < size; i++) {
            e[i] = (c[i].getReal() * c[i].getReal()
                    + c[i].getImaginary() * c[i].getImaginary()) / c.length;
            f[i] = frequencyStep * i;
            datas[index].add(f[i], e[i]);
         }
      }
   }
   
}
