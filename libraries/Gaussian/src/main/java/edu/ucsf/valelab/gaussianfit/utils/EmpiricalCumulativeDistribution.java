/*
 * Utility functions for display of Gaussian fitted data
 * Part of the Localization Microscopy Package
 * 
 * @author - Nico Stuurman,  2018
 * 
 * 
Copyright (c) 2012-2018, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.utils;

import java.util.Arrays;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

/**
 * @author nico
 */
public class EmpiricalCumulativeDistribution {

   public static Vector2D[] calculate(double[] values) {
      Arrays.sort(values);
      Vector2D[] result = new Vector2D[values.length];
      final double valuesd = (double) values.length;
      final double increment = 1d / valuesd;
      final double halfIncrement = 0.5 * increment;
      for (int i = 0; i < values.length; i++) {
         result[i] = new Vector2D(values[i], i * increment + halfIncrement);
         // result[i] = new Vector2D(values[i], ((double)i + 1d) / valuesd);
      }

      return result;

   }

}
