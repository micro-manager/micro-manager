package org.micromanager.acquisition.internal.acqengjcompat;

import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;

/**
 * Place to keep functions that can be shared between the various AcqEngJ adapters.
 */
public class AcqEngJUtils {

   /**
    * Determine whether the given PositionList includes positions for the Z drive.
    *
    * @param studio  the Studio
    * @param posList the PositionList to check
    * @return true if the PositionList includes Z drive positions
    */
   public static boolean posListHasZDrive(Studio studio, PositionList posList) {
      // assume that all positions contain the same drives
      if (posList == null || posList.getNumberOfPositions() == 0) {
         return false;
      }
      MultiStagePosition msp = posList.getPosition(0);
      for (int i = 0; i < msp.size(); i++) {
         StagePosition sp = msp.get(i);
         if (sp != null && sp.is1DStagePosition()) {
            String stageLabel = sp.getStageDeviceLabel();
            try {
               if (studio.core().getFocusDevice().equals(stageLabel)) {
                  return true;
               }
            } catch (Exception e) {
               studio.logs().logError(e);
            }
         }
      }
      return false;

   }


}
