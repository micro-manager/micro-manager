///////////////////////////////////////////////////////////////////////////////
//FILE:          MMImage5DReaderPlugin_.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:        Nenad Amodaj, nenad@amodaj.com, November 2006

//COPYRIGHT:     University of California, San Francisco, 2006

//LICENSE:       This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

//CVS:           $Id$

import ij.IJ;
import ij.Prefs;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ImageStatistics;

import java.awt.Color;
import java.io.File;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.ProgressBar;
import org.micromanager.utils.ReportingUtils;

/**
 * ImageJ plugin to read Micro-Manager image5d file format.
 */
public class MMImage5DReaderPlugin_ implements PlugIn {
   static String MMImage5DReaderDirKey = "MMImage5DReader.Dir";
   static MMStudioMainFrame frame_;
   ProgressBar progressBar;

   public void run(String arg) {
      // 1.32c is needed for reading prefs through IJ, we might need even later
      if (IJ.versionLessThan("1.42n"))
         return;
      
      // TODO: use global look and feel set
      // the code below can hang within ImageJ

      // create and display control panel frame
//    try {
//    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//    } catch (ClassNotFoundException e) {
//    // TODO Auto-generated catch block
//    ReportingUtils.logError(e);
//    } catch (InstantiationException e) {
//    // TODO Auto-generated catch block
//    ReportingUtils.logError(e);
//    } catch (IllegalAccessException e) {
//    // TODO Auto-generated catch block
//    ReportingUtils.logError(e);
//    } catch (UnsupportedLookAndFeelException e) {
//    // TODO Auto-generated catch block
//    ReportingUtils.logError(e);
//    }

      // choose the directory
      // --------------------
/*
      System.setProperty("apple.laf.useScreenMenuBar", "true");
*/
      /*
      File f = FileDialogs.openDir(frame_,
              "Choose a Micro-Manager image data set",
              MMStudioMainFrame.MM_DATA_SET);

      String openAcqDirectory;
        if (f != null) {
               if (f.isDirectory()) {
            openAcqDirectory = f.getAbsolutePath();
         } else {
            openAcqDirectory = f.getParent();
         }
         Prefs.set(MMImage5DReaderDirKey, openAcqDirectory);

         AcquisitionData ad = new AcquisitionData();
         try {

            // attempt to open metafile
            ad.load(openAcqDirectory);

            // create image 5d
            Image5D img5d = new Image5D(openAcqDirectory, ad.getImageJType(), ad.getImageWidth(),
                  ad.getImageHeight(), ad.getNumberOfChannels(), ad.getNumberOfSlices(),
                  ad.getNumberOfFrames(), false);

            img5d.setCalibration(ad.ijCal());
            
            // display ProgressBar
            progressBar = new ProgressBar ("Opening File...",0,ad.getNumberOfChannels() * ad.getNumberOfFrames() * ad.getNumberOfSlices() );

            Color colors[] = ad.getChannelColors();
            String names[] = ad.getChannelNames();
            for (int i=0; i<ad.getNumberOfChannels(); i++) {

               ChannelCalibration chcal = new ChannelCalibration();
               // set channel name
               chcal.setLabel(names[i]);
               img5d.setChannelCalibration(i+1, chcal);

               // set color
               img5d.setChannelColorModel(i+1, ChannelDisplayProperties.createModelFromColor(colors[i]));            
            }

            // set pixels
            int singleImageCounter = 0; 
            for (int i=0; i<ad.getNumberOfFrames(); i++)
               for (int j=0; j<ad.getNumberOfChannels(); j++)
                  for (int k=0; k<ad.getNumberOfSlices(); k++) {
                     img5d.setCurrentPosition(0, 0, j, k, i);

                     // insert pixels into the 5d image
                     Object img = ad.getPixels(i, j, k);
                     if (img != null) {
                        img5d.setPixels(img);

                        // set display settings for channels
                        if (k==0 && i==0) {
                           DisplaySettings ds[] = ad.getChannelDisplaySettings();
                           if (ds != null) {
                              // display properties are recorded in metadata use them...
                              double min = ds[j].min;
                              double max = ds[j].max;
                              img5d.setChannelMinMax(j+1, min, max);
                           } else {
                              // ...if not, autoscale channels based on the first slice of the first frame
                              ImageStatistics stats = img5d.getStatistics(); // get uncalibrated stats
                              double min = stats.min;
                              double max = stats.max;
                              img5d.setChannelMinMax(j+1, min, max);
                           }
                        }
                     } else {
                        // gap detected, let's try to fill in by using the most recent channel data
                        // NOTE: we assume that the gap is only in the frame dimension
                        // we don't know how to deal with z-slice gaps !!!!
                        // TODO: handle the case with Z-position gaps
                        if (i>0) { 
                           Object previousImg = img5d.getPixels(j+1, k+1, i);
                           if (previousImg != null)
                              img5d.setPixels(previousImg, j+1, k+1, i + 1);
                        }                        
                     }
                     singleImageCounter+=1;
                     progressBar.setProgress(singleImageCounter);
                  }

            progressBar.setVisible(false);
            progressBar = null;
            // pop-up 5d image window
            Image5DWindow i5dWin = new Image5DWindow(img5d);
            if (ad.getNumberOfChannels() == 1) {
               int modee = ChannelControl.ONE_CHANNEL_COLOR;
               if( null != img5d){
                  if( img5d.getProcessor() instanceof ColorProcessor )
                     modee = ChannelControl.RGB;
               }
               img5d.setDisplayMode(modee);
             } else {
               img5d.setDisplayMode(ChannelControl.OVERLAY);
             }

            // i5dWin.setAcquitionEngine(engine_);
            i5dWin.setAcquisitionData(ad);
            i5dWin.setAcqSavePath(openAcqDirectory);
            img5d.changes = false;

         } catch (MMAcqDataException e) {
            ReportingUtils.showError(e);
            if (progressBar != null) 
            {
               progressBar.setVisible(false);
               progressBar = null;
            }
         }        
      }    */
   }
}
