package org.micromanager.api.events;

// This class signals when the exposure time for a given camera has changed.
public class SLMExposureChangedEvent {
   private String deviceName_;
   private double newExposureTime_;

   public SLMExposureChangedEvent(String deviceName, double newExposureTime) {
      deviceName_ = deviceName;
      newExposureTime_ = newExposureTime;
   }
   public String getDeviceName() {
      return deviceName_;
   }
   public double getNewExposureTime() {
      return newExposureTime_;
   }
}
