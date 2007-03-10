package org.micromanager.navigation;

public class StagePosition {
   public double x;
   public double y;
   public double z;
   public String label;
   public String device;
   
   public StagePosition() {
      label = new String("Undefined");
      device = new String("Undefined");
      x = 0.0;
      y = 0.0;
      z = 0.0;
   }
}
