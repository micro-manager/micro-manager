package org.micromanager.api.events;

// This class signals when any XY stage is moved.
public class XYStagePositionChangedEvent {
   private String deviceName_;
   private double xPos_;
   private double yPos_;

   public XYStagePositionChangedEvent(String deviceName, 
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
