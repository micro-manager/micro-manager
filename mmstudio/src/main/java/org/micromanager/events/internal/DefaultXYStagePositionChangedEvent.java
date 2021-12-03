package org.micromanager.events.internal;

import org.micromanager.events.XYStagePositionChangedEvent;

/**
 * This class signals when any XY stage changes position.
 *
 * <p>This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.</p>
 */
public class DefaultXYStagePositionChangedEvent implements XYStagePositionChangedEvent {
   private final String deviceName_;
   private final double xPos_;
   private final double yPos_;

   /**
    * Event signalling that the position of 2D stage has changed.
    *
    * @param deviceName Name of the 2D stage device
    * @param xPos New X position (in microns)
    * @param yPos New Y position (in microns)
    */
   public DefaultXYStagePositionChangedEvent(String deviceName,
                                      double xPos, double yPos) {
      deviceName_ = deviceName;
      xPos_ = xPos;
      yPos_ = yPos;
   }

   /**
    * Returns the new X position of this 2D stage.
    *
    * @return New X position of the stage in microns
    */
   public double getXPos() {
      return xPos_;
   }

   /**
    * Returns the new Y position of this 2D stage.
    *
    * @return New Y position of the stage in microns
    */
   public double getYPos() {
      return yPos_;
   }

   /**
    * Name of the (XYStage) device that change position.
    *
    * @return Name of the (XYStage) device that changed position
    */
   public String getDeviceName() {
      return deviceName_;
   }
}
