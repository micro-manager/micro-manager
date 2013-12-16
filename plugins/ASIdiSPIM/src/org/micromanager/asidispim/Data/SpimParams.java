///////////////////////////////////////////////////////////////////////////////
//FILE:          SpimParams.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.Data;

import java.util.ArrayList;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Data.Properties.PropTypes;
import org.micromanager.asidispim.Utils.SpimParamsListenerInterface;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;

import java.util.ArrayList;
import java.util.List;



/**
 *
 * @author nico
 * @author Jon
 */
public class SpimParams implements DevicesListenerInterface {
   
   // a list of strings used as keys to the propInfo_ hashmap
   public static final String NR_REPEATS = "NRepeats";
   public static final String NR_SIDES = "NSides";
   public static final String NR_SLICES = "NSlices";
   public static final String NR_LINESCANS_PER_SLICE = "NLinesScansPerSlice";
   public static final String LINE_SCAN_PERIOD = "LineScanPeriodA";
   public static final String LINE_SCAN_PERIOD_B = "LineScanPeriodB";
   public static final String DELAY_BEFORE_SLICE = "DelayBeforeSliceA";
   public static final String DELAY_BEFORE_SIDE = "DelayBeforeSideA";
   public static final String FIRSTSIDE = "FirstSide";
   public static final String FIRSTSIDE_A_VAL = "A";
   public static final String FIRSTSIDE_B_VAL = "B";  
   
   private final List<SpimParamsListenerInterface> listeners_;
   public final Properties props_;  // note this is public! so we can call get/set methods on properties 
   
   public SpimParams(ScriptInterface gui, Devices devices) {
      listeners_ = new ArrayList<SpimParamsListenerInterface>();
      props_ = new Properties(gui, devices);
      
      // initialize the relevant properties
      props_.addPropertyData(FIRSTSIDE, "SPIMFirstSide", Devices.GALVOA, PropTypes.STRING);
      props_.addPropertyData(NR_REPEATS, "SPIMNumRepeats", Devices.GALVOA, PropTypes.INTEGER);
      props_.addPropertyData(NR_SIDES, "SPIMNumSides", Devices.GALVOA, PropTypes.INTEGER);
      props_.addPropertyData(NR_REPEATS, "SPIMNumRepeats", Devices.GALVOA, PropTypes.INTEGER);
      props_.addPropertyData(NR_SLICES, "SPIMNumSlices", Devices.GALVOA, PropTypes.INTEGER);
      props_.addPropertyData(NR_LINESCANS_PER_SLICE, "SPIMNumScansPerSlice", Devices.GALVOA, PropTypes.INTEGER);
      props_.addPropertyData(LINE_SCAN_PERIOD, "SingleAxisXPeriod(ms)", Devices.GALVOA, PropTypes.INTEGER);
      props_.addPropertyData(LINE_SCAN_PERIOD_B, "SingleAxisXPeriod(ms)", Devices.GALVOB, PropTypes.INTEGER);
      props_.addPropertyData(DELAY_BEFORE_SLICE, "SPIMDelayBeforeSlice(ms)", Devices.GALVOA, PropTypes.INTEGER);
      props_.addPropertyData(DELAY_BEFORE_SIDE, "SPIMDelayBeforeSide(ms)", Devices.GALVOA, PropTypes.INTEGER);
   }
   
   @Override
   public void devicesChangedAlert() {
      callListeners();
   }

   public void addListener(SpimParamsListenerInterface listener) {
      listeners_.add(listener);
   }

   public void removeListener(SpimParamsListenerInterface listener) {
      listeners_.remove(listener);
   }

   private void callListeners() {
      for (SpimParamsListenerInterface listener: listeners_) {
         listener.spimParamsChangedAlert();
      }
   }

}
