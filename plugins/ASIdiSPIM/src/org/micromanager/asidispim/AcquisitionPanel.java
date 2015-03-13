///////////////////////////////////////////////////////////////////////////////
//FILE:          AcquisitionPanel.java
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


import org.micromanager.asidispim.Data.AcquisitionModes;
import org.micromanager.asidispim.Data.CameraModes;
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.MultichannelModes;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.MyNumberUtils;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.asidispim.Utils.SliceTiming;
import org.micromanager.asidispim.Utils.StagePositionUpdater;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;

import org.json.JSONException;
import org.json.JSONObject;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.TaggedImage;

import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.ImageCache;
import org.micromanager.api.MMTags;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.DefaultTaggedImageSink;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

import com.swtdesigner.SwingResourceManager;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;

import org.micromanager.acquisition.ComponentTitledBorder;
import org.micromanager.asidispim.Data.ChannelSpec;
import org.micromanager.utils.MMFrame;

/**
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class AcquisitionPanel extends ListeningJPanel implements DevicesListenerInterface {

   private final Devices devices_;
   private final Properties props_;
   private final Joystick joystick_;
   private final Cameras cameras_;
   private final Prefs prefs_;
   private final Positions positions_;
   private final CMMCore core_;
   private final ScriptInterface gui_;
   private final JCheckBox advancedSliceTimingCB_;
   private final JSpinner numSlices_;
   private final JComboBox numSides_;
   private final JComboBox firstSide_;
   private final JSpinner numScansPerSlice_;
   private final JSpinner lineScanPeriod_;
   private final JSpinner delayScan_;
   private final JSpinner delayLaser_;
   private final JSpinner delayCamera_;
   private final JSpinner durationCamera_;  // NB: not the same as camera exposure
   private final JSpinner durationLaser_;
   private final JSpinner delaySide_;
   private final JLabel actualSlicePeriodLabel_;
   private final JLabel actualVolumeDurationLabel_;
   private final JLabel actualTimeLapseDurationLabel_;
   private final JSpinner numTimepoints_;
   private final JSpinner acquisitionInterval_;
   private final JToggleButton buttonStart_;
   private final JPanel volPanel_;
   private final JPanel slicePanel_;
   private final JPanel timepointPanel_;
   private final JPanel savePanel_;
   private final JPanel durationPanel_;
   private final JTextField rootField_;
   private final JTextField nameField_;
   private final JLabel acquisitionStatusLabel_;
   private int numTimePointsDone_;
   private final AtomicBoolean cancelAcquisition_ = new AtomicBoolean(false);  // true if we should stop acquisition
   private final AtomicBoolean acquisitionRunning_ = new AtomicBoolean(false);  // true if acquisition is in progress
   private final StagePositionUpdater posUpdater_;
   private final JSpinner stepSize_;
   private final JLabel desiredSlicePeriodLabel_;
   private final JSpinner desiredSlicePeriod_;
   private final JLabel desiredLightExposureLabel_;
   private final JSpinner desiredLightExposure_;
   private final JButton calculateSliceTiming_;
   private final JCheckBox minSlicePeriodCB_;
   private final JCheckBox separateTimePointsCB_;
   private final JCheckBox saveCB_;
   private final JCheckBox hideCB_;
   private final JComboBox spimMode_;
   private final JCheckBox navigationJoysticksCB_;
   private final JCheckBox usePositionsCB_;
   private final JSpinner positionDelay_;
   private final JCheckBox useTimepointsCB_;
   private final JPanel leftColumnPanel_;
   private final JPanel centerColumnPanel_;
   private final MMFrame sliceFrameAdvanced_;
   private SliceTiming sliceTiming_;
   private final MultiChannelSubPanel multiChannelPanel_;
   
   public AcquisitionPanel(ScriptInterface gui, 
           Devices devices, 
           Properties props, 
           Joystick joystick,
           Cameras cameras, 
           Prefs prefs, 
           StagePositionUpdater posUpdater,
           Positions positions) {
      super(MyStrings.PanelNames.ACQUSITION.toString(),
              new MigLayout(
              "",
              "[center]0[center]0[center]",
              "[top]0[]"));
      gui_ = gui;
      devices_ = devices;
      props_ = props;
      joystick_ = joystick;
      cameras_ = cameras;
      prefs_ = prefs;
      posUpdater_ = posUpdater;
      positions_ = positions;
      core_ = gui_.getMMCore();
      numTimePointsDone_ = 0;
      sliceTiming_ = new SliceTiming();
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);
      
      // added to spinner controls where we should re-calculate the displayed
      // slice period, volume duration, and time lapse duration
      ChangeListener recalculateTimingDisplayCL = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            updateDurationLabels();
         }
      };
      
      // added to combobox controls where we should re-calculate the displayed
      // slice period, volume duration, and time lapse duration
      ActionListener recalculateTimingDisplayAL = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateDurationLabels();
         }
      };
      
      // start volume (main) sub-panel

      volPanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]",
              "[]8[]"));

      volPanel_.setBorder(PanelUtils.makeTitledBorder("Volume Settings"));

      volPanel_.add(new JLabel("Number of sides:"));
      String [] str12 = {"1", "2"};
      numSides_ = pu.makeDropDownBox(str12, Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_NUM_SIDES, str12[1]);
      numSides_.addActionListener(recalculateTimingDisplayAL);
      volPanel_.add(numSides_, "wrap");

      volPanel_.add(new JLabel("First side:"));
      String[] ab = {Devices.Sides.A.toString(), Devices.Sides.B.toString()};
      firstSide_ = pu.makeDropDownBox(ab, Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_FIRST_SIDE, Devices.Sides.A.toString());
      volPanel_.add(firstSide_, "wrap");
      
      volPanel_.add(new JLabel("Delay before side [ms]:"));
      delaySide_ = pu.makeSpinnerFloat(0, 10000, 0.25,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_DELAY_SIDE, 0);
      delaySide_.addChangeListener(recalculateTimingDisplayCL);
      volPanel_.add(delaySide_, "wrap");

      volPanel_.add(new JLabel("Slices per volume:"));
      numSlices_ = pu.makeSpinnerInteger(1, 1000,
              Devices.Keys.PLUGIN,
              Properties.Keys.PLUGIN_NUM_SLICES, 20);
      numSlices_.addChangeListener(recalculateTimingDisplayCL);
      volPanel_.add(numSlices_, "wrap");
      
      volPanel_.add(new JLabel("Slice step size [\u00B5m]:"));
      stepSize_ = pu.makeSpinnerFloat(0, 100, 0.1,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_SLICE_STEP_SIZE,
            1.0);
      volPanel_.add(stepSize_, "wrap");
      
      // out of order so we can reference it
      desiredSlicePeriod_ = pu.makeSpinnerFloat(1, 1000, 0.25,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_DESIRED_SLICE_PERIOD, 30);
      
      minSlicePeriodCB_ = pu.makeCheckBox("Minimize slice period",
            Properties.Keys.PREFS_MINIMIZE_SLICE_PERIOD, panelName_, false); 
      minSlicePeriodCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean doMin = minSlicePeriodCB_.isSelected();
            desiredSlicePeriod_.setEnabled(!doMin);
            if (doMin) {
               recalculateSliceTiming(false);
            }
         }
      });
      volPanel_.add(minSlicePeriodCB_, "span 2, wrap");
      
      // special field that is enabled/disabled depending on whether advanced timing is enabled
      desiredSlicePeriodLabel_ = new JLabel("Slice period [ms]:"); 
      volPanel_.add(desiredSlicePeriodLabel_);
      volPanel_.add(desiredSlicePeriod_, "wrap");
      desiredSlicePeriod_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent ce) {
            // make sure is multiple of 0.25
            float userVal = PanelUtils.getSpinnerFloatValue(desiredSlicePeriod_);
            float nearestValid = MyNumberUtils.roundToQuarterMs(userVal);
            if (!MyNumberUtils.floatsEqual(userVal, nearestValid)) {
               PanelUtils.setSpinnerFloatValue(desiredSlicePeriod_, nearestValid);
            }
         }
      });
      
      // special field that is enabled/disabled depending on whether advanced timing is enabled
      desiredLightExposureLabel_ = new JLabel("Sample exposure [ms]:"); 
      volPanel_.add(desiredLightExposureLabel_);
      desiredLightExposure_ = pu.makeSpinnerFloat(2.5, 1000.5, 1,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_DESIRED_EXPOSURE, 8.5);
      desiredLightExposure_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent ce) {
            // make sure is 2.5, 2.5, 3.5, ... 
            float val = PanelUtils.getSpinnerFloatValue(desiredLightExposure_);
            float nearestValid = (float) Math.round(val+0.5f) - 0.5f; 
            if (!MyNumberUtils.floatsEqual(val, nearestValid)) {
               PanelUtils.setSpinnerFloatValue(desiredLightExposure_, nearestValid);
            }
         }
      });
      volPanel_.add(desiredLightExposure_, "wrap");
      
      calculateSliceTiming_ = new JButton("Calculate slice timing");
      calculateSliceTiming_.setToolTipText("Must recalculate after changing the camera ROI.");
      calculateSliceTiming_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            recalculateSliceTiming(!minSlicePeriodCB_.isSelected());
         }
      });
      volPanel_.add(calculateSliceTiming_, "center, span 2, wrap");
      
      // special checkbox to use the advanced timing settings
      // action handler added below after defining components it enables/disables
      advancedSliceTimingCB_ = pu.makeCheckBox("Use advanced timing settings",
            Properties.Keys.PREFS_ADVANCED_SLICE_TIMING, panelName_, false);
      volPanel_.add(advancedSliceTimingCB_, "left, span 2, wrap");
      
      // end volume sub-panel
      
      
      // start advanced slice timing frame
      // visibility of this frame is controlled from advancedTiming checkbox
      // this frame is separate from main plugin window
      
      sliceFrameAdvanced_ = new MMFrame();
      sliceFrameAdvanced_.setTitle("Advanced timing");
      sliceFrameAdvanced_.loadPosition(100, 100);

      slicePanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]",
              "[]8[]"));
      sliceFrameAdvanced_.add(slicePanel_);
      
      class SliceFrameAdapter extends WindowAdapter {
         @Override
         public void windowClosing(WindowEvent e) {
            advancedSliceTimingCB_.setSelected(false);
            sliceFrameAdvanced_.savePosition();
         }
      }
      
      sliceFrameAdvanced_.addWindowListener(new SliceFrameAdapter());
      
      JLabel scanDelayLabel =  new JLabel("Delay before scan [ms]:");
      slicePanel_.add(scanDelayLabel);
      delayScan_ = pu.makeSpinnerFloat(0, 10000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_SCAN, 0);
      delayScan_.addChangeListener(recalculateTimingDisplayCL);
      slicePanel_.add(delayScan_, "wrap");

      JLabel lineScanLabel = new JLabel("Lines scans per slice:");
      slicePanel_.add(lineScanLabel);
      numScansPerSlice_ = pu.makeSpinnerInteger(1, 1000,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_NUM_SCANSPERSLICE, 1);
      numScansPerSlice_.addChangeListener(recalculateTimingDisplayCL);
      slicePanel_.add(numScansPerSlice_, "wrap");

      JLabel lineScanPeriodLabel = new JLabel("Line scan period [ms]:");
      slicePanel_.add(lineScanPeriodLabel);
      lineScanPeriod_ = pu.makeSpinnerInteger(1, 10000,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_LINESCAN_PERIOD, 10);
      lineScanPeriod_.addChangeListener(recalculateTimingDisplayCL);
      slicePanel_.add(lineScanPeriod_, "wrap");
      
      JLabel delayLaserLabel = new JLabel("Delay before laser [ms]:");
      slicePanel_.add(delayLaserLabel);
      delayLaser_ = pu.makeSpinnerFloat(0, 10000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_LASER, 0);
      delayLaser_.addChangeListener(recalculateTimingDisplayCL);
      slicePanel_.add(delayLaser_, "wrap");
      
      JLabel durationLabel = new JLabel("Laser trig duration [ms]:");
      slicePanel_.add(durationLabel);
      durationLaser_ = pu.makeSpinnerFloat(0, 10000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DURATION_LASER, 1);
      durationLaser_.addChangeListener(recalculateTimingDisplayCL);
      slicePanel_.add(durationLaser_, "span 2, wrap");
      
      JLabel delayLabel = new JLabel("Delay before camera [ms]:");
      slicePanel_.add(delayLabel);
      delayCamera_ = pu.makeSpinnerFloat(0, 10000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_CAMERA, 0);
      delayCamera_.addChangeListener(recalculateTimingDisplayCL);
      slicePanel_.add(delayCamera_, "wrap");
      
      JLabel cameraLabel = new JLabel("Camera trig duration [ms]:");
      slicePanel_.add(cameraLabel);
      durationCamera_ = pu.makeSpinnerFloat(0, 1000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DURATION_CAMERA, 0);
      durationCamera_.addChangeListener(recalculateTimingDisplayCL);
      slicePanel_.add(durationCamera_, "wrap");
      
      final JComponent[] simpleTimingComponents = { desiredLightExposure_,
            calculateSliceTiming_, minSlicePeriodCB_, desiredSlicePeriodLabel_,
            desiredLightExposureLabel_};
      componentsSetEnabled(sliceFrameAdvanced_, advancedSliceTimingCB_.isSelected());
      componentsSetEnabled(simpleTimingComponents, !advancedSliceTimingCB_.isSelected());
      
      // this action listener takes care of enabling/disabling inputs
      // of the advanced slice timing window
      // we call this to get GUI looking right
      ItemListener sliceTimingDisableGUIInputs = new ItemListener() {
         @Override
         public void itemStateChanged(ItemEvent e) {
            boolean enabled = advancedSliceTimingCB_.isSelected();
            // set other components in this advanced timing frame
            componentsSetEnabled(sliceFrameAdvanced_, enabled);
            // also control some components in main volume settings sub-panel
            componentsSetEnabled(simpleTimingComponents, !enabled);
            desiredSlicePeriod_.setEnabled(!enabled && !minSlicePeriodCB_.isSelected());
         } 

      };
      
      // this action listener shows/hides the advanced timing frame
      ActionListener showAdvancedTimingFrame = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean enabled = advancedSliceTimingCB_.isSelected();
            if (enabled) {
               sliceFrameAdvanced_.setVisible(enabled);
            }
         }
      };
      
      sliceFrameAdvanced_.pack();
      sliceFrameAdvanced_.setResizable(false);
      
      // end slice Frame
      

      // start repeat (time lapse) sub-panel

      timepointPanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]",
              "[]8[]"));

      useTimepointsCB_ = pu.makeCheckBox("Time points",
            Properties.Keys.PREFS_USE_TIMEPOINTS, panelName_, false);
      useTimepointsCB_.setToolTipText("Perform a time-lapse acquisition");
      useTimepointsCB_.setEnabled(true);
      useTimepointsCB_.setFocusPainted(false); 
      ComponentTitledBorder componentBorder = 
            new ComponentTitledBorder(useTimepointsCB_, timepointPanel_, 
                  BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      timepointPanel_.setBorder(componentBorder);
      
      ChangeListener recalculateTimeLapseDisplay = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            updateActualTimeLapseDurationLabel();
         }
      };
      
      useTimepointsCB_.addChangeListener(recalculateTimeLapseDisplay);

      timepointPanel_.add(new JLabel("Number:"));
      numTimepoints_ = pu.makeSpinnerInteger(1, 32000,
              Devices.Keys.PLUGIN,
              Properties.Keys.PLUGIN_NUM_ACQUISITIONS, 1);
      numTimepoints_.addChangeListener(recalculateTimeLapseDisplay);
      timepointPanel_.add(numTimepoints_, "wrap");

      timepointPanel_.add(new JLabel("Interval [s]:"));
      acquisitionInterval_ = pu.makeSpinnerFloat(1, 32000, 0.1,
              Devices.Keys.PLUGIN,
              Properties.Keys.PLUGIN_ACQUISITION_INTERVAL, 60);
      acquisitionInterval_.addChangeListener(recalculateTimeLapseDisplay);
      timepointPanel_.add(acquisitionInterval_, "wrap");
      
      // enable/disable panel elements depending on checkbox state
      useTimepointsCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) { 
            componentsSetEnabled(timepointPanel_, useTimepointsCB_.isSelected());
         }
      });
      componentsSetEnabled(timepointPanel_, useTimepointsCB_.isSelected());  // initialize
      
      // end repeat sub-panel
      
      
      // start savePanel
      
      final int textFieldWidth = 16;
      savePanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]8[left]",
              "[]4[]"));
      savePanel_.setBorder(PanelUtils.makeTitledBorder("Data Saving Settings"));
      
      separateTimePointsCB_ = pu.makeCheckBox("Separate viewer / file for each time point",
            Properties.Keys.PREFS_SEPARATE_VIEWERS_FOR_TIMEPOINTS, panelName_, false); 
      savePanel_.add(separateTimePointsCB_, "span 3, left, wrap");
      
      hideCB_ = pu.makeCheckBox("Hide viewer",
            Properties.Keys.PREFS_HIDE_WHILE_ACQUIRING, panelName_, false); 
      savePanel_.add(hideCB_, "left");
      hideCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            // if viewer is hidden then force saving to disk
            if (hideCB_.isSelected()) {
               if (!saveCB_.isSelected()) {
                  saveCB_.doClick();
               }
               saveCB_.setEnabled(false);
            } else {
               saveCB_.setEnabled(true);
            }
         }
      });
      
      saveCB_ = pu.makeCheckBox("Save while acquiring",
            Properties.Keys.PREFS_SAVE_WHILE_ACQUIRING, panelName_, false);
      // init the save while acquiring CB; could also do two doClick() calls
      if (hideCB_.isSelected()) {
         saveCB_.setEnabled(false);
      }
      savePanel_.add(saveCB_, "span 2, center, wrap");

      JLabel dirRootLabel = new JLabel ("Directory root:");
      savePanel_.add(dirRootLabel);

      rootField_ = new JTextField();
      rootField_.setText( prefs_.getString(panelName_, 
              Properties.Keys.PLUGIN_DIRECTORY_ROOT, "") );
      rootField_.setColumns(textFieldWidth);
      savePanel_.add(rootField_, "span 2");

      JButton browseRootButton = new JButton();
      browseRootButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            setRootDirectory(rootField_);
            prefs_.putString(panelName_, Properties.Keys.PLUGIN_DIRECTORY_ROOT, 
                    rootField_.getText());
         }
      });
      browseRootButton.setMargin(new Insets(2, 5, 2, 5));
      browseRootButton.setText("...");
      savePanel_.add(browseRootButton, "wrap");

      JLabel namePrefixLabel = new JLabel();
      namePrefixLabel.setText("Name prefix:");
      savePanel_.add(namePrefixLabel);

      nameField_ = new JTextField("acq");
      nameField_.setText( prefs_.getString(panelName_,
              Properties.Keys.PLUGIN_NAME_PREFIX, "acq"));
      nameField_.setColumns(textFieldWidth);
      savePanel_.add(nameField_, "span 2, wrap");
      
      // since we use the name field even for acquisitions in RAM, 
      // we only need to gray out the directory-related components
      final JComponent[] saveComponents = { browseRootButton, rootField_, 
                                            dirRootLabel };
      componentsSetEnabled(saveComponents, saveCB_.isSelected());
      
      saveCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            componentsSetEnabled(saveComponents, saveCB_.isSelected());
         }
      });
      
      // end save panel
      
      // start duration report panel
      
      durationPanel_ = new JPanel(new MigLayout(
            "",
            "[right]6[left, 40%!]",
            "[]5[]"));
      durationPanel_.setBorder(PanelUtils.makeTitledBorder("Durations"));
      durationPanel_.setPreferredSize(new Dimension(125, 0));  // fix width so it doesn't constantly change depending on text
      
      durationPanel_.add(new JLabel("Slice:"));
      actualSlicePeriodLabel_ = new JLabel();
      durationPanel_.add(actualSlicePeriodLabel_, "wrap");
      
      durationPanel_.add(new JLabel("Volume:"));
      actualVolumeDurationLabel_ = new JLabel();
      durationPanel_.add(actualVolumeDurationLabel_, "wrap");
      
      durationPanel_.add(new JLabel("Total:"));
      actualTimeLapseDurationLabel_ = new JLabel();
      durationPanel_.add(actualTimeLapseDurationLabel_, "wrap");
      
      // end duration report panel
      
      buttonStart_ = new JToggleButton();
      buttonStart_.setIconTextGap(6);
      buttonStart_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (acquisitionRunning_.get()) {
               cancelAcquisition_.set(true);
            } else {
               runAcquisition();
            }
         }
      });
      updateStartButton();  // call once to initialize, isSelected() will be false

      acquisitionStatusLabel_ = new JLabel("");
      updateAcquisitionStatus(AcquisitionStatus.NONE);
      
      // Channel Panel (separate file for code)
      multiChannelPanel_ = new MultiChannelSubPanel(gui, devices_, props_, prefs_);
      multiChannelPanel_.addDurationLabelListener(this);
      
      // Position Panel
      final JPanel positionPanel = new JPanel();
      positionPanel.setLayout(new MigLayout("flowx, fillx","[right]10[left][10][]","[]8[]"));
      usePositionsCB_ = pu.makeCheckBox("Multiple positions (XY)",
            Properties.Keys.PREFS_USE_MULTIPOSITION, panelName_, false);
      usePositionsCB_.setToolTipText("Acquire datasest at multiple postions");
      usePositionsCB_.setEnabled(true);
      usePositionsCB_.setFocusPainted(false); 
      componentBorder = 
            new ComponentTitledBorder(usePositionsCB_, positionPanel, 
                  BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      positionPanel.setBorder(componentBorder);
      final JButton editPositionListButton = new JButton("Edit position list...");
      editPositionListButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            gui_.showXYPositionList();
         }
      });
      positionPanel.add(editPositionListButton, "span 2, center");
      
      // add empty fill space on right side of panel
      positionPanel.add(new JLabel(""), "wrap, growx");
      
      positionPanel.add(new JLabel("Post-move delay [ms]:"));
      positionDelay_ = pu.makeSpinnerFloat(0.0, 10000.0, 100.0,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_POSITION_DELAY,
            0.0);
      positionPanel.add(positionDelay_, "wrap");
      
      // enable/disable panel elements depending on checkbox state
      usePositionsCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) { 
            componentsSetEnabled(positionPanel, usePositionsCB_.isSelected());
         }
      });
      componentsSetEnabled(positionPanel, usePositionsCB_.isSelected());  // initialize
      
      // end of Position panel
      
      // checkbox to use navigation joystick settings or not
      // an "orphan" UI element
      navigationJoysticksCB_ = new JCheckBox("Use Navigation joystick settings");
      navigationJoysticksCB_.setSelected(prefs_.getBoolean(panelName_,
            Properties.Keys.PLUGIN_USE_NAVIGATION_JOYSTICKS, false));
      navigationJoysticksCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) { 
            if (navigationJoysticksCB_.isSelected()) {
               ASIdiSPIM.getFrame().getNavigationPanel().doJoystickSettings();
            } else {
               joystick_.unsetAllJoysticks();
            }
            prefs_.putBoolean(panelName_, Properties.Keys.PLUGIN_USE_NAVIGATION_JOYSTICKS,
                  navigationJoysticksCB_.isSelected());
         }
      });
      
      
      // set up tabbed panels for GUI
      // make 3 columns as own JPanels to get vertical space right
      // in each column without dependencies on other columns
      
      leftColumnPanel_ = new JPanel(new MigLayout(
            "",
            "[]",
            "[]6[]10[]10[]"));
      
      leftColumnPanel_.add(durationPanel_, "split 2");
      leftColumnPanel_.add(timepointPanel_, "wrap, growx");
      leftColumnPanel_.add(savePanel_, "wrap");
      leftColumnPanel_.add(new JLabel("SPIM mode: "), "split 2, left");
      AcquisitionModes acqModes = new AcquisitionModes(devices_, props_, prefs_);
      spimMode_ = acqModes.getComboBox(); 
      leftColumnPanel_.add(spimMode_, "wrap");
      leftColumnPanel_.add(buttonStart_, "split 2, left");
      leftColumnPanel_.add(acquisitionStatusLabel_);
      
      centerColumnPanel_ = new JPanel(new MigLayout(
            "",
            "[]",
            "[]"));
      
      centerColumnPanel_.add(positionPanel, "growx, wrap");
      centerColumnPanel_.add(multiChannelPanel_, "wrap");
      centerColumnPanel_.add(navigationJoysticksCB_, "wrap");
      
      // add the column panels to the main panel
      this.add(leftColumnPanel_);
      this.add(centerColumnPanel_);
      this.add(volPanel_);
      
      // properly initialize the advanced slice timing
      advancedSliceTimingCB_.addItemListener(sliceTimingDisableGUIInputs);
      sliceTimingDisableGUIInputs.itemStateChanged(null);
      //advancedSliceTimingCB_.doClick();
      //advancedSliceTimingCB_.doClick();
      advancedSliceTimingCB_.addActionListener(showAdvancedTimingFrame);
      
      updateDurationLabels();
      
      // for easy timing mode, calculate slice timing to start
      if (!advancedSliceTimingCB_.isSelected()
            && checkCamerasAssigned(false)) {
         calculateSliceTiming_.doClick();
      }
      
      
   }//end constructor
   
   public final void updateDurationLabels() {
      updateActualSlicePeriodLabel();
      updateActualVolumeDurationLabel();
      updateActualTimeLapseDurationLabel();
   }
   
   /**
    * Sets the acquisition name prefix programmatically.
    * Added so that name prefix can be changed from a script.
    * @param acqName
    */
   public void setAcquisitionNamePrefix(String acqName) {
      nameField_.setText(acqName);
   }
   
   private void updateStartButton() {
      boolean started = acquisitionRunning_.get();
      buttonStart_.setSelected(started);
      buttonStart_.setText(started ? "Stop!" : "Start!");
      buttonStart_.setBackground(started ? Color.red : Color.green);
      buttonStart_.setIcon(started ?
            SwingResourceManager.
            getIcon(MMStudio.class,
            "/org/micromanager/icons/cancel.png")
            : SwingResourceManager.getIcon(MMStudio.class,
                  "/org/micromanager/icons/arrow_right.png"));
   }
   
   /**
    * @return either "A" or "B"
    */
   private String getFirstSide() {
      return (String)firstSide_.getSelectedItem();
   }
   
   private boolean isFirstSideA() {
      return getFirstSide().equals("A");
   }

   /**
    * @return either 1 or 2
    */
   private int getNumSides() {
      if (numSides_.getSelectedIndex() == 1) {
         return 2;
      } else {
         return 1;
      }
   }
   
   private boolean isTwoSided() {
      return (numSides_.getSelectedIndex() == 1);
   }
   
   private int getNumTimepoints() {
      if (!useTimepointsCB_.isSelected()) {
         return 1;
      }
      return (Integer) numTimepoints_.getValue();
   }
   
   private int getNumChannels() {
      if (!multiChannelPanel_.isPanelEnabled()) {
         return 1;
      }
      return multiChannelPanel_.getUsedChannels().length;
   }
   
   private int getLineScanPeriod() {
      return (Integer) lineScanPeriod_.getValue();
   }
   
   private int getNumScansPerSlice() {
      return (Integer) numScansPerSlice_.getValue();
   }
   
   private int getNumSlices() {
      return (Integer) numSlices_.getValue();
   }
   
   private double getStepSizeUm() {
      return PanelUtils.getSpinnerFloatValue(stepSize_);
   }
   
   
   
   /**
    * 
    * @param showWarnings true to warn user about needing to change slice period
    * @return
    */
   private SliceTiming getTimingFromPeriodAndLightExposure(boolean showWarnings) {
      // uses algorithm Jon worked out in Octave code; each slice period goes like this:
      // 1. camera readout time (none if in overlap mode)
      // 2. any extra delay time
      // 3. camera reset time
      // 4. start scan 0.25ms before camera global exposure and shifted up in time to account for delay introduced by Bessel filter
      // 5. turn on laser as soon as camera global exposure, leave laser on for desired light exposure time
      // 7. end camera exposure in final 0.25ms, post-filter scan waveform also ends now
      
      final float scanLaserBufferTime = 0.25f;
      final Color foregroundColorOK = Color.BLACK;
      final Color foregroundColorError = Color.RED;
      final Component elementToColor  = desiredSlicePeriod_.getEditor().getComponent(0);
      
      SliceTiming s = new SliceTiming();
      float cameraResetTime = computeCameraResetTime();      // recalculate for safety
      float cameraReadoutTime = computeCameraReadoutTime();  // recalculate for safety
      
      // get delay between trigger and when exposure timer starts so we can 
      //   decrease camera exposure accordingly
      // for now simply recover "overhead time" in computeCameraReasetTime()
      // if readout/reset calculations change then this may need to be more sophisticated
      float cameraExposureDelayTime = cameraResetTime - cameraReadoutTime;
      
      float desiredPeriod = minSlicePeriodCB_.isSelected() ? 0 :
         PanelUtils.getSpinnerFloatValue(desiredSlicePeriod_);
      float desiredExposure = PanelUtils.getSpinnerFloatValue(desiredLightExposure_);
      
      float cameraReadout_max = MyNumberUtils.ceilToQuarterMs(cameraReadoutTime);
      float cameraReset_max = MyNumberUtils.ceilToQuarterMs(cameraResetTime);
      float slicePeriod = MyNumberUtils.roundToQuarterMs(desiredPeriod);
      int scanPeriod = Math.round(desiredExposure + 2*scanLaserBufferTime);
      // scan will be longer than laser by 0.25ms at both start and end
      float laserDuration = scanPeriod - 2*scanLaserBufferTime;  // will be integer plus 0.5
      
      // computer "extra" per-slice time: period minus camera reset and readout times minus (scan time - 0.25ms)
      // the last 0.25ms correction comes because we start the scan 0.25ms before camera global exposure
      float globalDelay = slicePeriod - cameraReadout_max - cameraReset_max - scanPeriod + scanLaserBufferTime;
      
      // if calculated delay is negative then we have to reduce exposure time in 1 sec increments
      if (globalDelay < 0) {
         float extraTimeNeeded = MyNumberUtils.ceilToQuarterMs(-1f*globalDelay);  // positive number
            globalDelay += extraTimeNeeded;
            if (showWarnings) {
               MyDialogUtils.showError(
                     "Increasing slice period to meet laser exposure constraint\n"
                           + "(time required for camera readout; readout time depends on ROI).\n");
               elementToColor.setForeground(foregroundColorError);
               // considered actually changing the value, but decided against it because
               // maybe the user just needs to set the ROI appropriately and recalculate
            } else {
               elementToColor.setForeground(foregroundColorOK);
            }
      } else {
         elementToColor.setForeground(foregroundColorOK);
      }
      
      // account for delay in scan position based on Bessel filter by starting the scan slightly earlier
      // than we otherwise would; delay is (empirically) ~0.33/(freq in kHz)
      // find better results adding 0.4/(freq in kHz) though
      // group delay for bessel filter approx 1/w or ~0.16/freq, or half/third the empirical value (not sure why discrepancy)
      float scanFilterFreq = Math.max(props_.getPropValueFloat(Devices.Keys.GALVOA,  Properties.Keys.SCANNER_FILTER_X),
            props_.getPropValueFloat(Devices.Keys.GALVOB,  Properties.Keys.SCANNER_FILTER_X));
      float scanDelayFilter = 0;
      if (scanFilterFreq != 0) {
         scanDelayFilter = MyNumberUtils.roundToQuarterMs(0.4f/scanFilterFreq);
      }
      
      // Add 0.25ms to globalDelay if it is 0 and we are on overlap mode and scan has been shifted forward
      // basically the last 0.25ms of scan time that would have determined the slice period isn't
      //   there any more because the scan time is moved up  => add in the 0.25ms at the start of the slice
      // in edge or level trigger mode the camera trig falling edge marks the end of the slice period
      // not sure if PCO pseudo-overlap needs this, probably not because adding 0.25ms overhead in that case
      if (MyNumberUtils.floatsEqual(cameraReadout_max, 0f)  // true iff overlap being used
            && (scanDelayFilter > 0.01f)) {
         globalDelay += 0.25f;
      }
      
      // If the PLogic card is used, account for 0.25ms delay it introduces
      // to the camera and laser trigger signals => subtract 0.25ms to the scanner delay
      // (start scanner 0.25ms later than it would be otherwise)
      // (really it is 0.25ms minus the evaluation time to generate the signals)
      // this time-shift opposes the Bessel filter delay
      if (devices_.isValidMMDevice(Devices.Keys.PLOGIC)) {
         scanDelayFilter -= 0.25f;
      }

      s.scanDelay = cameraReadout_max + globalDelay + cameraReset_max - scanDelayFilter - scanLaserBufferTime;  
      s.scanNum = 1;
      s.scanPeriod = scanPeriod;
      s.laserDelay = cameraReadout_max + globalDelay + cameraReset_max;
      s.laserDuration = laserDuration;
      s.cameraDelay = cameraReadout_max + globalDelay;
      s.cameraDuration = cameraReset_max + scanPeriod - scanLaserBufferTime;  // approx. same as exposure, can be used in bulb mode
      s.cameraExposure = s.cameraDuration
            - 0.01f  // give up 0.10ms of our 0.25ms overhead here because camera might round up
                     //  from the set exposure time and thus exceeding total period
            - cameraExposureDelayTime;
      
      
      // change camera duration for overlap mode to be short trigger
      // needed because exposure time is set by difference between pulses in this mode
      CameraModes.Keys cameraMode = CameraModes.getKeyFromPrefCode(
            prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_MODE, 0));
      if (cameraMode == CameraModes.Keys.OVERLAP) {
         // for Hamamatsu's "synchronous" or Zyla's "overlap" mode
         // send single short trigger
         s.cameraDuration = 1;
      }
      
      return s;
   }
   
   
   /**
    * @return true if the slice timing matches the current user parameters and ROI
    */
   private boolean isSliceTimingUpToDate() {
      SliceTiming newTiming = getTimingFromPeriodAndLightExposure(false);
      return sliceTiming_.equals(newTiming);
   }
   
   /**
    * Re-calculate the controller's timing settings for "easy timing" mode.
    * If the values are the same nothing happens.  If they should be changed,
    * then the controller's properties will be set.
    * @param showWarnings will show warning if the user-specified slice period too short
    */
   private void recalculateSliceTiming(boolean showWarnings) {
      if(!checkCamerasAssigned(true)) {
         return;
      }
      sliceTiming_ = getTimingFromPeriodAndLightExposure(showWarnings);
      PanelUtils.setSpinnerFloatValue(delayScan_, sliceTiming_.scanDelay);
      numScansPerSlice_.setValue(sliceTiming_.scanNum);
      lineScanPeriod_.setValue(sliceTiming_.scanPeriod);
      PanelUtils.setSpinnerFloatValue(delayLaser_, sliceTiming_.laserDelay);
      PanelUtils.setSpinnerFloatValue(durationLaser_, sliceTiming_.laserDuration);
      PanelUtils.setSpinnerFloatValue(delayCamera_, sliceTiming_.cameraDelay);
      PanelUtils.setSpinnerFloatValue(durationCamera_, sliceTiming_.cameraDuration );
   }
   
   /**
    * Compute slice period in ms based on controller's timing settings.
    * @return period in ms
    */
   private double computeActualSlicePeriod() {
      double period = Math.max(Math.max(
            PanelUtils.getSpinnerFloatValue(delayScan_) +   // scan time
            (getLineScanPeriod() * getNumScansPerSlice()),
                  PanelUtils.getSpinnerFloatValue(delayLaser_)
                  + PanelUtils.getSpinnerFloatValue(durationLaser_)  // laser time
            ),
            PanelUtils.getSpinnerFloatValue(delayCamera_)
            + PanelUtils.getSpinnerFloatValue(durationCamera_)  // camera time
            );
      return period;
   }

   /**
    * Update the displayed slice period.
    */
   private void updateActualSlicePeriodLabel() {
      actualSlicePeriodLabel_.setText(
            NumberUtils.doubleToDisplayString(computeActualSlicePeriod()) +
            " ms");
   }
   
   /**
    * Compute the volume duration in ms based on controller's timing settings.
    * @return duration in ms
    */
   private double computeActualVolumeDuration() {
      double duration = getNumSides() * getNumChannels() * 
            (PanelUtils.getSpinnerFloatValue(delaySide_) +
                  getNumSlices() * computeActualSlicePeriod());
      return duration;
   }
   
   /**
    * Update the displayed volume duration.
    */
   private void updateActualVolumeDurationLabel() {
      actualVolumeDurationLabel_.setText(
            NumberUtils.doubleToDisplayString(computeActualVolumeDuration()) +
            " ms");
   }
   
   /**
    * Compute the time lapse duration
    * @return duration in s
    */
   private double computeActualTimeLapseDuration() {
      double duration = (getNumTimepoints() - 1) * 
            PanelUtils.getSpinnerFloatValue(acquisitionInterval_)
            + computeActualVolumeDuration()/1000;
      return duration;
   }
   
   /**
    * Update the displayed time lapse duration.
    */
   private void updateActualTimeLapseDurationLabel() {
      String s = "";
      double duration = computeActualTimeLapseDuration();
      if (duration < 60) {  // less than 1 min
         s += NumberUtils.doubleToDisplayString(duration) + " s";
      } else if (duration < 60*60) { // between 1 min and 1 hour
         s += NumberUtils.doubleToDisplayString(Math.floor(duration/60)) + " min ";
         s += NumberUtils.doubleToDisplayString(Math.round(duration %  60)) + " s";
      } else { // longer than 1 hour
         s += NumberUtils.doubleToDisplayString(Math.floor(duration/(60*60))) + " hr ";
         s +=  NumberUtils.doubleToDisplayString(Math.round((duration % (60*60))/60)) + " min";
      }
      actualTimeLapseDurationLabel_.setText(s);
   }
   
   /**
    * Computes the reset time of the SPIM cameras set on Devices panel.
    * Handles single-side operation.
    * Needed for computing (semi-)optimized slice timing in "easy timing" mode.
    * @return
    */
   private float computeCameraResetTime() {
      float resetTime;
      if (isTwoSided()) {
         resetTime = Math.max(cameras_.computeCameraResetTime(Devices.Keys.CAMERAA),
               cameras_.computeCameraResetTime(Devices.Keys.CAMERAB));
      } else {
         if (isFirstSideA()) {
            resetTime = cameras_.computeCameraResetTime(Devices.Keys.CAMERAA);
         } else {
            resetTime = cameras_.computeCameraResetTime(Devices.Keys.CAMERAB);
         }
      }
      return resetTime;
   }
   
   /**
    * Computes the readout time of the SPIM cameras set on Devices panel.
    * Handles single-side operation.
    * Needed for computing (semi-)optimized slice timing in "easy timing" mode.
    * @return
    */
   private float computeCameraReadoutTime() {
      float readoutTime;
      CameraModes.Keys camMode = CameraModes.getKeyFromPrefCode(
            prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_MODE, 0));
      boolean isOverlap =  (camMode ==  CameraModes.Keys.OVERLAP ||
            camMode == CameraModes.Keys.PSEUDO_OVERLAP);
      if (isTwoSided()) {
         readoutTime = Math.max(cameras_.computeCameraReadoutTime(Devices.Keys.CAMERAA, isOverlap),
               cameras_.computeCameraReadoutTime(Devices.Keys.CAMERAB, isOverlap));
      } else {
         if (isFirstSideA()) {
            readoutTime = cameras_.computeCameraReadoutTime(Devices.Keys.CAMERAA, isOverlap);
         } else {
            readoutTime = cameras_.computeCameraReadoutTime(Devices.Keys.CAMERAB, isOverlap);
         }
      }
      return readoutTime;
   }
   
   /**
    * Makes sure that cameras are assigned to the desired sides and display error message
    * if not (e.g. if single-sided with side B first, then only checks camera for side B)
    * @return true if cameras assigned, false if not
    */
   private boolean checkCamerasAssigned(boolean showWarnings) {
      String firstCamera, secondCamera;
      boolean firstSideA = isFirstSideA(); 
      if (firstSideA) {
         firstCamera = devices_.getMMDevice(Devices.Keys.CAMERAA);
         secondCamera = devices_.getMMDevice(Devices.Keys.CAMERAB);
      } else {
         firstCamera = devices_.getMMDevice(Devices.Keys.CAMERAB);
         secondCamera = devices_.getMMDevice(Devices.Keys.CAMERAA);
      }
      if (firstCamera == null) {
         if (showWarnings) {
            MyDialogUtils.showError("Please select a valid camera for the first side (Imaging Path " +
                  (firstSideA ? "A" : "B") + ") on the Devices Panel");
         }
         return false;
      }
      if (isTwoSided() && secondCamera == null) {
         if (showWarnings) {
            MyDialogUtils.showError("Please select a valid camera for the second side (Imaging Path " +
                  (firstSideA ? "B" : "A") + ") on the Devices Panel.");
         }
         return false;
      }
      return true;
   }
   
   /**
    * used for updateAcquisitionStatus() calls 
    */
   private static enum AcquisitionStatus {
      NONE,
      ACQUIRING,
      WAITING,
      DONE,
   }
   
   private void updateAcquisitionStatus(AcquisitionStatus phase) {
      updateAcquisitionStatus(phase, 0);
   }
   
   private void updateAcquisitionStatus(AcquisitionStatus phase, int secsToNextAcquisition) {
      String text = "";
      switch(phase) {
      case NONE:
         text = "No acquisition in progress.";
         break;
      case ACQUIRING:
         text = "Acquiring time point "
               + NumberUtils.intToDisplayString(numTimePointsDone_)
               + " of "
               + NumberUtils.intToDisplayString(getNumTimepoints());
         break;
      case WAITING:
         text = "Finished "
               + NumberUtils.intToDisplayString(numTimePointsDone_)
               + " of "
               + NumberUtils.intToDisplayString(getNumTimepoints())
               + " time points; next in "
               + NumberUtils.intToDisplayString(secsToNextAcquisition)
               + " s.";
         break;
      case DONE:
         text = "Acquisition finished with "
               + NumberUtils.intToDisplayString(numTimePointsDone_)
               + " time points.";
         break;
      default:
         break;   
      }
      acquisitionStatusLabel_.setText(text);
   }

   /**
    * call setEnabled(boolean) on all components in list
    * @param components
    * @param enabled
    */
   private static void componentsSetEnabled(JComponent[] components, boolean enabled) {
      for (JComponent c : components) {
         c.setEnabled(enabled);
      }
   }
   
   /**
   * call setEnabled(boolean) on all components in frame/panel
    * @param panel
    * @param enabled
    */
   private static void componentsSetEnabled(Container container, boolean enabled) {
      for (Component comp : container.getComponents()) {
         comp.setEnabled(enabled);
      }
   }
   
   /**
    * Sets all the controller's properties according to volume settings
    * and otherwise gets controller all ready for acquisition
    * (except for final trigger).
    * @param side
    * @return false if there was some error that should abort acquisition
    */
   private boolean prepareControllerForAquisition(Devices.Sides side) {
      
      Devices.Keys galvoDevice = Devices.getSideSpecificKey(Devices.Keys.GALVOA, side);
      Devices.Keys piezoDevice = Devices.getSideSpecificKey(Devices.Keys.PIEZOA, side);
      
      boolean ignoreMissingScanner = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(),  
            Properties.Keys.PREFS_IGNORE_MISSING_SCANNER, false);
      boolean haveMissingScanner = !devices_.isValidMMDevice(galvoDevice);
      boolean skipScannerWarnings = ignoreMissingScanner && haveMissingScanner;
      
      // checks to prevent hard-to-diagnose other errors
      if (!ignoreMissingScanner && haveMissingScanner) {
         MyDialogUtils.showError("Scanner device required; please check Devices tab.");
            return false;
      }

      // if we are changing color slice by slice then set controller to do multiple slices per piezo move
      // otherwise just set to 1 slice per piezo move
      int numSlicesPerPiezo = 1;
      if (props_.getPropValueInteger(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_MULTICHANNEL_MODE)
            == MultichannelModes.Keys.SLICE_HW.getPrefCode()) {
         numSlicesPerPiezo = getNumChannels();
      }
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_SLICES_PER_PIEZO,
            numSlicesPerPiezo, skipScannerWarnings);
      
      // if we are changing color volume by volume then set controller to do multiple volumes per start trigger
      // otherwise just set to 1 volume per start trigger
      int numVolumesPerTrigger = 1;
      if (props_.getPropValueInteger(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_MULTICHANNEL_MODE)
            == MultichannelModes.Keys.VOLUME_HW.getPrefCode()) {
         numVolumesPerTrigger = getNumChannels();
      }
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_REPEATS, numVolumesPerTrigger, skipScannerWarnings);
      
      AcquisitionModes.Keys spimMode = (AcquisitionModes.Keys) spimMode_.getSelectedItem();
      
      // figure out the piezo parameters
      int numSlices = getNumSlices();
      float piezoAmplitude =  ( (numSlices - 1) * 
              PanelUtils.getSpinnerFloatValue(stepSize_));
      float piezoCenter = prefs_.getFloat(
            MyStrings.PanelNames.SETUP.toString() + side.toString(), 
            Properties.Keys.PLUGIN_PIEZO_CENTER_POS, 0);
      
      // if we set piezoAmplitude to 0 here then sliceAmplitude will also be 0
      if (spimMode.equals(AcquisitionModes.Keys.NO_SCAN)) {
         piezoAmplitude = 0.0f;
      }
      
      // tweak the parameters if we are using synchronous/overlap mode
      // object is to get exact same piezo/scanner positions in first
      // N frames (piezo/scanner will move to N+1st position but no image taken)
      CameraModes.Keys cameraMode = CameraModes.getKeyFromPrefCode(
            prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_MODE, 0));
      if (cameraMode == CameraModes.Keys.OVERLAP) {
         piezoAmplitude *= ((float)numSlices)/(numSlices-1);
         piezoCenter += piezoAmplitude/(2*numSlices);
         numSlices += 1;
      }
      
      float sliceRate = prefs_.getFloat(
            MyStrings.PanelNames.SETUP.toString() + side.toString(), 
            Properties.Keys.PLUGIN_RATE_PIEZO_SHEET, -80);
      if (MyNumberUtils.floatsEqual(sliceRate, 0.0f)) {
         MyDialogUtils.showError("Rate for slice " + side.toString() + 
               " cannot be zero. Re-do calibration on Setup tab.");
         return false;
      }
      float sliceOffset = prefs_.getFloat(
            MyStrings.PanelNames.SETUP.toString() + side.toString(), 
            Properties.Keys.PLUGIN_OFFSET_PIEZO_SHEET, 0);
      float sliceAmplitude = piezoAmplitude / sliceRate;
      float sliceCenter = (piezoCenter - sliceOffset) / sliceRate;

      // get the micro-mirror card ready
      // SA_AMPLITUDE_X_DEG and SA_OFFSET_X_DEG done by setup tabs
      boolean triangleWave = prefs_.getBoolean(
            MyStrings.PanelNames.SETTINGS.toString(),  
            Properties.Keys.PREFS_SCAN_OPPOSITE_DIRECTIONS, false);
      Properties.Values scanPattern = triangleWave ?
            Properties.Values.SAM_TRIANGLE : Properties.Values.SAM_RAMP;
      props_.setPropValue(galvoDevice, Properties.Keys.SA_PATTERN_X, 
              scanPattern, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SA_AMPLITUDE_Y_DEG,
            sliceAmplitude, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SA_OFFSET_Y_DEG,
            sliceCenter, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.BEAM_ENABLED,
            Properties.Values.NO, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_SLICES,
            numSlices, skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_NUM_SIDES,
            getNumSides(), skipScannerWarnings);
      props_.setPropValue(galvoDevice, Properties.Keys.SPIM_FIRSTSIDE,
            getFirstSide(), skipScannerWarnings);
      
      // get the piezo card ready; skip if no piezo specified
      if (devices_.isValidMMDevice(piezoDevice)) {
         if (spimMode.equals(AcquisitionModes.Keys.SLICE_SCAN_ONLY)) {
            piezoAmplitude = 0.0f;
         }
         props_.setPropValue(piezoDevice,
               Properties.Keys.SA_AMPLITUDE, piezoAmplitude);
         props_.setPropValue(piezoDevice,
               Properties.Keys.SA_OFFSET, piezoCenter);
         props_.setPropValue(piezoDevice,
               Properties.Keys.SPIM_NUM_SLICES, numSlices);
         props_.setPropValue(piezoDevice,
               Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED);
      }
      
      return true;
   }
   
   /**
    * Gets the associated PLogic BNC from the channel (containing preset name) 
    * @param channel
    * @return value 5, 6, 7, or 8; returns 0 if there is an error
    */
   private int getPLogicOutputFromChannel(ChannelSpec channel) {
      try {
         Configuration configData = core_.getConfigData(multiChannelPanel_.getChannelGroup(), channel.config_);
         if (!configData.isPropertyIncluded(devices_.getMMDevice(Devices.Keys.PLOGIC), Properties.Keys.PLOGIC_OUTPUT_CHANNEL.toString())) {
            MyDialogUtils.showError("Must include PLogic \"OutputChannel\" in preset for hardware switching");
            return 0;
         }
         String setting = configData.getSetting(devices_.getMMDevice(Devices.Keys.PLOGIC), Properties.Keys.PLOGIC_OUTPUT_CHANNEL.toString()).getPropertyValue();
         if (setting.equals(Properties.Values.PLOGIC_CHANNEL_BNC5.toString())) {
            return 5;
         } else if (setting.equals(Properties.Values.PLOGIC_CHANNEL_BNC6.toString())) {
            return 6;
         } else if (setting.equals(Properties.Values.PLOGIC_CHANNEL_BNC7.toString())) {
            return 7;
         } else if (setting.equals(Properties.Values.PLOGIC_CHANNEL_BNC8.toString())) {
            return 8;
         } else {
            MyDialogUtils.showError("Channel preset setting must use PLogic \"OutputChannel\" and be set to one of outputs 5-8 only");
            return 0;
         }
      } catch (Exception e) {
         MyDialogUtils.showError(e, "Could not get PLogic output from channel");
         return 0;
      }
   }

   /**
    * Programs the PLogic card for hardware channel switching
    * according to the selections in the Multichannel subpanel
    * @return false if there is a fatal error, true if successful
    */
   private boolean setupHardwareChannelSwitching() {
      
      final int counterLSBAddress = 3;
      final int counterMSBAddress = 4;
      final int laserTriggerAddress = 10;  // this should be (42 || 8) = (TTL1 || manual laser on)
      final int invertAddress = 64;
      
      if (!devices_.isValidMMDevice(Devices.Keys.PLOGIC)) {
         MyDialogUtils.showError("PLogic card required for hardware switching");
         return false;
      }
      
      // set up clock for counters
      MultichannelModes.Keys prefCode = MultichannelModes.getKeyFromPrefCode(
            props_.getPropValueInteger(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_MULTICHANNEL_MODE));
      switch (prefCode) {
      case SLICE_HW:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_CLOCK_LASER);
         break;
      case VOLUME_HW:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_CLOCK_SIDE);
         break;
      default:
         MyDialogUtils.showError("Unknown multichannel mode for hardware switching");
         return false;
      }
      
      // set up hardware counter
      switch (getNumChannels()) {
      case 1:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_1);
      break;
      case 2:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_2);
         break;
      case 3:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_3);
         break;
      case 4:
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
               Properties.Values.PLOGIC_PRESET_COUNT_4);
         break;
      default:
         MyDialogUtils.showError("Hardware channel switching only supports 1-4 channels");
         return false;
      }
      
      // speed things up by turning off updates, will restore value later
      String editCellUpdates = props_.getPropValueString(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES);
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, Properties.Values.NO);
      
      // initialize cells 13-16 which control BNCs 5-8
      for (int cellNum=13; cellNum<=16; cellNum++) {
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, cellNum);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_TYPE, Properties.Values.PLOGIC_AND4);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_2, laserTriggerAddress);
         // note that PLC diSPIM assumes "laser + side" output mode is selected for micro-mirror card
      }
      
      // identify BNC from the preset and set counter inputs for 13-16 appropriately 
      ChannelSpec[] channels = multiChannelPanel_.getUsedChannels();
      boolean[] hardwareChannelUsed = new boolean[4]; // initialized to all false
      for (int channelNum = 0; channelNum < channels.length; channelNum++) {
         // we already know there are between 1 and 4 channels
         int outputNum = getPLogicOutputFromChannel(channels[channelNum]);
         if (outputNum<5) {  // check for error in getPLogicOutputFromChannel()
            // restore update setting
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
            return false;  // already displayed error
         }
         // make sure we don't have multiple Micro-Manager channels using same hardware channel
         if (hardwareChannelUsed[outputNum-5]) {
            // restore update setting
            props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
            MyDialogUtils.showError("Multiple channels cannot use same laser for PLogic triggering");
            return false;
         } else {
            hardwareChannelUsed[outputNum-5] = true;
         }
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_POINTER_POSITION, outputNum + 8);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_1, invertAddress);  // enable this AND4
         // map the channel number to the equivalent addresses for the AND4
         // inputs should be either 3 (for LSB high) or 67 (for LSB low)
         //                     and 4 (for MSB high) or 68 (for MSB low)
         int in3 = (channelNum & 0x01) > 0 ? counterLSBAddress : counterLSBAddress + invertAddress;
         int in4 = (channelNum & 0x02) > 0 ? counterMSBAddress : counterMSBAddress + invertAddress; 
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_3, in3);
         props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_INPUT_4, in4);
      }
      
      // make sure cells 13-16 are controlling BNCs 5-8
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET,
            Properties.Values.PLOGIC_PRESET_BNC5_8_ON_13_16);
      
      // restore update setting
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_EDIT_CELL_UPDATES, editCellUpdates);
      
      return true;
   }

   /**
    * Implementation of acquisition that orchestrates image
    * acquisition itself rather than using the acquisition engine.
    * 
    * This methods is public so that the ScriptInterface can call it
    * Please do not access this yourself directly, instead use the API, e.g.
    *   import org.micromanager.asidispim.api.*;
    *   ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
    *   diSPIM.runAcquisition();
    */
   public void runAcquisition() {
      class acqThread extends Thread {
         acqThread(String threadName) {
            super(threadName);
         }

         @Override
         public void run() {
            ReportingUtils.logMessage("User requested start of diSPIM acquisition.");
            cancelAcquisition_.set(false);
            acquisitionRunning_.set(true);
            updateStartButton();
            boolean success = runAcquisitionPrivate();
            if (!success) {
               ReportingUtils.logError("Fatal error running diSPIM acquisition.");
            }
            acquisitionRunning_.set(false);
            updateStartButton();
         }
      }            
      acqThread acqt = new acqThread("diSPIM Acquisition");
      acqt.start(); 
   }
   
   /**
    * Actually runs the acquisition; does the dirty work of setting
    * up the controller, the circular buffer, starting the cameras,
    * grabbing the images and putting them into the acquisition, etc.
    * @return true if ran without any fatal errors.
    */
   private boolean runAcquisitionPrivate() {
      
      if (gui_.isAcquisitionRunning()) {
         MyDialogUtils.showError("An acquisition is already running");
         return false;
      }
      
      boolean liveModeOriginally = gui_.isLiveModeOn();
      if (liveModeOriginally) {
         gui_.enableLiveMode(false);
      }
      
      // stop the serial traffic for position updates during acquisition
      posUpdater_.pauseUpdates(true);
      
      // get MM device names for first/second cameras to acquire
      String firstCamera, secondCamera;
      boolean firstSideA = isFirstSideA(); 
      if (firstSideA) {
         firstCamera = devices_.getMMDevice(Devices.Keys.CAMERAA);
         secondCamera = devices_.getMMDevice(Devices.Keys.CAMERAB);
      } else {
         firstCamera = devices_.getMMDevice(Devices.Keys.CAMERAB);
         secondCamera = devices_.getMMDevice(Devices.Keys.CAMERAA);
      }
      
      boolean sideActiveA, sideActiveB;
      boolean twoSided = isTwoSided();
      if (twoSided) {
         sideActiveA = true;
         sideActiveB = true;
      } else {
         secondCamera = null;
         if (firstSideA) {
            sideActiveA = true;
            sideActiveB = false;
         } else {
            sideActiveA = false;
            sideActiveB = true;
         }
      }
      
      int nrSides = getNumSides();
      int nrSlices = getNumSlices();
      int nrChannels = getNumChannels();
      
      // set up channels
      int nrChannelsSoftware = nrChannels;  // how many times we trigger the controller
      int nrSlicesSoftware = nrSlices;
      String originalChannelConfig = "";
      boolean changeChannelPerVolumeSoftware = false;
      boolean useChannels =  multiChannelPanel_.isPanelEnabled();
      MultichannelModes.Keys multichannelMode = MultichannelModes.Keys.NONE;
      if (useChannels) {
         if (nrChannels < 1) {
            MyDialogUtils.showError("\"Channels\" is checked, but no channels are selected");
            return false;
         }
         // get current channel so that we can restore it
         originalChannelConfig = multiChannelPanel_.getCurrentConfig();
         multichannelMode = MultichannelModes.getKeyFromPrefCode(
               props_.getPropValueInteger(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_MULTICHANNEL_MODE));
         switch (multichannelMode) {
         case VOLUME:
            changeChannelPerVolumeSoftware = true;
            multiChannelPanel_.initializeChannelCycle();
            break;
         case VOLUME_HW:
         case SLICE_HW:
            if (!setupHardwareChannelSwitching()) {
               return false;
            }
            nrChannelsSoftware = 1;
            nrSlicesSoftware = nrSlices * nrChannels; 
            break;
         default:
            MyDialogUtils.showError("Unsupported multichannel mode \"" + multichannelMode.toString() + "\"");
            return false;
         }
      }
      
      // set up XY positions
      int nrPositions = 1;
      boolean usePositions = usePositionsCB_.isSelected();
      PositionList positionList = new PositionList();
      if (usePositions) {
         try {
            positionList = gui_.getPositionList();
            nrPositions = positionList.getNumberOfPositions();
         } catch (MMScriptException ex) {
            MyDialogUtils.showError(ex, "Error getting position list for multiple XY positions");
         }
         if (nrPositions < 1) {
            MyDialogUtils.showError("\"Positions\" is checked, but no positions are in position list");
            return false;
         }
      }
      
      // make sure we have cameras selected
      if (!checkCamerasAssigned(true)) {
         return false;
      }
      
      // make sure slice timings are up to date 
      if (!advancedSliceTimingCB_.isSelected()) {
         if(!isSliceTimingUpToDate()) {
            MyDialogUtils.showError("Slice timing is not up to date, please recalculate.");
            return false;
         }
      }
      
      float cameraReadoutTime = computeCameraReadoutTime();
      double exposureTime = sliceTiming_.cameraExposure;
      
      boolean show = !hideCB_.isSelected();
      boolean save = saveCB_.isSelected();
      boolean singleTimePointViewers = separateTimePointsCB_.isSelected();
      String rootDir = rootField_.getText();

      int nrRepeats;  // how many acquisition windows to open
      int nrFrames;   // how many Micro-manager "frames" = time points to take
      if (singleTimePointViewers) {
         nrFrames = 1;
         nrRepeats = getNumTimepoints();
      } else {
         nrFrames = getNumTimepoints();
         nrRepeats = 1;
      }
      long timepointsIntervalMs = Math.round(
              PanelUtils.getSpinnerFloatValue(acquisitionInterval_) * 1000d);
      
      AcquisitionModes.Keys spimMode = (AcquisitionModes.Keys) spimMode_.getSelectedItem();
      
      boolean autoShutter = core_.getAutoShutter();
      boolean shutterOpen = false;

      // more sanity checks
      double lineScanTime = computeActualSlicePeriod();
      if (exposureTime + cameraReadoutTime > lineScanTime) {
         MyDialogUtils.showError("Exposure time is longer than time needed for a line scan.\n" +
                 "This will result in dropped frames.\n" +
                 "Please change input");
         return false;
      }
      double volumeDuration = computeActualVolumeDuration();
      if (getNumTimepoints() > 1) {
         if (timepointsIntervalMs < volumeDuration) {
            MyDialogUtils.showError("Time point interval shorter than" +
                  " the time to collect a single volume.\n");
            return false;
         }
         // TODO verify if 0.5 second is good value for overhead time
         if (timepointsIntervalMs < (volumeDuration + 500)) {
            MyDialogUtils.showError("Micro-Manager requires ~0.5 second overhead time "
                  + "to finish up a volume before starting next one. "
                  + "Pester the developers if you need faster, it is probably possible.");
          return false;
         }
      }
      if (nrRepeats > 10 && separateTimePointsCB_.isSelected()) {
         if (!MyDialogUtils.getConfirmDialogResult(
               "This will generate " + nrRepeats + " separate windows. "
               + "Do you really want to proceed?",
               JOptionPane.OK_CANCEL_OPTION)) {
            return false;
         }
      }
      if (hideCB_.isSelected() && !saveCB_.isSelected()) {
         MyDialogUtils.showError("Must save data to disk if viewer is hidden");
         return false;
      }
      if (hideCB_.isSelected() && separateTimePointsCB_.isSelected()) {
         MyDialogUtils.showError("Cannot have hidden viewer with separate viewers per time point." +
               "Pester the developers if you really need this.");
         return false;
      }
      
      // it appears the circular buffer, which is used by both cameras, can only have one 
      // image size setting => we require same image height and width for second camera if two-sided
      if (twoSided) {
         try {
            Rectangle roi_1 = core_.getROI(firstCamera);
            Rectangle roi_2 = core_.getROI(secondCamera);
            if (roi_1.width != roi_2.width || roi_1.height != roi_2.height) {
               MyDialogUtils.showError("Camera ROI height and width must be equal because of Micro-Manager's circular buffer");
               return false;
            }
         } catch (Exception ex) {
            MyDialogUtils.showError(ex, "Problem getting camera ROIs");
         }
      }

      // empty out circular buffer
      try {
         core_.clearCircularBuffer();
      } catch (Exception ex) {
         MyDialogUtils.showError(ex, "Error emptying out the circular buffer");
         return false;
      }
      
      cameras_.setSPIMCamerasForAcquisition(true);

      numTimePointsDone_ = 0;
      
      // force saving as image stacks, not individual files
      // implementation assumes just two options, either 
      //  TaggedImageStorageDiskDefault.class or TaggedImageStorageMultipageTiff.class
      boolean separateImageFilesOriginally =
            ImageUtils.getImageStorageClass().equals(TaggedImageStorageDiskDefault.class);
      ImageUtils.setImageStorageClass(TaggedImageStorageMultipageTiff.class);
      
      // Set up controller SPIM parameters (including from Setup panel settings)
      if (sideActiveA) {
         boolean success = prepareControllerForAquisition(Devices.Sides.A);
         if (! success) {
            return false;
         }
      }
      if (sideActiveB) {
         boolean success = prepareControllerForAquisition(Devices.Sides.B);
         if (! success) {
            return false;
         }
      }
      
      // sets PLogic BNC3 output high to indicate acquisition is going on
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET, 
            Properties.Values.PLOGIC_PRESET_3, true);

      long acqStart = System.currentTimeMillis();
      boolean nonfatalError = false;

      // do not want to return from within this loop
      // loop is executed once per acquisition (i.e. once if separate viewers isn't selected)
      for (int tp = 0; tp < nrRepeats; tp++) {
         BlockingQueue<TaggedImage> bq = new LinkedBlockingQueue<TaggedImage>(10);
         String acqName;
         if (singleTimePointViewers) {
            acqName = gui_.getUniqueAcquisitionName(nameField_.getText() + "_" + tp);
         } else {
            acqName = gui_.getUniqueAcquisitionName(nameField_.getText());
         }
         try {
            // check for stop button before each acquisition
            if (cancelAcquisition_.get()) {
               throw new IllegalMonitorStateException("User stopped the acquisition");
            }
            
            ReportingUtils.logMessage("diSPIM plugin starting acquisition " + acqName);
            
            if (spimMode.equals(AcquisitionModes.Keys.NO_SCAN) && ! singleTimePointViewers) {
               // swap nrFrames and nrSlices
               gui_.openAcquisition(acqName, rootDir, nrSlices, nrSides * nrChannels,
                  nrFrames, nrPositions, show, save);
            } else {
               gui_.openAcquisition(acqName, rootDir, nrFrames, nrSides * nrChannels,
                  nrSlices, nrPositions, show, save);
            }
            core_.setExposure(firstCamera, exposureTime);
            if (twoSided) {
               core_.setExposure(secondCamera, exposureTime);
            }
            
            // set up channels (side A/B is treated as channel too)
            Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, 
               Color.PINK};
            if (useChannels) {
               ChannelSpec[] channels = multiChannelPanel_.getUsedChannels();
               for (int i = 0; i < channels.length; i++) {
                  String chName = "-" + channels[i].config_;
                  // same algorithm for channel index vs. specified channel and side as below
                  int channelIndex = i;
                  if (twoSided) {
                     channelIndex *= 2;
                  }
                  gui_.setChannelName(acqName, channelIndex, firstCamera + chName);
                  int cIndex = channelIndex % colors.length;
                  gui_.setChannelColor(acqName, channelIndex, colors[cIndex]);
                  if (twoSided) {
                     gui_.setChannelName(acqName, channelIndex + 1, secondCamera + chName);
                     cIndex = (channelIndex +1) % colors.length;
                     gui_.setChannelColor(acqName, channelIndex + 1, colors[cIndex]);
                  }
               }
            } else {
               gui_.setChannelName(acqName, 0, firstCamera);
               if (twoSided) {
                  gui_.setChannelName(acqName, 1, secondCamera);
                  gui_.setChannelColor(acqName, 0, colors[0]);
                  gui_.setChannelColor(acqName, 1, colors[1]);
               }
            }
            
            // initialize acquisition
            gui_.initializeAcquisition(acqName, (int) core_.getImageWidth(),
                    (int) core_.getImageHeight(), (int) core_.getBytesPerPixel(),
                    (int) core_.getImageBitDepth());
            
            // These metadata have to added after initialization, otherwise
            // they will not be shown?!
            gui_.setAcquisitionProperty(acqName, "NumberOfSides", 
                    NumberUtils.doubleToDisplayString(getNumSides()) );
            String firstSide = "B";
            if (firstSideA) {
               firstSide = "A";
            }            
            gui_.setAcquisitionProperty(acqName, "FirstSide", firstSide);
            gui_.setAcquisitionProperty(acqName, "SlicePeriod_ms", 
                  actualSlicePeriodLabel_.getText());
            gui_.setAcquisitionProperty(acqName, "LaserExposure_ms",
                  NumberUtils.doubleToDisplayString(
                        (double)PanelUtils.getSpinnerFloatValue(durationLaser_)));
            gui_.setAcquisitionProperty(acqName, "VolumeDuration", 
                    actualVolumeDurationLabel_.getText());
            gui_.setAcquisitionProperty(acqName, "SPIMmode", 
                    ((AcquisitionModes.Keys) spimMode_.getSelectedItem()).toString());
            // Multi-page TIFF saving code wants this one:
            // TODO: support other types than GRAY16
            gui_.setAcquisitionProperty(acqName, "PixelType", "GRAY16");
            gui_.setAcquisitionProperty(acqName, "z-step_um",  
                  NumberUtils.doubleToDisplayString(getStepSizeUm()) );
            
            // get circular buffer ready
            // do once here but not per-acquisition; need to ensure ROI changes registered
            core_.initializeCircularBuffer();
            
            // TODO: use new acquisition interface that goes through the pipeline
            //gui_.setAcquisitionAddImageAsynchronous(acqName); 
            MMAcquisition acq = gui_.getAcquisition(acqName);
        
            // Dive into MM internals since script interface does not support pipelines
            ImageCache imageCache = acq.getImageCache();
            VirtualAcquisitionDisplay vad = acq.getAcquisitionWindow();
            imageCache.addImageCacheListener(vad);

            // Start pumping images into the ImageCache
            DefaultTaggedImageSink sink = new DefaultTaggedImageSink(bq, imageCache);
            sink.start();

            // Loop over all the times we trigger the controller's acquisition
            // If the interval between frames is shorter than the time to acquire
            // them, we can switch to hardware based solution.  Not sure how important 
            // that feature is, so leave it out for now.
            for (int timePoint = 0; timePoint < nrFrames; timePoint++) {

               // handle intervals between time points
               long acqNow = System.currentTimeMillis();
               long delay = acqStart + timePoint * timepointsIntervalMs - acqNow;
               while (delay > 0 && !cancelAcquisition_.get()) {
                  updateAcquisitionStatus(AcquisitionStatus.WAITING, (int) (delay / 1000));
                  long sleepTime = Math.min(1000, delay);
                  Thread.sleep(sleepTime);
                  acqNow = System.currentTimeMillis();
                  delay = acqStart + timePoint * timepointsIntervalMs - acqNow;
               }

               // check for stop button before each time point
               if (cancelAcquisition_.get()) {
                  throw new IllegalMonitorStateException("User stopped the acquisition");
               }

               numTimePointsDone_++;
               updateAcquisitionStatus(AcquisitionStatus.ACQUIRING);

               // loop over all positions
               for (int positionNum = 0; positionNum < nrPositions; positionNum++) {
                  if (usePositions) {
                     // blocking call; will wait for stages to move
                     MultiStagePosition.goToPosition(positionList.getPosition(positionNum), core_);
                     
                     // wait any extra time the user requests
                     Thread.sleep(Math.round(PanelUtils.getSpinnerFloatValue(positionDelay_)));
                  }

                  // loop over all the times we trigger the controller
                  for (int channelNum = 0; channelNum < nrChannelsSoftware; channelNum++) {

                     // start the cameras
                     core_.startSequenceAcquisition(firstCamera, nrSlicesSoftware, 0, true);
                     if (twoSided) {
                        core_.startSequenceAcquisition(secondCamera, nrSlicesSoftware, 0, true);
                     }

                     // deal with shutter
                     if (autoShutter) {
                        core_.setAutoShutter(false);
                        shutterOpen = core_.getShutterOpen();
                        if (!shutterOpen) {
                           core_.setShutterOpen(true);
                        }
                     }

                     // deal with channel if needed (hardware channel switching doesn't happen here)
                     if (changeChannelPerVolumeSoftware) {
                        multiChannelPanel_.selectNextChannel();
                     }

                     // trigger the Tiger controller
                     // TODO generalize this for different ways of running SPIM
                     // only matters which device we trigger if there are two micro-mirror cards
                     if (firstSideA) {
                        props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SPIM_STATE,
                              Properties.Values.SPIM_RUNNING, true);
                     } else {
                        props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SPIM_STATE,
                              Properties.Values.SPIM_RUNNING, true);
                     }

                     ReportingUtils.logDebugMessage("Starting time point " + (timePoint+1) + " of " + nrFrames
                           + " with channel number " + channelNum);

                     // Wait for first image to create ImageWindow, so that we can be sure about image size
                     // Do not actually grab first image here, just make sure it is there
                     long start = System.currentTimeMillis();
                     long now = start;
                     long timeout;  // wait 5 seconds for first image to come
                     timeout = Math.max(5000, Math.round(1.2*computeActualVolumeDuration()));
                     while (core_.getRemainingImageCount() == 0 && (now - start < timeout)
                           && !cancelAcquisition_.get()) {
                        now = System.currentTimeMillis();
                        Thread.sleep(5);
                     }
                     if (now - start >= timeout) {
                        throw new Exception("Camera did not send first image within a reasonable time");
                     }

                     // grab all the images from the cameras, put them into the acquisition
                     int[] frNumber = new int[nrChannels*2];  // keep track of how many frames we have received for each "channel" (MM channel is our channel * 2 for the 2 cameras)
                     int[] cameraFrNumber = new int[2];     // keep track of how many frames we have received from the camera
                     boolean done = false;
                     long timeout2;  // how long to wait between images before timing out
                     timeout2 = Math.max(2000, Math.round(5*computeActualSlicePeriod()));
                     start = System.currentTimeMillis();
                     long last = start;
                     try {
                        while ((core_.getRemainingImageCount() > 0
                              || core_.isSequenceRunning(firstCamera)
                              || (twoSided && core_.isSequenceRunning(secondCamera)))
                              && !done) {
                           now = System.currentTimeMillis();
                           if (core_.getRemainingImageCount() > 0) {  // we have an image to grab
                              TaggedImage timg = core_.popNextTaggedImage();
                              String camera = (String) timg.tags.get("Camera");
                              
                              // figure out which channel index the acquisition is using
                              int cameraIndex = camera.equals(firstCamera) ? 0: 1;
                              int channelIndex;
                              switch (multichannelMode) {
                              case NONE:
                              case VOLUME:
                                 channelIndex = channelNum;
                                 break;
                              case VOLUME_HW:
                                 channelIndex = cameraFrNumber[cameraIndex] / nrSlices;  // want quotient only
                                 break;
                              case SLICE_HW:
                                 channelIndex = cameraFrNumber[cameraIndex] % nrChannels;  // want modulo arithmetic
                                 break;
                              default:
                                 // should never get here
                                 throw new Exception("Undefined channel mode");
                              }
                              
                              // 2nd camera always gets odd channel index 
                              // second side always comes after first side
                              if (twoSided) {
                                 channelIndex *= 2;
                              }
                              channelIndex += cameraIndex;
                              
                              // add image to acquisition
                              if (spimMode.equals(AcquisitionModes.Keys.NO_SCAN) && ! singleTimePointViewers) {
                                 // create time series for no scan
                                 addImageToAcquisition(acqName,
                                         frNumber[channelIndex], channelIndex, timePoint, 
                                         positionNum, now - acqStart, timg, bq);
                              } else { // standard, create Z-stacks
                                 addImageToAcquisition(acqName, timePoint, channelIndex,
                                       frNumber[channelIndex], positionNum,
                                       now - acqStart, timg, bq);
                              }
                              
                              // update our counters
                              frNumber[channelIndex]++;
                              cameraFrNumber[cameraIndex]++;
                              last = now;  // keep track of last image timestamp
                              
                           } else {  // no image ready yet
                              done = cancelAcquisition_.get();
                              Thread.sleep(1);
                              if (now - last >= timeout2) {
                                 ReportingUtils.logError("Camera did not send all expected images within" +
                                       " a reasonable period for timepoint " + (timePoint+1) + ".  Continuing anyway.");
                                 // allow other time points to continue by stopping acquisition manually
                                 // (in normal case the sequence acquisition stops itself after
                                 // all the expected images are returned)
                                 if (core_.isSequenceRunning(firstCamera)) {
                                    core_.stopSequenceAcquisition(firstCamera);
                                 }
                                 if (twoSided && core_.isSequenceRunning(secondCamera)) {
                                    core_.stopSequenceAcquisition(secondCamera);
                                 }
                                 nonfatalError = true;
                                 done = true;
                              }
                           }
                        }
                        
                        // update count if we stopped in the middle
                        if (cancelAcquisition_.get()) {
                           numTimePointsDone_--;
                        }
                        
                     } catch (InterruptedException iex) {
                        MyDialogUtils.showError(iex);
                     }
                  }
               }
            }
         } catch (IllegalMonitorStateException ex) {
            // do nothing, the acquisition was simply halted during its operation
         } catch (MMScriptException mex) {
            MyDialogUtils.showError(mex);
         } catch (Exception ex) {
            MyDialogUtils.showError(ex);
         } finally {  // end of this acquisition (could be about to restart if separate viewers)
            try {
               if (core_.isSequenceRunning(firstCamera)) {
                  core_.stopSequenceAcquisition(firstCamera);
               }
               if (twoSided && core_.isSequenceRunning(secondCamera)) {
                  core_.stopSequenceAcquisition(secondCamera);
               }
               if (autoShutter) {
                  core_.setAutoShutter(true);
                  if (shutterOpen) {
                     core_.setShutterOpen(false);
                  }
               }
               
               bq.put(TaggedImageQueue.POISON);
               // TODO: evaluate closeAcquisition call
               // at the moment, the Micro-Manager api has a bug that causes 
               // a closed acquisition not be really closed, causing problems
               // when the user closes a window of the previous acquisition
               // changed r14705 (2014-11-24)
               // gui_.closeAcquisition(acqName);
               ReportingUtils.logMessage("diSPIM plugin acquisition " + acqName + 
                     " took: " + (System.currentTimeMillis() - acqStart) + "ms");
               
            } catch (Exception ex) {
               // exception while stopping sequence acquisition, not sure what to do...
               MyDialogUtils.showError(ex, "Problem while finsihing acquisition");
            }
         }

      }
      
      // cleanup after end of all acquisitions
      
      // reset channel to original if we clobbered it
      if (useChannels) {
         multiChannelPanel_.setConfig(originalChannelConfig);
      }
      
      // the controller will end with both beams disabled and scan off so reflect
      // that in device properties
      props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.BEAM_ENABLED,
            Properties.Values.NO, true);
      props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.BEAM_ENABLED,
            Properties.Values.NO, true);
      props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SA_MODE_X,
            Properties.Values.SAM_DISABLED, true);
      props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SA_MODE_X,
            Properties.Values.SAM_DISABLED, true);
      
      // sets BNC3 output low again
      // this only happens after images have all been received (or timeout occurred)
      // but if using DemoCam devices then it happens too early
      // at least part of the problem is that both DemoCam devices "acquire" at the same time
      // instead of actually obeying the controller's triggers
      // as a result with DemoCam the side select (BNC4) isn't correct
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.PLOGIC_PRESET, 
            Properties.Values.PLOGIC_PRESET_2, true);

      // move piezos back to center (neutral) position
      if (devices_.isValidMMDevice(Devices.Keys.PIEZOA)) {
         positions_.setPosition(Devices.Keys.PIEZOA, Joystick.Directions.NONE, 0.0);
      }
      if (devices_.isValidMMDevice(Devices.Keys.PIEZOB)) {
         positions_.setPosition(Devices.Keys.PIEZOB, Joystick.Directions.NONE, 0.0);
      }
      
      if (cancelAcquisition_.get()) {  // if user stopped us in middle
         // make sure to stop the SPIM state machine in case the acquisition was cancelled
         props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SPIM_STATE,
               Properties.Values.SPIM_IDLE, true);
         props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SPIM_STATE,
               Properties.Values.SPIM_IDLE, true);
      }
      updateAcquisitionStatus(AcquisitionStatus.DONE);
      posUpdater_.pauseUpdates(false);
      
      if (separateImageFilesOriginally) {
         ImageUtils.setImageStorageClass(TaggedImageStorageDiskDefault.class);
      }
      cameras_.setSPIMCamerasForAcquisition(false);
      if (liveModeOriginally) {
         gui_.enableLiveMode(true);
      }
      
      if (nonfatalError) {
         MyDialogUtils.showError("Non-fatal error occurred during acquisition, see core log for details");
      }

      return true;
   }

   @Override
   public void saveSettings() {
      prefs_.putString(panelName_, Properties.Keys.PLUGIN_DIRECTORY_ROOT,
              rootField_.getText());
      prefs_.putString(panelName_, Properties.Keys.PLUGIN_NAME_PREFIX,
              nameField_.getText());

      // save controller settings
      props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SAVE_CARD_SETTINGS,
              Properties.Values.DO_SSZ, true);
      props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SAVE_CARD_SETTINGS,
              Properties.Values.DO_SSZ, true);
      props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SAVE_CARD_SETTINGS,
              Properties.Values.DO_SSZ, true);
      props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SAVE_CARD_SETTINGS,
              Properties.Values.DO_SSZ, true);
      props_.setPropValue(Devices.Keys.PLOGIC, Properties.Keys.SAVE_CARD_SETTINGS,
            Properties.Values.DO_SSZ, true);

   }

   /**
    * Gets called when this tab gets focus. Refreshes values from properties.
    */
   @Override
   public void gotSelected() {
      posUpdater_.pauseUpdates(true);
      props_.callListeners();
      if (navigationJoysticksCB_.isSelected()) {
         if (ASIdiSPIM.getFrame() != null) {
            ASIdiSPIM.getFrame().getNavigationPanel().doJoystickSettings();
         }
      } else {
         joystick_.unsetAllJoysticks();  // disable all joysticks on this tab
      }
      sliceFrameAdvanced_.setVisible(advancedSliceTimingCB_.isSelected());
      posUpdater_.pauseUpdates(false);
   }

   /**
    * called when tab looses focus.
    */
   @Override
   public void gotDeSelected() {
      sliceFrameAdvanced_.setVisible(false);
      saveSettings();
   }

   @Override
   public void devicesChangedAlert() {
      devices_.callListeners();
   }
   
   /**
    * Gets called when enclosing window closes
    */
   public void windowClosing() {
      sliceFrameAdvanced_.savePosition();
      sliceFrameAdvanced_.dispose();
   }
   
   @Override
   public void refreshDisplay() {
      updateDurationLabels();
   }

   private void setRootDirectory(JTextField rootField) {
      File result = FileDialogs.openDir(null,
              "Please choose a directory root for image data",
              MMStudio.MM_DATA_SET);
      if (result != null) {
         rootField.setText(result.getAbsolutePath());
      }
   }

   /**
    * The basic method for adding images to an existing data set. If the
    * acquisition was not previously initialized, it will attempt to initialize
    * it from the available image data. This version uses a blocking queue and is 
    * much faster than the one currently implemented in the ScriptInterface
    * Eventually, this function should be replaced by the ScriptInterface version
    * of the same.
    * @param name - named acquisition to add image to
    * @param frame - frame nr at which to insert the image
    * @param channel - channel at which to insert image
    * @param slice - (z) slice at which to insert image
    * @param position - position at which to insert image
    * @param ms - Time stamp to be added to the image metadata
    * @param taggedImg - image + metadata to be added
    * @param bq - Blocking queue to which the image should be added.  This queue
    * should be hooked up to the ImageCache belonging to this acquisitions
    * @throws java.lang.InterruptedException
    * @throws org.micromanager.utils.MMScriptException
    */
   public void addImageToAcquisition(String name,
           int frame,
           int channel,
           int slice,
           int position,
           long ms,
           TaggedImage taggedImg,
           BlockingQueue<TaggedImage> bq) throws MMScriptException, InterruptedException {

      MMAcquisition acq = gui_.getAcquisition(name);

      // verify position number is allowed 
      if (acq.getPositions() <= position) {
         throw new MMScriptException("The position number must not exceed declared"
               + " number of positions (" + acq.getPositions() + ")");
      }

      // verify that channel number is allowed 
      if (acq.getChannels() <= channel) {
         throw new MMScriptException("The channel number must not exceed declared"
               + " number of channels (" + + acq.getChannels() + ")");
      }

      JSONObject tags = taggedImg.tags;

      if (!acq.isInitialized()) {
         throw new MMScriptException("Error in the ASIdiSPIM logic.  Acquisition should have been initialized");
      }

      // create required coordinate tags
      try {
         MDUtils.setFrameIndex(tags, frame);
         tags.put(MMTags.Image.FRAME, frame);
         MDUtils.setChannelIndex(tags, channel);
         MDUtils.setSliceIndex(tags, slice);
         MDUtils.setPositionIndex(tags, position);
         MDUtils.setElapsedTimeMs(tags, ms);
         MDUtils.setImageTime(tags, MDUtils.getCurrentTime());
         MDUtils.setZStepUm(tags, PanelUtils.getSpinnerFloatValue(stepSize_));
         
         if (!tags.has(MMTags.Summary.SLICES_FIRST) && !tags.has(MMTags.Summary.TIME_FIRST)) {
            // add default setting
            tags.put(MMTags.Summary.SLICES_FIRST, true);
            tags.put(MMTags.Summary.TIME_FIRST, false);
         }

         if (acq.getPositions() > 1) {
            // if no position name is defined we need to insert a default one
            if (tags.has(MMTags.Image.POS_NAME)) {
               tags.put(MMTags.Image.POS_NAME, "Pos" + position);
            }
         }

         // update frames if necessary
         if (acq.getFrames() <= frame) {
            acq.setProperty(MMTags.Summary.FRAMES, Integer.toString(frame + 1));
         }

      } catch (JSONException e) {
         throw new MMScriptException(e);
      }

      bq.put(taggedImg);
   }
   
   
}