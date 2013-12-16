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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.micromanager.asidispim.Data.SpimParams;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.Labels;
import java.util.prefs.Preferences;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Utils.ListeningJTabbedPane;

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
      
      final ListeningJTabbedPane tabbedPane = new ListeningJTabbedPane();
        
      // all added tabs must be of type ListeningJPanel
      // only use addLTab, not addTab to guarantee this

      tabbedPane.addLTab("Devices", new DevicesPanel(gui_, devices_));
      tabbedPane.addLTab("SPIM Params", new SpimParamsPanel(spimParams_, devices_));
      final ListeningJPanel setupPanelA = new SetupPanel(
              gui_, devices_, Labels.Sides.A);
      tabbedPane.addLTab("Setup Side A",  setupPanelA);
      final ListeningJPanel setupPanelB = new SetupPanel(
              gui_, devices_, Labels.Sides.B);
      tabbedPane.addLTab("Setup Side B",  setupPanelB);
      final ListeningJPanel navigationPanel = new NavigationPanel(devices_);
      tabbedPane.addLTab("Navigate", navigationPanel);
      
      tabbedPane.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent e) {
            ((ListeningJPanel) tabbedPane.getSelectedComponent()).gotSelected();
         }
      });
            
      add(tabbedPane);
         
      setLocation(prefs_.getInt(XLOC, 100), prefs_.getInt(YLOC, 100));
      tabbedPane.setSelectedIndex(prefs_.getInt(TABINDEX, 0));
      
      final Timer stagePosUpdater = new Timer(100000000, new ActionListener() {  // was 1000
         public void actionPerformed(ActionEvent ae) {
            // update stage positions in devices
            devices_.updateStagePositions();
            // notify listeners that positions are updated
            setupPanelA.updateStagePositions();
            setupPanelB.updateStagePositions();
            navigationPanel.updateStagePositions();
         }
      });
      stagePosUpdater.start();
      
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            stagePosUpdater.stop();
            devices_.saveSettings();

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
