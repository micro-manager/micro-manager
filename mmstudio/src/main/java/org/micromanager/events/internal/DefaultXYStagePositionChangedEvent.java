package org.micromanager.events.internal;

import org.micromanager.events.XYStagePositionChangedEvent;

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
   public double getXPos() {
      return xPos_;
   }
   public double getYPos() {
      return yPos_;
   }
   public String getDeviceName() {
      return deviceName_;
   }
}
