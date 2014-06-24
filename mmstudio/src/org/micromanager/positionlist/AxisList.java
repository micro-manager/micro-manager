package org.micromanager.positionlist;

import java.util.ArrayList;
import java.util.prefs.Preferences;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.utils.ReportingUtils;

/**
 * List with Axis data.  Currently, we use only a single global instance 
 * of this class
 */
class AxisList {
   private ArrayList<AxisData> axisList_ = new ArrayList<AxisData>();
   private CMMCore core_;
   private Preferences prefs_;
   
   public AxisList(CMMCore core, Preferences prefs) {
      core_ = core;
      prefs_ = prefs;
      // Initialize the axisList.
      try {
         // add 1D stages
         StrVector stages = core_.getLoadedDevicesOfType(DeviceType.StageDevice);
         for (int i=0; i<stages.size(); i++) {
            axisList_.add(new AxisData(prefs_.getBoolean(stages.get(i),true), 
                    stages.get(i), AxisData.AxisType.oneD));
         }
         // read 2-axis stages
         StrVector stages2D = core_.getLoadedDevicesOfType(DeviceType.XYStageDevice);
         for (int i=0; i<stages2D.size(); i++) {
            axisList_.add(new AxisData(prefs_.getBoolean(stages2D.get(i),true), 
                    stages2D.get(i), AxisData.AxisType.twoD));
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }
   public AxisData get(int i) {
      if (i >=0 && i < axisList_.size()) {
         return axisList_.get(i);
      }
      return null;
   }
   public int getNumberOfPositions() {
      return axisList_.size();
   }
   public boolean use(String axisName) {
      for (int i=0; i< axisList_.size(); i++) {
         if (axisName.equals(get(i).getAxisName())) {
            return get(i).getUse();
         }
      }
      // not in the list??  It might be time to refresh the list.  
      return true;
   }
      
}
