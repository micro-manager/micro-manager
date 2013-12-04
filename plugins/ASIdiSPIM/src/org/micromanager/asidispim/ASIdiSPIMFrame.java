///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIMFrame.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim;

import java.util.prefs.Preferences;
import javax.swing.JTabbedPane;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author nico
 */
public class ASIdiSPIMFrame extends javax.swing.JFrame  
      implements MMListenerInterface {
   
   private ScriptInterface gui_;
   private Preferences prefs_;
   private Devices devices_;
   private SpimParams spimParams_;
   
   private static final String XLOC = "xloc";
   private static final String YLOC = "yloc";
   private static final String TABINDEX = "tabIndex";
   
   /**
    * Creates the ASIdiSPIM plugin frame
    * @param gui - Micro-Manager script interface
    */
   public ASIdiSPIMFrame(ScriptInterface gui)  {
      gui_ = gui;
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      devices_ = new Devices();
      spimParams_ = new SpimParams(gui_, devices_);
      devices_.addListener(spimParams_);
      
      final JTabbedPane tabbedPane = new JTabbedPane();
   
      tabbedPane.addTab("Devices", new DevicesPanel(gui_, devices_));
      tabbedPane.addTab("SPIM Params", new SpimParamsPanel(spimParams_, devices_));
      tabbedPane.addTab("Select Region-A", new RegionPanel(
              gui_, devices_, spimParams_, RegionPanel.Sides.A) );
      tabbedPane.addTab("Select Region-B", new RegionPanel(
              gui_, devices_, spimParams_, RegionPanel.Sides.B) );
            
      add(tabbedPane);
         
      setLocation(prefs_.getInt(XLOC, 100), prefs_.getInt(YLOC, 100));
      tabbedPane.setSelectedIndex(prefs_.getInt(TABINDEX, 0));
      
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            devices_.saveSettings();
            spimParams_.saveSettings();
            prefs_.putInt(XLOC, evt.getWindow().getX());
            prefs_.putInt(YLOC, evt.getWindow().getY());
            prefs_.putInt(TABINDEX, tabbedPane.getSelectedIndex());
         }
      });
      
      setTitle("ASI diSPIM Control");
      
      pack();
      
      setResizable(false);
          
   }
    
   

   // MMListener mandated member functions
   public void propertiesChangedAlert() {
      }

   public void propertyChangedAlert(String device, String property, String value) {
         }

   public void configGroupChangedAlert(String groupName, String newConfig) {
         }

   public void systemConfigurationLoaded() {
         }

   public void pixelSizeChangedAlert(double newPixelSizeUm) {
         }

   public void stagePositionChangedAlert(String deviceName, double pos) {
        }

   public void xyStagePositionChanged(String deviceName, double xPos, double yPos) {
         }

   public void exposureChanged(String cameraName, double newExposureTime) {
         }

}
