package org.micromanager.events.internal;

import org.micromanager.events.SLMExposureChangedEvent;

/**
 * This interface posts when the exposure time for a given Spatial Light Modulator
 * (such as a Digital Mirror Device) changes.
 *
 * <p>This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.</p>
 */
public class DefaultSLMExposureChangedEvent implements SLMExposureChangedEvent {
   private final String deviceName_;
   private final double newExposureTime_;

   public DefaultSLMExposureChangedEvent(String deviceName, double newExposureTime) {
      deviceName_ = deviceName;
      newExposureTime_ = newExposureTime;
   }

   /**
    * Name of the SLM device.
    *
    * @return Name of the (SLM) device that changes exposure.
    */
   public String getDeviceName() {
      return deviceName_;
   }

   /**
    * New exposure time of the SLM device (in ms).
    *
    * @return new exposure time of the (SLM) device.
    */
   public double getNewExposureTime() {
      return newExposureTime_;
   }
}
