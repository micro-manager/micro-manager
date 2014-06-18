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

import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.ListeningJTabbedPane;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.prefs.Preferences;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;
import org.micromanager.MMStudioMainFrame; 
import org.micromanager.asidispim.Utils.StagePositionUpdater;
import org.micromanager.internalinterfaces.LiveModeListener; 

// TODO make sure acquisition works for single SPIM
// TODO display center position in setup panel
// TODO support for laser shutters (update device panel too?)
// TODO display certain properties like positions, e.g. scan amplitudes/offsets
// TODO resolve whether Home/Stop should be added to 1axis stage API, use here if possible
// TODO add sethome property to device adapter and use it here


/**
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class ASIdiSPIMFrame extends javax.swing.JFrame  
      implements MMListenerInterface {
   
   private final Properties props_; 
   private Prefs prefs_;
   private Devices devices_;
   private final Joystick joystick_;
   private final Positions positions_;
   private final Cameras cameras_;
   
   private final DevicesPanel devicesPanel_;
   private final AcquisitionPanel acquisitionPanel_;
   private final SetupPanel setupPanelA_;
   private final SetupPanel setupPanelB_;
   private final NavigationPanel navigationPanel_;
   private final GuiSettingsPanel guiSettingsPanel_;
   private final DataAnalysisPanel dataAnalysisPanel_;
   private final HelpPanel helpPanel_;
   private final StagePositionUpdater stagePosUpdater_;
   
   private static final String MAIN_PREF_NODE = "Main"; 
   
   /**
    * Creates the ASIdiSPIM plugin frame
    * @param gui - Micro-Manager script interface
    */
   public ASIdiSPIMFrame(ScriptInterface gui)  {

      // create interface objects used by panels
      prefs_ = new Prefs(Preferences.userNodeForPackage(this.getClass()));
      devices_ = new Devices(gui, prefs_);
      props_ = new Properties(gui, devices_);
      positions_ = new Positions(gui, devices_);
      joystick_ = new Joystick(gui, devices_, props_);
      cameras_ = new Cameras(gui, devices_, props_);
      
      // create the panels themselves
      // in some cases dependencies create required ordering
      devicesPanel_ = new DevicesPanel(gui, devices_, props_);
      setupPanelA_ = new SetupPanel(gui, devices_, props_, joystick_, 
            Devices.Sides.A, positions_, cameras_, prefs_);
      setupPanelB_ = new SetupPanel(gui, devices_, props_, joystick_,
            Devices.Sides.B, positions_, cameras_, prefs_);
      // get initial positions, even if user doesn't want continual refresh
      stagePosUpdater_ = new StagePositionUpdater(gui, positions_, props_);  // needed for setup and navigation
      acquisitionPanel_ = new AcquisitionPanel(gui, devices_, props_, cameras_, 
              prefs_, stagePosUpdater_);
      guiSettingsPanel_ = new GuiSettingsPanel(gui, devices_, props_, prefs_, stagePosUpdater_);
      dataAnalysisPanel_ = new DataAnalysisPanel(gui, prefs_);
      stagePosUpdater_.oneTimeUpdate();  // needed for NavigationPanel
      navigationPanel_ = new NavigationPanel(gui, devices_, props_, joystick_,
            positions_, prefs_, cameras_);
      helpPanel_ = new HelpPanel(gui);
      
      // now add tabs to GUI
      // all added tabs must be of type ListeningJPanel
      // only use addLTab, not addTab to guarantee this
      final ListeningJTabbedPane tabbedPane = new ListeningJTabbedPane();
      tabbedPane.addLTab(devicesPanel_);
      tabbedPane.addLTab(acquisitionPanel_);
      tabbedPane.addLTab(setupPanelA_);
      tabbedPane.addLTab(setupPanelB_);
      tabbedPane.addLTab(navigationPanel_);
      tabbedPane.addLTab(guiSettingsPanel_);
      tabbedPane.addLTab(dataAnalysisPanel_);
      tabbedPane.addLTab(helpPanel_);

      // attach position updaters
      stagePosUpdater_.addPanel(setupPanelA_);
      stagePosUpdater_.addPanel(setupPanelB_);
      stagePosUpdater_.addPanel(navigationPanel_);

      // attach live mode listeners
      MMStudioMainFrame.getInstance().addLiveModeListener((LiveModeListener) setupPanelB_);
      MMStudioMainFrame.getInstance().addLiveModeListener((LiveModeListener) setupPanelA_);
      MMStudioMainFrame.getInstance().addLiveModeListener((LiveModeListener) navigationPanel_);
      
      // make sure gotSelected() gets called whenever we switch tabs
      tabbedPane.addChangeListener(new ChangeListener() {
         int lastSelectedIndex_ = tabbedPane.getSelectedIndex();
         @Override
         public void stateChanged(ChangeEvent e) {
            ((ListeningJPanel) tabbedPane.getComponentAt(lastSelectedIndex_)).gotDeSelected();
            ((ListeningJPanel) tabbedPane.getSelectedComponent()).gotSelected();
            lastSelectedIndex_ = tabbedPane.getSelectedIndex();
         }
      });
      
      // make sure plugin window is on the screen (if screen size changes)
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      if (screenSize.width < prefs_.getInt(MAIN_PREF_NODE, Prefs.Keys.WIN_LOC_X, 0)) {
         prefs_.putInt(MAIN_PREF_NODE, Prefs.Keys.WIN_LOC_X, 100);
      }
      if (screenSize.height < prefs_.getInt(MAIN_PREF_NODE, Prefs.Keys.WIN_LOC_Y, 0)) {
         prefs_.putInt(MAIN_PREF_NODE, Prefs.Keys.WIN_LOC_Y, 100);
      }
    
      // put pane back where it was last time
      // gotSelected will be called because we put this after adding the ChangeListener
      setLocation(prefs_.getInt(MAIN_PREF_NODE, Prefs.Keys.WIN_LOC_X, 100), prefs_.getInt(MAIN_PREF_NODE, Prefs.Keys.WIN_LOC_Y, 100));
      tabbedPane.setSelectedIndex(prefs_.getInt(MAIN_PREF_NODE, Prefs.Keys.TAB_INDEX, 0));

      // set up the window
      add(tabbedPane);  // add the pane to the GUI window
      setTitle("ASI diSPIM Control"); 
      pack();           // shrinks the window as much as it can
      setResizable(false);
      
      // take care of shutdown tasks when window is closed
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            // stop the timer for updating stages
            stagePosUpdater_.stop();

            // save selections as needed
            devices_.saveSettings();
            setupPanelA_.saveSettings();
            setupPanelB_.saveSettings();
            navigationPanel_.saveSettings();
            acquisitionPanel_.saveSettings();
            guiSettingsPanel_.saveSettings();

            // save pane location in prefs
            prefs_.putInt(MAIN_PREF_NODE, Prefs.Keys.WIN_LOC_X, evt.getWindow().getX());
            prefs_.putInt(MAIN_PREF_NODE, Prefs.Keys.WIN_LOC_Y, evt.getWindow().getY());
            prefs_.putInt(MAIN_PREF_NODE, Prefs.Keys.TAB_INDEX, tabbedPane.getSelectedIndex());
         }
      });
      
   }
  

   // MMListener mandated member functions
   @Override
   public void propertiesChangedAlert() {
      // doesn't seem to actually be called by core when property changes
     // props_.callListeners();
   }

   @Override
   public void propertyChangedAlert(String device, String property, String value) {
     // props_.callListeners();
   }

   @Override
   public void configGroupChangedAlert(String groupName, String newConfig) {
   }

   @Override
   public void systemConfigurationLoaded() {
   }

   @Override
   public void pixelSizeChangedAlert(double newPixelSizeUm) {
   }

   @Override
   public void stagePositionChangedAlert(String deviceName, double pos) {
   }

   @Override
   public void xyStagePositionChanged(String deviceName, double xPos, double yPos) {
   }

   @Override
   public void exposureChanged(String cameraName, double newExposureTime) {
   }
   
   @Override
   public void slmExposureChanged(String cameraName, double newExposureTime) {
   }
}
