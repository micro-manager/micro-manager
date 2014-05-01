package org.micromanager.api.events;

// This class signals when a single-axis drive has moved. 
public class StagePositionChangedEvent {
   private String deviceName_;
   private double pos_;

   public StagePositionChangedEvent(String deviceName, double pos) {
      deviceName_ = deviceName;
      pos_ = pos;
   }
   public double getPos() {
      return pos_;
   }
   public String getDeviceName() {
      return deviceName_;
   }
}
