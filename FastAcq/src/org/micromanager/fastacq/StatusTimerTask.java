package org.micromanager.fastacq;
import java.util.TimerTask;

import mmcorej.CMMCore;

public class StatusTimerTask extends TimerTask {

   private CMMCore core_;
   private String cameraName_;
   private GUIStatus gui_;
   private int totalCapacity_;
   
   public StatusTimerTask(CMMCore core, GUIStatus gui) {
      core_ = core;
      cameraName_ = core_.getCameraDevice();
      gui_ = gui;
   }

   public void run() {

      try {
         totalCapacity_ = core_.getBufferTotalCapacity();
         int remaining = core_.getRemainingImageCount();
         int free = core_.getBufferFreeCapacity();
         double avgInterval = core_.getBufferIntervalMs();
         int percentFree = 0;
         if (totalCapacity_ > 0)
            percentFree = free * 100 / totalCapacity_;
         boolean acquiring = core_.deviceBusy(cameraName_);
         String bufState = new String("Acquiring.");
         if (!acquiring) {
            if (core_.isBufferOverflowed())
               bufState = "Overflowed.";
            else
               bufState = "Finished.";
         }
         String msg = bufState + " Interval=" + avgInterval + " ms. In que: " + remaining +  ", " + percentFree + "% free";
         gui_.displayMessage(msg);
      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }
   
}


