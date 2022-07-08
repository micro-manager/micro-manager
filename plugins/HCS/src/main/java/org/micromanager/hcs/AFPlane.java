package org.micromanager.hcs;

import org.micromanager.MultiStagePosition;
import org.micromanager.StagePosition;


public class AFPlane {
   private double A;
   private double B;
   private double C;
   private double D;
   private boolean valid_ = false;
   private String zStage_;

   public AFPlane(MultiStagePosition posList[]) {
      A = 0.0;
      B = 0.0;
      C = 0.0;
      D = 0.0;

      if (posList.length == 3) {
         double x1, x2, x3;
         double y1, y2, y3;
         double z1, z2, z3;

         try {
            MultiStagePosition msp = posList[0];
            x1 = getX(msp);
            y1 = getY(msp);
            z1 = getZ(msp);

            msp = posList[1];
            x2 = getX(msp);
            y2 = getY(msp);
            z2 = getZ(msp);

            msp = posList[2];
            x3 = getX(msp);
            y3 = getY(msp);
            z3 = getZ(msp);
            
            for (int i = 0; i < msp.size(); i++) {
               StagePosition sp = msp.get(i);
               if (sp.is1DStagePosition()) {
                  zStage_ = sp.getStageDeviceLabel();
               }
            }
            if (zStage_.equals("")) {
               throw new NoSuchStageException("No 1D stage found in the stage list");
            }

            if (x1 != 0.0 && x2 != 0.0 && x3 != 0.0
                    && y1 != 0.0 && y2 != 0.0 && y3 != 0.0
                    && z1 != 0.0 && z2 != 0.0 && z3 != 0.0) {
               A = y1 * (z2 - z3) + y2 * (z3 - z1) + y3 * (z1 - z2);
               B = z1 * (x2 - x3) + z2 * (x3 - x1) + z3 * (x1 - x2);
               C = x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2);
               D = -(x1 * (y2 * z3 - y3 * z2) + x2 * (y3 * z1 - y1 * z3) + x3 * (y1 * z2 - y2 * z1));
            }
            valid_ = true;

         } catch (NoSuchStageException nsse) {
            // we are in a bad state, but in the constructor, so not much we can do
            // except for letting this be an invalid AFPlane
         }
      }
   }

   public boolean isValid() {
      return valid_;
   }

   public double getZPos(double x, double y) {
      if (C == 0.0) {
         return 0.0;
      }

      double z = (-D - A * x - B * y) / C;
      return z;
   }
   
   public String getZStage() { return zStage_; }

   private double getX(MultiStagePosition msp) throws NoSuchStageException {
      // only works correctly if there is only 1 2Dstage
      // this should be ensured by the calling code
      for (int i = 0; i < msp.size(); i++) {
         StagePosition sp = msp.get(i);
         if (sp.is2DStagePosition()) {
            return sp.get2DPositionX();
         }
      }
      throw new NoSuchStageException("No XY Stage selected");
   }

   private double getY(MultiStagePosition msp) throws NoSuchStageException {
      // only works correctly if there is a single 2Dstage
      // this should be ensured by the calling code
      for (int i = 0; i < msp.size(); i++) {
         StagePosition sp = msp.get(i);
         if (sp.is2DStagePosition()) {
            return sp.get2DPositionY();
         }
      }
      throw new NoSuchStageException("No XY Stage selected");
   }

   private double getZ(MultiStagePosition msp) throws NoSuchStageException {
      // only works correctly if there is a single 1Dstage
      // this should be ensured by the calling code
      for (int i = 0; i < msp.size(); i++) {
         StagePosition sp = msp.get(i);
         if (sp.is1DStagePosition()) {
            return sp.get1DPosition();
         }
      }
      throw new NoSuchStageException("No Z Stage selected");
   }

}
