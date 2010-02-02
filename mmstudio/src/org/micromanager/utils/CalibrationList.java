///////////////////////////////////////////////////////////////////////////////
//FILE:          CalibrationList.java
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
package org.micromanager.utils;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.swing.JOptionPane;

import mmcorej.CMMCore;
import mmcorej.StrVector;



public class CalibrationList {
   private ArrayList<Calibration> calibrationList_;
   private String label_;
   private Hashtable<String, String> properties_;
   private CMMCore core_;
   
   public CalibrationList(CMMCore core) {
      calibrationList_ = new ArrayList<Calibration>();
      label_ = new String("Undefined");
      properties_ = new Hashtable<String, String>();
      core_ = core;
   }

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
    * Add one Calibration to the list
    */
   public void add(Calibration cl) {
      calibrationList_.add(cl);
   }
   
   /**
    * Number of calibrations
    */
   public int size() {
     return calibrationList_.size();
   }
   
   /**
    * Returns calibration  based on index
    * @param idx - position index
    * @return
    */
   public Calibration get(int idx) {
      return calibrationList_.get(idx);
   }
   
   /**
    * Add a generalized property-value pair to the calibration
    */
   public void setProperty(String key, String value) {
      properties_.put(key, value);
   }

   private void handleException (Exception e) {
      String errText = "Exception occurred: " + e.getMessage();
      JOptionPane.showMessageDialog(null, errText);
   }

   /**
    * Return the array of property keys (names) associated with this calibration
    */
   public String[] getPropertyNames() {
      String keys[] = new String[properties_.size()];
      int i=0;
      for (Enumeration<String> e = properties_.keys(); e.hasMoreElements();)
         keys[i++] = e.nextElement();
      return keys;
   }
   
   /**
    * Checks if the calibration has a particular property
    */
   public boolean hasProperty(String key) {
      return properties_.containsKey(key);
   }
   
   /**
    * Returns property value for a given key (name) 
    */
   public String getProperty(String key) {
      if (properties_.containsKey(key))
         return properties_.get(key);
      else
         return null;
   }
   
   /**
    * Returns calibration label
    */
   public String getLabel() {
      return label_;
   }

   /**
    * Sets position label (such as well name, etc.)
    */
   public void setLabel(String lab) {
      label_ = lab;
   }
   
}
