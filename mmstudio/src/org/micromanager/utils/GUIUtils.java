///////////////////////////////////////////////////////////////////////////////
//FILE:          GUIUtils.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, 2005
//
// COPYRIGHT:    University of California San Francisco, 2005
//               100X Imaging Inc, 2009
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.utils;

import ij.process.ImageStatistics;

import java.awt.Color;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionListener;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;

import javax.swing.JTable;
import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.DisplaySettings;
import org.micromanager.metadata.MMAcqDataException;



public class GUIUtils {
   public static void setComboSelection(JComboBox cb, String sel){
      ActionListener[] listeners = cb.getActionListeners();
      for (int i=0; i<listeners.length; i++)            
         cb.removeActionListener(listeners[i]);
      cb.setSelectedItem(sel);
      for (int i=0; i<listeners.length; i++)            
         cb.addActionListener(listeners[i]);
   }

   public static void replaceComboContents(JComboBox cb, String[] items) {
      
      // remove listeners
      ActionListener[] listeners = cb.getActionListeners();
      for (int i=0; i<listeners.length; i++)            
         cb.removeActionListener(listeners[i]);

      if (cb.getItemCount() > 0)
         cb.removeAllItems();
      
      // add contents
      for (int i=0; i<items.length; i++){
         cb.addItem(items[i]);
      }
      
      // restore listeners
      for (int i=0; i<listeners.length; i++)            
         cb.addActionListener(listeners[i]);
   }
   
   /**
    * Automatically create 5D image window to display acquisition data
    * @param title - window title
    * @param ad - acquisition data to display
    */
   public static void createImage5DWin(String title, AcquisitionData ad) {
      try {

         // create image 5d
         Image5D img5d = new Image5D(title, ad.getImageJType(), ad.getImageWidth(),
               ad.getImageHeight(), ad.getNumberOfChannels(), ad.getNumberOfSlices(),
               ad.getNumberOfFrames(), false);

         img5d.setCalibration(ad.ijCal());

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
               }

         // pop-up 5d image window
         Image5DWindow i5dWin = new Image5DWindow(img5d);
         if (ad.getNumberOfChannels()==1)
            img5d.setDisplayMode(ChannelControl.ONE_CHANNEL_COLOR);
         else
            img5d.setDisplayMode(ChannelControl.OVERLAY);

         i5dWin.setAcquisitionData(ad);
         img5d.changes = false;

      } catch (MMAcqDataException e) {
         ReportingUtils.showError(e);
      }        
   }     

   /* 
    * This takes care of a Java bug that would throw several exceptions when a Projector device 
    * is attached in Windows.
    */
   public static void preventDisplayAdapterChangeExceptions() {
	   try {
		   if (System.getProperty("os.name").contains("Windows")) { // Check that we are in windows.
			   //Dynamically load sun.awt.Win32GraphicsEnvironment, because it seems to be missing from
			   //the Mac OS X JVM.
			   ClassLoader cl = ClassLoader.getSystemClassLoader ();
			   Class<?> envClass = cl.loadClass("sun.awt.Win32GraphicsEnvironment");
			   //Get the current local graphics environment.
			   GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			   //Send notification that display may have changed, so that display count is updated.
			   envClass.getDeclaredMethod("displayChanged").invoke(envClass.cast(ge));
			   ReportingUtils.logMessage("preventDisplayAdapterChangeExceptions() called.");
		   }
	   } catch (Exception e) {
           ReportingUtils.logError(e);
	   }

   }

   public static void setClickCountToStartEditing(JTable table, int count) {
       ((DefaultCellEditor) table.getDefaultEditor(String.class)).setClickCountToStart(count);
   }

    public static void stopEditingOnLosingFocus(final JTable table) {
        table.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                Component focused = e.getOppositeComponent();
                if (table!=focused && !table.isAncestorOf(focused))
                    table.getDefaultEditor(String.class).stopCellEditing();
            }
        });
    }
}
