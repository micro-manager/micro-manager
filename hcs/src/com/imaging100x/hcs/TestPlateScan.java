///////////////////////////////////////////////////////////////////////////////
//FILE:           TestPlateScan.java
//PROJECT:        Micro-Manager-S
//SUBSYSTEM:      high content screening
//-----------------------------------------------------------------------------

//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June 3, 2008

//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008

//LICENSE:        This file is distributed under the GPL license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

//CVS:            $Id: MetadataDlg.java 1275 2008-06-03 21:31:24Z nenad $

package com.imaging100x.hcs;

import java.awt.Color;

import mmcorej.CMMCore;

import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.WellAcquisitionData;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;


public class TestPlateScan {
   public static void main(String[] args) {
      String platePath = "c:/acquisitiondata/plate_test";
      String plateName = "96well_scan";
      String config = "MMConfig_demo_stream_proc_cal.cfg";

      // initialize core
      CMMCore mmc = new CMMCore();
      try {
         mmc.loadSystemConfiguration(config);

         // scanning parameters
         int numFrames = 1;
         int numSlices = 1;
         long intervalMs = 200;
         String[] channels = {"DAPI", "FITC", "Rhodamine"};
         Color[] colors = {Color.BLUE, Color.GREEN, Color.RED};
         int[] exposures = {100, 30, 110};
         String channelGroup = "Channel";

         SBSPlate plate = new SBSPlate();
         plate.initialize(SBSPlate.SBS_96_WELL);
         PositionList posList = plate.generateWellPositions(mmc.getXYStageDevice());
         System.out.println("Scanning plate with " + posList.getNumberOfPositions() + " wells.");

         // create plate data
         PlateAcquisitionData pad = new PlateAcquisitionData();
         pad.createNew(plateName, platePath, true);

         for (int k=0; k<posList.getNumberOfPositions(); k++) {
            String wellLabel = posList.getPosition(k).getLabel();
            MultiStagePosition.goToPosition(posList.getPosition(k), mmc);
            mmc.waitForDevice(mmc.getXYStageDevice());
            
            // create well data
            WellAcquisitionData wad = pad.createNewWell(wellLabel);
            
            // create site data
            AcquisitionData ad = wad.createNewImagingSite();
            ad.setDimensions(numFrames, channels.length, numSlices);
            
            // set parameters
            for (int i=0; i<colors.length; i++)
               ad.setChannelColor(i, colors[i].getRGB());
            for (int i=0; i<channels.length; i++)
               ad.setChannelName(i, channels[i]);

            System.out.println("Acquiring well: " + posList.getPosition(k).getLabel());

            for (int i=0; i<numFrames; i++) {
               long startMs = System.currentTimeMillis();
               for (int j=0; j<channels.length; j++) {
                  System.out.println("Acquiring channel " + channels[j] + ".");
                  mmc.setExposure(exposures[j]);
                  mmc.setConfig(channelGroup, channels[j]);
                  mmc.waitForConfig(channelGroup, channels[j]);
                  mmc.snapImage();
                  Object img = mmc.getImage();
                  if (i==0 && j==0) {
                     ad.setImagePhysicalDimensions((int)mmc.getImageWidth(), (int)mmc.getImageHeight(), (int)mmc.getBytesPerPixel());
                  }
                  ad.insertImage(img, i, j, 0);

               }
               if (i == 0)
                  ad.setChannelContrastBasedOnFrameAndSlice(i, 0);

               long itTook = System.currentTimeMillis() - startMs;
               long idle = intervalMs - itTook;
               if (idle > 0)
                  Thread.sleep(intervalMs - itTook);
            }
         }
      } catch (MMAcqDataException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }
}
