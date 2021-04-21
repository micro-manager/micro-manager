package org.micromanager.events.internal;

import org.micromanager.events.SLMExposureChangedEvent;

public class DefaultSLMExposureChangedEvent implements SLMExposureChangedEvent {
  private final String deviceName_;
  private final double newExposureTime_;

  public DefaultSLMExposureChangedEvent(String deviceName, double newExposureTime) {
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
