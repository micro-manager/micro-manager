package org.micromanager.events.internal;

import org.micromanager.events.ExposureChangedEvent;

public class DefaultExposureChangedEvent implements ExposureChangedEvent {
  private final String cameraName_;
  private final double newExposureTime_;

  public DefaultExposureChangedEvent(String cameraName, double newExposureTime) {
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
