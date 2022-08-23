///////////////////////////////////////////////////////////////////////////////
//FILE:           TestSBSPlate.java
//PROJECT:        Micro-Manager-S
//SUBSYSTEM:      high content screening
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June 3, 2008
//
//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008
//                
//LICENSE:        This file is distributed under the GPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//CVS:            $Id: MetadataDlg.java 1275 2008-06-03 21:31:24Z nenad $

package org.micromanager.hcs;

import org.micromanager.MultiStagePosition;

public class TestSBSPlate {

   public static void main(String[] args) {

      SBSPlate plate = new SBSPlate();
      plate.initialize(SBSPlate.SBS_96_WELL);
      WellPositionList wpl[] = plate.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME);
      System.out.println("Created plate with " + wpl.length + " wells.");
      for (WellPositionList wpl1 : wpl) {
         for (int j = 0; j < wpl1.getSitePositions().getNumberOfPositions(); j++) {
            MultiStagePosition mps = wpl1.getSitePositions().getPosition(j);
            System.out.println("Well " + mps.getLabel() + " : X=" + mps.getX() + ", Y=" + mps.getY());
         }
      }
   }

}
