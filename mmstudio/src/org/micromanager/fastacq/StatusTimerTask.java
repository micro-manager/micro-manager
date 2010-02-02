package org.micromanager.fastacq;
import java.util.TimerTask;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.utils.ReportingUtils;

public class StatusTimerTask extends TimerTask {

   private CMMCore core_;
   private String cameraName_;
   private GUIStatus gui_;
   private int totalCapacity_;
   boolean hasActualInterval_;
   
   public StatusTimerTask(CMMCore core, GUIStatus gui) {
      core_ = core;
      cameraName_ = core_.getCameraDevice();
      gui_ = gui;
      try {
         hasActualInterval_ = core_.hasProperty(cameraName_, MMCoreJ.getG_Keyword_ActualInterval_ms());
      } catch (Exception e) {
         ReportingUtils.logError(e);
         hasActualInterval_ = false;
      }
   }

   public void run() {
      try {
         totalCapacity_ = core_.getBufferTotalCapacity();
         int remaining = core_.getRemainingImageCount();
         int free = core_.getBufferFreeCapacity();
         //double avgInterval = core_.getBufferIntervalMs();
         double actualInterval = 0.0;
         if (hasActualInterval_)
            actualInterval = Double.parseDouble(core_.getProperty(cameraName_, MMCoreJ.getG_Keyword_ActualInterval_ms()));
         else
            actualInterval = core_.getBufferIntervalMs();
         int percentFree = 0;
         if (totalCapacity_ > 0)
            percentFree = free * 100 / totalCapacity_;
         boolean acquiring = core_.isSequenceRunning();
         String bufState = new String("Acquiring.");
         if (!acquiring) {
            if (core_.isBufferOverflowed())
               bufState = "Overflowed.";
            else
               bufState = "Finished.";
            
            // cancel the display thread beacuse acquistion is finished
            gui_.acquisitionFinished();
         }
         String msg = bufState + " Interval=" + actualInterval + " ms. In que: " + remaining +  ", " + percentFree + "% free";
         gui_.displayMessage(msg);
         
      } catch (InterruptedException e) {
         ReportingUtils.logError(e);
         cancel();
      } catch (Exception e) {
         ReportingUtils.showError(e, "Error in getting info from the camera");
         cancel();
      }
   }
   
}


