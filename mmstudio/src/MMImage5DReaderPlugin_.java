///////////////////////////////////////////////////////////////////////////////
// FILE:          MMImage5DReaderPlugin_.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, November 2006
//
// COPYRIGHT:     University of California, San Francisco, 2006
//
// LICENSE:       This file is distributed under the BSD license.
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
// CVS:           $Id$

import ij.IJ;
import ij.plugin.PlugIn;
import ij.process.ImageStatistics;

import java.awt.Color;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.DisplaySettings;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.utils.ProgressBar;

/**
 * ImageJ plugin to read Micro-Manager image5d file format.
 */
public class MMImage5DReaderPlugin_ implements PlugIn {
   static MMStudioMainFrame frame_;
   ProgressBar progressBar;

   public void run(String arg) {
      // TODO: use global look and feel set
      // the code below can hang within ImageJ
      
      // create and display control panel frame
//      try {
//         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//      } catch (ClassNotFoundException e) {
//         // TODO Auto-generated catch block
//         e.printStackTrace();
//      } catch (InstantiationException e) {
//         // TODO Auto-generated catch block
//         e.printStackTrace();
//      } catch (IllegalAccessException e) {
//         // TODO Auto-generated catch block
//         e.printStackTrace();
//      } catch (UnsupportedLookAndFeelException e) {
//         // TODO Auto-generated catch block
//         e.printStackTrace();
//      }
      
      // choose the directory
      // --------------------

      JFileChooser fc = new JFileChooser();
      fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
      String openAcqDirectory = new String("");
      fc.setSelectedFile(new File(openAcqDirectory));
      int retVal = fc.showOpenDialog(IJ.getInstance().getOwner());
      if (retVal == JFileChooser.APPROVE_OPTION) {
         File f = fc.getSelectedFile();
         if (f.isDirectory()) {
            openAcqDirectory = f.getAbsolutePath();
         } else {
            openAcqDirectory = f.getParent();
         }

         AcquisitionData ad = new AcquisitionData();
         try {

            // attempt to open metafile
            ad.load(openAcqDirectory);

            // create image 5d
            Image5D img5d = new Image5D(openAcqDirectory, ad.getImageJType(), ad.getImageWidth(),
                  ad.getImageHeight(), ad.getNumberOfChannels(), ad.getNumberOfSlices(),
                  ad.getNumberOfFrames(), false);

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
                     // read the file

                     // insert pixels into the 5d image
                     img5d.setPixels(ad.getPixels(i, j, k));

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
                     singleImageCounter+=1;
                     progressBar.setProgress(singleImageCounter);
                  }

            progressBar.setVisible(false);
            progressBar = null;
            // pop-up 5d image window
            Image5DWindow i5dWin = new Image5DWindow(img5d);
            if (ad.getNumberOfChannels()==1)
               img5d.setDisplayMode(ChannelControl.ONE_CHANNEL_COLOR);
            else
               img5d.setDisplayMode(ChannelControl.OVERLAY);

            // i5dWin.setAcquitionEngine(engine_);
            i5dWin.setMetadata(ad.getMetadata());
            img5d.changes = false;

         } catch (MMAcqDataException e) {
            JOptionPane.showMessageDialog(IJ.getInstance().getOwner(), e.getMessage());
            progressBar.setVisible(false);
            progressBar = null;
         }        
      }     
   }
}
