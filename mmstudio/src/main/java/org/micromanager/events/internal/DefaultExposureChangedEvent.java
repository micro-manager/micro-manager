package org.micromanager.events.internal;

import org.micromanager.events.ExposureChangedEvent;

/**
 * This class signals when the exposure time for a given camera has changed.
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultExposureChangedEvent implements ExposureChangedEvent {
   private final String cameraName_;
   private final double newExposureTime_;

   public DefaultExposureChangedEvent(String cameraName, double newExposureTime) {
      cameraName_ = cameraName;
      newExposureTime_ = newExposureTime;
   }

   /**
    * Camera whose exposure time changed.
    * @return Camera whose exposure time changed.
    */
   public String getCameraName() {
         return cameraName_;
      }

   /**
    * New exposure time
    * @return New exposure time.
    */
   public double getNewExposureTime() {
         return newExposureTime_;
      }
}
