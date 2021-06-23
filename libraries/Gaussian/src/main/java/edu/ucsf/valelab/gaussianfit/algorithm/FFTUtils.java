/**
 * Utility function used in calculation of Power Spectra
 * <p>
 * Copyright (c) 2012-2017, Regents of the University of California All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * The views and conclusions contained in the software and documentation are those of the authors
 * and should not be interpreted as representing official policies, either expressed or implied, of
 * the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.algorithm;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.transform.FastFourierTransformer;
import org.jfree.data.xy.XYSeries;

/**
 * @author nico
 */
public class FFTUtils {


   /**
    * simple and bad way to calculate next power of 2
    *
    * @param n query number
    * @return first number &gt; n that is a power of 2
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
    *
    * @param n query
    * @return highest number &lt; n that is a power of 2
    */
   public static int previousPowerOf2(int n) {
      int res = 1;
      while (res < n) {
         res <<= 1;
      }
      res >>= 1;
      return res;
   }

   /**
    * Calculates Power Spectrum density for the given datasets and add result to a XYSeries for
    * graphing using JFreeChart Currently, the dataset is truncated to the highest power of two Need
    * to add pWelch windowing and zero-padding to next highest power of two
    *
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
            if (plotMode == DataCollectionForm.PlotMode.X) {
               d[i] = spot.getXCenter();
            } else if (plotMode == DataCollectionForm.PlotMode.Y) {
               d[i] = spot.getYCenter();
            } else if (plotMode == DataCollectionForm.PlotMode.INT) {
               d[i] = spot.getIntensity();
            }
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
