///////////////////////////////////////////////////////////////////////////////
//FILE:           TestPlateAcquisitionData.java
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

package com.imaging100x.hcs;

import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.WellAcquisitionData;


public class TestPlateAcquisitionData {
   public static void main(String[] args) {
      String platePath = "c:/acquisitiondata/plate_test/96well_scan_1";
      
      PlateAcquisitionData pad = new PlateAcquisitionData();
      try {
         pad.load(platePath);
         System.out.println("Plate " + pad.getName() + " loaded.");
         WellAcquisitionData[] wells = pad.getWells();
         System.out.println("Plate contains " + wells.length + " wells.");
         // dump one well
         WellAcquisitionData well = wells[0];
         AcquisitionData ad[] = well.getImagingSites();
         for (int j=0; j<ad.length; j++) {
            System.out.println("Well: " + wells[0].getLabel() + ", site " + ad[j].getName() + ": channels " + ad[j].getNumberOfChannels());
         }
      } catch (MMAcqDataException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

}
