package org.micromanager.events.internal;

import org.micromanager.events.SLMExposureChangedEvent;

/**
 * This interface posts when the exposure time for a given Spatial Light Modulator
 * (such as a Digital Mirror Device) changes.
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultSLMExposureChangedEvent implements SLMExposureChangedEvent {
   private final String deviceName_;
   private final double newExposureTime_;

   public DefaultSLMExposureChangedEvent (String deviceName, double newExposureTime) {
      deviceName_ = deviceName;
      newExposureTime_ = newExposureTime;
   }

   /**
    * @return Name of the (SLM) device that changes exposure.
    */
   public String getDeviceName() {
      return deviceName_;
   }

   /**
    * @return new exposure time of thr (SLM) device.
    */
   public double getNewExposureTime() {
      return newExposureTime_;
   }
}
