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

import org.micromanager.asidispim.Data.CameraModes;
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.ListeningJTabbedPane;
import org.micromanager.asidispim.Utils.PiezoSleepPreventer;

import java.awt.Container;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.JLabel;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;
import org.micromanager.MMStudio;
import org.micromanager.asidispim.Utils.AutofocusUtils;
import org.micromanager.asidispim.Utils.ControllerUtils;

import static org.micromanager.asidispim.Utils.MyJavaUtils.isMac;

import org.micromanager.asidispim.Utils.StagePositionUpdater;
import org.micromanager.asidispim.api.ASIdiSPIMException;
import org.micromanager.internalinterfaces.LiveModeListener; 
import org.micromanager.utils.MMFrame;

//TODO devices tab automatically recognize default device names, e.g. autopopulate
//TODO "swap sides" button (during alignment)
//TODO alignment wizard that would guide through alignment steps
//TODO easy mode that pulls most-used bits from all panels
//TODO calibration for sheet width/offset (automatic based on image analysis?) and then optimize based on ROI
//TODO recalculate slice timing automatically changing assigned camera
//TODO add status bar to bottom of window (would include acquisition status, could show other messages too)
//TODO move acquisition start/stop to shared area below tabs
//TODO add ability of stage scan in 2nd dimension (for wide samples)
//TODO make it easy to discard a data set
//TODO make it easy to look through series of data sets
//TODO hardware Z-projection
//TODO camera control tab: set ROI, set separate acquisition/alignment exposure times and sweep rate, etc.
//TODO method to move sample diagonally (move X and F at same time) for sample finding
//TODO factor out common code for JComboBoxes like MulticolorModes, CameraModes, AcquisitionModes, etc.
//TODO cleanup prefs vs. props... maybe add boolean support for plugin device use only?
//TODO finish eliminating Prefs.Keys in favor of Properties.Keys with plugin values
//TODO separate properties into true hardware-related properties and plugin settings (maybe store plugin settings directly in prefs?)
//TODO have per-tab way of creating and accessing plugin settings in prefs
//TODO with prefs, make sure there is good way of specifying default value
//TODO save/load plugin settings from file (or image metadata) instead of from registry (nice to also include controller settings)
//TODO improve efficiency of camera code by pre-calculating key factors and updating when needed instead of calculating every time
//TODO add check for correct Hamamatsu model
//TODO fix slightly incorrect Hamamatsu timing calculation (apparent at small ROI)
//TODO execute autofocus during acquisition before the desired time point is reached instead of waiting until a timepoint should be collected
//       or else do autofocus after acquisition instead of before
//TODO merge all acquisition code instead of autofocus having its own version
//TODO better separate GUI from function!!, e.g. similar to MM's core/gui separation (~MVC framework)
//TODO smart default joystick settings (e.g. different defaults different panels/wheels)
//TODO calculate and show estimated disk space as part of "durations"
//TODO Make it easier to adjust stack center (or start and end) including autodetect start/end based on content
//TODO allow different stack center, number of slices, offset, or other settings for each XY position
//TODO iconify tab labels and/or other parts of plugin
//TODO use new J *=0 to more efficiently clear joystick settings (require comm card version 3.1)
//TODO use new W * to more efficiently get positions (will return in same order as comm card parses)
//TODO be more efficient in serial commands, including not resending same commands (largely obviated once shared properties on same card are implemented)

/**
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class ASIdiSPIMFrame extends MMFrame  
      implements MMListenerInterface {
   
   private final ScriptInterface gui_;
   private final Properties props_; 
   private final Prefs prefs_;
   private final Devices devices_;
   private final Joystick joystick_;
   private final Positions positions_;
   private final Cameras cameras_;
   private final ControllerUtils controller_;
   private final AutofocusUtils autofocus_;
   
   private final DevicesPanel devicesPanel_;
   private final AcquisitionPanel acquisitionPanel_;
   private final SetupPanel setupPanelA_;
   private final SetupPanel setupPanelB_;
   private final NavigationPanel navigationPanel_;
   private final SettingsPanel settingsPanel_;
   private final DataAnalysisPanel dataAnalysisPanel_;
   private final AutofocusPanel autofocusPanel_;
   private final CameraPanel cameraPanel_;
   private final HelpPanel helpPanel_;
   private final StatusSubPanel statusSubPanel_;
   private final StagePositionUpdater stagePosUpdater_;
   private final ListeningJTabbedPane tabbedPane_;
   private PiezoSleepPreventer piezoSleepPreventer_;
   private final LiveModeListener piezoSleepListener_;
   
   private final AtomicBoolean hardwareInUse_ = new AtomicBoolean(false);   // true if acquisition or autofocus running
   
   
   private static final String MAIN_PREF_NODE = "Main"; 
   
   /**
    * Creates the ASIdiSPIM plugin frame
    * @param gui - Micro-Manager script interface
    * @throws ASIdiSPIMException
    */
   public ASIdiSPIMFrame(ScriptInterface gui) throws ASIdiSPIMException {

      // create interface objects used by panels
      gui_ = gui;
      prefs_ = new Prefs(Preferences.userNodeForPackage(this.getClass()));
      devices_ = new Devices(gui_, prefs_);
      props_ = new Properties(gui_, devices_, prefs_);
      positions_ = new Positions(gui_, devices_);
      joystick_ = new Joystick(devices_, props_);
      cameras_ = new Cameras(gui_, devices_, props_, prefs_);
      controller_ = new ControllerUtils(gui_, props_, prefs_, devices_, positions_, cameras_);
      
      // make sure Live mode is turned off
      gui_.enableLiveMode(false);
      
      // create the panels themselves
      // in some cases dependencies create required ordering
      devicesPanel_ = new DevicesPanel(gui_, devices_, props_);
      stagePosUpdater_ = new StagePositionUpdater(positions_, props_);  // needed for setup and navigation
      
      autofocus_ = new AutofocusUtils(gui_, devices_, props_, prefs_,
            cameras_, stagePosUpdater_, positions_, controller_);
      
      acquisitionPanel_ = new AcquisitionPanel(gui_, devices_, props_, cameras_, 
            prefs_, stagePosUpdater_, positions_, controller_, autofocus_);
      setupPanelA_ = new SetupPanel(gui_, devices_, props_, joystick_,
            Devices.Sides.A, positions_, cameras_, prefs_, stagePosUpdater_,
            autofocus_, controller_);
      if (!ASIdiSPIM.oSPIM) {
         setupPanelB_ = new SetupPanel(gui_, devices_, props_, joystick_,
               Devices.Sides.B, positions_, cameras_, prefs_, stagePosUpdater_,
               autofocus_, controller_);
      } else {
         setupPanelB_ = null;
      }
      navigationPanel_ = new NavigationPanel(gui_, devices_, props_, joystick_,
            positions_, prefs_, cameras_, stagePosUpdater_);

      dataAnalysisPanel_ = new DataAnalysisPanel(prefs_, props_, devices_);
      autofocusPanel_ = new AutofocusPanel(gui_, devices_, props_, prefs_, autofocus_);
      cameraPanel_ = new CameraPanel(gui_, devices_, props_, prefs_, cameras_);
      settingsPanel_ = new SettingsPanel(gui_, devices_, props_, prefs_, stagePosUpdater_);
      stagePosUpdater_.oneTimeUpdate();  // needed for NavigationPanel
      helpPanel_ = new HelpPanel();
      statusSubPanel_ = new StatusSubPanel(devices_, props_, positions_, stagePosUpdater_);
      
      // now add tabs to GUI
      // all added tabs must be of type ListeningJPanel
      // only use addLTab, not addTab to guarantee this
      tabbedPane_ = new ListeningJTabbedPane();
      if (isMac()) {
         tabbedPane_.setTabPlacement(JTabbedPane.TOP);
      } else {
         tabbedPane_.setTabPlacement(JTabbedPane.LEFT);
      }
      tabbedPane_.addLTab(navigationPanel_);  // tabIndex = 0
      tabbedPane_.addLTab(setupPanelA_);      // tabIndex = 1
      if (!ASIdiSPIM.oSPIM) {
         tabbedPane_.addLTab(setupPanelB_);      // tabIndex = 2
      }
      tabbedPane_.addLTab(acquisitionPanel_); // tabIndex = 3
      tabbedPane_.addLTab(dataAnalysisPanel_);// tabIndex = 4
      tabbedPane_.addLTab(devicesPanel_);     // tabIndex = 5
      final int deviceTabIndex = tabbedPane_.getTabCount() - 1;
      tabbedPane_.addLTab(autofocusPanel_);   // tabIndex = 6
      tabbedPane_.addLTab(cameraPanel_);      // tabIndex = 7
      tabbedPane_.addLTab(settingsPanel_);    // tabIndex = 8
      tabbedPane_.addLTab(helpPanel_);        // tabIndex = 9
      final int helpTabIndex = tabbedPane_.getTabCount() - 1;
      
      // make taller tabs for easier navigation between them
      // we create own labels instead of having JTabbedPane do it from titles
      final Border paddingBorder = BorderFactory.createEmptyBorder(4,0,4,0);
      for (int i=0; i<tabbedPane_.getTabCount(); i++) {
         JLabel lab = new JLabel(((ListeningJPanel)tabbedPane_.getComponentAt(i)).getPanelName());
         lab.setBorder(paddingBorder);
         tabbedPane_.setTabComponentAt(i, lab);
      }
      
      // add the testing panel explicitly by uncommenting following lines
      // intended to only be done in short term for testing
      // TestingPanel testingPanel = new TestingPanel();
      // tabbedPane_.addLTab(testingPanel);

      // attach position updaters
      stagePosUpdater_.addPanel(setupPanelA_);
      if (!ASIdiSPIM.oSPIM) {
         stagePosUpdater_.addPanel(setupPanelB_);
      }
      stagePosUpdater_.addPanel(navigationPanel_);
      stagePosUpdater_.addPanel(statusSubPanel_);
      
      piezoSleepPreventer_ = new PiezoSleepPreventer(gui_, devices_, props_);

      // attach live mode listeners
      piezoSleepListener_ = new LiveModeListener() {
         // make sure to "wake up" any piezos with autosleep enabled before we start imaging 
         @Override
         public void liveModeEnabled(boolean enabled) {
            if (enabled) {
               piezoSleepPreventer_.start();
            } else {
               piezoSleepPreventer_.stop();
            }
         }
      };
      MMStudio.getInstance().getSnapLiveManager().addLiveModeListener(piezoSleepListener_);
      MMStudio.getInstance().getSnapLiveManager().addLiveModeListener((LiveModeListener) setupPanelA_);
      if (!ASIdiSPIM.oSPIM) {
         MMStudio.getInstance().getSnapLiveManager().addLiveModeListener((LiveModeListener) setupPanelB_);
      }
      MMStudio.getInstance().getSnapLiveManager().addLiveModeListener((LiveModeListener) navigationPanel_);
      
      // set scan for live mode to be triangle (now live mode setting not affected by SPIM setting)
      props_.setPropValue(new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SA_PATTERN_X, Properties.Values.SAM_TRIANGLE, true);
      
      // make sure gotDeSelected() and gotSelected() get called whenever we switch tabs
      tabbedPane_.addChangeListener(new ChangeListener() {
         int lastSelectedIndex_ = tabbedPane_.getSelectedIndex();
         @Override
         public void stateChanged(ChangeEvent e) {
            ((ListeningJPanel) tabbedPane_.getComponentAt(lastSelectedIndex_)).gotDeSelected();
            ((ListeningJPanel) tabbedPane_.getSelectedComponent()).gotSelected();
            lastSelectedIndex_ = tabbedPane_.getSelectedIndex();
         }
      });
      
      // put frame back where it was last time
      this.loadAndRestorePosition(100, 100);
      
      // clear any previous joystick settings
      joystick_.unsetAllJoysticks();
    
      // gotSelected will be called because we put this after adding the ChangeListener
      tabbedPane_.setSelectedIndex(helpTabIndex);  // setSelectedIndex(0) just after initialization doesn't fire ChangeListener, so switch to help panel first
      tabbedPane_.setSelectedIndex(prefs_.getInt(MAIN_PREF_NODE, Prefs.Keys.TAB_INDEX, deviceTabIndex));  // default to devicesPanel_ on first run

      // set up the window
      add(tabbedPane_);  // add the pane to the GUI window
      setTitle(ASIdiSPIM.menuName + " Control"); 
      pack();           // shrinks the window as much as it can
      setResizable(false);
      
      // take care of shutdown tasks when window is closed
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      
      // add status panel as an overlay that is visible from all tabs
      Container glassPane = (Container) getGlassPane();
      glassPane.setVisible(true);
      glassPane.setLayout(new MigLayout(
            "",
            "[" + this.getWidth() + "]",
            "[" + this.getHeight() + "]"));
      glassPane.add(statusSubPanel_, "dock south");
      
   }
   
   public void setHardwareInUse(boolean inuse) {
      hardwareInUse_.set(inuse);
   }
   
   public boolean getHardwareInUse() {
      return hardwareInUse_.get();
   }
   
   public void tabsSetEnabled(boolean enabled) {
      tabbedPane_.setEnabled(enabled);
   }
   
   /**
    * @return CameraModes.Keys value from Camera panel
    * (internal, edge, overlap, pseudo-overlap, light sheet) 
    */
   public CameraModes.Keys getSPIMCameraMode() {
      CameraModes.Keys val = null;
      try {
         val = ASIdiSPIM.getFrame().getCameraPanel().getSPIMCameraMode();
      } catch (Exception ex) {
         // this case in for when tab is first created and CameraPanel doesn't yet exist
         // arguably it's better to use the Java object when possible instead of always going to prefs
         val = CameraModes.getKeyFromPrefCode(prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
               Properties.Keys.PLUGIN_CAMERA_MODE, 0));
      }
      return val;
   }
   
   /**
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
    * @return the currently used instance of the NavigationPanel;
    */
   public NavigationPanel getNavigationPanel() {
      return navigationPanel_;
   }
   
   /**
    * For use of data analysis panel code (running deskew)
    * Do not get into the internals of this plugin without relying on
    * ASIdiSPIM.api
    * @return the currently used instance of the DataAnalysisPanel;
    */
   public DataAnalysisPanel getDataAnalysisPanel() {
      return dataAnalysisPanel_;
   }
   
   /**
    * For use of acquisition panel code (getting camera settings)
    * Do not get into the internals of this plugin without relying on
    * ASIdiSPIM.api
    * @return the currently used instance of the CameraPanel;
    */
   public CameraPanel getCameraPanel() {
      return cameraPanel_;
   }   
   
   /**
    * For use by the acquisition panel code (to update offset setting)
    * Do not get into the internals of this plugin without relying on
    * ASIdiSPIM.api
    * @param side side for which the setup panel is desired)
    * @return desired instance of the setup Panel (either A or B)
    */
   public SetupPanel getSetupPanel(Devices.Sides side) {
      if (side.equals(Devices.Sides.A))
         return setupPanelA_;
      else if (side.equals(Devices.Sides.B))
         return setupPanelB_;
      
      // this can not be reached unless someone adds more sides than A and B
      return null;
   }
   
   /**
    * Do not get into the internals of this plugin without relying on
    * ASIdiSPIM.api
    * @return the currently used instance of the AutofocusPanel;
    */
   public AutofocusPanel getAutofocusPanel() {
      return autofocusPanel_;
   }
   
   /**
    * Do not get into the internals of this plugin without relying on
    * ASIdiSPIM.api
    * @return the currently used instance of the AutofocusPanel;
    */
   public Devices getDevices() {
      return devices_;
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
     // if (device.equals("Core") && property.equals("Camera")) {
     //    could call cameras_.setCamera() here
     //}
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
   
   // TODO make this automatically call all panels' method
   private void saveSettings() {
      // save selections as needed
      devices_.saveSettings();
      setupPanelA_.saveSettings();
      if (!ASIdiSPIM.oSPIM) {
         setupPanelB_.saveSettings();
      }
      navigationPanel_.saveSettings();
      acquisitionPanel_.saveSettings();
      settingsPanel_.saveSettings();

      // save tab location in prefs (dialog location now handled by MMDialog)
      prefs_.putInt(MAIN_PREF_NODE, Prefs.Keys.TAB_INDEX, tabbedPane_.getSelectedIndex());
   }
   
// TODO make this automatically call all panels' method
   private void windowClosing() {
      // TODO force user to cancel any ongoing acquisition before closing
      acquisitionPanel_.windowClosing();
      setupPanelA_.windowClosing();
      if (!ASIdiSPIM.oSPIM) {
         setupPanelB_.windowClosing();
      }
   }
   
   @Override
   public void dispose() {
      stagePosUpdater_.stop();
      piezoSleepPreventer_.stop();
      saveSettings();
      windowClosing();
      MMStudio.getInstance().getSnapLiveManager().removeLiveModeListener(piezoSleepListener_);
      MMStudio.getInstance().getSnapLiveManager().removeLiveModeListener((LiveModeListener) setupPanelA_);
      MMStudio.getInstance().getSnapLiveManager().removeLiveModeListener((LiveModeListener) setupPanelB_);
      MMStudio.getInstance().getSnapLiveManager().removeLiveModeListener((LiveModeListener) navigationPanel_);
      super.dispose();
   }
}
