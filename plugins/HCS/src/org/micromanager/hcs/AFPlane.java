package org.micromanager.hcs;

import org.micromanager.navigation.MultiStagePosition;


public class AFPlane {
   private double A;
   private double B;
   private double C;
   private double D;

   public AFPlane(MultiStagePosition posList[]) {
      A = 0.0;
      B = 0.0;
      C = 0.0;
      D = 0.0;

      if (posList.length == 3) {
         double x1, x2, x3;
         double y1, y2, y3;
         double z1, z2, z3;

         MultiStagePosition mps = posList[0];
         x1 = mps.getX();
         y1 = mps.getY();
         z1 = mps.getZ();

         mps = posList[1];
         x2 = mps.getX();
         y2 = mps.getY();
         z2 = mps.getZ();

         mps = posList[2];
         x3 = mps.getX();
         y3 = mps.getY();
         z3 = mps.getZ();

         if (  x1 != 0.0 && x2 != 0.0 && x3 != 0.0 &&
               y1 != 0.0 && y2 != 0.0 && y3 != 0.0 &&
               z1 != 0.0 && z2 != 0.0 && z3 != 0.0) {          
            A = y1 * (z2 - z3) + y2 * (z3 - z1) + y3 * (z1 - z2);
            B = z1 * (x2 - x3) + z2 * (x3 - x1) + z3 * (x1 - x2);
            C = x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2);
            D = -(x1 * (y2*z3 - y3*z2) + x2 * (y3*z1 - y1*z3) + x3 * (y1*z2 - y2*z1));
         }
      }
   }

   double getZPos(double x, double y) {
      if (C == 0.0)
         return 0.0;

      double z = (-D - A * x - B *y) / C;
      return z;
   }

}

