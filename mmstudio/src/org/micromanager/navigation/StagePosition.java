package org.micromanager.navigation;

import java.text.DecimalFormat;

import mmcorej.DeviceType;

public class StagePosition {
   public double x;
   public double y;
   public double z;
   public String stageName;
   public int numAxes;
   private static DecimalFormat fmt = new DecimalFormat("#0.00");
   
   public StagePosition() {
      stageName = new String("Undefined");
      x = 0.0;
      y = 0.0;
      z = 0.0;
      numAxes=1;
   }
   
   public String getVerbose() {
      if (numAxes == 1)
         return new String(stageName + "(" + fmt.format(x) + ")");
      else if (numAxes == 2)
         return new String(stageName + "(" + fmt.format(x) + "," + fmt.format(y) + ")");
      else
         return new String(stageName + "(" + fmt.format(x) + "," + fmt.format(y) + "," + fmt.format(z) + ")");

   }
}
