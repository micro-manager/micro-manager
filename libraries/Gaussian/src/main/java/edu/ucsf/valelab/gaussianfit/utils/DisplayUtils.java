/*
 * Utility functions for display of Gaussian fitted data
 * Part of the Localization Microscopy Package
 * 
 * @author - Nico Stuurman,  2012
 * 
 * 
Copyright (c) 2012-2017, Regents of the University of California
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

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ImageStatistics;

/**
 * @author Nico Stuurman
 */
public class DisplayUtils {

   /*
    * More or less verbatim copy of the ImageJ code
    */
   public static void AutoStretch(ImagePlus sp) {
      Calibration cal = sp.getCalibration();
      sp.setCalibration(null);
      ImageStatistics stats = sp.getStatistics(); // get uncalibrated stats
      sp.setCalibration(cal);
      int limit = stats.pixelCount / 10;
      int[] histogram = stats.histogram;
      int autoThreshold = 5000;
      int threshold = stats.pixelCount / autoThreshold;
      int i = -1;
      boolean found;
      int count;
      do {
         i++;
         count = histogram[i];
         if (count > limit) {
            count = 0;
         }
         found = count > threshold;
      } while (!found && i < 255);
      int hmin = i;
      i = 256;
      do {
         i--;
         count = histogram[i];
         if (count > limit) {
            count = 0;
         }
         found = count > threshold;
      } while (!found && i > 0);
      int hmax = i;
      Roi roi = sp.getRoi();
      if (hmax >= hmin) {
         double min = stats.histMin + hmin * stats.binSize;
         double max = stats.histMin + hmax * stats.binSize;
         if (min == max) {
            min = stats.min;
            max = stats.max;
         }
         sp.setDisplayRange(min, max);
      }
   }

   public static void SetCalibration(ImagePlus sp, float pixelSize) {
      Calibration cal = new Calibration();
      cal.pixelWidth = pixelSize;
      cal.pixelHeight = pixelSize;
      cal.setUnit("nm");
      sp.setCalibration(cal);
   }

}
