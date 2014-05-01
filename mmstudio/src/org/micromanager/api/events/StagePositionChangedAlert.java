package org.micromanager.api.events;

// This class signals when a single-axis drive has moved. 
public class StagePositionChangedAlert {
   public String deviceName_;
   public double pos_;

   public StagePositionChangedAlert(String deviceName, double pos) {
      deviceName_ = deviceName;
      pos_ = pos;
   }
}
