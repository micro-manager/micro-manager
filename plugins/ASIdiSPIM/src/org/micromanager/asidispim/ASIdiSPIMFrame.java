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

import net.miginfocom.swing.MigLayout;

import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.ListeningJTabbedPane;

import java.awt.Container;
import java.util.prefs.Preferences;

import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;
import org.micromanager.MMStudio;
import org.micromanager.asidispim.Utils.StagePositionUpdater;
import org.micromanager.internalinterfaces.LiveModeListener; 
import org.micromanager.utils.MMFrame;

// TODO easy mode that pulls most-used bits from all panels
// TODO "neutral position" indicator
// TODO autofocus for finding calibration endpoints and also offset (slope doesn't change but offset can) 
// TODO camera control ROI panel
// TODO track Z/F for sample finding
// TODO integrate with MDA acquisition?
// TODO finish eliminating Prefs.Keys in favor of Properties.Keys with plugin values
// TODO save/load plugin settings from file instead of from registry (nice to also include controller settings)
// TODO handle camera binning
// TODO add check for correct Hamamatsu model
// TODO support for laser shutters (update device panel too?)
// TODO display certain properties like positions, e.g. scan amplitudes/offsets
// TODO resolve whether Home/Stop should be added to 1axis stage API, use here if possible
// TODO add sethome property to device adapter and use it here
// TODO automatically find scanner/pizeo focus (http://dx.doi.org/10.1364/OE.16.008670)


/**
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class ASIdiSPIMFrame extends MMFrame  
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
   private final SettingsPanel settingsPanel_;
   private final DataAnalysisPanel dataAnalysisPanel_;
   private final HelpPanel helpPanel_;
   private final StatusSubPanel statusSubPanel_;
   private final StagePositionUpdater stagePosUpdater_;
   private final ListeningJTabbedPane tabbedPane_;
   
   private static final String MAIN_PREF_NODE = "Main"; 
   
   /**
    * Creates the ASIdiSPIM plugin frame
    * @param gui - Micro-Manager script interface
    */
   public ASIdiSPIMFrame(ScriptInterface gui)  {

      // create interface objects used by panels
      prefs_ = new Prefs(Preferences.userNodeForPackage(this.getClass()));
      devices_ = new Devices(gui, prefs_);
      props_ = new Properties(gui, devices_, prefs_);
      positions_ = new Positions(gui, devices_);
      joystick_ = new Joystick(devices_, props_);
      cameras_ = new Cameras(gui, devices_, props_, prefs_);
      
      // create the panels themselves
      // in some cases dependencies create required ordering
      devicesPanel_ = new DevicesPanel(gui, devices_, props_);
      setupPanelA_ = new SetupPanel(gui, devices_, props_, joystick_, 
            Devices.Sides.A, positions_, cameras_, prefs_);
      setupPanelB_ = new SetupPanel(gui, devices_, props_, joystick_,
            Devices.Sides.B, positions_, cameras_, prefs_);
      // get initial positions, even if user doesn't want continual refresh
      stagePosUpdater_ = new StagePositionUpdater(positions_, props_);  // needed for setup and navigation
      navigationPanel_ = new NavigationPanel(gui, devices_, props_, joystick_,
            positions_, prefs_, cameras_);
      acquisitionPanel_ = new AcquisitionPanel(gui, devices_, props_, joystick_, 
            cameras_, prefs_, stagePosUpdater_, positions_);
      dataAnalysisPanel_ = new DataAnalysisPanel(gui, prefs_);
      settingsPanel_ = new SettingsPanel(devices_, props_, prefs_, stagePosUpdater_);
      stagePosUpdater_.oneTimeUpdate();  // needed for NavigationPanel
      helpPanel_ = new HelpPanel();
      statusSubPanel_ = new StatusSubPanel(devices_, props_, positions_, stagePosUpdater_);
      
      // now add tabs to GUI
      // all added tabs must be of type ListeningJPanel
      // only use addLTab, not addTab to guarantee this
      tabbedPane_ = new ListeningJTabbedPane();
      tabbedPane_.setTabPlacement(JTabbedPane.LEFT);
      tabbedPane_.addLTab(navigationPanel_);  // tabIndex = 0
      tabbedPane_.addLTab(setupPanelA_);      // tabIndex = 1
      tabbedPane_.addLTab(setupPanelB_);      // tabIndex = 2
      tabbedPane_.addLTab(acquisitionPanel_); // tabIndex = 3
      tabbedPane_.addLTab(dataAnalysisPanel_);// tabIndex = 4
      tabbedPane_.addLTab(devicesPanel_);     // tabIndex = 5
      tabbedPane_.addLTab(settingsPanel_);    // tabIndex = 6
      tabbedPane_.addLTab(helpPanel_);        // tabIndex = 7

      // attach position updaters
      stagePosUpdater_.addPanel(setupPanelA_);
      stagePosUpdater_.addPanel(setupPanelB_);
      stagePosUpdater_.addPanel(navigationPanel_);
      stagePosUpdater_.addPanel(statusSubPanel_);

      // attach live mode listeners
      MMStudio.getInstance().getSnapLiveManager().addLiveModeListener((LiveModeListener) setupPanelB_);
      MMStudio.getInstance().getSnapLiveManager().addLiveModeListener((LiveModeListener) setupPanelA_);
      MMStudio.getInstance().getSnapLiveManager().addLiveModeListener((LiveModeListener) navigationPanel_);
      
      // make sure gotSelected() gets called whenever we switch tabs
      tabbedPane_.addChangeListener(new ChangeListener() {
         int lastSelectedIndex_ = tabbedPane_.getSelectedIndex();
         @Override
         public void stateChanged(ChangeEvent e) {
            ((ListeningJPanel) tabbedPane_.getComponentAt(lastSelectedIndex_)).gotDeSelected();
            ((ListeningJPanel) tabbedPane_.getSelectedComponent()).gotSelected();
            lastSelectedIndex_ = tabbedPane_.getSelectedIndex();
         }
      });
      
      // put dialog back where it was last time
      this.loadAndRestorePosition(100, 100, WIDTH, WIDTH);
    
      // gotSelected will be called because we put this after adding the ChangeListener
      tabbedPane_.setSelectedIndex(7);  // setSelectedIndex(0) just after initialization doesn't fire ChangeListener, so switch to help panel first
      tabbedPane_.setSelectedIndex(prefs_.getInt(MAIN_PREF_NODE, Prefs.Keys.TAB_INDEX, 5));  // default to devicesPanel_ on first run

      // set up the window
      add(tabbedPane_);  // add the pane to the GUI window
      setTitle("ASI diSPIM Control"); 
      pack();           // shrinks the window as much as it can
      setResizable(false);
      
      // take care of shutdown tasks when window is closed
      // TODO figure out if we really need this with dispose method below
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            // stop the timer for updating stages
            stagePosUpdater_.stop();
            saveSettings();
         }
      });
      
      
      // add status panel as an overlay that is visible from all tabs
      Container glassPane = (Container) getGlassPane();
      glassPane.setVisible(true);
      glassPane.setLayout(new MigLayout(
            "",
            "[" + this.getWidth() + "]",
            "[" + this.getHeight() + "]"));
      glassPane.add(statusSubPanel_, "dock south");
      
   }
   
   /**
    * This accessor function should really only be used by the ScriptInterface
    * Do not get into the internals of this plugin without relying on
    * ASIdiSPIM.api
    * @return 
    */
   public AcquisitionPanel getAcquisitionPanel() {
      return acquisitionPanel_;
   }
   
   /**
    * For use of acquisition panel code (getting joystick settings)
    * Do not get into the internals of this plugin without relying on
    * ASIdiSPIM.api
    * @return 
    */
   public NavigationPanel getNavigationPanel() {
      return navigationPanel_;
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
   
   private void saveSettings() {
      // save selections as needed
      devices_.saveSettings();
      setupPanelA_.saveSettings();
      setupPanelB_.saveSettings();
      navigationPanel_.saveSettings();
      acquisitionPanel_.saveSettings();
      settingsPanel_.saveSettings();

      // save tab location in prefs (dialog location now handled by MMDialog)
      prefs_.putInt(MAIN_PREF_NODE, Prefs.Keys.TAB_INDEX, tabbedPane_.getSelectedIndex());
   }
   
   @Override
   public void dispose() {
      if (stagePosUpdater_ != null) {
         stagePosUpdater_.stop();
      }
      saveSettings();
      super.dispose();
   }
}
