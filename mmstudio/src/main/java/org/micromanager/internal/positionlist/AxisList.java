package org.micromanager.internal.positionlist;

import java.util.ArrayList;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * List with Axis data.  Currently, we use only a single global instance 
 * of this class
 */
class AxisList {
   private ArrayList<AxisData> axisList_ = new ArrayList<AxisData>();
   private CMMCore core_;
   
   public AxisList(CMMCore core) {
      core_ = core;
      // Initialize the axisList.
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      try {
         // add 1D stages
         StrVector stages = core_.getLoadedDevicesOfType(DeviceType.StageDevice);
         for (int i=0; i<stages.size(); i++) {
            axisList_.add(new AxisData(profile.getBoolean(
                        AxisList.class, stages.get(i), true), 
                    stages.get(i), AxisData.AxisType.oneD));
         }
         // read 2-axis stages
         StrVector stages2D = core_.getLoadedDevicesOfType(DeviceType.XYStageDevice);
         for (int i=0; i<stages2D.size(); i++) {
            axisList_.add(new AxisData(profile.getBoolean(
                        AxisList.class, stages2D.get(i), true), 
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
