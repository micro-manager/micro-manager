package org.micromanager.api.events;

// This class signals when the exposure time for a given camera has changed.
public class ExposureChangedEvent {
   public String cameraName_;
   public double newExposureTime_;

   public ExposureChangedEvent(String cameraName, double newExposureTime) {
      cameraName_ = cameraName;
      newExposureTime_ = newExposureTime;
   }
}
