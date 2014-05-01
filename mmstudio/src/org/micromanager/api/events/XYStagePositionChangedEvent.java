package org.micromanager.api.events;

// This class signals when any XY stage is moved.
public class XYStagePositionChangedEvent {
   public String deviceName_;
   public double xPos_;
   public double yPos_;

   public XYStagePositionChangedEvent(String deviceName, 
         double xPos, double yPos) {
      deviceName_ = deviceName;
      xPos_ = xPos;
      yPos_ = yPos;
   }
}
