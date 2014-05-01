package org.micromanager.api.events;

// This class signals when the exposure time for a given camera has changed.
public class ExposureChangedEvent {
   private String cameraName_;
   private double newExposureTime_;

   public ExposureChangedEvent(String cameraName, double newExposureTime) {
      cameraName_ = cameraName;
      newExposureTime_ = newExposureTime;
   }
   public String getCameraName() {
      return cameraName_;
   }
   public double getNewExposureTime() {
      return newExposureTime_;
   }
}
