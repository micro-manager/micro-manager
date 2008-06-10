package com.imaging100x.hcs;

import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;

public class TestSBSPlate {

   /**
    * @param args
    */
   public static void main(String[] args) {

      SBSPlate plate = new SBSPlate();
      plate.initialize(SBSPlate.SBS_96_WELL);
      PositionList posList = plate.generateWellPositions();
      System.out.println("Created plate with " + posList.getNumberOfPositions() + " wells.");
      for (int i=0; i<posList.getNumberOfPositions(); i++) {
         MultiStagePosition mps = posList.getPosition(i);
         System.out.println("Well " + mps.getLabel() + " : X=" + mps.getX() + ", Y=" + mps.getY());
      }
   }

}
