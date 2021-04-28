package org.micromanager.events.internal;

import org.micromanager.events.XYStagePositionChangedEvent;

/**
 * This class signals when any XY stage changes position.
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultXYStagePositionChangedEvent implements XYStagePositionChangedEvent {
   private final String deviceName_;
   private final double xPos_;
   private final double yPos_;

   public DefaultXYStagePositionChangedEvent(String deviceName,
                                      double xPos, double yPos) {
      deviceName_ = deviceName;
      xPos_ = xPos;
      yPos_ = yPos;
   }

   /**
    * @return New X position of the stage in microns
    */
   public double getXPos() {
      return xPos_;
   }

   /**
    * @return New Y position of the stage in microns
    */
   public double getYPos() {
      return yPos_;
   }

   /**
    * Name of the (XYStage) device that change position
    * @return Name of the (XYStage) device that changed position
    */
   public String getDeviceName() {
      return deviceName_;
   }
}
