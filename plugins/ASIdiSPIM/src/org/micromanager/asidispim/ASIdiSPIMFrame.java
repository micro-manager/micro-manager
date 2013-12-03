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

import java.awt.GridLayout;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
   
   /**
    * Creates the ASIdiSPIM plugin frame
    * @param gui - Micro-Manager script interface
    */
   public ASIdiSPIMFrame(ScriptInterface gui)  {
      gui_ = gui;
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      devices_ = new Devices();
      spimParams_ = new SpimParams();
      
      
      JTabbedPane tabbedPane = new JTabbedPane();
   
      tabbedPane.addTab("Devices", new DevicesPanel(gui_, devices_));
      tabbedPane.addTab("SPIM Params", new SpimParamsPanel(spimParams_));
      
      add(tabbedPane);
      
     
      setLocation(prefs_.getInt(XLOC, 100), prefs_.getInt(YLOC, 100));
      
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            devices_.saveSettings();
            spimParams_.saveSettings();
            prefs_.putInt(XLOC, evt.getWindow().getX());
            prefs_.putInt(YLOC, evt.getWindow().getY());
         }
      });
      
      setTitle("ASI diSPIM Control");
      
      pack();
      
      setResizable(false);
          
   }
   
    
   private JComponent makeTextPanel(String text) {
      JPanel panel = new JPanel(false);
      JLabel filler = new JLabel(text);
      filler.setHorizontalAlignment(JLabel.CENTER);
      panel.setLayout(new GridLayout(1, 1));
      panel.add(filler);
      return panel;
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
