///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// DESCRIPTION:  Contains list of calibrations
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, June 2008
//
// COPYRIGHT:    University of California, San Francisco, 2008
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
//

package org.micromanager.internal.utils;

import java.util.ArrayList;
import mmcorej.CMMCore;
import mmcorej.StrVector;


/**
 * List of Pixel Size Calibrations.
 */
public final class CalibrationList {
   private final ArrayList<Calibration> calibrationList_;
   private String label_;
   private final CMMCore core_;

   /**
    * Constructs Pixel Size Calibration list based on data provided by the Core.
    *
    * @param core MM Core instance
    */
   public CalibrationList(final CMMCore core) {
      calibrationList_ = new ArrayList<>();
      label_ = "Undefined";
      core_ = core;
   }

   /**
    * Asks the Core for the Pixel Size Calibrations it knows about.
    */
   public void getCalibrationsFromCore() {
      calibrationList_.clear();
      StrVector calibrations = core_.getAvailablePixelSizeConfigs();
      for (int i = 0; i < calibrations.size(); i++) {
         try {
            Calibration cal = new Calibration();
            cal.setConfiguration(core_.getPixelSizeConfigData(calibrations.get(i)));
            cal.setLabel(calibrations.get(i));
            cal.setPixelSizeUm(core_.getPixelSizeUmByID(calibrations.get(i)));

            calibrationList_.add(cal);
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }
   }

   /**
    * Finds a Calibration that matches the current system state, or null if none was found.
    *
    * @return index of active calibration in the list or null if not found
    *     or an error occurred
    */
   public Integer getActiveCalibration() {
      try {
         String calibration = core_.getCurrentPixelSizeConfig(true);
         for (int i = 0; i < calibrationList_.size(); i++) {
            if (calibration.equals(calibrationList_.get(i).getLabel())) {
               return i;
            }
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }

      return null;
   }

   /**
    * Add one Calibration to the list.
    *
    * @param cl Calibration to be added to the list
    */
   public void add(Calibration cl) {
      calibrationList_.add(cl);
   }

   /**
    * Number of calibrations.
    *
    * @return number of calibrations in the list
    */
   public int size() {
      return calibrationList_.size();
   }

   /**
    * Returns calibration  based on index.
    *
    * @param idx - position index
    * @return calibration
    */
   public Calibration get(int idx) {
      return calibrationList_.get(idx);
   }

   /**
    * Returns calibration label.
    *
    * @return Label of the calibration
    */
   public String getLabel() {
      return label_;
   }

   /**
    * Sets  label of the calibration.
    *
    * @param label - Name of the calibration
    */
   public void setLabel(String label) {
      label_ = label;
   }
}