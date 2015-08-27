/*
 * Utility functions for display of Gaussian fitted data
 * Part of the Localization Microscopy Package
 * 
 * Copyright UCSF, 2012.  BSD license
 */
package edu.valelab.gaussianfit.utils;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ImageStatistics;

/**
 *
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
