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

import ij.gui.ImageWindow;
import ij.process.ColorProcessor;
import ij.process.ImageStatistics;
import java.awt.AWTEvent;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionListener;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JFrame;

import javax.swing.JTable;
import org.micromanager.api.ImageFocusListener;
import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.DisplaySettings;
import org.micromanager.metadata.MMAcqDataException;



public class GUIUtils {
   private static String DIALOG_POSITION = "dialogPosition";

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
		   if (JavaUtils.isWindows()) { // Check that we are in windows.
			   //Dynamically load sun.awt.Win32GraphicsEnvironment, because it seems to be missing from
			   //the Mac OS X JVM.
			   ClassLoader cl = ClassLoader.getSystemClassLoader ();
			   Class<?> envClass = cl.loadClass("sun.awt.Win32GraphicsEnvironment");
			   //Get the current local graphics environment.
			   GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			   //Send notification that display may have changed, so that display count is updated.
			   envClass.getDeclaredMethod("displayChanged").invoke(envClass.cast(ge));
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

   public static void recallPosition(final JFrame win) {
      Preferences prefs = Preferences.userNodeForPackage(win.getClass());
      Point dialogPosition = (Point) JavaUtils.getObjectFromPrefs(prefs, DIALOG_POSITION, null);
      if (dialogPosition == null) {
         Dimension screenDims = JavaUtils.getScreenDimensions();
         dialogPosition = new Point((screenDims.width - win.getWidth()) / 2, (screenDims.height - win.getHeight()) / 2);
      }
      win.setLocation(dialogPosition);

      win.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            storePosition(win);
         }
      });
   }

   private static void storePosition(JFrame win) {
      Preferences prefs = Preferences.userNodeForPackage(win.getClass());
      JavaUtils.putObjectInPrefs(prefs, DIALOG_POSITION, win.getLocation());
   }

   public static void registerImageFocusListener(final ImageFocusListener listener) {
      AWTEventListener awtEventListener = new AWTEventListener() {
         public void eventDispatched(AWTEvent event) {
            if (event instanceof WindowEvent) {
               if (0 != (event.getID() & WindowEvent.WINDOW_GAINED_FOCUS)) {
                  if (event.getSource() instanceof ImageWindow) {
                     ImageWindow focusedWindow = (ImageWindow) event.getSource();
                     if (focusedWindow.isVisible()) {
                        listener.focusReceived(focusedWindow);
                     }
                  }
               }
            }
         }
      };

      Toolkit.getDefaultToolkit().addAWTEventListener(awtEventListener,
              AWTEvent.WINDOW_FOCUS_EVENT_MASK);
   }
}
