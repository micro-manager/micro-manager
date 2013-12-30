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

import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Data.Setup;
import org.micromanager.asidispim.Data.SpimParams;
import org.micromanager.asidispim.Data.Operation;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.Labels;

import java.util.prefs.Preferences;

import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.micromanager.MMStudioMainFrame;

import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Utils.ListeningJTabbedPane;
import org.micromanager.internalinterfaces.LiveModeListener;

/**
 *
 * @author nico
 */
@SuppressWarnings("serial")
public class ASIdiSPIMFrame extends javax.swing.JFrame  
      implements MMListenerInterface {
   
   public static Properties props_;  // using like global variable so I don't have to pass object all the way down to event handlers
   
   private ScriptInterface gui_;
   private Preferences prefs_;
   private Devices devices_;
   private SpimParams spimParams_;
   private Operation oper_;
   private Setup setup_;
   
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
      props_ = new Properties(gui_, devices_);  // doesn't have its own frame, but is an object used by other classes
      spimParams_ = new SpimParams();
      devices_.addListener(spimParams_);
      oper_ = new Operation();
      setup_ = new Setup();  // this data is shared between the instances of SetupPanel
      
      final ListeningJTabbedPane tabbedPane = new ListeningJTabbedPane();
        
      // all added tabs must be of type ListeningJPanel
      // only use addLTab, not addTab to guarantee this
      tabbedPane.addLTab("Devices", new DevicesPanel(gui_, devices_));
      tabbedPane.addLTab("SPIM Params", new SpimParamsPanel(spimParams_, devices_));
      final ListeningJPanel setupPanelA = new SetupPanel(
              setup_, gui_, devices_, Labels.Sides.A);
      tabbedPane.addLTab("Setup Side A",  setupPanelA);
      MMStudioMainFrame.getInstance().addLiveModeListener((LiveModeListener) setupPanelA);
      final ListeningJPanel setupPanelB = new SetupPanel(
              setup_, gui_, devices_, Labels.Sides.B);
      tabbedPane.addLTab("Setup Side B",  setupPanelB);      
      MMStudioMainFrame.getInstance().addLiveModeListener((LiveModeListener) setupPanelB);
      final ListeningJPanel navigationPanel = new NavigationPanel(devices_);
      tabbedPane.addLTab("Navigate", navigationPanel);
      final ListeningJPanel operationPanel = new OperationPanel(oper_, devices_);
      tabbedPane.addLTab("Operate", operationPanel);
      
      tabbedPane.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent e) {
            ((ListeningJPanel) tabbedPane.getSelectedComponent()).gotSelected();
         }
      });
            
      add(tabbedPane);
         
      setLocation(prefs_.getInt(XLOC, 100), prefs_.getInt(YLOC, 100));
      tabbedPane.setSelectedIndex(prefs_.getInt(TABINDEX, 0));
      
      final Timer stagePosUpdater = new Timer(1000, new ActionListener() { 
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
