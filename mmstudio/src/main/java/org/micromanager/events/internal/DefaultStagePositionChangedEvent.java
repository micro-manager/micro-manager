package org.micromanager.events.internal;

import org.micromanager.events.StagePositionChangedEvent;

/**
 * This class signals when a single-axis drive has moved.
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultStagePositionChangedEvent implements StagePositionChangedEvent {
   private final String deviceName_;
   private final double pos_;

   public DefaultStagePositionChangedEvent(String deviceName, double pos) {
      deviceName_ = deviceName;
      pos_ = pos;
   }

   /**
    * The new (current) position of the stage
    * @return The new (current) position of the stage
    */

   public double getPos() {
         return pos_;
      }


   /**
    * Name of the stage that moved
    * @return Name of the stage that moved
    */
   public String getDeviceName() {
         return deviceName_;
      }

}
