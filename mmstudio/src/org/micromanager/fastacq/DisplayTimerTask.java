package org.micromanager.fastacq;
import java.util.TimerTask;

import mmcorej.CMMCore;

import org.micromanager.api.DeviceControlGUI;
import org.micromanager.utils.ReportingUtils;

public class DisplayTimerTask extends TimerTask {

   private CMMCore core_;
   private DeviceControlGUI parentGUI_;
   
   public DisplayTimerTask(CMMCore core, DeviceControlGUI pg) {
      parentGUI_ = pg;
      core_ = core;
   }

   public void run() {

      try {
         if (core_.isSequenceRunning()) 
         {
            updateImage();
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
         cancel();
      }
   }
   
   public void updateImage() {
      try {
         // update image window
         Object img = core_.getLastImage();
         if (img != null)
            parentGUI_.displayImage(img);
         //
      } catch (Exception e){
         ReportingUtils.logError(e);
      }
   }   
}


