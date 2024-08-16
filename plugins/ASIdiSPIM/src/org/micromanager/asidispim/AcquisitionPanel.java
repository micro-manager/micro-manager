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
import org.micromanager.asidispim.Data.MultichannelModes;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Data.Devices.Sides;
import org.micromanager.asidispim.Data.Icons;
import org.micromanager.asidispim.Data.AcquisitionSettings;
import org.micromanager.asidispim.Data.ChannelSpec;
import org.micromanager.asidispim.Data.Joystick.Directions;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.MyNumberUtils;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.asidispim.Utils.SliceTiming;
import org.micromanager.asidispim.Utils.StagePositionUpdater;
import org.micromanager.asidispim.Utils.ControllerUtils;
import org.micromanager.asidispim.Utils.DeviceUtils;
import org.micromanager.asidispim.Utils.AutofocusUtils;
import org.micromanager.asidispim.Utils.MovementDetector;
import org.micromanager.asidispim.Utils.MovementDetector.Method;
import org.micromanager.asidispim.api.ASIdiSPIMException;
import org.micromanager.asidispim.api.RunnableType;
import org.micromanager.asidispim.table.AcquisitionTableFrame;
import org.micromanager.asidispim.api.AcquisitionStatus;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.DefaultFormatter;
import javax.swing.BorderFactory;

import net.miginfocom.swing.MigLayout;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.StagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.ImageCache;
import org.micromanager.api.MMTags;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.ComponentTitledBorder;
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
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;


/**
 * The acquisition panel.
 * 
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class AcquisitionPanel extends ListeningJPanel implements DevicesListenerInterface {

   private final Devices devices_;
   private final Properties props_;
   private final Cameras cameras_;
   private final Prefs prefs_;
   private final ControllerUtils controller_;
   private final AutofocusUtils autofocus_;
   private final Positions positions_;
   private final CMMCore core_;
   private final ScriptInterface gui_;
   private final JCheckBox advancedSliceTimingCB_;
   private final JSpinner numSlices_;
   private final JComboBox numSides_;
   private final JComboBox firstSide_;
   private final JSpinner numScansPerSlice_;
   private final JSpinner lineScanDuration_;
   private final JSpinner delayScan_;
   private final JSpinner delayLaser_;
   private final JSpinner delayCamera_;
   private final JSpinner durationCamera_;  // NB: not the same as camera exposure
   private final JSpinner exposureCamera_;  // NB: only used in advanced timing mode
   private final JCheckBox alternateBeamScanCB_;
   private final JSpinner durationLaser_;
   private final JSpinner delaySide_;
   private final JLabel actualSlicePeriodLabel_;
   private final JLabel actualVolumeDurationLabel_;
   private final JLabel actualTimeLapseDurationLabel_;
   private final JSpinner numTimepoints_;
   private final JSpinner acquisitionInterval_;
   private final JToggleButton buttonStart_;
   private final JButton buttonTestAcq_;
   private final JPanel volPanel_;
   private final JPanel sliceAdvancedPanel_;
   private final JPanel timepointPanel_;
   private final JPanel savePanel_;
   private final JPanel durationPanel_;
   private final JPanel positionPanel_;
   private final JFormattedTextField rootField_;
   private final JFormattedTextField prefixField_;
   private final JLabel acquisitionStatusLabel_;
   private int numTimePointsDone_;
   private int numPositionsDone_;
   private final AtomicBoolean cancelAcquisition_ = new AtomicBoolean(false);  // true if we should stop acquisition
   private final AtomicBoolean acquisitionRequested_ = new AtomicBoolean(false);  // true if acquisition has been requested to start or is underway
   private final AtomicBoolean acquisitionRunning_ = new AtomicBoolean(false);   // true if the acquisition is actually underway
   private final StagePositionUpdater posUpdater_;
   private final JSpinner stepSize_;
   private final JLabel desiredSlicePeriodLabel_;
   private final JSpinner desiredSlicePeriod_;
   private final JLabel desiredLightExposureLabel_;
   private final JSpinner desiredLightExposure_;
   private final JCheckBox minSlicePeriodCB_;
   private final JCheckBox separateTimePointsCB_;
   private final JCheckBox saveCB_;
   private final JComboBox spimMode_;
   private final JCheckBox navigationJoysticksCB_;
   private final JCheckBox usePositionsCB_;
   private final JSpinner positionDelay_;
   private final JCheckBox useTimepointsCB_;
   private final JCheckBox useAutofocusCB_;
   private final JCheckBox useMovementCorrectionCB_;
   private final JPanel leftColumnPanel_;
   private final JPanel centerColumnPanel_;
   private final JPanel rightColumnPanel_;
   private final MMFrame sliceFrameAdvanced_;
   private SliceTiming sliceTiming_;
   private final MultiChannelSubPanel multiChannelPanel_;
   private final Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA,
            Color.PINK, Color.CYAN, Color.YELLOW, Color.ORANGE};
   private String lastAcquisitionPath_;
   private String lastAcquisitionName_;
   private AcquisitionStatus acquisitionStatus_;
   private MMAcquisition acq_;
   private String[] channelNames_;
   private int nrRepeats_;  // how many separate acquisitions to perform
   private boolean resetXaxisSpeed_;
   private boolean resetXSupaxisSpeed_;
   private boolean resetZaxisSpeed_;
   private final AcquisitionPanel acquisitionPanel_;
   private final JComponent[] simpleTimingComponents_;
   private final JPanel slicePanel_;
   private final JPanel slicePanelContainer_;
   private final JPanel lightSheetPanel_;
   private final JPanel normalPanel_;
   double zStepUm_;  // hold onto local copy so we don't have to keep querying
   double xPositionUm_;  // hold onto local copy so we don't have to keep querying
   double yPositionUm_;  // hold onto local copy so we don't have to keep querying
   double zPositionUm_;  // hold onto local copy so we don't have to keep querying
   private final JButton gridButton_; 
   private final XYZGridPanel gridPanel_;
   private final MMFrame gridFrame_;
   private ImagePlus overviewIP_;
   private Point2D.Double overviewMin_;
   private Point2D.Double overviewMax_;
   private double overviewWidthExpansion_ = 0.0;
   private double overviewDownsampleY_ = 1;
   private double overviewPixelSize_ = 1;
   private double overviewCompressX_ = 1;
   private double extraChannelOffset_ = 0.0;
   private final List<Runnable> runnablesStartAcquisition_;
   private final List<Runnable> runnablesEndAcquisition_;
   private final List<Runnable> runnablesStartTimepoint_;
   private final List<Runnable> runnablesEndTimepoint_;
   private final List<Runnable> runnablesStartPosition_;
   private final List<Runnable> runnablesEndPosition_;
   public String acqName_;
   private boolean updatingTiming_;  // when true don't update timing to avoid recursion with recalculateTimingDisplayCL and recalculateTimingDisplayAL
   
   private static final int XYSTAGETIMEOUT = 20000;
   
   public AcquisitionPanel(ScriptInterface gui, 
           Devices devices, 
           Properties props, 
           Cameras cameras, 
           Prefs prefs,
           StagePositionUpdater posUpdater,
           Positions positions,
           ControllerUtils controller,
           AutofocusUtils autofocus) {
      super(MyStrings.PanelNames.ACQUSITION.toString(),
              new MigLayout(
              "",
              "[center]0[center]0[center]",
              "0[top]0"));
      gui_ = gui;
      devices_ = devices;
      props_ = props;
      cameras_ = cameras;
      prefs_ = prefs;
      posUpdater_ = posUpdater;
      positions_ = positions;
      controller_ = controller;
      autofocus_ = autofocus;
      core_ = gui_.getMMCore();
      numTimePointsDone_ = 0;
      numPositionsDone_ = 0;
      sliceTiming_ = new SliceTiming();
      lastAcquisitionPath_ = "";
      lastAcquisitionName_ = "";
      acquisitionStatus_ = AcquisitionStatus.NONE;
      acq_ = null;
      channelNames_ = null;
      resetXaxisSpeed_ = true;
      resetXSupaxisSpeed_ = true;
      resetZaxisSpeed_ = true;
      acquisitionPanel_ = this;
      
      runnablesStartAcquisition_ = new ArrayList<Runnable>();
      runnablesEndAcquisition_ = new ArrayList<Runnable>();
      runnablesStartTimepoint_ = new ArrayList<Runnable>();
      runnablesEndTimepoint_ = new ArrayList<Runnable>();
      runnablesStartPosition_ = new ArrayList<Runnable>();
      runnablesEndPosition_ = new ArrayList<Runnable>();
      
      updatingTiming_ = false;
      
      final AcquisitionTableFrame playlistFrame = new AcquisitionTableFrame(gui_, prefs_, this);
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);
      
      // added to spinner controls where we should re-calculate the displayed
      // slice period, volume duration, and time lapse duration
      ChangeListener recalculateTimingDisplayCL = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            if (!updatingTiming_) {
               if (advancedSliceTimingCB_.isSelected()) {
                  // need to update sliceTiming_ from property values
                  sliceTiming_ = getTimingFromAdvancedSettings();
               }
               updateDurationLabels();
            }
         }
      };
      
      // added to combobox controls where we should re-calculate the displayed
      // slice period, volume duration, and time lapse duration
      ActionListener recalculateTimingDisplayAL = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (!updatingTiming_) {
               updateDurationLabels();
            }
         }
      };
      
      // start volume sub-panel

      volPanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]",
              "4[]8[]"));

      volPanel_.setBorder(PanelUtils.makeTitledBorder("Volume Settings"));

      if (!ASIdiSPIM.oSPIM) {
      } else {
         props_.setPropValue(Devices.Keys.PLUGIN,
               Properties.Keys.PLUGIN_NUM_SIDES, "1");
      }
      volPanel_.add(new JLabel("Number of sides:"));
      final String [] str12 = {"1", "2"};
      numSides_ = pu.makeDropDownBox(str12, Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_NUM_SIDES, str12[1]);
      numSides_.addActionListener(recalculateTimingDisplayAL);
      if (!ASIdiSPIM.oSPIM) {
      } else {
         numSides_.setEnabled(false);
      }
      volPanel_.add(numSides_, "wrap");

      volPanel_.add(new JLabel("First side:"));
      final String[] ab = {Devices.Sides.A.toString(), Devices.Sides.B.toString()};
      if (!ASIdiSPIM.oSPIM) {
      } else {
         props_.setPropValue(Devices.Keys.PLUGIN,
               Properties.Keys.PLUGIN_FIRST_SIDE, Devices.Sides.A.toString());
      }
      firstSide_ = pu.makeDropDownBox(ab, Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_FIRST_SIDE, Devices.Sides.A.toString());
      firstSide_.addActionListener(recalculateTimingDisplayAL);
      if (!ASIdiSPIM.oSPIM) {
      } else {
         firstSide_.setEnabled(false);
      }
      volPanel_.add(firstSide_, "wrap");
      
      volPanel_.add(new JLabel("Delay before side [ms]:"));
      // used to read/write directly to galvo/micro-mirror firmware, but want different stage scan behavior
      delaySide_ = pu.makeSpinnerFloat(0, 10000, 0.25,
              Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_DELAY_BEFORE_SIDE, 50);
      pu.addListenerLast(delaySide_, recalculateTimingDisplayCL);
      volPanel_.add(delaySide_, "wrap");

      volPanel_.add(new JLabel("Slices per side:"));
      numSlices_ = pu.makeSpinnerInteger(1, 65000,
              Devices.Keys.PLUGIN,
              Properties.Keys.PLUGIN_NUM_SLICES, 20);
      pu.addListenerLast(numSlices_, recalculateTimingDisplayCL);
      volPanel_.add(numSlices_, "wrap");
      
      volPanel_.add(new JLabel("Slice step size [\u00B5m]:"));
      stepSize_ = pu.makeSpinnerFloat(0, 100, 0.1,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_SLICE_STEP_SIZE,
            1.0);
      pu.addListenerLast(stepSize_, recalculateTimingDisplayCL);  // needed only for stage scanning b/c acceleration time related to speed
      volPanel_.add(stepSize_, "wrap");
      
      // end volume sub-panel
      
      
      // start slice timing controls, have 2 options with advanced timing checkbox shared
      slicePanel_ = new JPanel(new MigLayout(
            "",
            "[right]10[center]",
            "0[]0[]"));
      
      slicePanel_.setBorder(PanelUtils.makeTitledBorder("Slice Settings"));
      
      
      // start light sheet controls
      lightSheetPanel_ = new JPanel(new MigLayout(
            "",
            "[right]10[center]",
            "4[]8"));
      
      lightSheetPanel_.add(new JLabel("Scan reset time [ms]:"));
      JSpinner lsScanReset = pu.makeSpinnerFloat(1, 100, 0.25,  // practical lower limit of 1ms
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SCAN_RESET, 3);
      lsScanReset.addChangeListener(PanelUtils.coerceToQuarterIntegers(lsScanReset));
      pu.addListenerLast(lsScanReset, recalculateTimingDisplayCL);
      lightSheetPanel_.add(lsScanReset, "wrap");
      
      lightSheetPanel_.add(new JLabel("Scan settle time [ms]:"));
      JSpinner lsScanSettle = pu.makeSpinnerFloat(0.25, 100, 0.25,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SCAN_SETTLE, 1);
      lsScanSettle.addChangeListener(PanelUtils.coerceToQuarterIntegers(lsScanSettle));
      pu.addListenerLast(lsScanSettle, recalculateTimingDisplayCL);
      lightSheetPanel_.add(lsScanSettle, "wrap");
      
      lightSheetPanel_.add(new JLabel("Shutter width [\u00B5m]:"));
      JSpinner lsShutterWidth = pu.makeSpinnerFloat(0.1, 100, 1,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SHUTTER_WIDTH, 5);
      pu.addListenerLast(lsShutterWidth, recalculateTimingDisplayCL);
      lightSheetPanel_.add(lsShutterWidth, "wrap");
      
      lightSheetPanel_.add(new JLabel("1 / (shutter speed):"));
      JSpinner lsShutterSpeed = pu.makeSpinnerInteger(1, 10,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SHUTTER_SPEED, 1);
      pu.addListenerLast(lsShutterSpeed, recalculateTimingDisplayCL);
      lightSheetPanel_.add(lsShutterSpeed, "wrap");
      
      // end light sheet controls
      
      // start "normal" (not light sheet) controls
      
      normalPanel_ = new JPanel(new MigLayout(
            "",
            "[right]10[center]",
            "4[]8"));
      
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
            desiredSlicePeriodLabel_.setEnabled(!doMin);
            recalculateSliceTiming(false);
         }
      });
      normalPanel_.add(minSlicePeriodCB_, "span 2, wrap");
      
      // special field that is enabled/disabled depending on whether advanced timing is enabled
      desiredSlicePeriodLabel_ = new JLabel("Slice period [ms]:"); 
      normalPanel_.add(desiredSlicePeriodLabel_);
      normalPanel_.add(desiredSlicePeriod_, "wrap");
      desiredSlicePeriod_.addChangeListener(PanelUtils.coerceToQuarterIntegers(desiredSlicePeriod_));
      desiredSlicePeriod_.addChangeListener(recalculateTimingDisplayCL);
      
      // special field that is enabled/disabled depending on whether advanced timing is enabled
      desiredLightExposureLabel_ = new JLabel("Sample exposure [ms]:"); 
      normalPanel_.add(desiredLightExposureLabel_);
      desiredLightExposure_ = pu.makeSpinnerFloat(1.0, 1000, 0.25,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_DESIRED_EXPOSURE, 8.5);
      desiredLightExposure_.addChangeListener(PanelUtils.coerceToQuarterIntegers(desiredLightExposure_));
      desiredLightExposure_.addChangeListener(recalculateTimingDisplayCL);
      normalPanel_.add(desiredLightExposure_);
      
      // end normal simple slice timing controls
      
      slicePanelContainer_ = new JPanel(new MigLayout("", "0[center]0", "0[]0"));
      slicePanelContainer_.add(getSPIMCameraMode() == CameraModes.Keys.LIGHT_SHEET ?
            lightSheetPanel_ : normalPanel_, "growx");
      slicePanel_.add(slicePanelContainer_, "span 2, center, wrap");
      
      // special checkbox to use the advanced timing settings
      // action handler added below after defining components it enables/disables
      advancedSliceTimingCB_ = pu.makeCheckBox("Use advanced timing settings",
            Properties.Keys.PREFS_ADVANCED_SLICE_TIMING, panelName_, false);
      slicePanel_.add(advancedSliceTimingCB_, "span 2, left");
      
      // end slice sub-panel
      
      
      // start advanced slice timing frame
      // visibility of this frame is controlled from advancedTiming checkbox
      // this frame is separate from main plugin window
      
      sliceFrameAdvanced_ = new MMFrame("diSPIM_Advanced_Timing");
      sliceFrameAdvanced_.setIconImage(Icons.MICROSCOPE.getImage());
      sliceFrameAdvanced_.setTitle("Advanced Timing");
      sliceFrameAdvanced_.loadPosition(100, 100);
      
      sliceAdvancedPanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]",
              "[]8[]"));
      sliceFrameAdvanced_.add(sliceAdvancedPanel_);
      
      class SliceFrameAdapter extends WindowAdapter {
         @Override
         public void windowClosing(WindowEvent e) {
            advancedSliceTimingCB_.setSelected(false);
            sliceFrameAdvanced_.savePosition();
         }
      }
      
      sliceFrameAdvanced_.addWindowListener(new SliceFrameAdapter());
      
      JLabel scanDelayLabel =  new JLabel("Delay before scan [ms]:");
      sliceAdvancedPanel_.add(scanDelayLabel);
      delayScan_ = pu.makeSpinnerFloat(0, 10000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_SCAN, 0);
      delayScan_.addChangeListener(PanelUtils.coerceToQuarterIntegers(delayScan_));
      delayScan_.addChangeListener(recalculateTimingDisplayCL);
      sliceAdvancedPanel_.add(delayScan_, "wrap");

      JLabel lineScanLabel = new JLabel("Lines scans per slice:");
      sliceAdvancedPanel_.add(lineScanLabel);
      numScansPerSlice_ = pu.makeSpinnerInteger(1, 1000,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_NUM_SCANSPERSLICE, 1);
      numScansPerSlice_.addChangeListener(recalculateTimingDisplayCL);
      sliceAdvancedPanel_.add(numScansPerSlice_, "wrap");

      JLabel lineScanPeriodLabel = new JLabel("Line scan duration [ms]:");
      sliceAdvancedPanel_.add(lineScanPeriodLabel);
      lineScanDuration_ = pu.makeSpinnerFloat(1, 10000, 0.25,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_DURATION_SCAN, 10);
      lineScanDuration_.addChangeListener(PanelUtils.coerceToQuarterIntegers(lineScanDuration_));
      lineScanDuration_.addChangeListener(recalculateTimingDisplayCL);
      sliceAdvancedPanel_.add(lineScanDuration_, "wrap");
      
      JLabel delayLaserLabel = new JLabel("Delay before laser [ms]:");
      sliceAdvancedPanel_.add(delayLaserLabel);
      delayLaser_ = pu.makeSpinnerFloat(0, 10000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_LASER, 0);
      delayLaser_.addChangeListener(PanelUtils.coerceToQuarterIntegers(delayLaser_));
      delayLaser_.addChangeListener(recalculateTimingDisplayCL);
      sliceAdvancedPanel_.add(delayLaser_, "wrap");
      
      JLabel durationLabel = new JLabel("Laser trig duration [ms]:");
      sliceAdvancedPanel_.add(durationLabel);
      durationLaser_ = pu.makeSpinnerFloat(0, 10000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DURATION_LASER, 1);
      durationLaser_.addChangeListener(PanelUtils.coerceToQuarterIntegers(durationLaser_));
      durationLaser_.addChangeListener(recalculateTimingDisplayCL);
      sliceAdvancedPanel_.add(durationLaser_, "span 2, wrap");
      
      JLabel delayLabel = new JLabel("Delay before camera [ms]:");
      sliceAdvancedPanel_.add(delayLabel);
      delayCamera_ = pu.makeSpinnerFloat(0, 10000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_CAMERA, 0);
      delayCamera_.addChangeListener(PanelUtils.coerceToQuarterIntegers(delayCamera_));
      delayCamera_.addChangeListener(recalculateTimingDisplayCL);
      sliceAdvancedPanel_.add(delayCamera_, "wrap");
      
      JLabel cameraLabel = new JLabel("Camera trig duration [ms]:");
      sliceAdvancedPanel_.add(cameraLabel);
      durationCamera_ = pu.makeSpinnerFloat(0, 1000, 0.25,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DURATION_CAMERA, 0);
      durationCamera_.addChangeListener(PanelUtils.coerceToQuarterIntegers(durationCamera_));
      durationCamera_.addChangeListener(recalculateTimingDisplayCL);
      sliceAdvancedPanel_.add(durationCamera_, "wrap");
      
      JLabel exposureLabel = new JLabel("Camera exposure [ms]:");
      sliceAdvancedPanel_.add(exposureLabel);
      exposureCamera_ = pu.makeSpinnerFloat(0, 1000, 0.25,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_ADVANCED_CAMERA_EXPOSURE, 10f);
      exposureCamera_.addChangeListener(recalculateTimingDisplayCL);
      sliceAdvancedPanel_.add(exposureCamera_, "wrap");
      
      alternateBeamScanCB_ = pu.makeCheckBox("Alternate scan direction",
            Properties.Keys.PREFS_SCAN_OPPOSITE_DIRECTIONS, panelName_, false);
      sliceAdvancedPanel_.add(alternateBeamScanCB_, "center, span 2, wrap");
      
      simpleTimingComponents_ = new JComponent[]{ desiredLightExposure_,
            minSlicePeriodCB_, desiredSlicePeriodLabel_,
            desiredLightExposureLabel_};
      final JComponent[] advancedTimingComponents = {
            delayScan_, numScansPerSlice_, lineScanDuration_, 
            delayLaser_, durationLaser_, delayCamera_,
            durationCamera_, exposureCamera_, alternateBeamScanCB_};
      PanelUtils.componentsSetEnabled(advancedTimingComponents, advancedSliceTimingCB_.isSelected());
      PanelUtils.componentsSetEnabled(simpleTimingComponents_, !advancedSliceTimingCB_.isSelected());
      
      // this action listener takes care of enabling/disabling inputs
      // of the advanced slice timing window
      // we call this to get GUI looking right
      ItemListener sliceTimingDisableGUIInputs = new ItemListener() {
         @Override
         public void itemStateChanged(ItemEvent e) {
            boolean enabled = advancedSliceTimingCB_.isSelected();
            // set other components in this advanced timing frame
            PanelUtils.componentsSetEnabled(advancedTimingComponents, enabled);
            // also control some components in main volume settings sub-panel
            PanelUtils.componentsSetEnabled(simpleTimingComponents_, !enabled);
            desiredSlicePeriod_.setEnabled(!enabled && !minSlicePeriodCB_.isSelected());
            desiredSlicePeriodLabel_.setEnabled(!enabled && !minSlicePeriodCB_.isSelected());
            updateDurationLabels();
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
      numTimepoints_ = pu.makeSpinnerInteger(1, 100000,
              Devices.Keys.PLUGIN,
              Properties.Keys.PLUGIN_NUM_ACQUISITIONS, 1);
      numTimepoints_.addChangeListener(recalculateTimeLapseDisplay);
      numTimepoints_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent arg0) {
            // update nrRepeats_ variable so the acquisition can be extended or shortened
            //   as long as we have separate timepoints
            if (acquisitionRunning_.get() && getSavingSeparateFile()) {
               nrRepeats_ = getNumTimepoints();
            }
         }
      });
      timepointPanel_.add(numTimepoints_, "wrap");

      timepointPanel_.add(new JLabel("Interval [s]:"));
      acquisitionInterval_ = pu.makeSpinnerFloat(0.001, 1000000, 0.1,
              Devices.Keys.PLUGIN,
              Properties.Keys.PLUGIN_ACQUISITION_INTERVAL, 60);
      acquisitionInterval_.addChangeListener(recalculateTimeLapseDisplay);
      timepointPanel_.add(acquisitionInterval_, "wrap");
      
      // enable/disable panel elements depending on checkbox state
      useTimepointsCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) { 
            PanelUtils.componentsSetEnabled(timepointPanel_, useTimepointsCB_.isSelected());
         }
      });
      PanelUtils.componentsSetEnabled(timepointPanel_, useTimepointsCB_.isSelected());  // initialize
      
      // end repeat sub-panel
      
      
      // start savePanel
      
      // TODO for now these settings aren't part of acquisition settings
      // TODO consider whether that should be changed
      
      final int textFieldWidth = 16;
      savePanel_ = new JPanel(new MigLayout(
              "",
              "[right]10[center]8[left]",
              "[]4[]"));
      savePanel_.setBorder(PanelUtils.makeTitledBorder("Data Saving Settings"));
      
      separateTimePointsCB_ = pu.makeCheckBox("Separate viewer / file for each time point",
            Properties.Keys.PREFS_SEPARATE_VIEWERS_FOR_TIMEPOINTS, panelName_, false);
      
      saveCB_ = pu.makeCheckBox("Save while acquiring",
            Properties.Keys.PREFS_SAVE_WHILE_ACQUIRING, panelName_, false);

      // make sure that when separate viewer is enabled then saving gets enabled too
      separateTimePointsCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (separateTimePointsCB_.isSelected() && !saveCB_.isSelected()) {
               saveCB_.doClick();  // setSelected() won't work because need to call its listener
            }
         }
      });
      savePanel_.add(separateTimePointsCB_, "span 3, left, wrap");
      savePanel_.add(saveCB_, "skip 1, span 2, center, wrap");

      JLabel dirRootLabel = new JLabel ("Directory root:");
      savePanel_.add(dirRootLabel);

      DefaultFormatter formatter = new DefaultFormatter();
      formatter.setOverwriteMode(false);
      rootField_ = new JFormattedTextField(formatter);
      rootField_.setText( prefs_.getString(panelName_, 
              Properties.Keys.PLUGIN_DIRECTORY_ROOT, "") );
      rootField_.addPropertyChangeListener(new PropertyChangeListener() {
         // will respond to commitEdit() as well as GUI edit on commit
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            prefs_.putString(panelName_, Properties.Keys.PLUGIN_DIRECTORY_ROOT,
                  rootField_.getText());
         }
      });
      rootField_.setColumns(textFieldWidth);
      savePanel_.add(rootField_, "span 2");

      JButton browseRootButton = new JButton();
      browseRootButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            setRootDirectory(rootField_);
            prefs_.putString(panelName_, Properties.Keys.PLUGIN_DIRECTORY_ROOT, rootField_.getText());
            playlistFrame.getAcquisitionTable().updateSaveDirectoryRoot(rootField_.getText());
         }
      });
      browseRootButton.setMargin(new Insets(2, 5, 2, 5));
      browseRootButton.setText("...");
      savePanel_.add(browseRootButton, "wrap");

      JLabel namePrefixLabel = new JLabel();
      namePrefixLabel.setText("Name prefix:");
      savePanel_.add(namePrefixLabel);

      prefixField_ = new JFormattedTextField(formatter);
      prefixField_.setText( prefs_.getString(panelName_,
              Properties.Keys.PLUGIN_NAME_PREFIX, "acq"));
      prefixField_.addPropertyChangeListener(new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            prefs_.putString(panelName_, Properties.Keys.PLUGIN_NAME_PREFIX, prefixField_.getText());
            playlistFrame.getAcquisitionTable().updateSaveNamePrefix(prefixField_.getText());
         }
      });
      prefixField_.setColumns(textFieldWidth);
      savePanel_.add(prefixField_, "span 2, wrap");
      
      // since we use the name field even for acquisitions in RAM, 
      // we only need to gray out the directory-related components
      final JComponent[] saveComponents = { browseRootButton, rootField_, 
                                            dirRootLabel };
      PanelUtils.componentsSetEnabled(saveComponents, saveCB_.isSelected());
      
      saveCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            PanelUtils.componentsSetEnabled(saveComponents, saveCB_.isSelected());
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
      
      buttonTestAcq_ = new JButton("Test Acq");
      buttonTestAcq_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            runTestAcquisition(Devices.Sides.NONE);
         }
      });
      
      buttonStart_ = new JToggleButton();
      buttonStart_.setIconTextGap(6);
      buttonStart_.setOpaque(true);
      buttonStart_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (isAcquisitionRequested()) {
               stopAcquisition();
            } else {
               runAcquisition();
            }
         }
      });
      updateStartButton();  // call once to initialize, isSelected() will be false
      
      // open the multi-acquisition playlist frame button
      final JButton buttonOpenPlaylistFrame = new JButton("Playlist...");
      buttonOpenPlaylistFrame.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
             playlistFrame.setVisible(true);
         }
      });

      // make the height of buttons match the start button (easier on the eye)
      Dimension sizeStart = buttonStart_.getPreferredSize();
      Dimension sizeTest = buttonTestAcq_.getPreferredSize();
      Dimension sizePlaylist = buttonOpenPlaylistFrame.getPreferredSize();
      sizeTest.height = sizeStart.height;
      sizePlaylist.height = sizeStart.height;
      buttonTestAcq_.setPreferredSize(sizeTest);
      buttonOpenPlaylistFrame.setPreferredSize(sizePlaylist);

      acquisitionStatusLabel_ = new JLabel("");
      acquisitionStatusLabel_.setBackground(prefixField_.getBackground());
      acquisitionStatusLabel_.setOpaque(true);
      updateAcquisitionStatus(AcquisitionStatus.NONE);
      
      // Channel Panel (separate file for code)
      multiChannelPanel_ = new MultiChannelSubPanel(gui, devices_, props_, prefs_);
      multiChannelPanel_.addDurationLabelListener(this);
      
      // Position Panel
      positionPanel_ = new JPanel();
      positionPanel_.setLayout(new MigLayout("flowx, fillx","[right]10[left][10][]","[]8[]"));
      usePositionsCB_ = pu.makeCheckBox("Multiple positions (XY)",
            Properties.Keys.PREFS_USE_MULTIPOSITION, panelName_, false);
      usePositionsCB_.setToolTipText("Acquire datasest at multiple postions");
      usePositionsCB_.setEnabled(true);
      usePositionsCB_.setFocusPainted(false); 
      componentBorder = 
            new ComponentTitledBorder(usePositionsCB_, positionPanel_, 
                  BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      positionPanel_.setBorder(componentBorder);
      
      usePositionsCB_.addChangeListener(recalculateTimingDisplayCL);
      
      final JButton editPositionListButton = new JButton("Edit position list...");
      editPositionListButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            gui_.showXYPositionList();
         }
      });
      positionPanel_.add(editPositionListButton);
      
      gridButton_ = new JButton("XYZ grid...");
      positionPanel_.add(gridButton_, "wrap");
      
      gridFrame_ = new MMFrame("diSPIM_XYZ_grid");
      gridFrame_.setIconImage(Icons.MICROSCOPE.getImage());
      gridFrame_.setTitle("XYZ Grid");
      gridFrame_.loadPosition(100, 100);
      gridPanel_ = new XYZGridPanel(gui_, devices_, props_, prefs_, posUpdater_, positions_);
      gridFrame_.add(gridPanel_);
      gridFrame_.pack();
      gridFrame_.setResizable(false);
      
      // enable access to the XYZ grid frame from the playlist
      playlistFrame.setXYZGridFrame(gridFrame_);
      
      class GridFrameAdapter extends WindowAdapter {
         @Override
         public void windowClosing(WindowEvent e) {
            gridPanel_.windowClosing();
         }
      }
      gridFrame_.addWindowListener(new GridFrameAdapter());

      gridButton_.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
             gridButton_.setSelected(true);
             gridFrame_.setVisible(true);
          }
      });
      
      positionPanel_.add(new JLabel("Post-move delay [ms]:"));
      positionDelay_ = pu.makeSpinnerFloat(0.0, 100000.0, 100.0,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_POSITION_DELAY,
            0.0);
      positionPanel_.add(positionDelay_, "wrap");
      
      // enable/disable panel elements depending on checkbox state
      usePositionsCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) { 
            PanelUtils.componentsSetEnabled(positionPanel_, usePositionsCB_.isSelected());
            gridButton_.setEnabled(true);  // leave this always enabled
         }
      });
      PanelUtils.componentsSetEnabled(positionPanel_, usePositionsCB_.isSelected());  // initialize
      gridButton_.setEnabled(true);  // leave this always enabled
      
      // end of Position panel
      
      // checkbox to use navigation joystick settings or not
      // an "orphan" UI element
      navigationJoysticksCB_ = new JCheckBox("Use Navigation joystick settings");
      navigationJoysticksCB_.setSelected(prefs_.getBoolean(panelName_,
            Properties.Keys.PLUGIN_USE_NAVIGATION_JOYSTICKS, false));
      navigationJoysticksCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateJoysticks();
            prefs_.putBoolean(panelName_, Properties.Keys.PLUGIN_USE_NAVIGATION_JOYSTICKS,
                  navigationJoysticksCB_.isSelected());
         }
      });
      
      // checkbox to signal that autofocus should be used during acquisition
      // another orphan UI element
      useAutofocusCB_ = new JCheckBox("Autofocus periodically");
      useAutofocusCB_.setSelected(prefs_.getBoolean(panelName_, 
              Properties.Keys.PLUGIN_ACQUSITION_USE_AUTOFOCUS, false));
      useAutofocusCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            prefs_.putBoolean(panelName_, 
                    Properties.Keys.PLUGIN_ACQUSITION_USE_AUTOFOCUS, 
                    useAutofocusCB_.isSelected());
           }
      });
      
      // checkbox to signal that movement should be corrected during acquisition
      // Yet another orphan UI element
      useMovementCorrectionCB_ = new JCheckBox("Motion correction");
      useMovementCorrectionCB_.setSelected(prefs_.getBoolean(panelName_, 
              Properties.Keys.PLUGIN_ACQUSITION_USE_MOVEMENT_CORRECTION, false));
      useMovementCorrectionCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            prefs_.putBoolean(panelName_, 
                    Properties.Keys.PLUGIN_ACQUSITION_USE_MOVEMENT_CORRECTION, 
                    useMovementCorrectionCB_.isSelected());
           }
      });
      // since this isn't implemented disable it for now
      useMovementCorrectionCB_.setSelected(false);
      useMovementCorrectionCB_.setEnabled(false);
      useMovementCorrectionCB_.setToolTipText("Not fully implemented");
      
      
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
      leftColumnPanel_.add(new JLabel("Acquisition mode: "), "split 2, right");
      AcquisitionModes acqModes = new AcquisitionModes(devices_, prefs_);
      spimMode_ = acqModes.getComboBox();
      spimMode_.addActionListener(recalculateTimingDisplayAL);
      leftColumnPanel_.add(spimMode_, "left, wrap");
      leftColumnPanel_.add(buttonStart_, "split 4, left");
      leftColumnPanel_.add(new JLabel("    "));
      leftColumnPanel_.add(buttonTestAcq_, "");
      leftColumnPanel_.add(buttonOpenPlaylistFrame, "wrap");
      leftColumnPanel_.add(new JLabel("Status:"), "split 2, left");
      leftColumnPanel_.add(acquisitionStatusLabel_);
      
      centerColumnPanel_ = new JPanel(new MigLayout("", "[]", "[]"));
      
      centerColumnPanel_.add(positionPanel_, "growx, wrap");
      centerColumnPanel_.add(multiChannelPanel_, "wrap");
      centerColumnPanel_.add(navigationJoysticksCB_, "wrap");
      centerColumnPanel_.add(useAutofocusCB_, "split 2");
      centerColumnPanel_.add(useMovementCorrectionCB_);
      
      rightColumnPanel_ = new JPanel(new MigLayout("", "[center]0", "[]0[]"));
      
      rightColumnPanel_.add(volPanel_, "growx, wrap");
      rightColumnPanel_.add(slicePanel_, "growx");
      
      // add the column panels to the main panel
      super.add(leftColumnPanel_);
      super.add(centerColumnPanel_);
      super.add(rightColumnPanel_);
      
      // properly initialize the advanced slice timing
      advancedSliceTimingCB_.addItemListener(sliceTimingDisableGUIInputs);
      sliceTimingDisableGUIInputs.itemStateChanged(null);
      advancedSliceTimingCB_.addActionListener(showAdvancedTimingFrame);
      
      // included is calculating slice timing
      updateDurationLabels();
      
      // update local variables
      zStepUm_ = PanelUtils.getSpinnerFloatValue(stepSize_);
      refreshXYZPositions();
      
   }//end constructor


   private void updateJoysticks() {
      if (ASIdiSPIM.getFrame() != null) {
         ASIdiSPIM.getFrame().getNavigationPanel().
         doJoystickSettings(navigationJoysticksCB_.isSelected());
      }
   }
   
   public final void updateDurationLabels() {
      updateActualSlicePeriodLabel();
      updateActualVolumeDurationLabel();
      updateActualTimeLapseDurationLabel();
   }
   
   private void updateCalibrationOffset(final Devices.Sides side, 
           final AutofocusUtils.FocusResult score) {
      if (score.focusSuccess_) {
         double maxDelta = props_.getPropValueFloat(Devices.Keys.PLUGIN,
               Properties.Keys.PLUGIN_AUTOFOCUS_MAXOFFSETCHANGE);
         if (Math.abs(score.offsetDelta_) <= maxDelta) {
            ASIdiSPIM.getFrame().getSetupPanel(side).updateCalibrationOffset(score);
         } else {
            ReportingUtils.logMessage("autofocus successful for side " + side + " but offset change too much to automatically update");
         }
      }
   }
   
   public SliceTiming getSliceTiming() {
      return sliceTiming_;
   }
   
   /**
    * Sets the acquisition name prefix programmatically.
    * Added so that name prefix can be changed from a script.
    * @param acqName
    */
   public void setAcquisitionNamePrefix(String acqName) {
      prefixField_.setText(acqName);
   }
   
   private void updateStartButton() {
      boolean started = isAcquisitionRequested();
      buttonStart_.setSelected(started);
      buttonStart_.setText(started ? "Stop Acq!" : "Start Acq!");
      buttonStart_.setBackground(started ? Color.red : Color.green);
      buttonStart_.setIcon(started ? Icons.CANCEL : Icons.ARROW_RIGHT);
      buttonTestAcq_.setEnabled(!started);
   }
   
   /**
    * @return CameraModes.Keys value from Camera panel
    * (edge, overlap, pseudo-overlap, light sheet) 
    */
   private CameraModes.Keys getSPIMCameraMode() {
      CameraModes.Keys val;
      try {
         val = ASIdiSPIM.getFrame().getSPIMCameraMode();
      } catch (Exception ex) {
         val = CameraModes.getKeyFromPrefCode(prefs_.getInt(MyStrings.PanelNames.SETTINGS.toString(),
            Properties.Keys.PLUGIN_CAMERA_MODE, 1));  // default is edge
      }
      return val;
   }
   
   /**
    * convenience method to avoid having to regenerate acquisition settings
    */
   private int getNumPositions() {
      if (usePositionsCB_.isSelected()) {
         try {
            return gui_.getPositionList().getNumberOfPositions();
         } catch (MMScriptException e) {
            return 1;
         }
      } else {
         return 1;
      }
   }
   
   /**
    * convenience method to avoid having to regenerate acquisition settings
    */
   private int getNumTimepoints() {
      if (useTimepointsCB_.isSelected()) {
         return (Integer) numTimepoints_.getValue();
      } else {
         return 1;
      }
   }
   
   /**
    * convenience method to avoid having to regenerate acquisition settings
    * public for API use
    * @return Number of Sides
    */
   public int getNumSides() {
      if (numSides_.getSelectedIndex() == 1) {
         return 2;
      } else {
         return 1;
      }
   }
   
   /**
    * convenience method to avoid having to regenerate acquisition settings
    * public for API use
    * @return true if the first side is side A
    */
   public boolean isFirstSideA() {
      return ((String) firstSide_.getSelectedItem()).equals("A");
   }

   public Devices.Sides getFirstSide() {
      return isFirstSideA() ? Devices.Sides.A : Devices.Sides.B;
   }
   
   /**
    * convenience method to avoid having to regenerate acquisition settings.
    * public for API use
    * @return Time between starts of acquisition when doing a time-lapse acquisition 
    */
   public double getTimepointInterval() {
      return PanelUtils.getSpinnerFloatValue(acquisitionInterval_);
   }

   /**
    * Gathers all current acquisition settings into dedicated POD object
    * @return
    */
   public AcquisitionSettings getCurrentAcquisitionSettings() {
      AcquisitionSettings acqSettings = new AcquisitionSettings();
      acqSettings.spimMode = getAcquisitionMode();
      acqSettings.isStageScanning = (acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN
            || acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED
            || acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_UNIDIRECTIONAL
            || acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL);
      acqSettings.isStageStepping = (acqSettings.spimMode == AcquisitionModes.Keys.STAGE_STEP_SUPPLEMENTAL_UNIDIRECTIONAL);
      acqSettings.useTimepoints = useTimepointsCB_.isSelected();
      acqSettings.numTimepoints = getNumTimepoints();
      acqSettings.timepointInterval = getTimepointInterval();
      acqSettings.useMultiPositions = usePositionsCB_.isSelected();
      acqSettings.useChannels = multiChannelPanel_.isMultiChannel();
      acqSettings.channelMode = multiChannelPanel_.getChannelMode();
      acqSettings.numChannels = multiChannelPanel_.getNumChannels();
      acqSettings.channels = multiChannelPanel_.getUsedChannels();
      acqSettings.channelGroup = multiChannelPanel_.getChannelGroup();
      acqSettings.useAutofocus = useAutofocusCB_.isSelected();
      acqSettings.useMovementCorrection = useMovementCorrectionCB_.isSelected();
      acqSettings.acquireBothCamerasSimultaneously = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(),
            Properties.Keys.PLUGIN_ACQUIRE_BOTH_CAMERAS_SIMULT, false);
      acqSettings.numSides = getNumSides();
      acqSettings.firstSideIsA = isFirstSideA();
      acqSettings.delayBeforeSide = PanelUtils.getSpinnerFloatValue(delaySide_);
      acqSettings.numSlices = (Integer) numSlices_.getValue();
      acqSettings.stepSizeUm = PanelUtils.getSpinnerFloatValue(stepSize_);
      acqSettings.minimizeSlicePeriod = minSlicePeriodCB_.isSelected();
      acqSettings.desiredSlicePeriod = PanelUtils.getSpinnerFloatValue(desiredSlicePeriod_);
      acqSettings.desiredLightExposure = PanelUtils.getSpinnerFloatValue(desiredLightExposure_); 
      acqSettings.centerAtCurrentZ = false;
      acqSettings.sliceTiming = sliceTiming_;
      acqSettings.cameraMode = getSPIMCameraMode();
      acqSettings.hardwareTimepoints = false; //  when running acquisition we check this and set to true if needed
      acqSettings.separateTimepoints = getSavingSeparateFile();
      acqSettings.usePathPresets = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(),
            Properties.Keys.PLUGIN_USE_PATH_GROUP_ACQ, true);
      acqSettings.numSimultCameras = prefs_.getBoolean(MyStrings.PanelNames.CAMERAS.toString(),
            Properties.Keys.PLUGIN_USE_SIMULT_CAMERAS, false) 
            ? props_.getPropValueInteger(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_NUM_SIMULT_CAMERAS)
                  : 0;
      acqSettings.useAdvancedSliceTiming = advancedSliceTimingCB_.isSelected();
      acqSettings.saveDirectoryRoot = rootField_.getText();
      acqSettings.saveNamePrefix = prefixField_.getText();
      // missing from this init:
      // durationSlice
      // durationVolume
      // durationTotal
      return acqSettings;
   }
   
   /**
    * Sets the acquisition settings from an AcquisitionSettings object, 
    * updating the ui, so that getCurrentAcquisitionSettings will be 
    * populated correctly when running an acquisition.
    * 
    * @param acqSettings the acquisition settings
    */
   public void setAcquisitionSettings(final AcquisitionSettings acqSettings) {
       // Settings that don't need to update the gui =>
       // isStageScanning, isStageStepping, numChannels

       // channels
       multiChannelPanel_.setPanelEnabled(acqSettings.useChannels);
       multiChannelPanel_.setChannelMode(acqSettings.channelMode);
       multiChannelPanel_.setChannelGroup(acqSettings.channelGroup);
       multiChannelPanel_.removeAllChannels();
       // Note: setChannelEnabled only adds channels with unique preset names
       // only used channels are saved ("Use?" checkbox is checked)
       for (ChannelSpec channel : acqSettings.channels) {
	       multiChannelPanel_.setChannelEnabled(channel.config_, channel.useChannel_);
	   }
       
       // settings other than channels that need to update the gui
       spimMode_.setSelectedItem(acqSettings.spimMode);
       updateCheckBox(useTimepointsCB_, acqSettings.useTimepoints);
       numTimepoints_.setValue(acqSettings.numTimepoints);
       acquisitionInterval_.setValue(acqSettings.timepointInterval);
       updateCheckBox(usePositionsCB_, acqSettings.useMultiPositions);
       useAutofocusCB_.setSelected(acqSettings.useAutofocus);
       useMovementCorrectionCB_.setSelected(acqSettings.useMovementCorrection);
       numSides_.setSelectedIndex(acqSettings.numSides-1);
       firstSide_.setSelectedItem(acqSettings.firstSideIsA ? "A" : "B");
       delaySide_.setValue(acqSettings.delayBeforeSide);
       numSlices_.setValue(acqSettings.numSlices);
       stepSize_.setValue(acqSettings.stepSizeUm);
       updateCheckBox(minSlicePeriodCB_, acqSettings.minimizeSlicePeriod);
       desiredSlicePeriod_.setValue(acqSettings.desiredSlicePeriod);
       desiredLightExposure_.setValue(acqSettings.desiredLightExposure);
       separateTimePointsCB_.setSelected(acqSettings.separateTimepoints);
       rootField_.setText(acqSettings.saveDirectoryRoot);
       prefixField_.setText(acqSettings.saveNamePrefix);
       advancedSliceTimingCB_.setSelected(acqSettings.useAdvancedSliceTiming);
       
       // update camera panel
       final CameraPanel cameraPanel = ASIdiSPIM.getFrame().getCameraPanel();
       cameraPanel.setSPIMCameraMode(acqSettings.cameraMode);
       cameraPanel.setNumSimultaneousCameras(acqSettings.numSimultCameras);
       updateCheckBox(cameraPanel.getUseSimultaneousCamerasCheckBox(), 
               (acqSettings.numSimultCameras > 0) ? true : false);
       
       // update settings panel
       final SettingsPanel settingsPanel = ASIdiSPIM.getFrame().getSettingsPanel();
       updateCheckBox(settingsPanel.getAcquireBothCamerasCheckBox(), 
               acqSettings.acquireBothCamerasSimultaneously);
       updateCheckBox(settingsPanel.getPathGroupCheckBox(), 
               acqSettings.usePathPresets);
       
       // update advanced timing settings frame
       PanelUtils.setSpinnerFloatValue(delayScan_, acqSettings.sliceTiming.scanDelay);
       numScansPerSlice_.setValue(acqSettings.sliceTiming.scanNum);
       PanelUtils.setSpinnerFloatValue(lineScanDuration_, acqSettings.sliceTiming.scanPeriod);
       PanelUtils.setSpinnerFloatValue(delayLaser_, acqSettings.sliceTiming.laserDelay);
       PanelUtils.setSpinnerFloatValue(durationLaser_, acqSettings.sliceTiming.laserDuration);
       PanelUtils.setSpinnerFloatValue(delayCamera_, acqSettings.sliceTiming.cameraDelay);
       PanelUtils.setSpinnerFloatValue(durationCamera_, acqSettings.sliceTiming.cameraDuration );
       PanelUtils.setSpinnerFloatValue(exposureCamera_, acqSettings.sliceTiming.cameraExposure );
       // TODO: alternate scan direction is unaccounted for
       
       // refresh ui
       revalidate();
       repaint();
   }

   /**
    * gets the correct value for the slice timing's sliceDuration field
    * based on other values of slice timing
    * @param s the slice timing
    * @return
    */
   private float getSliceDuration(final SliceTiming s) {
      // slice duration is the max out of the scan time, laser time, and camera time
      return Math.max(Math.max(
            s.scanDelay +
            (s.scanPeriod * s.scanNum),     // scan time
            s.laserDelay + s.laserDuration  // laser time
            ),
            s.cameraDelay + s.cameraDuration // camera time
            );
   }
   
   /**
    * gets the slice timing from advanced settings
    * (normally these advanced settings are read-only and we populate them
    * ourselves depending on the user's requests and our algorithm below) 
    * @return
    */
   private SliceTiming getTimingFromAdvancedSettings() {
      SliceTiming s = new SliceTiming();
      s.scanDelay = PanelUtils.getSpinnerFloatValue(delayScan_);
      s.scanNum = (Integer) numScansPerSlice_.getValue();
      s.scanPeriod = PanelUtils.getSpinnerFloatValue(lineScanDuration_);
      s.laserDelay = PanelUtils.getSpinnerFloatValue(delayLaser_);
      s.laserDuration = PanelUtils.getSpinnerFloatValue(durationLaser_);
      s.cameraDelay = PanelUtils.getSpinnerFloatValue(delayCamera_);
      s.cameraDuration = PanelUtils.getSpinnerFloatValue(durationCamera_);
      s.cameraExposure = PanelUtils.getSpinnerFloatValue(exposureCamera_);
      s.sliceDuration = getSliceDuration(s);
      return s;
   }
   
   /**
    * Special case for different settings for single-objective light sheet (or really any time we have static sheet instead of scanned beam)
    * @param showWarnings true to warn user about needing to change slice period
    * @return
    */
   private SliceTiming getTimingSingleObjective(boolean showWarnings) {
      SliceTiming s = new SliceTiming();
      final float cameraResetTime = computeCameraResetTime();      // recalculate for safety, 0 for light sheet
      final float cameraReadoutTime = computeCameraReadoutTime();  // recalculate for safety, 0 for overlap

      // copied this bit from the original getTimingFromPeriodAndLightExposure()
      final Devices.Keys camKey = Devices.Keys.CAMERAA;  // only camera for single-objective is
      final Devices.Libraries camLibrary = devices_.getMMDeviceLibrary(camKey);
      final float actualCameraResetTime = (camLibrary == Devices.Libraries.PVCAM
            && (props_.getPropValueString(camKey, Properties.Keys.PVCAM_CHIPNAME).equals(Properties.Values.PRIME_95B_CHIPNAME) 
                  || props_.getPropValueString(camKey, Properties.Keys.PVCAM_CHIPNAME).equals(Properties.Values.KINETIX_CHIPNAME)))
            ? (float) props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_READOUT_TIME) / 1e6f
            : cameraResetTime; // everything but Photometrics Prime 95B and Kinetix

      final float cameraTotalTime = MyNumberUtils.ceilToQuarterMs(cameraResetTime + cameraReadoutTime);
      final AcquisitionSettings acqSettings = getCurrentAcquisitionSettings();
      final float laserDuration = MyNumberUtils.roundToQuarterMs(acqSettings.desiredLightExposure);
      final float slicePeriodMin = Math.max(laserDuration, cameraTotalTime);   // max of laser on time (for static light sheet) and total camera reset/readout time; will add excess later
      final float sliceDeadTime = MyNumberUtils.roundToQuarterMs(slicePeriodMin - laserDuration);
      final float sliceLaserInterleaved = (acqSettings.channelMode == MultichannelModes.Keys.SLICE_HW ? 0.25f : 0.f);  // extra quarter millisecond to make sure interleaved slices works (otherwise laser signal never goes low)
      final Color foregroundColorOK = Color.BLACK;
      final Color foregroundColorError = Color.RED;
      final Component elementToColor  = desiredSlicePeriod_.getEditor().getComponent(0);
      
      switch (acqSettings.cameraMode) {
      case PSEUDO_OVERLAP:  // e.g. Kinetix
         s.scanNum = 1;
         s.scanDelay = 0.f;
         s.scanPeriod = 0.25f;
         s.cameraDelay = 0.25f;
         s.cameraDuration = laserDuration;
         s.cameraExposure = laserDuration;
         s.laserDelay = sliceDeadTime;
         s.laserDuration = laserDuration;
         break;
      case OVERLAP:  // e.g. Fusion
         if (acqSettings.useChannels && acqSettings.numChannels > 1 && acqSettings.channelMode == MultichannelModes.Keys.SLICE_HW) {
            // for interleaved slices we should illuminate during global exposure but not during readout/reset time after each trigger
            s.scanNum = 1;
            s.scanDelay = 0.f;
            s.scanPeriod = 1.0f;
            s.cameraDelay = 0.f;
            s.cameraDuration = 1.0f;
            s.cameraExposure = 0.25f;
            s.laserDelay = sliceDeadTime+MyNumberUtils.ceilToQuarterMs(cameraResetTime);
            s.laserDuration = laserDuration;
         } else {
            // the usual case
            s.scanNum = 1;
            s.scanDelay = 0.f;
            s.scanPeriod = 1.0f;
            s.cameraDelay = 0.f;
            s.cameraDuration = 1.0f;
            s.cameraExposure = 0.25f;
            s.laserDelay = sliceDeadTime+sliceLaserInterleaved;
            s.laserDuration = laserDuration;
         }
         break;
      case EDGE:
         // should illuminate during the entire exposure (or as much as needed) => will be exposing during camera reset and readout too
         // note that this may be faster than overlap for interleaved channels
         s.scanNum = 1;
         s.scanDelay = 0.f;
         s.scanPeriod = 1.0f;
         s.cameraDelay = sliceLaserInterleaved;
         s.cameraDuration = 1.0f;
         s.cameraExposure = laserDuration - MyNumberUtils.ceilToQuarterMs(actualCameraResetTime + cameraReadoutTime);
         s.laserDelay = sliceDeadTime+sliceLaserInterleaved;
         s.laserDuration = laserDuration;
         break;
      
      // TODO this requires some thinking through, here is a mostly complete update but I don't want to adjust now
//      case PSEUDO_OVERLAP:  // e.g. Kinetix
//         // laser on for requested duration, with laser delay to make the total slice time as requested
//         // camera exposure starts just before laser
//         // scan period set very fast (it shouldn't matter)
//         s.scanNum = 1;
//         s.scanDelay = 0.f;
//         s.scanPeriod = 0.25f;
//         s.cameraDelay = sliceDeadTime;
//         s.cameraDuration = 0.25f;
//         s.cameraExposure = laserDuration;
//         s.laserDelay = sliceDeadTime;
//         s.laserDuration = laserDuration;
//         break;
//      case OVERLAP:  // e.g. Fusion, trigger sets start/stop of camera readout
//         // 0.25ms camera trigger with 0 delay
//         // camera exposure setting doesn't matter
//         // laser on for requested duration, with laser delay to make the total slice time as requested
//         // scan period set very fast (it shouldn't matter)
//         s.scanNum = 1;
//         s.scanDelay = 0.f;
//         s.scanPeriod = 0.25f;
//         s.cameraDelay = sliceDeadTime;
//         s.cameraDuration = 0.25f;
//         s.cameraExposure = laserDuration;
//         s.laserDelay = sliceDeadTime;
//         s.laserDuration = laserDuration;
//         break;
//      case EDGE:  // trigger sets start of camera exposure, readout when exposure time is over
//         // 0.25ms camera trigger with 0 delay
//         // camera exposure time the same as the laser duration
//         s.scanNum = 1;
//         s.scanDelay = 0.25f;
//         s.scanPeriod = 1.0f;
//         s.cameraDelay = 0.f;
//         s.cameraDuration = 0.25f;
//         s.cameraExposure = laserDuration - MyNumberUtils.roundToQuarterMs(cameraReadoutTime));
//         s.laserDelay = sliceDeadTime;
//         s.laserDuration = laserDuration;
//         break;
      default:
         if (showWarnings) {
            MyDialogUtils.showError("Invalid camera mode");
         }
         s.valid = false;
      }
      
      // if a specific slice period was requested, add corresponding delay to scan/laser/camera
      elementToColor.setForeground(foregroundColorOK);
      if (!acqSettings.minimizeSlicePeriod) {
         float globalDelay = acqSettings.desiredSlicePeriod - getSliceDuration(s);  // both should be in 0.25ms increments // TODO fix;
         if (acqSettings.cameraMode == CameraModes.Keys.LIGHT_SHEET) {
            globalDelay = 0;
         }
         if (globalDelay < 0) {
            globalDelay = 0;
            if (showWarnings) {  // only true when user has specified period that is unattainable
                  MyDialogUtils.showError(
                        "Increasing slice period to meet laser exposure constraint\n"
                              + "(time required for camera readout; readout time depends on ROI).");
                  elementToColor.setForeground(foregroundColorError);
            }
         }
         s.scanDelay += globalDelay;
         s.cameraDelay += globalDelay;
         s.laserDelay += globalDelay;
      }

      // fix corner case of (exposure time + readout time) being greater than the slice duration
      // most of the time the slice duration is already larger
      float globalDelay = MyNumberUtils.ceilToQuarterMs((s.cameraExposure + cameraReadoutTime) - getSliceDuration(s));
      if (globalDelay > 0) {
         s.scanDelay += globalDelay;
         s.cameraDelay += globalDelay;
         s.laserDelay += globalDelay;
      }
      
      // update the slice duration based on our new values
      s.sliceDuration = getSliceDuration(s);
      return s;
   }
   
   /**
    * @param showWarnings true to warn user about needing to change slice period
    * @return
    */
   private SliceTiming getTimingFromPeriodAndLightExposure(boolean showWarnings) {
      // uses algorithm Jon worked out in Octave code; each slice period goes like this:
      // 1. camera readout time (none if in overlap mode, 0.25ms in pseudo-overlap)
      // 2. any extra delay time
      // 3. camera reset time
      // 4. start scan 0.25ms before camera global exposure and shifted up in time to account for delay introduced by Bessel filter
      // 5. turn on laser as soon as camera global exposure, leave laser on for desired light exposure time
      // 7. end camera exposure in final 0.25ms, post-filter scan waveform also ends now
      final float scanLaserBufferTime = MyNumberUtils.roundToQuarterMs(0.25f);  // below assumed to be multiple of 0.25ms
      final Color foregroundColorOK = Color.BLACK;
      final Color foregroundColorError = Color.RED;
      final Component elementToColor  = desiredSlicePeriod_.getEditor().getComponent(0);
      
      SliceTiming s = new SliceTiming();
      final float cameraResetTime = computeCameraResetTime();      // recalculate for safety, 0 for light sheet
      final float cameraReadoutTime = computeCameraReadoutTime();  // recalculate for safety, 0 for overlap
      
      // can we use acquisition settings directly? because they may be in flux
      final AcquisitionSettings acqSettings = getCurrentAcquisitionSettings();
      
      final float cameraReadout_max = MyNumberUtils.ceilToQuarterMs(cameraReadoutTime);
      final float cameraReset_max = MyNumberUtils.ceilToQuarterMs(cameraResetTime);
      
      // we will wait cameraReadout_max before triggering camera, then wait another cameraReset_max for global exposure
      // this will also be in 0.25ms increment
      final float globalExposureDelay_max = cameraReadout_max + cameraReset_max;
      
      final float laserDuration = MyNumberUtils.roundToQuarterMs(acqSettings.desiredLightExposure);
      final float scanDuration = laserDuration + 2*scanLaserBufferTime;
      // scan will be longer than laser by 0.25ms at both start and end
      
      // account for delay in scan position due to Bessel filter by starting the scan slightly earlier
      // than we otherwise would (Bessel filter selected b/c stretches out pulse without any ripples)
      // delay to start is (empirically) 0.07ms + 0.25/(freq in kHz)
      // delay to midpoint is empirically 0.38/(freq in kHz)
      // group delay for 5th-order bessel filter ~0.39/freq from theory and ~0.4/freq from IC datasheet 
      final float scanFilterFreq = Math.max(props_.getPropValueFloat(Devices.Keys.GALVOA,  Properties.Keys.SCANNER_FILTER_X),
            props_.getPropValueFloat(Devices.Keys.GALVOB,  Properties.Keys.SCANNER_FILTER_X));
      float scanDelayFilter = 0;
      if (scanFilterFreq != 0) {
         scanDelayFilter = MyNumberUtils.roundToQuarterMs(0.39f/scanFilterFreq);
      }
      
      // If the PLogic card is used, account for 0.25ms delay it introduces to
      // the camera and laser trigger signals => subtract 0.25ms from the scanner delay
      // (start scanner 0.25ms later than it would be otherwise)
      // this time-shift opposes the Bessel filter delay
      // scanDelayFilter won't be negative unless scanFilterFreq is more than 3kHz which shouldn't happen
      if (devices_.isValidMMDevice(Devices.Keys.PLOGIC)) {
         scanDelayFilter -= 0.25f;
      }
      
      s.scanDelay = globalExposureDelay_max - scanLaserBufferTime   // start scan 0.25ms before camera's global exposure
            - scanDelayFilter;    // start galvo moving early due to card's Bessel filter and delay of TTL signals via PLC
      s.scanNum = 1;
      s.scanPeriod = scanDuration;
      s.laserDelay = globalExposureDelay_max;  // turn on laser as soon as camera's global exposure is reached
      s.laserDuration = laserDuration;
      s.cameraDelay = cameraReadout_max;       // camera must readout last frame before triggering again
      
      final Devices.Keys camKey = isFirstSideA() ? Devices.Keys.CAMERAA : Devices.Keys.CAMERAB;
      final Devices.Libraries camLibrary = devices_.getMMDeviceLibrary(camKey);
      
      // figure out desired time for camera to be exposing (including reset time)
      // because both camera trigger and laser on occur on 0.25ms intervals (i.e. we may not
      //    trigger the laser until 0.24ms after global exposure) use cameraReset_max
      // special adjustment for Photometrics cameras that possibly has extra clear time which is counted in reset time
      //    but not in the camera exposure time
      // Assumed but didn't check that this applies to Kinetix camera too
      final float actualCameraResetTime = (camLibrary == Devices.Libraries.PVCAM 
            && (props_.getPropValueString(camKey, Properties.Keys.PVCAM_CHIPNAME).equals(Properties.Values.PRIME_95B_CHIPNAME) 
                  || props_.getPropValueString(camKey, Properties.Keys.PVCAM_CHIPNAME).equals(Properties.Values.KINETIX_CHIPNAME)))
            ? (float) props_.getPropValueInteger(camKey, Properties.Keys.PVCAM_READOUT_TIME) / 1e6f
            : cameraResetTime; // everything but Photometrics Prime 95B and Kinetix
      final float cameraExposure = MyNumberUtils.ceilToQuarterMs(actualCameraResetTime) + laserDuration;

      switch (acqSettings.cameraMode) {
      case EDGE:
         s.cameraDuration = 1;  // doesn't really matter, 1ms should be plenty fast yet easy to see for debugging
         s.cameraExposure = cameraExposure + 0.1f;  // add 0.1ms as safety margin, may require adding an additional 0.25ms to slice
         // slight delay between trigger and actual exposure start
         //   is included in exposure time for Hamamatsu and negligible for Andor and PCO cameras
         // ensure not to miss triggers by not being done with readout in time for next trigger, add 0.25ms if needed
         if (getSliceDuration(s) < (s.cameraExposure + cameraReadoutTime)) {
            ReportingUtils.logDebugMessage("Added 0.25ms in edge-trigger mode to make sure camera exposure long enough without dropping frames");
            s.cameraDelay += 0.25f;
            s.laserDelay += 0.25f;
            s.scanDelay += 0.25f;
         }
         break;
      case LEVEL:  // AKA "bulb mode", TTL rising starts exposure, TTL falling ends it
         s.cameraDuration = MyNumberUtils.ceilToQuarterMs(cameraExposure);
         s.cameraExposure = 1;  // doesn't really matter, controlled by TTL
         break;
      case OVERLAP:  // only Hamamatsu or Andor
         s.cameraDuration = 1;  // doesn't really matter, 1ms should be plenty fast yet easy to see for debugging
         s.cameraExposure = 1;  // doesn't really matter, controlled by interval between triggers
         break;
      case PSEUDO_OVERLAP:  // PCO or Photometrics, enforce 0.25ms between end exposure and start of next exposure by triggering camera 0.25ms into the slice
         s.cameraDuration = 1;  // doesn't really matter, 1ms should be plenty fast yet easy to see for debugging
         if (null != camLibrary) {
            switch (camLibrary) {
            case PCOCAM:
               s.cameraExposure = getSliceDuration(s) - s.cameraDelay;  // s.cameraDelay should be 0.25ms for PCO
               break;
            case PVCAM:
               s.cameraExposure = cameraExposure;
               break;
            default:
               MyDialogUtils.showError("Unknown camera library for pseudo-overlap calculations");
               break;
            }
         }
         if (s.cameraDelay < 0.24f) {
            MyDialogUtils.showError("Camera delay should be at least 0.25ms for pseudo-overlap mode.");
         }
         break;
      case LIGHT_SHEET:
         // each slice period goes like this:
         // 1. scan reset time (use to add any extra settling time to the start of each slice)
         // 2. start scan, wait scan settle time
         // 3. trigger camera/laser when scan settle time elapses
         // 4. scan for total of exposure time plus readout time (total time some row is exposing) plus settle time plus extra 0.25ms to prevent artifacts
         // 5. laser turns on 0.25ms before camera trigger and stays on until exposure is ending
         // TODO revisit this after further experimentation
         s.cameraDuration = 1;  // only need to trigger camera
         final float shutterWidth = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SHUTTER_WIDTH);
         final int shutterSpeed = props_.getPropValueInteger(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SHUTTER_SPEED); 
         float pixelSize = (float) core_.getPixelSizeUm();
         if (pixelSize < 1e-6) {  // can't compare equality directly with floating point values so call < 1e-9 is zero or negative
            pixelSize = 0.1625f;  // default to pixel size of 40x with sCMOS = 6.5um/40
         }
         final double rowReadoutTime = getRowReadoutTime();
         s.cameraExposure = (float) (rowReadoutTime * (int)(shutterWidth/pixelSize) * shutterSpeed);
         //s.cameraExposure = (float) (rowReadoutTime * shutterWidth / pixelSize * shutterSpeed);
         final float totalExposure_max = MyNumberUtils.ceilToQuarterMs(cameraReadoutTime + s.cameraExposure + 0.05f);  // 50-300us extra cushion time
         final float scanSettle = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SCAN_SETTLE);
         final float scanReset = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SCAN_RESET);
         s.scanDelay = scanReset - scanDelayFilter;
         s.scanPeriod = scanSettle + (totalExposure_max*shutterSpeed) + scanLaserBufferTime;
         s.cameraDelay = scanReset + scanSettle;
         s.laserDelay = s.cameraDelay - scanLaserBufferTime;  // trigger laser just before camera to make sure it's on already
         s.laserDuration = (totalExposure_max*shutterSpeed) + scanLaserBufferTime;  // laser will turn off as exposure is ending
         break;
      case INTERNAL:
      default:
         if (showWarnings) {
            MyDialogUtils.showError("Invalid camera mode");
         }
         s.valid = false;
         break;
      }
      
      // fix corner case of negative calculated scanDelay
      if (s.scanDelay < 0) {
         s.cameraDelay -= s.scanDelay;
         s.laserDelay -= s.scanDelay;
         s.scanDelay = 0;  // same as (-= s.scanDelay)
      }
      
      // if a specific slice period was requested, add corresponding delay to scan/laser/camera
      elementToColor.setForeground(foregroundColorOK);
      if (!acqSettings.minimizeSlicePeriod) {
         float globalDelay = acqSettings.desiredSlicePeriod - getSliceDuration(s);  // both should be in 0.25ms increments // TODO fix;
         if (acqSettings.cameraMode == CameraModes.Keys.LIGHT_SHEET) {
            globalDelay = 0;
         }
         if (globalDelay < 0) {
            globalDelay = 0;
            if (showWarnings) {  // only true when user has specified period that is unattainable
                  MyDialogUtils.showError(
                        "Increasing slice period to meet laser exposure constraint\n"
                              + "(time required for camera readout; readout time depends on ROI).");
                  elementToColor.setForeground(foregroundColorError);
            }
         }
         s.scanDelay += globalDelay;
         s.cameraDelay += globalDelay;
         s.laserDelay += globalDelay;
      }

//      // Add 0.25ms to globalDelay if it is 0 and we are on overlap mode and scan has been shifted forward
//      // basically the last 0.25ms of scan time that would have determined the slice period isn't
//      //   there any more because the scan time is moved up  => add in the 0.25ms at the start of the slice
//      // in edge or level trigger mode the camera trig falling edge marks the end of the slice period
//      // not sure if PCO pseudo-overlap needs this, probably not because adding 0.25ms overhead in that case
//      if (MyNumberUtils.floatsEqual(cameraReadout_max, 0f)  // true iff overlap being used
//            && (scanDelayFilter > 0.01f)) {
//         globalDelay += 0.25f;
//      }
      
      // fix corner case of (exposure time + readout time) being greater than the slice duration
      // most of the time the slice duration is already larger
      float globalDelay = MyNumberUtils.ceilToQuarterMs((s.cameraExposure + cameraReadoutTime) - getSliceDuration(s));
      if (globalDelay > 0) {
         s.scanDelay += globalDelay;
         s.cameraDelay += globalDelay;
         s.laserDelay += globalDelay;
      }
      
      // update the slice duration based on our new values
      s.sliceDuration = getSliceDuration(s);
      
      return s;
   }
   
   /**
    * Re-calculate the controller's timing settings for "easy timing" mode.
    * Changes panel variable sliceTiming_.
    * The controller's properties will be set as needed
    * Change/action listers are short-circuited here 
    * @param showWarnings will show warning if the user-specified slice period too short
    *                      or if cameras aren't assigned
    */
   private void recalculateSliceTiming(boolean showWarnings) {
      if(!checkCamerasAssigned(showWarnings)) {
         return;
      }
      // if user is providing his own slice timing don't change it
      if (advancedSliceTimingCB_.isSelected()) {
         return;
      }
      updatingTiming_ = true;
      if (ASIdiSPIM.SCOPE) {
         sliceTiming_ = getTimingSingleObjective(showWarnings);
      } else {
         sliceTiming_ = getTimingFromPeriodAndLightExposure(showWarnings);
      }
      PanelUtils.setSpinnerFloatValue(delayScan_, sliceTiming_.scanDelay);
      numScansPerSlice_.setValue(sliceTiming_.scanNum);
      PanelUtils.setSpinnerFloatValue(lineScanDuration_, sliceTiming_.scanPeriod);
      PanelUtils.setSpinnerFloatValue(delayLaser_, sliceTiming_.laserDelay);
      PanelUtils.setSpinnerFloatValue(durationLaser_, sliceTiming_.laserDuration);
      PanelUtils.setSpinnerFloatValue(delayCamera_, sliceTiming_.cameraDelay);
      PanelUtils.setSpinnerFloatValue(durationCamera_, sliceTiming_.cameraDuration );
      PanelUtils.setSpinnerFloatValue(exposureCamera_, sliceTiming_.cameraExposure );
      updatingTiming_ = false;
   }
   
   /**
    * Update the displayed slice period.
    */
   private void updateActualSlicePeriodLabel() {
      recalculateSliceTiming(false);
      actualSlicePeriodLabel_.setText(
            NumberUtils.doubleToDisplayString(
                    sliceTiming_.sliceDuration) +
            " ms");
   }
   
   /**
    * gets the acceleration used during scanning, or 0.0 if supplemental stage is being used
    * @param acqSettings
    * @return
    */
   private double getScanStageAcceleration(AcquisitionSettings acqSettings) {
      if (acqSettings.spimMode == AcquisitionModes.Keys.STAGE_STEP_SUPPLEMENTAL_UNIDIRECTIONAL
            || acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL) {
         return 0.0;
      } else {
         return controller_.computeScanAcceleration(
               controller_.computeScanSpeed(acqSettings)) + 1;  // extra 1 for rounding up that often happens in controller
      }
   }
   
   /**
    * calculate the total ramp time for stage scan in units of milliseconds (includes both acceleration and settling time
    *   given by "delay before side" setting)
    * @param acqSettings
    * @return
    */
   private double getStageRampDuration(AcquisitionSettings acqSettings) {
      final double accelerationX = getScanStageAcceleration(acqSettings);
      ReportingUtils.logDebugMessage("stage ramp duration is " + (acqSettings.delayBeforeSide + accelerationX) + " milliseconds");
      return acqSettings.delayBeforeSide + accelerationX;
   }
   
   /**
    * calculate the retrace time in stage scan raster mode in units of milliseconds
    * @param acqSettings
    * @return
    */
   private double getStageRetraceDuration(AcquisitionSettings acqSettings) {
      final double retraceSpeed;
      if (acqSettings.spimMode == AcquisitionModes.Keys.STAGE_STEP_SUPPLEMENTAL_UNIDIRECTIONAL
            || acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL) {
         if (devices_.getMMDeviceLibrary(Devices.Keys.SUPPLEMENTAL_X) == Devices.Libraries.PI_GCS_2) {
            retraceSpeed = props_.getPropValueFloat(Devices.Keys.SUPPLEMENTAL_X, Properties.Keys.VELOCITY);
         } else {
            retraceSpeed = 1;  // wild-guess default
         }
      } else {
         final float retraceRelativeSpeedPercent;
         if (props_.hasProperty(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_RETRACE_SPEED)) {
            // this added in firmware v3.30; if not present then we set to firmware default hardcoded previously
            retraceRelativeSpeedPercent = props_.getPropValueFloat(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_RETRACE_SPEED);
         } else {
            retraceRelativeSpeedPercent = 67.f;
         }
         retraceSpeed = retraceRelativeSpeedPercent/100f*props_.getPropValueFloat(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_MAX_MOTOR_SPEED_X);
      }
      final DeviceUtils du = new DeviceUtils(gui_, devices_, props_, prefs_);
      final double speedFactor = du.getStageGeometricSpeedFactor(acqSettings.firstSideIsA);
      final double scanDistance = acqSettings.numSlices * acqSettings.stepSizeUm * speedFactor;
      final double accelerationX = getScanStageAcceleration(acqSettings);
      final double retraceDuration = scanDistance/retraceSpeed + accelerationX*2;
      ReportingUtils.logDebugMessage("stage retrace duration is " + retraceDuration + " milliseconds");
      return retraceDuration;
   }

   /**
    * Compute the volume duration in ms based on controller's timing settings.
    * Includes time for multiple channels.  However, does not include for multiple positions.
    * @param acqSettings Settings for the acquisition
    * @return duration in ms
    */
   public double computeActualVolumeDuration(AcquisitionSettings acqSettings) {
      final MultichannelModes.Keys channelMode = acqSettings.channelMode;
      final int numChannels = acqSettings.numChannels;
      final int numSides = acqSettings.numSides;
      final float delayBeforeSide = acqSettings.delayBeforeSide;
      int numCameraTriggers = acqSettings.numSlices;
      if (acqSettings.cameraMode == CameraModes.Keys.OVERLAP) {
        numCameraTriggers += 1;
      }
      // stackDuration is per-side, per-channel, per-position
      
      final double stackDuration = numCameraTriggers * acqSettings.sliceTiming.sliceDuration;
      if (acqSettings.isStageScanning || acqSettings.isStageStepping) {
         final double rampDuration = getStageRampDuration(acqSettings);
         final double retraceTime = getStageRetraceDuration(acqSettings);
         // TODO double-check these calculations below, at least they are better than before ;-)
         if (acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN) {
            if (channelMode == MultichannelModes.Keys.SLICE_HW) {
               return retraceTime + (numSides * ((rampDuration * 2) + (stackDuration * numChannels)));
            } else {  // "normal" stage scan with volume channel switching
               if (numSides == 1) {
                  // single-view so will retrace at beginning of each channel
                  return ((rampDuration * 2) + stackDuration + retraceTime) * numChannels;
               } else {
                  // will only retrace at very start/end
                  return retraceTime + (numSides * ((rampDuration * 2) + stackDuration) * numChannels);
               }
            }
         } else if (acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_UNIDIRECTIONAL
               || acqSettings.spimMode == AcquisitionModes.Keys.STAGE_STEP_SUPPLEMENTAL_UNIDIRECTIONAL
               || acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL) {
            if (channelMode == MultichannelModes.Keys.SLICE_HW) {
               return ((rampDuration * 2) + (stackDuration * numChannels) + retraceTime) * numSides;
            } else {  // "normal" stage scan with volume channel switching
               return ((rampDuration * 2) + stackDuration + retraceTime) * numChannels * numSides;
            }
         } else {  // interleaved mode => one-way pass collecting both sides
            if (channelMode == MultichannelModes.Keys.SLICE_HW) {
               // single pass with all sides and channels
               return retraceTime + (rampDuration * 2 + stackDuration * numSides * numChannels);
            } else {  // one-way pass collecting both sides, then rewind for next channel
               return ((rampDuration * 2) + (stackDuration * numSides) + retraceTime) * numChannels;
            }
         }
      } else { // piezo scan
         double channelSwitchDelay = 0;
         if (channelMode == MultichannelModes.Keys.VOLUME) {
               channelSwitchDelay = 500;   // estimate channel switching overhead time as 0.5s
               // actual value will be hardware-dependent
         }
         if (channelMode == MultichannelModes.Keys.SLICE_HW) {
            return numSides * (delayBeforeSide + stackDuration * numChannels);  // channelSwitchDelay = 0
         } else {
            return numSides * numChannels
                  * (delayBeforeSide + stackDuration)
                  + (numChannels - 1) * channelSwitchDelay;
         }
      }
   }
   
   /**
    * Compute the timepoint duration in ms.  Only difference from computeActualVolumeDuration()
    * is that it also takes into account the multiple positions, if any.
    * @return duration in ms
    */
   private double computeTimepointDuration() {
      AcquisitionSettings acqSettings = getCurrentAcquisitionSettings();
      final double volumeDuration = computeActualVolumeDuration(acqSettings);
      if (acqSettings.useMultiPositions) {
         try {
            // use 1.5 seconds motor move between positions
            // (could be wildly off but was estimated using actual system
            // and then slightly padded to be conservative to avoid errors
            // where positions aren't completed in time for next position)
            // could estimate the actual time by analyzing the position's relative locations
            //   and using the motor speed and acceleration time
            return gui_.getPositionList().getNumberOfPositions() *
                  (volumeDuration + 1500 + PanelUtils.getSpinnerFloatValue(positionDelay_));
         } catch (MMScriptException ex) {
            MyDialogUtils.showError(ex, "Error getting position list for multiple XY positions");
         }
      }
      return volumeDuration;
   }
   
  /**
   * Compute the volume duration in ms based on controller's timing settings.
   * Includes time for multiple channels.
   * @return duration in ms
   */
  private double computeActualVolumeDuration() {
     return computeActualVolumeDuration(getCurrentAcquisitionSettings());
  }

   /**
    * Update the displayed volume duration.
    */
   private void updateActualVolumeDurationLabel() {
      double duration = computeActualVolumeDuration();
      if (duration > 1000) {
         actualVolumeDurationLabel_.setText(
               NumberUtils.doubleToDisplayString(duration/1000d) +
               " s"); // round to ms
      } else {
         actualVolumeDurationLabel_.setText(
               NumberUtils.doubleToDisplayString(Math.round(10*duration)/10d) +
               " ms");  // round to tenth of ms
      }
   }
   
   /**
    * Compute the time lapse duration
    * @return duration in s
    */
   private double computeActualTimeLapseDuration() {
      double duration = (getNumTimepoints() - 1) * getTimepointInterval() 
            + computeTimepointDuration()/1000;
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
    * Computes the per-row readout time of the SPIM cameras set on Devices panel.
    * Handles single-side operation.
    * Needed for computing camera exposure in light sheet mode
    * @return
    */
   private double getRowReadoutTime() {
      if (getNumSides() > 1) {
         return Math.max(cameras_.getRowReadoutTime(Devices.Keys.CAMERAA),
               cameras_.getRowReadoutTime(Devices.Keys.CAMERAB));
      } else {
         Devices.Keys camKey = Devices.getSideSpecificKey(Devices.Keys.CAMERAA, getFirstSide());
         return cameras_.getRowReadoutTime(camKey);
      }
   }
   
   /**
    * Computes the reset time of the SPIM cameras set on Devices panel.
    * Handles single-side operation.
    * Needed for computing (semi-)optimized slice timing in "easy timing" mode.
    * @return
    */
   private float computeCameraResetTime() {
      CameraModes.Keys camMode = getSPIMCameraMode();
      if (getNumSides() > 1) {
         return Math.max(cameras_.computeCameraResetTime(Devices.Keys.CAMERAA, camMode),
               cameras_.computeCameraResetTime(Devices.Keys.CAMERAB, camMode));
      } else {
         Devices.Keys camKey = Devices.getSideSpecificKey(Devices.Keys.CAMERAA, getFirstSide());
         return cameras_.computeCameraResetTime(camKey, camMode);
      }
   }
   
   /**
    * Computes the readout time of the SPIM cameras set on Devices panel.
    * Handles single-side operation.
    * Needed for computing (semi-)optimized slice timing in "easy timing" mode.
    * @return
    */
   private float computeCameraReadoutTime() {
      CameraModes.Keys camMode = getSPIMCameraMode();
      if (getNumSides() > 1) {
         return Math.max(cameras_.computeCameraReadoutTime(Devices.Keys.CAMERAA, camMode),
               cameras_.computeCameraReadoutTime(Devices.Keys.CAMERAB, camMode));
      } else {
         Devices.Keys camKey = Devices.getSideSpecificKey(Devices.Keys.CAMERAA, getFirstSide());
         return cameras_.computeCameraReadoutTime(camKey, camMode);
      }
   }
   
   /**
    * Makes sure that cameras are assigned to the desired sides and display error message
    * if not (e.g. if single-sided with side B first, then only checks camera for side B)
    * @return true if cameras assigned, false if not
    */
   private boolean checkCamerasAssigned(boolean showWarnings) {
      String firstCamera, secondCamera;
      final boolean firstSideA = isFirstSideA();
      if (prefs_.getBoolean(MyStrings.PanelNames.CAMERAS.toString(),
            Properties.Keys.PLUGIN_USE_SIMULT_CAMERAS, false)) { 
         firstCamera = devices_.getMMDevice(Devices.Keys.CAMERA_A1);
         secondCamera = devices_.getMMDevice(Devices.Keys.CAMERA_A2);
      } else {
         if (firstSideA) {
            firstCamera = devices_.getMMDevice(Devices.Keys.CAMERAA);
            secondCamera = devices_.getMMDevice(Devices.Keys.CAMERAB);  // might be null for single-sided
         } else {
            firstCamera = devices_.getMMDevice(Devices.Keys.CAMERAB);
            secondCamera = devices_.getMMDevice(Devices.Keys.CAMERAA);
         }
      }
      if (firstCamera == null) {
         if (showWarnings) {
            MyDialogUtils.showError("Please select a valid camera for the first side (Imaging Path " +
                  (firstSideA ? "A" : "B") + ") on the Devices Panel");
         }
         return false;
      }
      if (getNumSides()> 1  && secondCamera == null) {
         if (showWarnings) {
            MyDialogUtils.showError("Please select a valid camera for the second side (Imaging Path " +
                  (firstSideA ? "B" : "A") + ") on the Devices Panel.");
         }
         return false;
      }
      return true;
   }
   
   /**
    * Utility method to update status text in GUI according to the current phase of acquisition
    * @param phase
    */
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
         text = "Acquiring";
         if (numTimePointsDone_ > 1) {
            text += " time point "
               + NumberUtils.intToDisplayString(numTimePointsDone_)
               + " of "
               + NumberUtils.intToDisplayString(getNumTimepoints())
               + "; ";
         }
         if (numPositionsDone_ > 0) {
            text += " position "
                  + NumberUtils.intToDisplayString(numPositionsDone_)
                  + " of "
                  + NumberUtils.intToDisplayString(getNumPositions());
         }
         // TODO make sure the number of timepoints can't change during an acquisition
         // (or maybe we make a hidden feature where the acquisition can be terminated by changing)
         break;
      case WAITING:
         text = "Next timepoint ("
               + NumberUtils.intToDisplayString(numTimePointsDone_+1)
               + " of "
               + NumberUtils.intToDisplayString(getNumTimepoints())
               + ") in "
               + NumberUtils.intToDisplayString(secsToNextAcquisition)
               + " s.";
         break;
      case DONE:
         text = "Finished with "
               + NumberUtils.intToDisplayString(numTimePointsDone_)
               + " time points and "
               + NumberUtils.intToDisplayString(numPositionsDone_)
               + " positions";
         break;
      case NON_FATAL_ERROR:
         text = "Non-fatal error.  Finished with "
               + NumberUtils.intToDisplayString(numTimePointsDone_)
               + " time points and "
               + NumberUtils.intToDisplayString(numPositionsDone_)
               + " positions";
         break;
      default:
         break;   
      }
      acquisitionStatusLabel_.setText(text);
      acquisitionStatus_ = phase;
   }
   
   private boolean requiresPiezos(AcquisitionModes.Keys mode) {
      switch (mode) {
      case STAGE_SCAN:
      case NONE:
      case SLICE_SCAN_ONLY:
      case STAGE_SCAN_INTERLEAVED:
      case STAGE_SCAN_UNIDIRECTIONAL:
      case STAGE_STEP_SUPPLEMENTAL_UNIDIRECTIONAL:
      case STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL:
      case NO_SCAN:
      case EXT_TRIG_ACQ:
         return false;
      case PIEZO_SCAN_ONLY:
      case PIEZO_SLICE_SCAN:
         return true;
      default:
         MyDialogUtils.showError("Unspecified acquisition mode " + mode.toString());
         return true;
      }
   }
   
   /**
    * runs a test acquisition with the following features:
    *   - not saved to disk
    *   - window can be closed without prompting to save
    *   - timepoints disabled
    *   - autofocus disabled
    * @param side Devices.Sides.NONE to run as specified in acquisition tab,
    *   Devices.Side.A or B to run only that side
    */
   public void runTestAcquisition(final Devices.Sides side) {
      final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
         @Override
         protected Void doInBackground() throws Exception {
            ReportingUtils.logDebugMessage("User requested start of test diSPIM acquisition with side " + side.toString() + " selected.");
            cancelAcquisition_.set(false);
            acquisitionRequested_.set(true);
            updateStartButton();
            final boolean hideErrors = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(), 
                  Properties.Keys.PLUGIN_ACQUIRE_FAIL_QUIETLY, true);
            AcquisitionStatus success = runAcquisitionPrivate(true, side, hideErrors);
            if (success == AcquisitionStatus.FATAL_ERROR) {
               ReportingUtils.logError("Fatal error running test diSPIM acquisition.");
            }
            acquisitionRequested_.set(false);
            acquisitionRunning_.set(false);
            updateStartButton();
            // deskew automatically if we were supposed to
            AcquisitionModes.Keys spimMode = getAcquisitionMode();
            if (spimMode == AcquisitionModes.Keys.STAGE_SCAN || spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED
                    || spimMode == AcquisitionModes.Keys.STAGE_SCAN_UNIDIRECTIONAL
                    || spimMode == AcquisitionModes.Keys.STAGE_STEP_SUPPLEMENTAL_UNIDIRECTIONAL
                    || spimMode == AcquisitionModes.Keys.STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL
                    ) {
               if (prefs_.getBoolean(MyStrings.PanelNames.DATAANALYSIS.toString(),
                       Properties.Keys.PLUGIN_DESKEW_AUTO_TEST, false)) {
                  ASIdiSPIM.getFrame().getDataAnalysisPanel().runDeskew(acquisitionPanel_);
               }
            }
            return null;
         }
         
         @Override
         protected void done() {
            // update the beam and piezo/galvo
            // a little bit complicated to handle all situations
            if (side == Devices.Sides.NONE) {  // call from acquisition panel
               if (getNumSides()>1) { // two-sided
                  ASIdiSPIM.getFrame().getSetupPanel(Devices.Sides.A).refreshCameraBeamSettings();
                  ASIdiSPIM.getFrame().getSetupPanel(Devices.Sides.A).centerPiezoAndGalvo();
                  ASIdiSPIM.getFrame().getSetupPanel(Devices.Sides.B).refreshCameraBeamSettings();
                  ASIdiSPIM.getFrame().getSetupPanel(Devices.Sides.B).centerPiezoAndGalvo();
               } else {
                  final Devices.Sides side2 = getFirstSide();
                  ASIdiSPIM.getFrame().getSetupPanel(side2).refreshCameraBeamSettings();
                  ASIdiSPIM.getFrame().getSetupPanel(side2).centerPiezoAndGalvo();
               }
            } else { // coming from setup panels
               ASIdiSPIM.getFrame().getSetupPanel(side).refreshCameraBeamSettings();
               ASIdiSPIM.getFrame().getSetupPanel(side).centerPiezoAndGalvo();
            }
         }
      };
      worker.execute();
   }
   
   /**
    * runs an overview acquisition that gets passed to other code to display as a 2D projection
    */
   public void runOverviewAcquisition() {
      
      class OverviewTask extends SwingWorker<Void, Void> {

         OverviewTask() {
            // empty constructor for now 
         };
      
         @Override
         protected Void doInBackground() throws Exception {

            if (core_.getPixelSizeUm() < 1e-6) {  // can't compare equality directly with floating point values so call < 1e-9 is zero or negative
               ReportingUtils.showError("Need to configure pixel size in Micro-Manager to use overview acquisition.");
               return null;
            }
            
            if (ASIdiSPIM.getFrame().getHardwareInUse()) {
               ReportingUtils.showError("Cannot run overview acquisition while another"
                     + " autofocus or acquisition is ongoing.");
               return null;
            }
            
            // get settings that we will use
            String camera = devices_.getMMDevice(Devices.Keys.CAMERAA);
            Devices.Keys cameraDevice = Devices.Keys.CAMERAA;
            boolean usingDemoCam = devices_.getMMDeviceLibrary(Devices.Keys.CAMERAA).equals(Devices.Libraries.DEMOCAM);
            final Devices.Sides side = 
                  props_.getPropValueString(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_OVERVIEW_SIDE).equals("B")
                  ? Devices.Sides.B : Devices.Sides.A;
            if (side.equals(Devices.Sides.B)) {
               camera = devices_.getMMDevice(Devices.Keys.CAMERAB);
               cameraDevice = Devices.Keys.CAMERAB;
               usingDemoCam = devices_.getMMDeviceLibrary(Devices.Keys.CAMERAB).equals(Devices.Libraries.DEMOCAM);
            }
            final String overviewChannel = props_.getPropValueString(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_OVERVIEW_CHANNEL);
            final double downsampleY = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_OVERVIEW_XY_DOWNSAMPLE_FACTOR);
            overviewDownsampleY_ = downsampleY;
            final double downsampleSpacing = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_OVERVIEW_SLICE_DOWNSAMPLE_FACTOR);
            final double thicknessFactor = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_OVERVIEW_SLICE_THICKNESS_FACTOR);
            final double positionFactorUser = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_OVERVIEW_SLICE_POSITION_FACTOR);
            final double positionFactor;
            // make sure position offset doesn't make ROI run off of image on either end
            if (positionFactorUser + thicknessFactor/2 > 1.0) {
               positionFactor = 1.0 - thicknessFactor/2;
            } else if (positionFactorUser - thicknessFactor/2 < 0.0) {
               positionFactor = thicknessFactor/2;
            } else {
               positionFactor = positionFactorUser;
            }
            double deskewFactor = props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_DESKEW_FACTOR);
            if (deskewFactor == 0.0) {
               deskewFactor = 1.0;
            }
            final boolean overwriteWindow = prefs_.getBoolean(MyStrings.PanelNames.DATAANALYSIS.toString(),
                  Properties.Keys.PLUGIN_OVERVIEW_OVERWRITE_WINDOW, true);
            
            // initialize stage scanning so we can restore state
            Point2D.Double xyPosUm = new Point2D.Double();
            float origXSpeed = 1f;  // don't want 0 in case something goes wrong
            float origXAccel = 1f;  // don't want 0 in case something goes wrong
            float origZSpeed = 1f;  // don't want 0 in case something goes wrong
            try {
               xyPosUm = core_.getXYStagePosition(devices_.getMMDevice(Devices.Keys.XYSTAGE));
               origXSpeed = props_.getPropValueFloat(Devices.Keys.XYSTAGE,
                     Properties.Keys.STAGESCAN_MOTOR_SPEED_X);
               origXAccel = props_.getPropValueFloat(Devices.Keys.XYSTAGE,
                     Properties.Keys.STAGESCAN_MOTOR_ACCEL_X);
               if (devices_.isValidMMDevice(Devices.Keys.UPPERZDRIVE)) {
                  origZSpeed = props_.getPropValueFloat(Devices.Keys.UPPERZDRIVE,
                        Properties.Keys.STAGESCAN_MOTOR_SPEED_Z);
               }
            } catch (Exception ex) {
               // do nothing
            }

            // if X speed is less than 0.2 mm/s then it probably wasn't restored to correct speed some other time
            // we offer to set it to a more normal speed in that case, until the user declines and we stop asking
            if (origXSpeed < 0.2 && resetXaxisSpeed_) {
               resetXaxisSpeed_ = MyDialogUtils.getConfirmDialogResult(
                     "Max speed of X axis is small, perhaps it was not correctly restored after stage scanning previously.  Do you want to set it to 1 mm/s now?",
                     JOptionPane.YES_NO_OPTION);
               // once the user selects "no" then resetXaxisSpeed_ will be false and stay false until plugin is launched again
               if (resetXaxisSpeed_) {
                  props_.setPropValue(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_MOTOR_SPEED_X, 1f);
                  origXSpeed = 1f;
               }
            }
            // if Z speed is less than 0.2 mm/s then it probably wasn't restored to correct speed some other time
            // we offer to set it to a more normal speed in that case, until the user declines and we stop asking
            if (origZSpeed < 0.2 && resetZaxisSpeed_) {
               resetZaxisSpeed_ = MyDialogUtils.getConfirmDialogResult(
                     "Max speed of Z axis is small, perhaps it was not correctly restored after stage scanning previously.  Do you want to set it to 1 mm/s now?",
                     JOptionPane.YES_NO_OPTION);
               // once the user selects "no" then resetZaxisSpeed_ will be false and stay false until plugin is launched again
               if (resetZaxisSpeed_) {
                  props_.setPropValue(Devices.Keys.UPPERZDRIVE, Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, 1f);
                  origZSpeed = 1f;
               }
            }
            
            ReportingUtils.logDebugMessage("User requested start of overview diSPIM acquisition with side " + side.toString() + " selected.");

            // start with current acquisition settings, then modify a few of them for the overview acquisition
            AcquisitionSettings acqSettingsTmp = getCurrentAcquisitionSettings();
            int nrImages = (int)Math.round(acqSettingsTmp.numSlices/downsampleSpacing);
            acqSettingsTmp.isStageScanning = true;
            acqSettingsTmp.isStageStepping = false;
            acqSettingsTmp.hardwareTimepoints = false;
            acqSettingsTmp.spimMode = AcquisitionModes.Keys.STAGE_SCAN_UNIDIRECTIONAL;
            acqSettingsTmp.channelMode = MultichannelModes.Keys.NONE;
            acqSettingsTmp.numChannels = 1;
            acqSettingsTmp.numSlices = nrImages;
            acqSettingsTmp.numTimepoints = 1;
            acqSettingsTmp.timepointInterval = 1;
            acqSettingsTmp.numSides = 1;
            acqSettingsTmp.firstSideIsA = side.equals(Sides.A);
            acqSettingsTmp.useTimepoints = false;
            acqSettingsTmp.useMultiPositions = true;
            acqSettingsTmp.stepSizeUm *= downsampleSpacing;
            
            if (acqSettingsTmp.useChannels) {
               if (!multiChannelPanel_.isChannelValid(overviewChannel)) {
                  MyDialogUtils.showError("Invalid channel selected for overview acquisition (Data Analysis tab).");
                  return null;
               }
               acqSettingsTmp.useChannels = false;
               multiChannelPanel_.selectChannel(overviewChannel);
            }

            posUpdater_.pauseUpdates(true);
            final AcquisitionSettings acqSettings = acqSettingsTmp;
            controller_.prepareControllerForAquisition(acqSettings, extraChannelOffset_);
            final double zStepUm = controller_.getActualStepSizeUm();

            // overview acquisition code below adapted from autofocus code (any bugs may have been copied)

            boolean liveModeOriginally = false;
            String originalCamera = gui_.getMMCore().getCameraDevice();
            try {
               liveModeOriginally = gui_.isLiveModeOn();
               if (liveModeOriginally) {
                  gui_.enableLiveMode(false);
                  gui_.getMMCore().waitForDevice(originalCamera);
               }

               ASIdiSPIM.getFrame().setHardwareInUse(true);

               // deal with shutter before starting acquisition
               // needed despite core's handling because of DemoCamera
               boolean autoShutter = gui_.getMMCore().getAutoShutter();
               boolean shutterOpen = gui_.getMMCore().getShutterOpen();
               if (autoShutter) {
                  gui_.getMMCore().setAutoShutter(false);
                  if (!shutterOpen) {
                     gui_.getMMCore().setShutterOpen(true);
                  }
               }
               
               gui_.getMMCore().setCameraDevice(camera);
               gui_.getMMCore().initializeCircularBuffer();  // also does clearCircularBuffer()
               cameras_.setCameraForAcquisition(cameraDevice, true);
               prefs_.putFloat(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FIRST.toString(),
                     (float)gui_.getMMCore().getExposure());
               gui_.getMMCore().setExposure((double) acqSettings.sliceTiming.cameraExposure);
               gui_.refreshGUIFromCache();
               
               // design decision to write the grid to the position list and use it even if "Multiple positions (XY)" has been left unchecked
               // maybe this is not a good idea, should revisit later
               
               boolean noPositions = false;  // will set to true in the case that there are no position list (just use current position)
               int nrPositions = 0;
               PositionList positionList = new PositionList();
               positionList = gui_.getPositionList();
               nrPositions = positionList.getNumberOfPositions();
               if (nrPositions < 1) {
                  noPositions = true;
                  nrPositions = 1;
               }
               
               // find bounding positions
               double minX, minY, maxX, maxY;
               if (noPositions) {
                  Point2D.Double pos2D = core_.getXYStagePosition(devices_.getMMDevice(Devices.Keys.XYSTAGE));
                  minX = pos2D.x;
                  maxX = pos2D.x;
                  minY = pos2D.y;
                  maxY = pos2D.y;
               } else {
                  StagePosition p = positionList.getPosition(0).get(devices_.getMMDevice(Devices.Keys.XYSTAGE));
                  minX = p.x;
                  maxX = minX;
                  minY = p.y;
                  maxY = minY;
                  for (int i=1; i<nrPositions; ++i) {
                     p = positionList.getPosition(i).get(devices_.getMMDevice(Devices.Keys.XYSTAGE));
                     minX = Math.min(p.x, minX);
                     minY = Math.min(p.y, minY);
                     maxX = Math.max(p.x, maxX);
                     maxY = Math.max(p.y, maxY);
                  }
               }
               overviewMin_ = new Point2D.Double(minX, minY);
               overviewMax_ = new Point2D.Double(maxX, maxY);
               
               // get geometric factors
               double compressX = Math.sqrt(0.5);  // factor between 0 and 1 (divide by this to replicate top view) 
               DeviceUtils du = new DeviceUtils(gui_, devices_, props_, prefs_);
               int deskewSign = -1;
               try {
                  deskewSign = du.getDeskewSign(0, acqSettings.spimMode, acqSettings.numSides > 1, acqSettings.firstSideIsA);
                  compressX = du.getStageTopViewCompressFactor(acqSettings.firstSideIsA);  // check logic that uses this below; correct empirically for 45 degrees but use doesn't make full sense to me
               } catch(Exception ex) {
                  // ignore errors, stick with defaults
               }
               overviewCompressX_ = compressX;

               // create the image that we will display as the overview
               // core should know correct image dimensions because we have set up camera already
               final int imageWidth = (int)core_.getImageWidth();
               final int imageHeight = (int)core_.getImageHeight();
               final double pixelSize = core_.getPixelSizeUm();
               final double pixelScaled = pixelSize * downsampleY;
               overviewPixelSize_ = pixelScaled;
               final int roiWidth = (int)Math.round(imageWidth*thicknessFactor);
               final int roiOffset = (int)(positionFactor*imageWidth - roiWidth/2);
               final int scaledWidth = (int)Math.round(roiWidth/downsampleY/compressX);  // scale width by additional geometric factor b/c just doing max projection
               final int scaledHeight = (int)Math.round(imageHeight/downsampleY);
               final double zStepPx = zStepUm / pixelSize;
               final double dx = zStepPx * du.getStageGeometricShiftFactor(true) * deskewFactor / downsampleY / compressX;
               if (dx < 1e-9) {
                  throw new ASIdiSPIMException("Expected dx to be positive");
               }
               final int width_expansion = (int) Math.abs(Math.ceil(dx*(nrImages-1)));
               overviewWidthExpansion_ = width_expansion;
               final int extraWidth = (int)Math.ceil((maxX-minX)/pixelScaled);
               final int extraHeight = (int)Math.ceil((maxY-minY)/pixelScaled);
               final int totalWidth = scaledWidth + width_expansion + extraWidth;
               final int totalHeight = scaledHeight + extraHeight;
               final int bitDepth = 16;
               
               ImagePlus overviewImagePlus = IJ.createImage("Overview", totalWidth, totalHeight, 1, bitDepth);  // hard-code to short (16-bit) pixels
               overviewIP_ = overviewImagePlus;  // track latest displayed ImagePlus for use outside the acquisition 
               overviewImagePlus.getProcessor().setBackgroundValue(0.0);
               overviewImagePlus.getProcessor().fill();
               ij.measure.Calibration cal = overviewImagePlus.getCalibration();
               cal.pixelWidth = pixelScaled;
               cal.pixelHeight = pixelScaled;
               cal.setUnit("um");
               if (overwriteWindow) {
                  java.awt.Window win = ij.WindowManager.getWindow("Overview");
                  while (win != null) {
                     ij.WindowManager.setWindow(win);
                     IJ.run("Close");
                     win = ij.WindowManager.getWindow("Overview");
                  }
               }
               overviewImagePlus.show();
               
               // do acquisition looping over all positions
               for (int positionNum = 0; positionNum < nrPositions; positionNum++) {
                  numPositionsDone_ = positionNum + 1;
                  updateAcquisitionStatus(AcquisitionStatus.ACQUIRING);

                  // make sure user didn't stop things
                  if (cancelAcquisition_.get()) {
                     throw new IllegalMonitorStateException("User stopped the acquisition");
                  }

                  // TODO extract the stage scanning code so it isn't copy/paste here and in acquisition code
                  
                  // want to move between positions move stage fast, so we 
                  //   will clobber stage scanning setting so need to restore it
                  float scanXSpeed = 1f;
                  float scanXAccel = 1f;
                  float scanZSpeed = 1f;
                  scanXSpeed = props_.getPropValueFloat(Devices.Keys.XYSTAGE,
                        Properties.Keys.STAGESCAN_MOTOR_SPEED_X);
                  props_.setPropValue(Devices.Keys.XYSTAGE,
                        Properties.Keys.STAGESCAN_MOTOR_SPEED_X, origXSpeed);
                  scanXAccel = props_.getPropValueFloat(Devices.Keys.XYSTAGE,
                        Properties.Keys.STAGESCAN_MOTOR_ACCEL_X);
                  props_.setPropValue(Devices.Keys.XYSTAGE,
                        Properties.Keys.STAGESCAN_MOTOR_ACCEL_X, origXAccel);
                  if (devices_.isValidMMDevice(Devices.Keys.UPPERZDRIVE)) {
                     scanZSpeed = props_.getPropValueFloat(Devices.Keys.UPPERZDRIVE,
                           Properties.Keys.STAGESCAN_MOTOR_SPEED_Z);
                     props_.setPropValue(Devices.Keys.UPPERZDRIVE,
                           Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, origZSpeed);
                  }

                  final Point2D.Double pos2D;
                  if (noPositions) {
                     pos2D = core_.getXYStagePosition(devices_.getMMDevice(Devices.Keys.XYSTAGE));  // read actual position
                  } else {
                     final MultiStagePosition nextPosition = positionList.getPosition(positionNum);
                     StagePosition pos = nextPosition.get(devices_.getMMDevice(Devices.Keys.XYSTAGE));
                     pos2D = new Point2D.Double(pos.x, pos.y);  // use position list position since we have it
                     try {
                        // blocking call; will wait for stages to move
                        MultiStagePosition.goToPosition(nextPosition, core_);
                     } catch (Exception ex) {
                        // ignore errors here, only log them.  Seems to happen if stage is undefined
                        ReportingUtils.logError(ex, "Couldn't fully go to requested position");
                     }
                  }

                  // for stage scanning: restore speed and set up scan at new position 
                  props_.setPropValue(Devices.Keys.XYSTAGE,
                        Properties.Keys.STAGESCAN_MOTOR_SPEED_X, scanXSpeed);
                  props_.setPropValue(Devices.Keys.XYSTAGE,
                        Properties.Keys.STAGESCAN_MOTOR_ACCEL_X, scanXAccel);
                  if (devices_.isValidMMDevice(Devices.Keys.UPPERZDRIVE)) {
                     props_.setPropValue(Devices.Keys.UPPERZDRIVE,
                           Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, scanZSpeed);
                  }
                  
                  // this always gets called for overview
                  controller_.prepareStageScanForAcquisition(pos2D.x, pos2D.y, acqSettings.spimMode);

                  // wait any extra time the user requests
                  Thread.sleep(Math.round(PanelUtils.getSpinnerFloatValue(positionDelay_)));

                  gui_.getMMCore().startSequenceAcquisition(camera, acqSettings.numSlices, 0, true);
                  boolean success = controller_.triggerControllerStartAcquisition(acqSettings.spimMode, acqSettings.firstSideIsA);
                  if (!success) {
                     throw new ASIdiSPIMException("Failed to trigger controller");
                  }

                  long startTime = System.currentTimeMillis();
                  long now = startTime;
                  long timeout = 20000;  // wait 20 seconds for first image to come, maybe have long move for stage
                  while (gui_.getMMCore().getRemainingImageCount() == 0
                        && (now - startTime < timeout)) {
                     now = System.currentTimeMillis();
                     Thread.sleep(5);
                  }
                  if (now - startTime >= timeout) {
                     throw new ASIdiSPIMException("Camera did not send first image within a reasonable time");
                  }

                  // get and process the incoming images
                  double xPosPass = (double)(totalWidth-scaledWidth);
                  boolean done = false;
                  int counter = 0;
                  startTime = System.currentTimeMillis();
                  while ((gui_.getMMCore().getRemainingImageCount() > 0
                        || gui_.getMMCore().isSequenceRunning(camera))
                        && !done) {
                     now = System.currentTimeMillis();
                     if (gui_.getMMCore().getRemainingImageCount() > 0) {  // we have an image to grab
                        TaggedImage timg = gui_.getMMCore().popNextTaggedImage();
                        // reset our wait timer since we got an image
                        startTime = System.currentTimeMillis();

                        // crop/scale/flip incoming camera image to make it match overview
                        ImageProcessor ip = AutofocusUtils.makeProcessor(timg);
                        ip.setInterpolationMethod(ImageProcessor.BILINEAR);
                        ip.setRoi(roiOffset, 0, roiWidth, imageHeight);
                        ImageProcessor cropped = ip.crop().convertToShort(false);
                        ImageProcessor scaledCamera = cropped.resize(scaledWidth, scaledHeight, true);
                        // match sample orientation in physical space; neither ASI nor Shroff conventions match physical coordinates of XY stage but operation different in two cases
                        if (deskewSign<0) {
                           scaledCamera.flipVertical();
                        } else {
                           scaledCamera.flipHorizontal();
                        }
                        
                        // figure out where in the overview we will add the incoming camera image data
                        int xPosInsert = (int) Math.round(xPosPass - (pos2D.x-minX)/pixelScaled);
                        int yPosInsert = (int) Math.round((pos2D.y-minY)/pixelScaled);

                        // "copy" the camera pixel data while keeping the maximum value for each pixel
                        (overviewImagePlus.getProcessor()).copyBits(scaledCamera, xPosInsert, yPosInsert, ij.process.Blitter.MAX);
                        
                        // update the displayed overview image
                        overviewImagePlus.setRoi(xPosInsert, yPosInsert, scaledWidth, scaledHeight);
                        overviewImagePlus.updateAndDraw();
                        
                        xPosPass -= dx;
                        counter++;
                        if (counter >= nrImages) {
                           done = true;
                        }
                        
                        // add som extra per-image delay for demo cam
                        if (usingDemoCam) {
                           Thread.sleep((long)sliceTiming_.sliceDuration);
                        }
                        
                     } else { // no image ready yet
                        done = cancelAcquisition_.get();
                        Thread.sleep(1);
                     }
                     if (now - startTime > timeout) {
                        // no images within a reasonable amount of time => exit
                        throw new ASIdiSPIMException("No image arrived in 5 seconds");
                     }
                  }  // end loop getting images from one pass
                  
                  // update the contrast at the end of each pass, using the whole image
                  overviewImagePlus.deleteRoi();
                  ij.plugin.ContrastEnhancer contrast = new ij.plugin.ContrastEnhancer();
                  contrast.stretchHistogram(overviewImagePlus, 0.4);
                  overviewImagePlus.updateAndRepaintWindow();

                  // if we are using demo camera then add some extra time to let controller finish
                  // since we got images without waiting for controller to actually send triggers
                  if (usingDemoCam) {
                     Thread.sleep(1000);
                  }
                  
                  // wait 3 seconds or until core reports acquisition finished
                  // this is work-around to apparent bug where images are finished but Core doesn't realize it
                  startTime = System.currentTimeMillis();
                  done = false;
                  while (gui_.getMMCore().isSequenceRunning(camera)) {
                     Thread.sleep(1);
                     done = (System.currentTimeMillis() - startTime) > 3000;
                  }

                  // clean up shutter
                  gui_.getMMCore().setShutterOpen(shutterOpen);
                  gui_.getMMCore().setAutoShutter(autoShutter);
                  
                  // cleanup planar correction move if any
                  if (devices_.isValidMMDevice(Devices.Keys.UPPERZDRIVE)) {
                     props_.setPropValue(Devices.Keys.UPPERZDRIVE, Properties.Keys.TTLINPUT_MODE, Properties.Values.TTLINPUT_MODE_NONE, true);
                     props_.setPropValue(Devices.Keys.UPPERZDRIVE, Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, origZSpeed, true);
                  }
                  
                  // make sure SPIM state machine on micromirror and SCAN of XY card are stopped (should normally be but sanity check)
                  stopSPIMStateMachines(acqSettings);
                  
               }// end loop over each position
               
            } catch (Exception ex) {
               MyDialogUtils.showError("Error in overview acquisition " + ex.getMessage());
            } finally {

               ASIdiSPIM.getFrame().setHardwareInUse(false);
               
               try {
                  gui_.getMMCore().stopSequenceAcquisition(camera);
                  gui_.getMMCore().setCameraDevice(originalCamera);

                  controller_.cleanUpControllerAfterAcquisition(acqSettings, false);
                  
                  // if we did stage scanning restore its position and speed
                  try {
                     // make sure stage scanning state machine is stopped, otherwise setting speed/position won't take
                     props_.setPropValue(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_STATE,
                           Properties.Values.SPIM_IDLE);
                     props_.setPropValue(Devices.Keys.XYSTAGE,
                           Properties.Keys.STAGESCAN_MOTOR_SPEED_X, origXSpeed);
                     props_.setPropValue(Devices.Keys.XYSTAGE,
                           Properties.Keys.STAGESCAN_MOTOR_ACCEL_X, origXAccel);
                     if (devices_.isValidMMDevice(Devices.Keys.UPPERZDRIVE)) {
                        props_.setPropValue(Devices.Keys.UPPERZDRIVE,
                              Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, origZSpeed);
                     }
                     core_.setXYPosition(devices_.getMMDevice(Devices.Keys.XYSTAGE), 
                           xyPosUm.x, xyPosUm.y);
                  } catch (Exception ex) {
                     MyDialogUtils.showError("Could not restore XY stage position after acquisition");
                  }

                  cameras_.setCameraForAcquisition(cameraDevice, false);
                  gui_.getMMCore().setExposure(camera, prefs_.getFloat(MyStrings.PanelNames.SETTINGS.toString(),
                        Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FIRST.toString(), 10f));
                  gui_.refreshGUIFromCache();

                  if (liveModeOriginally) {
                     gui_.getMMCore().waitForDevice(camera);
                     gui_.getMMCore().waitForDevice(originalCamera);
                     gui_.enableLiveMode(true);
                  }

               } catch (Exception ex) {
                  MyDialogUtils.showError("Error while restoring hardware state after overview image.");
               }
               finally {
                  posUpdater_.pauseUpdates(false);
                  updateAcquisitionStatus(AcquisitionStatus.DONE);
               }
            }
            
            return null;
         }
      
         @Override
         public void done() {
            acquisitionRequested_.set(false);
            updateStartButton();
            ASIdiSPIM.getFrame().tabsSetEnabled(true);
         }
      
      };
      
      // this is main body of method to set up the overview acquisition
      gridPanel_.computeGrid(false, false);
      cancelAcquisition_.set(false);
      acquisitionRequested_.set(true);
      ASIdiSPIM.getFrame().gotoAcquisitionTab();
      ASIdiSPIM.getFrame().tabsSetEnabled(false);
      updateStartButton();
      (new OverviewTask()).execute();  // execute acquisition on separate thread; done() gets called afterwards
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
            ReportingUtils.logDebugMessage("User requested start of diSPIM acquisition.");
            if (isAcquisitionRequested()) { // don't allow acquisition to be requested again, just return
               ReportingUtils.logError("another acquisition already running");
               return;
            }
            cancelAcquisition_.set(false);
            acquisitionRequested_.set(true);
            ASIdiSPIM.getFrame().tabsSetEnabled(false);
            updateStartButton();
            final boolean hideErrors = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(), 
                  Properties.Keys.PLUGIN_ACQUIRE_FAIL_QUIETLY, true);
            AcquisitionStatus success = runAcquisitionPrivate(false, Devices.Sides.NONE, hideErrors);
            if (success == AcquisitionStatus.FATAL_ERROR) {
               MyDialogUtils.showError("Fatal error running diSPIM acquisition.", hideErrors);
            }
            acquisitionRequested_.set(false);
            updateStartButton();
            ASIdiSPIM.getFrame().tabsSetEnabled(true);
         }
      }            
      acqThread acqt = new acqThread("diSPIM Acquisition");
      acqt.start(); 
   }
   
   private Color getChannelColor(int channelIndex) {
      return (colors[channelIndex % colors.length]);
   }
   
   /**
    * Actually runs the acquisition; does the dirty work of setting
    * up the controller, the circular buffer, starting the cameras,
    * grabbing the images and putting them into the acquisition, etc.
    * @param testAcq true if running test acquisition only (see runTestAcquisition() javadoc)
    * @param testAcqSide only applies to test acquisition, passthrough from runTestAcquisition() 
    * @param hideErrors true if errors are to be logged only and not displayed; false is default
    * @return true if ran without any fatal errors.  If non-fatal error is encountered you can check 
    */
   private AcquisitionStatus runAcquisitionPrivate(boolean testAcq, Devices.Sides testAcqSide, boolean hideErrors) {
      // sanity check, shouldn't call this unless we aren't running an acquisition
      if (gui_.isAcquisitionRunning()) {
         MyDialogUtils.showError("An acquisition is already running", hideErrors);
         return AcquisitionStatus.FATAL_ERROR;
      }
      
      if (ASIdiSPIM.getFrame().getHardwareInUse()) {
         MyDialogUtils.showError("Hardware is being used by something else (maybe autofocus?)", hideErrors);
         return AcquisitionStatus.FATAL_ERROR;
      }
      
      boolean liveModeOriginally = gui_.isLiveModeOn();
      if (liveModeOriginally) {
         gui_.enableLiveMode(false);
      }
      
      // make sure slice timings are up to date
      // do this automatically; we used to prompt user if they were out of date
      // do this before getting snapshot of sliceTiming_ in acqSettings
      recalculateSliceTiming(!minSlicePeriodCB_.isSelected());
      
      if (!sliceTiming_.valid) {
         MyDialogUtils.showError("Error in calculating the slice timing; is the camera mode set correctly?", hideErrors);
         return AcquisitionStatus.FATAL_ERROR;
      }
      
      AcquisitionSettings acqSettingsOrig = getCurrentAcquisitionSettings();
      
      if (acqSettingsOrig.cameraMode == CameraModes.Keys.LIGHT_SHEET
            && core_.getPixelSizeUm() < 1e-6) {  // can't compare equality directly with floating point values so call < 1e-9 is zero or negative
         MyDialogUtils.showError("Need to configure pixel size in Micro-Manager to use light sheet mode.", hideErrors);
         return AcquisitionStatus.FATAL_ERROR;
      }
      
      // if a test acquisition then only run single timpoint, no autofocus
      // allow multi-positions for test acquisition for now, though perhaps this is not desirable
      if (testAcq) {
         acqSettingsOrig.useTimepoints = false;
         acqSettingsOrig.numTimepoints = 1;
         acqSettingsOrig.useAutofocus = false;
         acqSettingsOrig.separateTimepoints = false;
         
         // if called from the setup panels then the side will be specified
         //   so we can do an appropriate single-sided acquisition
         // if called from the acquisition panel then NONE will be specified
         //   and run according to existing settings
         if (testAcqSide != Devices.Sides.NONE) {
            acqSettingsOrig.numSides = 1;
            acqSettingsOrig.firstSideIsA = (testAcqSide == Devices.Sides.A);
         }
         
         // work around limitation of not being able to use PLogic per-volume switching with single side
         // => do per-volume switching instead (only difference should be extra time to switch)
         if (acqSettingsOrig.useChannels && acqSettingsOrig.channelMode == MultichannelModes.Keys.VOLUME_HW
               && acqSettingsOrig.numSides < 2) {
            acqSettingsOrig.channelMode = MultichannelModes.Keys.VOLUME;
         }
         
      }
      
      
      double volumeDuration = computeActualVolumeDuration(acqSettingsOrig);
      double timepointDuration = computeTimepointDuration();
      long timepointIntervalMs = Math.round(acqSettingsOrig.timepointInterval*1000);
     
      // use hardware timing if < 1 second between timepoints
      // experimentally need ~0.5 sec to set up acquisition, this gives a bit of cushion
      // cannot do this in getCurrentAcquisitionSettings because of mutually recursive
      // call with computeActualVolumeDuration()
      if ( acqSettingsOrig.useTimepoints
            && acqSettingsOrig.numTimepoints > 1
            && timepointIntervalMs < (timepointDuration + 750)
            && !acqSettingsOrig.isStageScanning) {
         acqSettingsOrig.hardwareTimepoints = true;
      }
      
      if (acqSettingsOrig.useMultiPositions) {
         if (acqSettingsOrig.hardwareTimepoints
               || ((acqSettingsOrig.numTimepoints > 1) 
                     && (timepointIntervalMs < timepointDuration*1.2))) {
            // change to not hardwareTimepoints and warn user but allow acquisition to continue
            acqSettingsOrig.hardwareTimepoints = false;
            MyDialogUtils.showError("Timepoint interval may not be sufficient "
                  + "depending on actual time required to change positions. "
                  + "Proceed at your own risk.", hideErrors);
         }
      }
      
      if (acqSettingsOrig.numSimultCameras > 0) {
         if (!acqSettingsOrig.firstSideIsA || acqSettingsOrig.numSides != 1) {
            MyDialogUtils.showError("Using simultaneous cameras requires single-sided (Path A) acquisition.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (acqSettingsOrig.useTimepoints) {
            // not fundamentally impossible (except hardware timepoints), but because we file the channels in the
            //     timepoint index of acquisition to work-around restriction of displaying only 8 channels
            MyDialogUtils.showError("Cannot use time points with simultaneous cameras on PathA", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (acqSettingsOrig.hardwareTimepoints) {
            // currently this situation can never occur but leave check here just in case we find other way around
            //    8-channel display limit
            MyDialogUtils.showError("Cannot use hardware time points with simultaneous cameras on PathA", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      // get MM device names for first/second cameras to acquire
      String firstCamera, secondCamera;
      Devices.Keys firstCameraKey, secondCameraKey;
      boolean firstSideA = acqSettingsOrig.firstSideIsA; 
      if (firstSideA) {
         firstCamera = devices_.getMMDevice(Devices.Keys.CAMERAA);
         firstCameraKey = Devices.Keys.CAMERAA;
         secondCamera = devices_.getMMDevice(Devices.Keys.CAMERAB);
         secondCameraKey = Devices.Keys.CAMERAB;
      } else {
         firstCamera = devices_.getMMDevice(Devices.Keys.CAMERAB);
         firstCameraKey = Devices.Keys.CAMERAB;
         secondCamera = devices_.getMMDevice(Devices.Keys.CAMERAA);
         secondCameraKey = Devices.Keys.CAMERAA;
      }
      
      boolean sideActiveA, sideActiveB;
      final boolean twoSided = acqSettingsOrig.numSides > 1;
      if (twoSided) {
         sideActiveA = true;
         sideActiveB = true;
      } else {
         if (!acqSettingsOrig.acquireBothCamerasSimultaneously) {
            secondCamera = null;
         }
         if (firstSideA) {
            sideActiveA = true;
            sideActiveB = false;
         } else {
            sideActiveA = false;
            sideActiveB = true;
         }
      }
      
      // if we are told to use path presets but nothing is actually used then set to false
      if (acqSettingsOrig.usePathPresets) {
         boolean needA = sideActiveA && !props_.getPropValueString(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_PATH_CONFIG_A).equals("");
         boolean needB = sideActiveB && !props_.getPropValueString(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_PATH_CONFIG_B).equals("");
         if (!(needA || needB)) {
            acqSettingsOrig.usePathPresets = false;
         }
      }
      
      acqSettingsOrig.durationSliceMs = (float)getSliceDuration(acqSettingsOrig.sliceTiming);
      acqSettingsOrig.durationVolumeMs = (float)volumeDuration;
      acqSettingsOrig.durationTotalSec = (float)computeActualTimeLapseDuration();
      
      // now acqSettings should be read-only
      final AcquisitionSettings acqSettings = new AcquisitionSettings(acqSettingsOrig);
      
      // generate string for log file
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      final String acqSettingsJSON = gson.toJson(acqSettings);
      
      final boolean acqBothCameras = acqSettings.acquireBothCamerasSimultaneously;
      final boolean acqSimultSideA = (acqSettings.numSimultCameras != 0);
      final boolean camActiveA = (sideActiveA || acqBothCameras) && !acqSimultSideA;
      final boolean camActiveB = (sideActiveB || acqBothCameras) && !acqSimultSideA;
      final boolean camActiveA1 = acqSettings.numSimultCameras >= 1;
      final boolean camActiveA2 = acqSettings.numSimultCameras >= 2;
      final boolean camActiveA3 = acqSettings.numSimultCameras >= 3;
      final boolean camActiveA4 = acqSettings.numSimultCameras >= 4;
      
      if (camActiveA) {
         if (!verifyCamera(Devices.Keys.CAMERAA))
            return AcquisitionStatus.FATAL_ERROR;
      }
      
      if (camActiveB) {
         if (!verifyCamera(Devices.Keys.CAMERAB))
            return AcquisitionStatus.FATAL_ERROR;
      }
      
      if (camActiveA1) {
         if (!verifyCamera(Devices.Keys.CAMERA_A1))
            return AcquisitionStatus.FATAL_ERROR;
      }
      
      if (camActiveA2) {
         if (!verifyCamera(Devices.Keys.CAMERA_A2))
            return AcquisitionStatus.FATAL_ERROR;
      }
      
      if (camActiveA3) {
         if (!verifyCamera(Devices.Keys.CAMERA_A3))
            return AcquisitionStatus.FATAL_ERROR;
      }
      
      if (camActiveA4) {
         if (!verifyCamera(Devices.Keys.CAMERA_A4))
            return AcquisitionStatus.FATAL_ERROR;
      }
      
      if (sideActiveA) {
         if (!devices_.isValidMMDevice(Devices.Keys.GALVOA)) {
            MyDialogUtils.showError("Using side A but no scanner specified for that side.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (requiresPiezos(acqSettings.spimMode) && !devices_.isValidMMDevice(Devices.Keys.PIEZOA)) {
            MyDialogUtils.showError("Using side A and acquisition mode requires piezos but no piezo specified for that side.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      if (sideActiveB) {
         if (!devices_.isValidMMDevice(Devices.Keys.GALVOB)) {
            MyDialogUtils.showError("Using side B but no scanner specified for that side.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (requiresPiezos(acqSettings.spimMode) && !devices_.isValidMMDevice(Devices.Keys.PIEZOB)) {
            MyDialogUtils.showError("Using side B and acquisition mode requires piezos but no piezo specified for that side.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      boolean usingDemoCam = (devices_.getMMDeviceLibrary(Devices.Keys.CAMERAA).equals(Devices.Libraries.DEMOCAM) && camActiveA)
            || (devices_.getMMDeviceLibrary(Devices.Keys.CAMERAB).equals(Devices.Libraries.DEMOCAM) && camActiveB)
            || (devices_.getMMDeviceLibrary(Devices.Keys.CAMERA_A1).equals(Devices.Libraries.DEMOCAM) && camActiveA1)
            || (devices_.getMMDeviceLibrary(Devices.Keys.CAMERA_A2).equals(Devices.Libraries.DEMOCAM) && camActiveA2)
            || (devices_.getMMDeviceLibrary(Devices.Keys.CAMERA_A3).equals(Devices.Libraries.DEMOCAM) && camActiveA3)
            || (devices_.getMMDeviceLibrary(Devices.Keys.CAMERA_A4).equals(Devices.Libraries.DEMOCAM) && camActiveA4);
      
      // get names of cameras for simultaneous, if applicable
      // other strings firstCamera and secondCamera aren't overwritten but should be ignored later on
      // strings are set to null even if defined but not used => can use strings to tell if we are using camera
      String cameraA1 = null;
      String cameraA2 = null;
      String cameraA3 = null;
      String cameraA4 = null;
      if (acqSimultSideA) {
         // set original names/keys to blank so we can't accidentally use them
         firstCamera = "";
         firstCameraKey = null;
         secondCamera = "";
         secondCameraKey = null;
         cameraA1 = devices_.getMMDevice(Devices.Keys.CAMERA_A1);
         if (cameraA1 == null) {
            MyDialogUtils.showError("Trying to use undefinied simultaneous camera #1", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (camActiveA2) {
            cameraA2 = devices_.getMMDevice(Devices.Keys.CAMERA_A2);
            if (cameraA2 == null) {
               MyDialogUtils.showError("Trying to use undefinied simultaneous camera #2", hideErrors);
               return AcquisitionStatus.FATAL_ERROR;
            }
         }
         if (camActiveA3) {
            cameraA3 = devices_.getMMDevice(Devices.Keys.CAMERA_A3);
            if (cameraA3 == null) {
               MyDialogUtils.showError("Trying to use undefinied simultaneous camera #3", hideErrors);
               return AcquisitionStatus.FATAL_ERROR;
            }
         }
         if (camActiveA4) {
            cameraA4 = devices_.getMMDevice(Devices.Keys.CAMERA_A4);
            if (cameraA4 == null) {
               MyDialogUtils.showError("Trying to use undefinied simultaneous camera #4", hideErrors);
               return AcquisitionStatus.FATAL_ERROR;
            }
         }
      }
      
      // set up channels
      int nrChannelsSoftware = acqSettings.numChannels;  // how many times we trigger the controller per stack
      int nrSlicesSoftware = acqSettings.numSlices;      // how many images we receive from the camera
      String originalChannelConfig = "";
      boolean changeChannelPerVolumeSoftware = false;
      boolean changeChannelPerVolumeDoneFirst = false;
      extraChannelOffset_ = getChannelOffset();
      if (acqSettings.useChannels) {
         if (acqSettings.numChannels < 1) {
            MyDialogUtils.showError("\"Channels\" is checked, but no channels are selected", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         // get current channel so that we can restore it, then set channel appropriately
         originalChannelConfig = multiChannelPanel_.getCurrentConfig();
         switch (acqSettings.channelMode) {
         case VOLUME:
            changeChannelPerVolumeSoftware = true;
            changeChannelPerVolumeDoneFirst = true;
            multiChannelPanel_.initializeChannelCycle();
            extraChannelOffset_ = multiChannelPanel_.selectNextChannelAndGetOffset();
            // actually do channel selection later on
            break;
         case VOLUME_HW:
         case SLICE_HW:
            if (acqSettings.numChannels == 1) {  // only 1 channel selected so don't have to really use hardware switching
               multiChannelPanel_.initializeChannelCycle();
               extraChannelOffset_ = multiChannelPanel_.selectNextChannelAndGetOffset();
            } else {  // we have at least 2 channels
               // intentionally leave extraChannelOffset_ untouched so that it can be specified by user by choosing a preset
               //   for the channel in the main Micro-Manager window
               boolean success = controller_.setupHardwareChannelSwitching(acqSettings, hideErrors);
               if (!success) {
                  MyDialogUtils.showError("Couldn't set up slice hardware channel switching.", hideErrors);
                  return AcquisitionStatus.FATAL_ERROR;
               }
               nrChannelsSoftware = 1;
               nrSlicesSoftware = acqSettings.numSlices * acqSettings.numChannels;
            }
            break;
         default:
            MyDialogUtils.showError("Unsupported multichannel mode \"" + acqSettings.channelMode.toString() + "\"", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      if (twoSided && acqBothCameras) {
         nrSlicesSoftware *= 2;
      }
      
      if (acqSettings.hardwareTimepoints) {
         if (acqSettings.cameraMode == CameraModes.Keys.OVERLAP) {
            // in hardwareTimepoints case we trigger controller once for all timepoints => need to
            //   adjust number of frames we expect back from the camera during MM's SequenceAcquisition
            //   (we will drop some of them, see checkForSkips code below)
            // conceptually we just need a single extra trigger, but the firmware accepts a number of slice repeats (NR R)
            //   and so we have to provide numChannel triggers which is (numChannel-1) "extra"
            // then it doesn't matter for this calculation whether we are using interleaved or volume-by-volume
            nrSlicesSoftware = acqSettings.numTimepoints * ((acqSettings.numSlices + 1) * acqSettings.numChannels);
            if (twoSided && acqBothCameras) {
               nrSlicesSoftware *= 2;
            }
            // the very last trigger won't ever return a frame so subtract 1
            nrSlicesSoftware -= 1;
         } else {
            // we get back one image per trigger for all trigger modes other than OVERLAP
            //   and we have already computed how many images that is (nrSlicesSoftware)
            nrSlicesSoftware *= acqSettings.numTimepoints;
            if (twoSided && acqBothCameras) {
               nrSlicesSoftware *= 2;
            }
         }
      }
      
      // set up XY positions
      int nrPositions = 1;
      PositionList positionList = new PositionList();
      if (acqSettings.useMultiPositions) {
         try {
            positionList = gui_.getPositionList();
            nrPositions = positionList.getNumberOfPositions();
         } catch (MMScriptException ex) {
            MyDialogUtils.showError(ex, "Error getting position list for multiple XY positions", hideErrors);
         }
         if (nrPositions < 1) {
            MyDialogUtils.showError("\"Positions\" is checked, but no positions are in position list", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      // make sure we have cameras selected
      if (!checkCamerasAssigned(!hideErrors)) {
         return AcquisitionStatus.FATAL_ERROR;
      }
      
      final float cameraReadoutTime = computeCameraReadoutTime();
      final double exposureTime = acqSettings.sliceTiming.cameraExposure;
      
      final boolean save = saveCB_.isSelected() && !testAcq;
      final String rootDir = rootField_.getText();
      
      // make sure we have a valid directory to save in
      final File dir = new File(rootDir);
      if (save) {
         try {
            if (!dir.exists()) {
               if (!dir.mkdir()) {
                  throw new Exception();
               }
            }
         } catch (Exception ex) {
            MyDialogUtils.showError("Could not create directory for saving acquisition data.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      if (acqSettings.separateTimepoints) {
         // because separate timepoints closes windows when done, force the user to save data to disk to avoid confusion
         if (!save) {
            MyDialogUtils.showError("For separate timepoints, \"Save while acquiring\" must be enabled.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         // for separate timepoints, make sure the directory is empty to make sure naming pattern is "clean"
         // this is an arbitrary choice to avoid confusion later on when looking at file names
         if (dir.list().length > 0) {
            MyDialogUtils.showError("For separate timepoints the saving directory must be empty.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      int nrFrames;   // how many Micro-manager "frames" = time points to take
      if (acqSettings.separateTimepoints) {
         nrFrames = 1;
         nrRepeats_ = acqSettings.numTimepoints;
      } else {
         nrFrames = acqSettings.numTimepoints;
         nrRepeats_ = 1;
      }
      
      AcquisitionModes.Keys spimMode = acqSettings.spimMode;
      
      boolean autoShutter = core_.getAutoShutter();
      boolean shutterOpen = false;  // will read later
      String originalCamera = core_.getCameraDevice();

      // more sanity checks
      // TODO move these checks earlier, before we set up channels and XY positions
      
      // make sure stage scan is supported if selected
      if (acqSettings.isStageScanning) {
         if (!devices_.isTigerDevice(Devices.Keys.XYSTAGE)
              || !props_.hasProperty(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_NUMLINES)) {
            MyDialogUtils.showError("Must have stage with scan-enabled firmware for stage scanning.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED) {
            if (acqSettings.numSides < 2) {
               MyDialogUtils.showError("Interleaved stage scan requires two sides.", hideErrors);
               return AcquisitionStatus.FATAL_ERROR;
            }
            if (acqSettings.usePathPresets) {
               MyDialogUtils.showError("Cannot use path presets with interleaved stage scan", hideErrors);
               return AcquisitionStatus.FATAL_ERROR;
            }
         }
         if (acqSettings.usePathPresets && 
        		 prefs_.getBoolean(MyStrings.PanelNames.AUTOFOCUS.toString(), 
        		 Properties.Keys.PLUGIN_AUTOFOCUS_EVERY_STAGE_PASS, false)) {
             MyDialogUtils.showError("Cannot use path presets with autofocus every stage pass.", hideErrors);
             return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      if (acqSettings.isStageStepping) {
         if (!acqSettings.useChannels || acqSettings.numChannels < 2 || acqSettings.channelMode != MultichannelModes.Keys.SLICE_HW) {
            MyDialogUtils.showError("Stage step supplemental acquisition can only be used for multiple channels with changing "
                  + "channels every slice. Pester the developer to extend this functionality to single channel and/or volume switching.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      double sliceDuration = acqSettings.sliceTiming.sliceDuration;
      if (exposureTime + cameraReadoutTime > sliceDuration) {
         // should only only possible to mess this up using advanced timing settings
         // or if there are errors in our own calculations
         MyDialogUtils.showError("Exposure time of " + exposureTime +
               " is longer than time needed for a line scan with" +
               " readout time of " + cameraReadoutTime + "\n" + 
               "This will result in dropped frames. " +
               "Please change input", hideErrors);
         return AcquisitionStatus.FATAL_ERROR;
      }
      
      // if we want to do hardware timepoints make sure there's not a problem
      // lots of different situations where hardware timepoints can't be used...
      if (acqSettings.hardwareTimepoints) {
         if (acqSettings.useChannels && acqSettings.channelMode == MultichannelModes.Keys.VOLUME_HW) {
            // both hardware time points and volume channel switching use SPIMNumRepeats property
            // TODO this seems a severe limitation, maybe this could be changed in the future via firmware change
            MyDialogUtils.showError("Cannot use hardware time points (small time point interval)"
                  + " with hardware channel switching volume-by-volume.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (acqSettings.isStageScanning) {
            // stage scanning needs to be triggered for each time point
            MyDialogUtils.showError("Cannot use hardware time points (small time point interval)"
                  + " with stage scanning.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (acqSettings.separateTimepoints) {
            MyDialogUtils.showError("Cannot use hardware time points (small time point interval)"
                  + " with separate viewers/file for each time point.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (acqSettings.useAutofocus) {
            MyDialogUtils.showError("Cannot use hardware time points (small time point interval)"
                  + " with autofocus during acquisition.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (acqSettings.useMovementCorrection) {
             MyDialogUtils.showError("Cannot use hardware time points (small time point interval)"
                  + " with movement correction during acquisition.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (acqSettings.useChannels && acqSettings.channelMode == MultichannelModes.Keys.VOLUME) {
            MyDialogUtils.showError("Cannot use hardware time points (small time point interval)"
                  + " with software channels (need to use PLogic channel switching).", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
         if (spimMode == AcquisitionModes.Keys.NO_SCAN) {
            MyDialogUtils.showError("Cannot do hardware time points when no scan mode is used."
                  + " Use the number of slices to set the number of images to acquire.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      if (acqSettings.useChannels && acqSettings.channelMode == MultichannelModes.Keys.VOLUME_HW
            && acqSettings.numSides < 2) {
         MyDialogUtils.showError("Cannot do PLogic channel switching of volume when only one"
               + " side is selected. Pester the developers if you need this.", hideErrors);
         return AcquisitionStatus.FATAL_ERROR;
      }
      
      // make sure we aren't trying to collect timepoints faster than we can
      if (!acqSettings.useMultiPositions && acqSettings.numTimepoints > 1) {
         if (timepointIntervalMs < volumeDuration) {
            MyDialogUtils.showError("Time point interval shorter than" +
                  " the time to collect a single volume.", hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      // Autofocus settings; only used if acqSettings.useAutofocus is true
      boolean autofocusEveryStagePass = false;
      boolean autofocusAtT0 = false;
      int autofocusEachNFrames = 10;
      String autofocusChannel = "";
      if (acqSettings.useAutofocus) {
         autofocusAtT0 = prefs_.getBoolean(MyStrings.PanelNames.AUTOFOCUS.toString(), 
               Properties.Keys.PLUGIN_AUTOFOCUS_ACQBEFORESTART, false);
         autofocusEveryStagePass = prefs_.getBoolean(MyStrings.PanelNames.AUTOFOCUS.toString(), 
        		 Properties.Keys.PLUGIN_AUTOFOCUS_EVERY_STAGE_PASS, false);
         autofocusEachNFrames = props_.getPropValueInteger(Devices.Keys.PLUGIN, 
               Properties.Keys.PLUGIN_AUTOFOCUS_EACHNIMAGES);
         autofocusChannel = props_.getPropValueString(Devices.Keys.PLUGIN,
               Properties.Keys.PLUGIN_AUTOFOCUS_CHANNEL);
         // double-check that selected channel is valid if we are doing multi-channel
         if (acqSettings.useChannels) {
            if (!multiChannelPanel_.isChannelValid(autofocusChannel)) {
               MyDialogUtils.showError("Invalid autofocus channel selected on autofocus tab.", hideErrors);
               return AcquisitionStatus.FATAL_ERROR;
            }
         }
      }
      
      // Movement Correction settings; only used if acqSettings.useMovementCorrection is true
      int correctMovementEachNFrames = 10;
      String correctMovementChannel = "";
      int cmChannelNumber = -1;
      if (acqSettings.useMovementCorrection) {
         correctMovementEachNFrames = props_.getPropValueInteger(Devices.Keys.PLUGIN, 
               Properties.Keys.PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_EACHNIMAGES);
         correctMovementChannel = props_.getPropValueString(Devices.Keys.PLUGIN,
                 Properties.Keys.PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_CHANNEL);
         // double-check that selected channel is valid if we are doing multi-channel
         if (acqSettings.useChannels) {
            if (!multiChannelPanel_.isChannelValid(correctMovementChannel)) {
               MyDialogUtils.showError("Invalid movement correction channel selected on autofocus tab.", hideErrors);
               return AcquisitionStatus.FATAL_ERROR;
            }
         }
      }
      
      // the circular buffer, which is used by both cameras, can only have one image size setting
      //    => require same image height and width for both cameras if both are used
      if (acqSimultSideA) {
         try {
            Rectangle roi_1 = core_.getROI(cameraA1);
            Rectangle roi_2 = (camActiveA2) ? core_.getROI(cameraA2) : roi_1;
            Rectangle roi_3 = (camActiveA3) ? core_.getROI(cameraA3) : roi_1;
            Rectangle roi_4 = (camActiveA4) ? core_.getROI(cameraA4) : roi_1;
            if (
                  roi_1.width != roi_2.width || roi_1.height != roi_2.height
                 || roi_1.width != roi_3.width || roi_1.height != roi_3.height
                 || roi_1.width != roi_4.width || roi_1.height != roi_4.height
                 || roi_2.width != roi_3.width || roi_2.height != roi_3.height
                 || roi_2.width != roi_4.width || roi_2.height != roi_4.height
                 || roi_3.width != roi_4.width || roi_3.height != roi_4.height
                  ) {
               MyDialogUtils.showError("All cameras' ROI height and width must be equal because of Micro-Manager's circular buffer", hideErrors);
               return AcquisitionStatus.FATAL_ERROR;
            }
         } catch (Exception ex) {
            MyDialogUtils.showError(ex, "Problem getting camera ROIs");
         }
      } else if (twoSided || acqBothCameras) {
         try {
            Rectangle roi_1 = core_.getROI(firstCamera);
            Rectangle roi_2 = core_.getROI(secondCamera);
            if (roi_1.width != roi_2.width || roi_1.height != roi_2.height) {
               MyDialogUtils.showError("Two cameras' ROI height and width must be equal because of Micro-Manager's circular buffer", hideErrors);
               return AcquisitionStatus.FATAL_ERROR;
            }
         } catch (Exception ex) {
            MyDialogUtils.showError(ex, "Problem getting camera ROIs", hideErrors);
         }
      }
      
      
      //////////////////////////////////////////////////////
      // sanity checks done, proceed setting up acquisition
      
      if (acqSimultSideA) {
         cameras_.setCameraForAcquisition(Devices.Keys.CAMERA_A1, true);
         if (camActiveA2) {
            cameras_.setCameraForAcquisition(Devices.Keys.CAMERA_A2, true);
         }
         if (camActiveA3) {
            cameras_.setCameraForAcquisition(Devices.Keys.CAMERA_A3, true);
         }
         if (camActiveA4) {
            cameras_.setCameraForAcquisition(Devices.Keys.CAMERA_A4, true);
         }
      } else {
         cameras_.setCameraForAcquisition(firstCameraKey, true);
         if (twoSided || acqBothCameras) {
            cameras_.setCameraForAcquisition(secondCameraKey, true);
         }
      }
      
      // save exposure time, will restore at end of acquisition
      try {
         if (acqSimultSideA) {
            prefs_.putFloat(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FIRST.toString(),
                  (float)core_.getExposure(devices_.getMMDevice(Devices.Keys.CAMERA_A1)));
            if (camActiveA2) {
               prefs_.putFloat(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_SECOND.toString(),
                     (float)core_.getExposure(devices_.getMMDevice(Devices.Keys.CAMERA_A2)));
            }
            if (camActiveA3) {
               prefs_.putFloat(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_THIRD.toString(),
                     (float)core_.getExposure(devices_.getMMDevice(Devices.Keys.CAMERA_A3)));
            }
            if (camActiveA4) {
               prefs_.putFloat(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FOURTH.toString(),
                     (float)core_.getExposure(devices_.getMMDevice(Devices.Keys.CAMERA_A4)));
            }
         } else {
            prefs_.putFloat(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FIRST.toString(),
                  (float)core_.getExposure(devices_.getMMDevice(firstCameraKey)));
         }
         if (twoSided || acqBothCameras) {
            prefs_.putFloat(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_SECOND.toString(),
                  (float)core_.getExposure(devices_.getMMDevice(secondCameraKey)));
         }
      } catch (Exception ex) {
         MyDialogUtils.showError(ex, "could not cache exposure", hideErrors);
      }
      
      try {
         if (acqSimultSideA) {
            core_.setExposure(cameraA1, exposureTime);
            if (camActiveA2) {
               core_.setExposure(cameraA2, exposureTime);
            }
            if (camActiveA3) {
               core_.setExposure(cameraA3, exposureTime);
            }
            if (camActiveA4) {
               core_.setExposure(cameraA4, exposureTime);
            }
         } else {
        	// Andor Zyla cameras change the LineScanSpeed property based on the exposure time, 
        	// and setExposure rounds the argument which would change the light sheet speed
        	if (!(devices_.getMMDeviceLibrary(firstCameraKey) == Devices.Libraries.ANDORCAM)) {
                core_.setExposure(firstCamera, exposureTime);
                if (twoSided || acqBothCameras) {
                    core_.setExposure(secondCamera, exposureTime);
                }
        	}
         }
         gui_.refreshGUIFromCache();
      } catch (Exception ex) {
         MyDialogUtils.showError(ex, "could not set exposure", hideErrors);
      }
      
      // seems to have a problem if the core's camera has been set to some other
      // camera before we start doing things, so set to a SPIM camera
      try {
         if (acqSimultSideA) {
            core_.setCameraDevice(cameraA1);
         } else {
            core_.setCameraDevice(firstCamera);
         }
      } catch (Exception ex) {
         MyDialogUtils.showError(ex, "could not set camera", hideErrors);
      }

      // empty out circular buffer
      try {
         core_.clearCircularBuffer();
      } catch (Exception ex) {
         MyDialogUtils.showError(ex, "Error emptying out the circular buffer", hideErrors);
         return AcquisitionStatus.FATAL_ERROR;
      }
      
      // stop the serial traffic for position updates during acquisition
      // if we return from this function (including aborting) we need to unpause
      posUpdater_.pauseUpdates(true);
      
      // initialize stage scanning so we can restore state
      Point2D.Double xyPosUm = new Point2D.Double();
      double xSupPosUm = 0.0;
      float origXSpeed = 1f;  // don't want 0 in case something goes wrong
      float origXAccel = 1f;  // don't want 0 in case something goes wrong
      float origXSupSpeed = 1f;  // don't want 0 in case something goes wrong
      float origZSpeed = 1f;  // don't want 0 in case something goes wrong
      if (acqSettings.isStageScanning) {
         try {
            xyPosUm = core_.getXYStagePosition(devices_.getMMDevice(Devices.Keys.XYSTAGE));
            origXSpeed = props_.getPropValueFloat(Devices.Keys.XYSTAGE,
                  Properties.Keys.STAGESCAN_MOTOR_SPEED_X);
            origXAccel = props_.getPropValueFloat(Devices.Keys.XYSTAGE,
                  Properties.Keys.STAGESCAN_MOTOR_ACCEL_X);
            if (devices_.isValidMMDevice(Devices.Keys.SUPPLEMENTAL_X)) {
               if (devices_.getMMDeviceLibrary(Devices.Keys.SUPPLEMENTAL_X) == Devices.Libraries.PI_GCS_2) {
                  origXSupSpeed = props_.getPropValueFloat(Devices.Keys.SUPPLEMENTAL_X, Properties.Keys.VELOCITY);
               }
            }
            if (devices_.isValidMMDevice(Devices.Keys.UPPERZDRIVE)) {
               origZSpeed = props_.getPropValueFloat(Devices.Keys.UPPERZDRIVE,
                     Properties.Keys.STAGESCAN_MOTOR_SPEED_Z);
            }
         } catch (Exception ex) {
            MyDialogUtils.showError("Could not get XY stage position, speed, or acceleration for stage scan initialization", hideErrors);
            posUpdater_.pauseUpdates(false);
            return AcquisitionStatus.FATAL_ERROR;
         }
         
         // if X speed is less than 0.2 mm/s then it probably wasn't restored to correct speed some other time
         // we offer to set it to a more normal speed in that case, until the user declines and we stop asking
         if (origXSpeed < 0.2 && resetXaxisSpeed_) {
            resetXaxisSpeed_ = MyDialogUtils.getConfirmDialogResult(
                  "Max speed of X axis is small, perhaps it was not correctly restored after stage scanning previously.  Do you want to set it to 1 mm/s now?",
                  JOptionPane.YES_NO_OPTION);
            // once the user selects "no" then resetXaxisSpeed_ will be false and stay false until plugin is launched again
            if (resetXaxisSpeed_) {
               props_.setPropValue(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_MOTOR_SPEED_X, 1f);
               origXSpeed = 1f;
            }
         }
         // if X supplemental speed is less than 0.2 mm/s then it probably wasn't restored to correct speed some other time
         // we offer to set it to a more normal speed in that case, until the user declines and we stop asking
         if (origXSupSpeed < 0.2 && resetXSupaxisSpeed_) {
            resetXSupaxisSpeed_ = MyDialogUtils.getConfirmDialogResult(
                  "Max speed of supplemental X axis is small, perhaps it was not correctly restored after stage scanning previously.  Do you want to set it to 1 mm/s now?",
                  JOptionPane.YES_NO_OPTION);
            // once the user selects "no" then resetXSupaxisSpeed_ will be false and stay false until plugin is launched again
            if (resetXSupaxisSpeed_) {
               props_.setPropValue(Devices.Keys.SUPPLEMENTAL_X, Properties.Keys.VELOCITY, 1f);
               origXSupSpeed = 1f;
            }
         }
         // if Z speed is less than 0.2 mm/s then it probably wasn't restored to correct speed some other time
         // we offer to set it to a more normal speed in that case, until the user declines and we stop asking
         if (origZSpeed < 0.2 && resetZaxisSpeed_) {
            resetZaxisSpeed_ = MyDialogUtils.getConfirmDialogResult(
                  "Max speed of Z axis is small, perhaps it was not correctly restored after stage scanning previously.  Do you want to set it to 1 mm/s now?",
                  JOptionPane.YES_NO_OPTION);
            // once the user selects "no" then resetZaxisSpeed_ will be false and stay false until plugin is launched again
            if (resetZaxisSpeed_) {
               props_.setPropValue(Devices.Keys.UPPERZDRIVE, Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, 1f);
               origZSpeed = 1f;
            }
         }
      }
      
      if (acqSettings.isStageStepping) {
         try {
            xSupPosUm = core_.getPosition(devices_.getMMDevice(Devices.Keys.SUPPLEMENTAL_X));
         } catch (Exception e) {
            MyDialogUtils.showError("Could not get supplemental X stage position for stage step initialization", hideErrors);
            posUpdater_.pauseUpdates(false);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      numTimePointsDone_ = 0;
      numPositionsDone_ = 0;
      
      // force saving as image stacks, not individual files
      // implementation assumes just two options, either 
      //  TaggedImageStorageDiskDefault.class or TaggedImageStorageMultipageTiff.class
      boolean separateImageFilesOriginally =
            ImageUtils.getImageStorageClass().equals(TaggedImageStorageDiskDefault.class);
      ImageUtils.setImageStorageClass(TaggedImageStorageMultipageTiff.class);
      
      // Set up controller SPIM parameters (including from Setup panel settings)
      // want to do this, even with demo cameras, so we can test everything else
      if (!acqSettings.usePathPresets) {  // special case with path presets handled later
         if (!controller_.prepareControllerForAquisition(acqSettings, extraChannelOffset_)) {
            posUpdater_.pauseUpdates(false);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }
      
      boolean nonfatalError = false;
      long acqButtonStart = System.currentTimeMillis();
      //String acqName = "";
      acqName_ = "";
      acq_ = null;
      
      // execute any start-acquisition runnables
      for (Runnable r : runnablesStartAcquisition_) {
         try {
            r.run();
         } catch(Exception ex) {
            MyDialogUtils.showError("start acquisition runnable threw exception: " + r.toString(), hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }

      // do not want to return from within this loop => throw exception instead
      // loop is executed once per acquisition (i.e. once if separate viewers isn't selected
      //   or once per timepoint if separate viewers is selected)
      long repeatStart = System.currentTimeMillis();
      for (int acqNum = 0; !cancelAcquisition_.get() && acqNum < nrRepeats_; acqNum++) {
         // handle intervals between (software-timed) repeats
         // only applies when doing separate viewers for each timepoint
         // and have multiple timepoints
         long repeatNow = System.currentTimeMillis();
         long repeatdelay = repeatStart + acqNum * timepointIntervalMs - repeatNow;
         while (repeatdelay > 0 && !cancelAcquisition_.get()) {
            updateAcquisitionStatus(AcquisitionStatus.WAITING, (int) (repeatdelay / 1000));
            long sleepTime = Math.min(1000, repeatdelay);
            try {
               Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
               MyDialogUtils.showError(e, hideErrors);
            }
            repeatNow = System.currentTimeMillis();
            repeatdelay = repeatStart + acqNum * timepointIntervalMs - repeatNow;
         }
         
         BlockingQueue<TaggedImage> bq = new LinkedBlockingQueue<TaggedImage>(40);  // increased from original 10
         ImageCache imageCache = null;
         
         // try to close last acquisition viewer if there could be one open (only in single acquisition per timepoint mode)
         if (acqSettings.separateTimepoints && (acq_!=null) && !cancelAcquisition_.get()) {
            try {
               // following line needed due to some arcane internal reason, otherwise
               //   call to closeAcquisitionWindow() fails silently. 
               //   See http://sourceforge.net/p/micro-manager/mailman/message/32999320/
               acq_.promptToSave(false);
               gui_.closeAcquisitionWindow(acqName_);
            } catch (Exception ex) {
               // do nothing if unsuccessful
            }
         }
         
         if (acqSettings.separateTimepoints) {
            // call to getUniqueAcquisitionName is extra safety net, we have checked that directory is empty before starting
            acqName_ = gui_.getUniqueAcquisitionName(prefixField_.getText() + "_" + String.format("%04d", acqNum));
         } else {
            acqName_ = gui_.getUniqueAcquisitionName(prefixField_.getText());
         }
         
         long extraStageScanTimeout = 0;
         if (acqSettings.isStageScanning || acqSettings.isStageStepping) {
            // approximately compute the extra time to wait for stack to begin (ramp up time) by getting
            //   the volume duration and subtracting the acquisition duration.  NB this is relatively crude method; 
            //   since we are computing a timeout we err on the side of having extraStageScanTimeout be too large.
            extraStageScanTimeout = (long) Math.ceil(computeActualVolumeDuration(acqSettings)
                  - (acqSettings.numSlices * acqSettings.numChannels * acqSettings.sliceTiming.sliceDuration));
         }
         
         long extraMultiXYTimeout = 0;
         if (acqSettings.useMultiPositions) {
            // give 20 extra seconds to arrive at intended XY position instead of trying to get fancy about computing actual move time
            extraMultiXYTimeout = XYSTAGETIMEOUT;
            // furthermore make sure that the main timeout value is at least 20ms because MM's position list uses this (via MultiStagePosition.goToPosition)
            if (props_.getPropValueInteger(Devices.Keys.CORE, Properties.Keys.CORE_TIMEOUT_MS) < XYSTAGETIMEOUT) {
               props_.setPropValue(Devices.Keys.CORE, Properties.Keys.CORE_TIMEOUT_MS, XYSTAGETIMEOUT);
            }
         }
         
         // 100 second timeout only when using external trigger
         long extraExternalTrigTimeout = (acqSettings.spimMode == AcquisitionModes.Keys.EXT_TRIG_ACQ) ? 100000 : 0;

         VirtualAcquisitionDisplay vad = null;
         WindowListener wl_acq = null;
         WindowListener[] wls_orig = null;
         try {
            // check for stop button before each acquisition
            if (cancelAcquisition_.get()) {
               throw new IllegalMonitorStateException("User stopped the acquisition");
            }

            // dump errors and then clear them; the serial traffic will appear in the corelog if we need them
            // hardcode addresses 1, 2, 3, and 6 which are almost always present
            props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "1 DU Y");
            props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "1 DU X");
            props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "2 DU Y");
            props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "2 DU X");
            props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "3 DU Y");
            props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "3 DU X");
            props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "6 DU Y");
            props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "6 DU X");
            
            // flag that we are actually running acquisition now
            acquisitionRunning_.set(true);
            
            ReportingUtils.logMessage("diSPIM plugin starting acquisition " + acqName_ + " with following settings: " + acqSettingsJSON);
            
            final int numMMChannels;
            if (acqSimultSideA) {
               numMMChannels = acqSettings.numSimultCameras;
            } else {
               numMMChannels = acqSettings.numSides * acqSettings.numChannels * (acqBothCameras ? 2 : 1);
            }
            
            if (acqSimultSideA) {
               gui_.openAcquisition(acqName_, rootDir, 1, numMMChannels,
                     acqSettings.numSlices*acqSettings.numChannels, nrPositions, true, save);
//               gui_.openAcquisition(acqName, rootDir, acqSettings.numChannels, numMMChannels,
//                     acqSettings.numSlices, nrPositions, true, save);
            } else if (spimMode == AcquisitionModes.Keys.NO_SCAN && !acqSettings.separateTimepoints) {
               // swap nrFrames and numSlices
               gui_.openAcquisition(acqName_, rootDir, acqSettings.numSlices, numMMChannels,
                  nrFrames, nrPositions, true, save);
            } else {
               gui_.openAcquisition(acqName_, rootDir, nrFrames, numMMChannels,
                  acqSettings.numSlices, nrPositions, true, save);
            }
            
            channelNames_ = new String[numMMChannels];
            
            // generate channel names and colors
            // also builds viewString for MultiViewRegistration metadata
            String viewString = "";
            final String SEPARATOR = "_";
            if (acqSimultSideA) {
               // for this mode we file cameras as channels and different channels as timepoints
               channelNames_[0] = cameraA1;
               if (camActiveA2)
                  channelNames_[1] = cameraA2;
               if (camActiveA3)
                  channelNames_[2] = cameraA3;
               if (camActiveA4)
                  channelNames_[3] = cameraA4;
               for (int camNum=0; camNum<acqSettings.numSimultCameras; ++camNum) {
                  viewString += NumberUtils.intToDisplayString(0) + SEPARATOR;
               }
            } else {
               for (int reflect=0; reflect<2; reflect++) {
                  // only run for loop once unless acqBothCameras is true
                  // if acqBothCameras is true then run second time to add "epi" channels
                  if (reflect > 0 && !acqBothCameras) {
                     continue;
                  }
                  // set up channels (side A/B is treated as channel too)
                  if (acqSettings.useChannels) {
                     ChannelSpec[] channels = multiChannelPanel_.getUsedChannels();
                     for (int i = 0; i < channels.length; i++) {
                        String chName = "-" + channels[i].config_ + (reflect>0 ? "-epi" : "");
                        // same algorithm for channel index vs. specified channel and side as in comments of code below
                        //   that figures out the channel where to file each incoming image
                        int channelIndex = i;
                        if (twoSided) {
                           channelIndex *= 2;
                        }
                        channelIndex += reflect*numMMChannels/2;
                        channelNames_[channelIndex] = firstCamera + chName;
                        viewString += NumberUtils.intToDisplayString(0) + SEPARATOR;
                        if (twoSided) {
                           channelNames_[channelIndex+1] = secondCamera + chName;
                           viewString += NumberUtils.intToDisplayString(90) + SEPARATOR;
                        }
                     }
                  } else {  // single-channel
                     int channelIndex = reflect*numMMChannels/2;
                     channelNames_[channelIndex] = firstCamera + (reflect>0 ? "-epi" : "");
                     viewString += NumberUtils.intToDisplayString(0) + SEPARATOR;
                     if (twoSided) {
                        channelNames_[channelIndex+1] = secondCamera + (reflect>0 ? "-epi" : "");
                        viewString += NumberUtils.intToDisplayString(90) + SEPARATOR;
                     }
                  }
               }
            }
            // strip last separator of viewString (for Multiview Reconstruction)
            viewString = viewString.substring(0, viewString.length() - 1);
            
            // assign channel names and colors
            for (int i = 0; i < numMMChannels; i++) {
               gui_.setChannelName(acqName_, i, channelNames_[i]);
               gui_.setChannelColor(acqName_, i, getChannelColor(i));
            }
            
            if (acqSettings.useMovementCorrection) {
               for (int i = 0; i < acqSettings.numChannels; i++) {
                  if (channelNames_[i].equals(firstCamera + "-" + correctMovementChannel)) {
                     cmChannelNumber = i;
                  }
               }
               if (cmChannelNumber == -1) {
                  MyDialogUtils.showError("The channel selected for movement correction on the auitofocus tab was not found in this acquisition");
                  return AcquisitionStatus.FATAL_ERROR;
               }
            }

            zStepUm_ = (acqSettings.isStageScanning || acqSettings.isStageStepping)
                  ? controller_.getActualStepSizeUm()  // computed step size, accounting for quantization of controller
                  : acqSettings.stepSizeUm;  // should be same as PanelUtils.getSpinnerFloatValue(stepSize_)
            
            // initialize acquisition
            gui_.initializeAcquisition(acqName_, (int) core_.getImageWidth(),
                    (int) core_.getImageHeight(), (int) core_.getBytesPerPixel(),
                    (int) core_.getImageBitDepth());
            gui_.promptToSaveAcquisition(acqName_, !testAcq);
            
            // These metadata have to be added after initialization, 
            // otherwise they will not be shown?!
            gui_.setAcquisitionProperty(acqName_, "NumberOfSides", 
                    NumberUtils.intToCoreString(acqSettings.numSides));
            gui_.setAcquisitionProperty(acqName_, "FirstSide", acqSettings.firstSideIsA ? "A" : "B");
            gui_.setAcquisitionProperty(acqName_, "SlicePeriod_ms", 
                  actualSlicePeriodLabel_.getText());
            gui_.setAcquisitionProperty(acqName_, "LaserExposure_ms",
                  NumberUtils.doubleToCoreString(acqSettings.desiredLightExposure));
            gui_.setAcquisitionProperty(acqName_, "VolumeDuration",
                    actualVolumeDurationLabel_.getText());
            gui_.setAcquisitionProperty(acqName_, "SPIMmode", spimMode.toString()); 
            // Multi-page TIFF saving code wants this one (cameras are all 16-bits, so not much reason for anything else)
            gui_.setAcquisitionProperty(acqName_, "PixelType", "GRAY16");
            gui_.setAcquisitionProperty(acqName_, "UseAutofocus", 
                  acqSettings.useAutofocus ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
            gui_.setAcquisitionProperty(acqName_, "UsePathPresets", 
                    acqSettings.usePathPresets ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
            gui_.setAcquisitionProperty(acqName_, "UseMotionCorrection", 
                  acqSettings.useMovementCorrection ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
            gui_.setAcquisitionProperty(acqName_, "HardwareTimepoints", 
                  acqSettings.hardwareTimepoints ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
            gui_.setAcquisitionProperty(acqName_, "SeparateTimepoints", 
                  acqSettings.separateTimepoints ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
            gui_.setAcquisitionProperty(acqName_, "CameraMode", acqSettings.cameraMode.toString()); 
            gui_.setAcquisitionProperty(acqName_, "z-step_um", NumberUtils.doubleToCoreString(zStepUm_));
            // Properties for use by MultiViewRegistration plugin
            // Format is: x_y_z, set to 1 if we should rotate around this axis.
            gui_.setAcquisitionProperty(acqName_, "MVRotationAxis", "0_1_0");
            gui_.setAcquisitionProperty(acqName_, "MVRotations", viewString);
            // save XY and SPIM head position in metadata
            // update positions first at expense of two extra serial transactions
            refreshXYZPositions();
            gui_.setAcquisitionProperty(acqName_, "Position_X",
                  positions_.getPositionString(Devices.Keys.XYSTAGE, Directions.X));
            gui_.setAcquisitionProperty(acqName_, "Position_Y",
                  positions_.getPositionString(Devices.Keys.XYSTAGE, Directions.Y));
            gui_.setAcquisitionProperty(acqName_, "Position_SPIM_Head",
                  positions_.getPositionString(Devices.Keys.UPPERZDRIVE));
            gui_.setAcquisitionProperty(acqName_, "SPIMAcqSettings", acqSettingsJSON);
            gui_.setAcquisitionProperty(acqName_, "SPIMtype", ASIdiSPIM.oSPIM ? (ASIdiSPIM.SCOPE ? "SCOPE": "oSPIM") : "diSPIM");
            gui_.setAcquisitionProperty(acqName_, "AcquisitionName", acqName_);
            gui_.setAcquisitionProperty(acqName_, "Prefix", acqName_);
            gui_.setAcquisitionProperty(acqName_, MMTags.Summary.SLICES_FIRST, Boolean.TRUE.toString());  // whether slices are inner loop compared to channel; not sure why but Nico had this set to true forever so leaving it
            gui_.setAcquisitionProperty(acqName_, MMTags.Summary.TIME_FIRST, Boolean.FALSE.toString());
            gui_.setAcquisitionProperty(acqName_, "StageScanAnglePathA", // used in deskew routine
                    Float.toString(props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_STAGESCAN_ANGLE_PATHA)));
            //Boolean.toString(acqSimultSideA));   // whether timepoints are inner loop compared to position; false for all diSPIM use cases except simultaneous on PathA where we use timepoints as "channels"
            gui_.setAcquisitionProperty(acqName_, "AcquisitionName", acqName_);
            
            // get circular buffer ready
            // do once here but not per-trigger; need to ensure ROI changes registered
            core_.initializeCircularBuffer();  // superset of clearCircularBuffer()
            
            // TODO: use new acquisition interface that goes through the pipeline
            //gui_.setAcquisitionAddImageAsynchronous(acqName); 
            acq_ = gui_.getAcquisition(acqName_);
            
            // Dive into MM internals since script interface does not support pipelines
            imageCache = acq_.getImageCache();
            vad = acq_.getAcquisitionWindow();
            imageCache.addImageCacheListener(vad);
            
            // Start pumping images into the ImageCache
            DefaultTaggedImageSink sink = new DefaultTaggedImageSink(bq, imageCache);
            sink.start();
            
            // remove usual window listener(s) and replace it with our own
            //   that will prompt before closing and cancel acquisition if confirmed
            // this should be considered a hack, it may not work perfectly
            // I have confirmed that there is only one windowListener and it seems to 
            //   also be related to window closing
            // Note that ImageJ's acquisition window is AWT instead of Swing
            wls_orig = vad.getImagePlus().getWindow().getWindowListeners();
            for (WindowListener l : wls_orig) {
               vad.getImagePlus().getWindow().removeWindowListener(l);
            }
            wl_acq = new WindowAdapter() {
               @Override
               public void windowClosing(WindowEvent arg0) {
                  // if running acquisition only close if user confirms
                  if (acquisitionRunning_.get()) {
                     boolean stop = MyDialogUtils.getConfirmDialogResult(
                           "Do you really want to abort the acquisition?",
                           JOptionPane.YES_NO_OPTION);
                     if (stop) {
                        cancelAcquisition_.set(true);                        
                     }
                  }
               }
            };
            vad.getImagePlus().getWindow().addWindowListener(wl_acq);
            
            // patterned after implementation in MMStudio.java
            // will be null if not saving to disk
            lastAcquisitionPath_ = acq_.getImageCache().getDiskLocation();
            lastAcquisitionName_ = acqName_;
            
                           
            // only used when motion correction was requested
            MovementDetector[] movementDetectors = new MovementDetector[nrPositions];
               
            // Transformation matrices to convert between camera and stage coordinates
            final Vector3D yAxis = new Vector3D(0.0, 1.0, 0.0);
            final Rotation camARotation = new Rotation( yAxis, Math.toRadians(-45) );
            final Rotation camBRotation = new Rotation ( yAxis, Math.toRadians(45) );

            final Vector3D zeroPoint = new Vector3D(0.0, 0.0, 0.0);  // cache a zero point for efficiency
            
            // make sure all devices have arrived, e.g. a stage isn't still moving
            try {
               core_.waitForSystem();
            } catch (Exception e) {
               if (!hideErrors) {
                  ReportingUtils.logError(e);
               } else {
                  ReportingUtils.logError("error waiting for system");
               }
            }
            
            // make sure the PI stage doesn't stay busy for more than 3 seconds; if so then assume it's a hard error
            if (devices_.isValidMMDevice(Devices.Keys.SUPPLEMENTAL_X) &&
                  devices_.getMMDeviceLibrary(Devices.Keys.SUPPLEMENTAL_X) == Devices.Libraries.PI_GCS_2) {
               boolean busy = true;
               int count = 0;
               final int totalTimeMs = 3000;
               final int sleepTimeMs = 25;
               while (busy && (count < (totalTimeMs/sleepTimeMs))) {
                  busy = core_.deviceBusy(devices_.getMMDevice(Devices.Keys.SUPPLEMENTAL_X));
                  if (busy) {
                     count++;
                     Thread.sleep(sleepTimeMs);
                  }
               }
               if (busy) {
                  acquisitionStatus_ = AcquisitionStatus.FATAL_ERROR;
                  throw new Exception("PI stage reporting busy; force-quit acquisition");
               }
            }
            
            // check if we are raising the SPIM head to the load position between every acquisition
            final boolean raiseSPIMHead = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(), 
                  Properties.Keys.PLUGIN_RAISE_SPIM_HEAD_BETWEEN_ACQS, false);
            
            // Loop over all the times we trigger the controller's acquisition
            //  (although if multi-channel with volume switching is selected there
            //   is inner loop to trigger once per channel)
            // remember acquisition start time for software-timed timepoints
            // For hardware-timed timepoints we only trigger the controller once
            long acqStart = System.currentTimeMillis();
            if (raiseSPIMHead) {
	       	    ASIdiSPIM.getFrame().getNavigationPanel().raiseSPIMHead();
	            core_.waitForDevice(devices_.getMMDevice(Devices.Keys.UPPERZDRIVE));
            }
            for (int trigNum = 0; trigNum < nrFrames; trigNum++) {
               // handle intervals between (software-timed) time points
               // when we are within the same acquisition
               // (if separate viewer is selected then nothing bad happens here
               // but waiting during interval handled elsewhere)
               long acqNow = System.currentTimeMillis();
               long delay = acqStart + trigNum * timepointIntervalMs - acqNow;
               while (delay > 0 && !cancelAcquisition_.get()) {
                  updateAcquisitionStatus(AcquisitionStatus.WAITING, (int) (delay / 1000));
                  long sleepTime = Math.min(1000, delay);
                  Thread.sleep(sleepTime);
                  acqNow = System.currentTimeMillis();
                  delay = acqStart + trigNum * timepointIntervalMs - acqNow;
               }
               
               // check for stop button before each time point
               if (cancelAcquisition_.get()) {
                  throw new IllegalMonitorStateException("User stopped the acquisition");
               }
               
               int timePoint = acqSettings.separateTimepoints ? acqNum : trigNum ;
               
               // execute any start-timepoint runnables
               for (Runnable r : runnablesStartTimepoint_) {
                  try {
                     r.run();
                  } catch(Exception ex) {
                     MyDialogUtils.showError("start timepoint runnable threw exception: " + r.toString(), hideErrors);
                     return AcquisitionStatus.FATAL_ERROR;
                  }
               }
               
               // this is where we autofocus if requested
               if (acqSettings.useAutofocus) {
                  // Note that we will not autofocus as expected when using hardware
                  // timing.  Seems OK, since hardware timing will result in short
                  // acquisition times that do not need autofocus.  We have already
                  // ensured that we aren't doing both
                  if ( (autofocusAtT0 && timePoint == 0) || ( (timePoint > 0) && 
                          (timePoint % autofocusEachNFrames == 0 ) ) ) {
                     if (acqSettings.useChannels) {
                        multiChannelPanel_.selectChannel(autofocusChannel);
                     }
                     if (sideActiveA) {
                        if (acqSettings.usePathPresets) {
                           controller_.setPathPreset(Devices.Sides.A);
                           // blocks until all devices done
                        }
                        AutofocusUtils.FocusResult score = autofocus_.runFocus(
                                this, Devices.Sides.A, false,
                                sliceTiming_, false);
                        updateCalibrationOffset(Devices.Sides.A, score);
                     }
                     if (sideActiveB) {
                        if (acqSettings.usePathPresets) {
                           controller_.setPathPreset(Devices.Sides.B);
                           // blocks until all devices done
                        }
                        AutofocusUtils.FocusResult score = autofocus_.runFocus(
                              this, Devices.Sides.B, false,
                              sliceTiming_, false);
                        updateCalibrationOffset(Devices.Sides.B, score);
                     }
                     // Restore settings of the controller
                     controller_.prepareControllerForAquisition(acqSettings, extraChannelOffset_);
                     if (acqSettings.useChannels && acqSettings.channelMode != MultichannelModes.Keys.VOLUME) {
                        controller_.setupHardwareChannelSwitching(acqSettings, hideErrors);
                     }
                  }
               }

               numTimePointsDone_++;

               // loop over all positions
               for (int positionNum = 0; positionNum < nrPositions; positionNum++) {
                  numPositionsDone_ = positionNum + 1;
                  updateAcquisitionStatus(AcquisitionStatus.ACQUIRING);
                  
                  // execute any start-position runnables
                  for (Runnable r : runnablesStartPosition_) {
                     try {
                        r.run();
                     } catch(Exception ex) {
                        MyDialogUtils.showError("start position runnable threw exception: " + r.toString(), hideErrors);
                        return AcquisitionStatus.FATAL_ERROR;
                     }
                  }
                  
                  if (acqSettings.useMultiPositions) {
                     
                     // make sure user didn't stop things
                     if (cancelAcquisition_.get()) {
                        throw new IllegalMonitorStateException("User stopped the acquisition");
                     }
                     
                     // want to move between positions move stage fast, so we 
                     //   will clobber stage scanning setting so need to restore it
                     float scanXSpeed = 1f;
                     float scanXAccel = 1f;
                     float scanXSupSpeed = 1f;
                     float scanZSpeed = 1f;
                     if (acqSettings.isStageScanning) {
                        scanXSpeed = props_.getPropValueFloat(Devices.Keys.XYSTAGE,
                              Properties.Keys.STAGESCAN_MOTOR_SPEED_X);
                        props_.setPropValue(Devices.Keys.XYSTAGE,
                              Properties.Keys.STAGESCAN_MOTOR_SPEED_X, origXSpeed);
                        scanXAccel = props_.getPropValueFloat(Devices.Keys.XYSTAGE,
                              Properties.Keys.STAGESCAN_MOTOR_ACCEL_X);
                        props_.setPropValue(Devices.Keys.XYSTAGE,
                              Properties.Keys.STAGESCAN_MOTOR_ACCEL_X, origXAccel);
                        if (devices_.isValidMMDevice(Devices.Keys.SUPPLEMENTAL_X)) {
                           scanXSupSpeed = props_.getPropValueFloat(Devices.Keys.SUPPLEMENTAL_X,
                                 Properties.Keys.VELOCITY);
                           props_.setPropValue(Devices.Keys.SUPPLEMENTAL_X,
                                 Properties.Keys.VELOCITY, origXSupSpeed, true);
                        }
                        if (devices_.isValidMMDevice(Devices.Keys.UPPERZDRIVE)) {
                           scanZSpeed = props_.getPropValueFloat(Devices.Keys.UPPERZDRIVE,
                                 Properties.Keys.STAGESCAN_MOTOR_SPEED_Z);
                           props_.setPropValue(Devices.Keys.UPPERZDRIVE,
                                 Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, origZSpeed);
                        }
                     }
                     
                     final MultiStagePosition nextPosition = positionList.getPosition(positionNum);
                     
                     // move to the next position
                     if (raiseSPIMHead && positionNum == 0) {
                    	 // move to the XY position first
                         StagePosition posXY = nextPosition.get(devices_.getMMDevice(Devices.Keys.XYSTAGE)); 
                         if (posXY != null) {
	                         core_.setXYPosition(posXY.stageName, posXY.x, posXY.y);
	                         core_.waitForDevice(devices_.getMMDevice(Devices.Keys.XYSTAGE));
                         }
                     	 // lower SPIM head after we move into position
                         StagePosition posZ = nextPosition.get(devices_.getMMDevice(Devices.Keys.UPPERZDRIVE));
                         if (posZ != null) {
	                         core_.setPosition(posZ.stageName, posZ.x);
	                         core_.waitForDevice(devices_.getMMDevice(Devices.Keys.UPPERZDRIVE));
                         }
                         // handle all remaining stage motion
                         MultiStagePosition.goToPosition(nextPosition, core_);
                     } else {
                         // blocking call; will wait for stages to move
                         // NB: assume planar correction is handled by marked Z position; this seems better
                         //   than making it impossible for user to select different Z positions e.g. for YZ grid
                         MultiStagePosition.goToPosition(nextPosition, core_);
                     }

                     // update local stage positions after move (xPositionUm_, yPositionUm_, zPositionUm_)
                     refreshXYZPositions();

                     // for stage scanning: restore speed and set up scan at new position 
                     // non-multi-position situation is handled in prepareControllerForAquisition instead
                     if (acqSettings.isStageScanning) {
                        StagePosition pos = nextPosition.get(devices_.getMMDevice(Devices.Keys.XYSTAGE));  // get ideal position from position list, not current position
                        controller_.prepareStageScanForAcquisition(pos.x, pos.y, acqSettings.spimMode);
                        props_.setPropValue(Devices.Keys.XYSTAGE,
                              Properties.Keys.STAGESCAN_MOTOR_SPEED_X, scanXSpeed);
                        props_.setPropValue(Devices.Keys.XYSTAGE,
                              Properties.Keys.STAGESCAN_MOTOR_ACCEL_X, scanXAccel);
                        if (devices_.isValidMMDevice(Devices.Keys.SUPPLEMENTAL_X)) {
                           props_.setPropValue(Devices.Keys.SUPPLEMENTAL_X,
                                 Properties.Keys.VELOCITY, scanXSupSpeed, true);
                        }
                        if (devices_.isValidMMDevice(Devices.Keys.UPPERZDRIVE)) {
                           props_.setPropValue(Devices.Keys.UPPERZDRIVE,
                                 Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, scanZSpeed);
                        }
                     } else if (acqSettings.isStageStepping) {
                        controller_.moveSupplementalToStartPosition();
                     }
                     
                     StagePosition posXY = nextPosition.get(devices_.getMMDevice(Devices.Keys.XYSTAGE)); 
                     StagePosition posZ = nextPosition.get(devices_.getMMDevice(Devices.Keys.UPPERZDRIVE));
                     if ((posXY != null) && (posZ != null)) {
                        ReportingUtils.logMessage("Tried to go to position (" + posXY.x + ", " + posXY.y + ", " + posZ.z + ")"
                              + "and actually went to position (" + xPositionUm_ + ", " + yPositionUm_ + ", " + zPositionUm_ + ").");
                     } else if (posXY != null) {
                        ReportingUtils.logMessage("Tried to go to position (" + posXY.x + ", " + posXY.y + ")"
                              + "and actually went to position (" + xPositionUm_ + ", " + yPositionUm_ + ").");
                     } else if (posZ != null) {
                        ReportingUtils.logMessage("Tried to go to position (" + posZ.z + ")"
                              + "and actually went to position (" + zPositionUm_ + ").");
                     }
                     
                     // wait any extra time the user requests
                     Thread.sleep(Math.round(PanelUtils.getSpinnerFloatValue(positionDelay_)));
                  }
                  
                  // figure out how many images so we can compare we expect total
                  final int nrCameras;
                  if (acqSettings.numSimultCameras > 0) {
                     nrCameras = acqSettings.numSimultCameras;
                  } else if (twoSided || acqBothCameras) {
                     nrCameras = 2;
                  } else {
                     nrCameras = 1;
                  }
                  final int expectedNrImages = nrSlicesSoftware * nrCameras;
                  int totalImages = 0;
                  
                  int nrOuterLoop = 1;
                  if (acqSettings.usePathPresets && acqSettings.numSides > 1) {
                     // with path presets we have to trigger controller once for each side so double the number of triggers for double-sided
                     nrOuterLoop = 2;
                  }
                  
                  // loop over all the times we trigger the controller for each position
                  // usually just once, but will be the number of channels if we have
                  //  multiple channels and aren't using PLogic to change between them
                  // if we are using path presets then trigger controller for each side and do that in "outer loop"
                  //   (i.e. the channels will run subsequent to each other, then switch sides and run through them again
                  for (int outerLoop = 0; outerLoop < nrOuterLoop; ++outerLoop) {
                     
                     // counters we use to file images into the right spot
                     int[] channelImageNr = new int[4*acqSettings.numChannels];  // keep track of how many frames we have received for each MM "channel"
                     int[] cameraImageNr = new int[4];       // keep track of how many images we have received from the camera
                     int[] tpNumber = new int[2*acqSettings.numChannels];  // keep track of which timepoint we are on for hardware timepoints
                     
                     for (int channelNum = 0; channelNum < nrChannelsSoftware; channelNum++) {
                        try {
                           // flag that we are using the cameras/controller
                           ASIdiSPIM.getFrame().setHardwareInUse(true);
                           
                           // deal with channel if needed (hardware channel switching doesn't happen here)
                           if (changeChannelPerVolumeSoftware) {  // if we are changing channel every volume
                              if (changeChannelPerVolumeDoneFirst) {
                                 // skip selecting next channel the very first time only
                                 changeChannelPerVolumeDoneFirst = false;
                              } else {
                                 double newOffset = multiChannelPanel_.selectNextChannelAndGetOffset();
                                 if (!MyNumberUtils.floatsEqual(newOffset, extraChannelOffset_)) { 
                                    extraChannelOffset_ = newOffset;
                                    controller_.prepareControllerForAquisitionOffsetOnly(acqSettings, extraChannelOffset_);
                                 }
                              }
                              
                              if (acqSettings.isStageScanning) {
                                 controller_.preparePlanarCorrectionForAcquisition();
                              }
                           }
                           
                           // deal with side-specific preset if needed
                           // have to trigger the controller once for each side in this special case
                           // "outer loop" is sides, inner loop is channels
                           boolean pathPresetsFirst = true;
                           boolean currentSideA = firstSideA;
                           Devices.Sides currentSide = currentSideA ? Devices.Sides.A : Devices.Sides.B;
                           if (acqSettings.usePathPresets) {
                              if (outerLoop > 0) {
                                 currentSideA = !currentSideA;
                                 currentSide = currentSideA ? Devices.Sides.A : Devices.Sides.B;
                                 pathPresetsFirst = false;
                              }
                              // for 2-sided acquisition with path presets we run 2 single-sided acquisitions
                              //   so set controller accordingly
                              if (acqSettings.numSides > 1) {
                                 AcquisitionSettings acqSettingsOneSide = new AcquisitionSettings(acqSettings);
                                 acqSettingsOneSide.numSides = 1;
                                 acqSettingsOneSide.firstSideIsA = currentSideA;
                                 controller_.prepareControllerForAquisition(acqSettingsOneSide, extraChannelOffset_);
                              }
                              
                              // make sure appropriate path preset is applied
                              controller_.setPathPreset(currentSide);
                           }

                           // special case: single-sided piezo acquisition risks illumination piezo sleeping
                           // prevent this from happening by sending relative move of 0 like we do in live mode before each trigger
                           // NB: this won't help for hardware-timed timepoints
                           final Devices.Keys piezoIllumKey = firstSideA ? Devices.Keys.PIEZOB : Devices.Keys.PIEZOA;
                           if (!twoSided && props_.getPropValueInteger(piezoIllumKey, Properties.Keys.AUTO_SLEEP_DELAY) > 0) {
                              core_.setRelativePosition(devices_.getMMDevice(piezoIllumKey), 0);
                           }
                           
                           // special case: if we are doing autofocus every stage pass do it now (after setting channel)
                           // Note that we have previously made sure that we aren't doing this with usePathPresets 
                           //    set to true (I think it would be possible but add significant complexity to this code) 
                           if (acqSettings.isStageScanning && autofocusEveryStagePass) {
                              ASIdiSPIM.getFrame().setHardwareInUse(false);
                              if (sideActiveA) {
                                 if (acqSettings.usePathPresets) {
                                    controller_.setPathPreset(Devices.Sides.A);
                                    // blocks until all devices done
                                 }
                                 AutofocusUtils.FocusResult score = autofocus_.runFocus(
                                       this, Devices.Sides.A, false,
                                       sliceTiming_, false);
                                 updateCalibrationOffset(Devices.Sides.A, score);
                              }
                              if (sideActiveB) {
                                 if (acqSettings.usePathPresets) {
                                    controller_.setPathPreset(Devices.Sides.B);
                                    // blocks until all devices done
                                 }
                                 AutofocusUtils.FocusResult score = autofocus_.runFocus(
                                       this, Devices.Sides.B, false,
                                       sliceTiming_, false);
                                 updateCalibrationOffset(Devices.Sides.B, score);
                              }
                              // Restore settings of the controller
                              controller_.prepareControllerForAquisition(acqSettings, extraChannelOffset_);
                              if (acqSettings.useChannels && acqSettings.channelMode != MultichannelModes.Keys.VOLUME) {
                                 controller_.setupHardwareChannelSwitching(acqSettings, hideErrors);
                              }
                              ASIdiSPIM.getFrame().setHardwareInUse(true);
                           }
                           
                           // deal with shutter before starting acquisition
                           shutterOpen = core_.getShutterOpen();
                           if (autoShutter) {
                              core_.setAutoShutter(false);
                              if (!shutterOpen) {
                                 core_.setShutterOpen(true);
                              }
                           }

                           // trigger the state machine on the controller before triggering cameras for demo cameras
                           if (usingDemoCam) {
                              boolean success = controller_.triggerControllerStartAcquisition(spimMode, firstSideA);
                              if (!success) {
                                 throw new Exception("Controller triggering not successful for demo cam");
                              }
                              // wait for stage scan move to initial position if needed
                              Thread.sleep(extraStageScanTimeout);
                           }
                           
                           // start the cameras so they are awaiting triggers
                           if (acqSimultSideA) {
                              core_.startSequenceAcquisition(cameraA1, nrSlicesSoftware, 0, true);
                              if (camActiveA2) {
                                 core_.startSequenceAcquisition(cameraA2, nrSlicesSoftware, 0, true);
                              }
                              if (camActiveA3) {
                                 core_.startSequenceAcquisition(cameraA3, nrSlicesSoftware, 0, true);
                              }
                              if (camActiveA4) {
                                 core_.startSequenceAcquisition(cameraA4, nrSlicesSoftware, 0, true);
                              }
                           } else {
                              if (acqSettings.usePathPresets) {
                                 core_.startSequenceAcquisition(pathPresetsFirst ? firstCamera : secondCamera, nrSlicesSoftware, 0, true);
                              } else {  // usual case
                                 core_.startSequenceAcquisition(firstCamera, nrSlicesSoftware, 0, true);
                                 if (twoSided || acqBothCameras) {
                                    core_.startSequenceAcquisition(secondCamera, nrSlicesSoftware, 0, true);
                                 }
                              }
                           }
                           
                           // trigger the state machine on the controller after triggering cameras as usual case
                           if (!usingDemoCam) {
                              boolean success = controller_.triggerControllerStartAcquisition(spimMode, firstSideA);
                              if (!success) {
                                 throw new Exception("Controller triggering not successful for demo cam");
                              }
                           }

                           ReportingUtils.logDebugMessage("Starting time point " + (timePoint+1) + " of " + nrFrames
                                 + " with (software) channel number " + channelNum);
                           
                           // Wait for first image to create ImageWindow, so that we can be sure about image size
                           // Do not actually grab first image here, just make sure it is there
                           long start = System.currentTimeMillis();
                           long now = start;
                           final long timeout = Math.max(3000, Math.round(10*sliceDuration + 2*acqSettings.delayBeforeSide))
                                 + extraStageScanTimeout + extraMultiXYTimeout + extraExternalTrigTimeout;
                           while (core_.getRemainingImageCount() == 0 && (now - start < timeout)
                                 && !cancelAcquisition_.get()) {
                              now = System.currentTimeMillis();
                              Thread.sleep(5);
                           }
                           if (now - start >= timeout) {
                              String msg = "Camera did not send first image within a reasonable time.\n";
                              if (acqSettings.isStageScanning) {
                                 msg += "Make sure jumpers are correct on XY card and also micro-micromirror card.";
                                 ReportingUtils.logMessage("Stage speed is " + props_.getPropValueFloat(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_MOTOR_SPEED_X));
                                 positions_.getUpdatedPosition(Devices.Keys.XYSTAGE, Directions.X);
                              } else if (acqSettings.spimMode == AcquisitionModes.Keys.EXT_TRIG_ACQ) {
                                 msg += "Make sure external pulse is provided within 10 seconds of starting acquistion";
                              } else {
                                 msg += "Make sure camera trigger cables are connected properly.";
                              }
                              throw new Exception(msg);
                           }
                           
                           // if hardware timepoints and overlap mode then we have to drop spurious images (in both multi-channel and single-channel)
                           final boolean checkForSkips = acqSettings.hardwareTimepoints && (acqSettings.cameraMode == CameraModes.Keys.OVERLAP);
                           int[] imagesToSkip = new int[2*acqSettings.numChannels];  // only used if checkForSkips is true
                           
                           // grab all the images from the cameras, put them into the acquisition
                           boolean done = false;
                           long timeout2 = Math.max(1000, Math.round(5*sliceDuration));
                           totalImages = 0;
                           if (acqSettings.isStageScanning) {  // for stage scanning have to allow extra time for turn-around
                              timeout2 += (2*(long)Math.ceil(getStageRampDuration(acqSettings)));  // ramp up and then down
                              timeout2 += 5000;   // ample extra time for turn-around (e.g. antibacklash move in Y), interestingly 500ms extra seems insufficient for reasons I don't understand yet so just pad this for now  // TODO figure out why turn-aronud is taking so long
                              if (acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_UNIDIRECTIONAL
                                    || acqSettings.spimMode == AcquisitionModes.Keys.STAGE_STEP_SUPPLEMENTAL_UNIDIRECTIONAL
                                    || acqSettings.spimMode == AcquisitionModes.Keys.STAGE_SCAN_SUPPLEMENTAL_UNIDIRECTIONAL) {
                                 timeout2 += (long)Math.ceil(getStageRetraceDuration(acqSettings));  // in unidirectional case also need to rewind
                              }
                           }
                           if (acqSettings.spimMode == AcquisitionModes.Keys.EXT_TRIG_ACQ) {
                              timeout2 += 100000;
                           }
                           
                           start = System.currentTimeMillis();
                           long last = start;
                           try {
                              while ((core_.getRemainingImageCount() > 0
                                    || (!acqSimultSideA && ( 
                                          core_.isSequenceRunning(firstCamera)
                                          || ((twoSided || acqBothCameras) && core_.isSequenceRunning(secondCamera)))
                                          || (acqSettings.usePathPresets && !pathPresetsFirst && core_.isSequenceRunning(secondCamera)
                                          )
                                       )
                                    || (acqSimultSideA && (
                                          core_.isSequenceRunning(cameraA1)
                                          || (camActiveA2 && core_.isSequenceRunning(cameraA2))
                                          || (camActiveA3 && core_.isSequenceRunning(cameraA3))
                                          || (camActiveA4 && core_.isSequenceRunning(cameraA4))
                                          )
                                       ))
                                    && !done) {
                                 now = System.currentTimeMillis();
                                 if (core_.getRemainingImageCount() > 0) {  // we have an image to grab
                                    TaggedImage timg = core_.popNextTaggedImage();
                                    totalImages++;

                                    // figure out which channel index this frame belongs to
                                    // "channel index" is channel of MM acquisition
                                    // channel indexes will go from 0 to (numSides * numChannels - 1) for standard (non-reflective) imaging
                                    // if double-sided then second camera gets odd channel indexes (1, 3, etc.)
                                    //    and adjacent pairs will be same color (e.g. 0 and 1 will be from first color, 2 and 3 from second, etc.)
                                    // if acquisition from both cameras (reflective imaging) then
                                    //    second half of channel indices are from opposite (epi) view
                                    // if acqSimultSideA is true, then dataset "channel" corresponds to which camera and
                                    //     and dataset "timepoint" corresponds to which real channel  
                                    // e.g. for 3-color 1-sided (A first) standard (non-reflective) then
                                    //    0 will be A-illum A-cam 1st color
                                    //    2 will be A-illum A-cam 2nd color
                                    //    4 will be A-illum A-cam 3rd color
                                    // e.g. for 3-color 2-sided (A first) standard (non-reflective) then
                                    //    0 will be A-illum A-cam 1st color
                                    //    1 will be B-illum B-cam 1st color
                                    //    2 will be A-illum A-cam 2nd color
                                    //    3 will be B-illum B-cam 2nd color
                                    //    4 will be A-illum A-cam 3rd color
                                    //    5 will be B-illum B-cam 3rd color
                                    // e.g. for 3-color 1-sided (A first) both camera (reflective) then
                                    //    0 will be A-illum A-cam 1st color
                                    //    1 will be A-illum A-cam 2nd color
                                    //    2 will be A-illum A-cam 3rd color
                                    //    3 will be A-illum B-cam 1st color
                                    //    4 will be A-illum B-cam 2nd color
                                    //    5 will be A-illum B-cam 3rd color
                                    // e.g. for 3-color 2-sided (A first) both camera (reflective) then
                                    //    0 will be A-illum A-cam 1st color
                                    //    1 will be B-illum B-cam 1st color
                                    //    2 will be A-illum A-cam 2nd color
                                    //    3 will be B-illum B-cam 2nd color
                                    //    4 will be A-illum A-cam 3rd color
                                    //    5 will be B-illum B-cam 3rd color
                                    //    6 will be A-illum B-cam 1st color
                                    //    7 will be B-illum A-cam 1st color
                                    //    8 will be A-illum B-cam 2nd color
                                    //    9 will be B-illum A-cam 2nd color
                                    //   10 will be A-illum B-cam 3rd color
                                    //   11 will be B-illum A-cam 3rd color
                                    String camera = (String) timg.tags.get("Camera");
                                    final int cameraIndex;
                                    if (acqSimultSideA) {
                                       if (camera.equals(cameraA1))
                                          cameraIndex = 0;
                                       else if (camera.equals(cameraA2))
                                          cameraIndex = 1;
                                       else if (camera.equals(cameraA3))
                                          cameraIndex = 2;
                                       else if (camera.equals(cameraA4))
                                          cameraIndex = 3;
                                       else {
                                          cameraIndex = 0;
                                          throw new Exception("Unexpected image from camera " + camera);
                                       }
                                    } else {
                                       cameraIndex = camera.equals(firstCamera) ? 0: 1;
                                    }
                                    int channelIndex_tmp;
                                    if (acqSimultSideA) {
                                       channelIndex_tmp = cameraIndex;  // for acqSimultSideA channelIndex always equals cameraIndex
                                    } else {
                                       switch (acqSettings.channelMode) {
                                       case NONE:
                                       case VOLUME:
                                          channelIndex_tmp = channelNum;
                                          break;
                                       case VOLUME_HW:
                                          channelIndex_tmp = cameraImageNr[cameraIndex] / acqSettings.numSlices;  // want quotient only
                                          break;
                                       case SLICE_HW:
                                          channelIndex_tmp = cameraImageNr[cameraIndex] % acqSettings.numChannels;  // want modulo arithmetic
                                          break;
                                       default:
                                          // should never get here
                                          throw new Exception("Undefined channel mode");
                                       }
                                       if (acqBothCameras) {
                                          if (twoSided) {  // 2-sided, both cameras
                                             channelIndex_tmp = channelIndex_tmp * 2 + cameraIndex;
                                             // determine whether first or second side by whether we've seen half the images yet
                                             if (cameraImageNr[cameraIndex] > nrSlicesSoftware/2) {
                                                // second illumination side => second half of channels
                                                channelIndex_tmp += 2*acqSettings.numChannels;
                                             }
                                          } else {  // 1-sided, both cameras
                                             channelIndex_tmp += cameraIndex*acqSettings.numChannels;
                                          }
                                       } else {  // normal situation, non-reflective imaging
                                          if (twoSided) {
                                             channelIndex_tmp *= 2;
                                          }
                                          channelIndex_tmp += cameraIndex;
                                       }
                                    }
                                    final int channelIndex = channelIndex_tmp;
                                    
                                    if (checkForSkips && imagesToSkip[channelIndex] != 0) {
                                       imagesToSkip[channelIndex]--;
                                       continue;  // goes to next iteration of this loop without doing anything else
                                    }

                                    final int actualTimePoint;
                                    int actualFrameNr = 0;
                                    if (acqSimultSideA) {
                                       // somehow was slow writing/saving datasets when using both timepoint and channel axes
                                       // so for now at least fall back on filing all images from same camera into a single "dataset channel" even for different channels
                                       // another approach which I didn't try would be to create a separate acquisition for each camera and file images in the appropriate one
//                                       if (acqSettings.channelMode == MultichannelModes.Keys.SLICE_HW) {
//                                          actualTimePoint = cameraImageNr[cameraIndex] % acqSettings.numChannels;  // want modulo arithmetic
//                                          actualFrameNr = cameraImageNr[cameraIndex] / acqSettings.numChannels;    // want quotient only
//                                       } else {
                                          actualTimePoint = 0;
                                          actualFrameNr = cameraImageNr[cameraIndex];
//                                       }
                                    } else if (acqSettings.hardwareTimepoints) {
                                       actualTimePoint = tpNumber[channelIndex];
                                    } else if (acqSettings.separateTimepoints) {
                                       // if we are doing separate timepoints then frame is always 0
                                       actualTimePoint = 0;
                                    } else {
                                       actualTimePoint = timePoint;
                                    }
                                    // note that hardwareTimepoints and separateTimepoints can never both be true

                                    // add image to acquisition
                                    if (acqSimultSideA) {
                                       addImageToAcquisition(acq_,
                                             actualTimePoint, channelIndex,
                                             actualFrameNr, positionNum,
                                             now - acqStart, timg, bq);
                                        // ReportingUtils.logError("wrote image from camera index " + channelIndex + " to timepoint " + actualTimePoint + " in frame " + actualFrameNr);
                                    } else if (spimMode == AcquisitionModes.Keys.NO_SCAN && !acqSettings.separateTimepoints) {
                                       // create time series for no scan
                                       addImageToAcquisition(acq_,
                                             channelImageNr[channelIndex],
                                             channelIndex, actualTimePoint, 
                                             positionNum, now - acqStart, timg, bq);
                                    } else { // standard, create Z-stacks
                                       addImageToAcquisition(acq_,
                                             actualTimePoint, channelIndex,
                                             channelImageNr[channelIndex], positionNum,
                                             now - acqStart, timg, bq);
                                       
                                       // for troubleshooting record the images coming in
                                       ReportingUtils.logDebugMessage("added image: camera index " + channelIndex + " to timepoint " + actualTimePoint 
                                             + " in frame " + channelImageNr[channelIndex] + " in position " + positionNum
                                             + " with free capacity " + core_.getBufferFreeCapacity());
                                    }

                                    // update our counters to be ready for next image
                                    channelImageNr[channelIndex]++;
                                    cameraImageNr[cameraIndex]++;

                                    // for hardware timepoints then we only send one trigger and manually keep track of which channel/timepoint comes next
                                    // here we check to see if we moved onto next timepoint in hardware timepoints mode and if so take appropriate actions
                                    if (acqSettings.hardwareTimepoints
                                          && channelImageNr[channelIndex] >= acqSettings.numSlices) {  // only do this if we are done with the slices in this MM channel

                                       // we just finished filling one MM channel with all its slices so go to next timepoint for this channel
                                       channelImageNr[channelIndex] = 0;
                                       tpNumber[channelIndex]++;
                                       
                                       // determine whether to drop the next image
                                       // only happens for hardware timepoints and overlap camera mode
                                       if (checkForSkips) {
                                          // there is always one extra image per MM channel with hardware timepoints with overlap camera mode
                                          // obviously true for single channel and multi-channel with volume switching
                                          // for interleaved channels we have to provide numChannels * (numSlices + 1) triggers so we have more extra than we need
                                          imagesToSkip[channelIndex] = 1;
                                       }

                                       
                                       // Update acquisition status message for hardware acquisition
                                       //   (for non-hardware acquisition message is updated elsewhere)
                                       // Arbitrarily choose first (always present) channel to do this on.
                                       // At same time execute runnable if any (and hope that actual images from other channels arrive first)
                                       if (channelIndex == 0 && (numTimePointsDone_ < acqSettings.numTimepoints)) {
                                          numTimePointsDone_++;
                                          updateAcquisitionStatus(AcquisitionStatus.ACQUIRING);
                                          
                                          for (Runnable r : runnablesEndTimepoint_) {
                                             try {
                                                r.run();
                                             } catch(Exception ex) {
                                                MyDialogUtils.showError("end timepoint runnable threw exception: " + r.toString(), hideErrors);
                                                return AcquisitionStatus.FATAL_ERROR;
                                             }
                                          }

                                          for (Runnable r : runnablesStartTimepoint_) {
                                             try {
                                                r.run();
                                             } catch(Exception ex) {
                                                MyDialogUtils.showError("start timepoint runnable threw exception: " + r.toString(), hideErrors);
                                                return AcquisitionStatus.FATAL_ERROR;
                                             }
                                          }
                                       }
                                       
                                    }

                                    last = now;  // keep track of last image timestamp
                                    
                                    // add som extra per-image delay for demo cam
                                    if (usingDemoCam) {
                                       Thread.sleep((long)sliceTiming_.sliceDuration);
                                    }

                                 } else {  // no image ready yet
                                    done = cancelAcquisition_.get();
                                    Thread.sleep(1);
                                    if (now - last >= timeout2) {
                                       if (acqSimultSideA) {
                                          ReportingUtils.logError("First cam seq: " + ((cameraA1 != null) ? core_.isSequenceRunning(cameraA1) : "not used"));
                                          ReportingUtils.logError("Second cam seq: " + ((cameraA2 != null) ? core_.isSequenceRunning(cameraA2) : "not used"));
                                          ReportingUtils.logError("Third cam seq: " + ((cameraA3 != null) ? core_.isSequenceRunning(cameraA3) : "not used"));
                                          ReportingUtils.logError("Fourth cam seq: " + ((cameraA4 != null) ? core_.isSequenceRunning(cameraA4) : "not used"));
                                       } else {
                                          ReportingUtils.logError("First cam seq: " + core_.isSequenceRunning(firstCamera) +
                                                ((twoSided || acqBothCameras) ? ("   Second cam seq: " + core_.isSequenceRunning(secondCamera)) : "") +
                                                "   nrSlicesSoftware: " + nrSlicesSoftware +
                                                "   total images: " + totalImages);
                                       }
                                       ReportingUtils.logError("Camera did not send all expected images within" +
                                             " a reasonable period for timepoint " + numTimePointsDone_ + " and "
                                             + "position " + numPositionsDone_ + " .  Continuing anyway.");
                                       nonfatalError = true;
                                       done = true;
                                    }
                                 }
                              }

                              // update count if we stopped in the middle
                              if (cancelAcquisition_.get()) {
                                 numTimePointsDone_--;
                                 numPositionsDone_--;
                              }

                              // if we are using demo camera then add some extra time to let controller finish
                              if (usingDemoCam) {
                                 Thread.sleep(200);  // for serial communication overhead
                                 if (acqSettings.isStageScanning) {
                                    Thread.sleep(1000 + extraStageScanTimeout);  // extra 1 second plus ramp time for stage scanning 
                                 }
                              }

                           } catch (InterruptedException iex) {
                              MyDialogUtils.showError(iex, hideErrors);
                           }

                           if (acqSettings.hardwareTimepoints) {
                              break;  // only trigger controller once
                           }

                        } catch (Exception ex) {
                           MyDialogUtils.showError(ex, hideErrors);
                        } finally {
                           // cleanup at the end of each time we trigger the controller
                           // NB the acquisition is still open

                           ASIdiSPIM.getFrame().setHardwareInUse(false);

                           // put shutter back to original state
                           core_.setShutterOpen(shutterOpen);
                           core_.setAutoShutter(autoShutter);

                           // make sure cameras aren't running anymore
                           if (acqSimultSideA) {
                              if (core_.isSequenceRunning(cameraA1)) {
                                 core_.stopSequenceAcquisition(cameraA1);
                              }
                              if (camActiveA2) {
                                 if (core_.isSequenceRunning(cameraA2)) {
                                    core_.stopSequenceAcquisition(cameraA2);
                                 }
                              }
                              if (camActiveA3) {
                                 if (core_.isSequenceRunning(cameraA3)) {
                                    core_.stopSequenceAcquisition(cameraA3);
                                 }
                              }
                              if (camActiveA4) {
                                 if (core_.isSequenceRunning(cameraA4)) {
                                    core_.stopSequenceAcquisition(cameraA4);
                                 }
                              }
                           } else {
                              if (core_.isSequenceRunning(firstCamera)) {
                                 core_.stopSequenceAcquisition(firstCamera);
                              }
                              if ((twoSided || acqBothCameras) && core_.isSequenceRunning(secondCamera)) {
                                 core_.stopSequenceAcquisition(secondCamera);
                              }
                           }
                           
                           if (expectedNrImages != totalImages) {
                              ReportingUtils.logError("Expected " + expectedNrImages + " total, but only received " + totalImages);
                              nonfatalError = true;
                           }
                           
                           // cleanup planar correction move if any
                           if (devices_.isValidMMDevice(Devices.Keys.UPPERZDRIVE)) {
                              props_.setPropValue(Devices.Keys.UPPERZDRIVE, Properties.Keys.TTLINPUT_MODE, Properties.Values.TTLINPUT_MODE_NONE, true);
                              props_.setPropValue(Devices.Keys.UPPERZDRIVE, Properties.Keys.STAGESCAN_MOTOR_SPEED_Z, origZSpeed, true);
                           }
                           
                           // make sure SPIM state machine on micromirror and SCAN of XY card are stopped (should normally be but sanity check)
                           stopSPIMStateMachines(acqSettings);
                           
                           // write message with final results of our counters for debugging 
                           String msg = "Finished acquisition (controller trigger cycle) with counters at Channels: ";
                           for (int i=0; i<4*acqSettings.numChannels; ++i) {
                              msg += channelImageNr[i] + ", ";
                           }
                           msg += ",   Cameras: ";
                           for (int i=0; i<4; ++i) {
                              msg += cameraImageNr[i] + ", ";
                           }
                           msg += ",   TimePoints: ";
                           for (int i=0; i<2*acqSettings.numChannels; ++i) {
                              msg += tpNumber[i] + ", ";
                           }
                           ReportingUtils.logMessage(msg);
                        }// end finally cleanup statement
                     }// end loop over software channels
                  }// end outer loop of times we trigger controller
                  
                  // execute any end-position runnables
                  for (Runnable r : runnablesEndPosition_) {
                     try {
                        r.run();
                     } catch(Exception ex) {
                        MyDialogUtils.showError("end position runnable threw exception: " + r.toString(), hideErrors);
                        return AcquisitionStatus.FATAL_ERROR;
                     }
                  }
                  
                  if (acqSettings.useMovementCorrection && 
                          (timePoint % correctMovementEachNFrames) == 0) {
                     if (movementDetectors[positionNum] == null) {
                         // Transform from camera space to stage space:
                        Rotation rotation  = camBRotation;
                        if (firstSideA) {
                           rotation = camARotation;
                        }
                        movementDetectors[positionNum] = new MovementDetector(
                                prefs_, acq_, cmChannelNumber, positionNum, rotation);
                     }
                     
                     Vector3D movement = movementDetectors[positionNum].detectMovement(
                             Method.PhaseCorrelation);
                     
                     String msg1 = "TimePoint: " + timePoint + ", Detected movement.  X: " + movement.getX() +
                                ", Y: " + movement.getY() + ", Z: " + movement.getZ();
                     System.out.println(msg1);
                     
                     if (!movement.equals(zeroPoint)) {
                        String msg = "ASIdiSPIM motion corrector moving stages: X: " + movement.getX() +
                                ", Y: " + movement.getY() + ", Z: " + movement.getZ();
                        gui_.logMessage(msg);
                        System.out.println(msg);

                        // if we are using the position list, update the position in the list
                        if (acqSettings.useMultiPositions) {
                           MultiStagePosition position = positionList.getPosition(positionNum);
                           StagePosition pos = position.get(devices_.getMMDevice(Devices.Keys.XYSTAGE));
                           pos.x += movement.getX();
                           pos.y += movement.getY();
                           StagePosition zPos = position.get(devices_.getMMDevice(Devices.Keys.UPPERZDRIVE));
                           if (zPos != null) {
                              zPos.x += movement.getZ();
                           }
                        } else {
                           // only a single position, move the stage now
                           core_.setRelativeXYPosition(devices_.getMMDevice(Devices.Keys.XYSTAGE),
                                   movement.getX(), movement.getY());
                           core_.setRelativePosition(devices_.getMMDevice(Devices.Keys.UPPERZDRIVE),
                                   movement.getZ());
                        }

                     }
                  }
               }//end loop over positions
               
               // execute any end-timepoint runnables
               for (Runnable r : runnablesEndTimepoint_) {
                  try {
                     r.run();
                  } catch(Exception ex) {
                     MyDialogUtils.showError("end timepoint runnable threw exception: " + r.toString(), hideErrors);
                     return AcquisitionStatus.FATAL_ERROR;
                  }
               }
               
               if (acqSettings.hardwareTimepoints) {
                  break;
               }
            }
         } catch (IllegalMonitorStateException ex) {
            // do nothing, the acquisition was simply halted during its operation
            // will log error message during finally clause
         } catch (MMScriptException mex) {
            MyDialogUtils.showError(mex, hideErrors);
         } catch (Exception ex) {
            MyDialogUtils.showError(ex, hideErrors);
         } finally {  // end of this acquisition (could be about to restart if separate viewers)
            try {
               // restore original window listeners
               try {
                  vad.getImagePlus().getWindow().removeWindowListener(wl_acq);
                  for (WindowListener l : wls_orig) {
                     vad.getImagePlus().getWindow().addWindowListener(l);
                  }
               } catch (Exception ex) {
                  // do nothing, window is probably gone
               }
               
               if (cancelAcquisition_.get()) {
                  ReportingUtils.logMessage("User stopped the acquisition");
               }
               
               bq.put(TaggedImageQueue.POISON);
               // TODO: evaluate closeAcquisition call
               // at the moment, the Micro-Manager api has a bug that causes 
               // a closed acquisition not be really closed, causing problems
               // when the user closes a window of the previous acquisition
               // changed r14705 (2014-11-24)
               // gui_.closeAcquisition(acqName);
               ReportingUtils.logMessage("diSPIM plugin acquisition " + acqName_ + 
                     " took: " + (System.currentTimeMillis() - acqButtonStart) + "ms");
               
               // dump errors again
               props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "1 DU Y");
               props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "2 DU Y");
               props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "3 DU Y");
               props_.setPropValue(Devices.Keys.TIGERCOMM, Properties.Keys.SERIAL_COMMAND, "6 DU Y");
               
               waitForBlockingQueue(imageCache);
               
//               while(gui_.isAcquisitionRunning()) {
//            	   Thread.sleep(10);
//            	   ReportingUtils.logMessage("waiting for acquisition to finish.");
//               }
               
               // flag that we are done with acquisition
               acquisitionRunning_.set(false);
               
               // write acquisition settings if requested
               if (lastAcquisitionPath_!=null && prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_WRITE_ACQ_SETTINGS_FILE, false)) {
                  String path = "";
                  try {
                     path = lastAcquisitionPath_ + File.separator + "AcqSettings.txt";
                     PrintWriter writer = new PrintWriter(path);
                     writer.println(acqSettingsJSON);
                     writer.flush();
                     writer.close();
                  } catch (Exception ex) {
                     MyDialogUtils.showError(ex, "Could not save acquisition settings to file as requested to path " + path, hideErrors);
                  }
               }
               
            } catch (Exception ex) {
               // exception while stopping sequence acquisition, not sure what to do...
               MyDialogUtils.showError(ex, "Problem while finishing acquisition", hideErrors);
            }
         }

      }// for loop over acquisitions
      
      // cleanup after end of all acquisitions
      
      // TODO be more careful and always do these if we actually started acquisition, 
      // even if exception happened
      
      if (acqSimultSideA) {
         if (cameraA1 != null) {
            cameras_.setCameraForAcquisition(Devices.Keys.CAMERA_A1, false);
         }
         if (cameraA2 != null) {
            cameras_.setCameraForAcquisition(Devices.Keys.CAMERA_A2, false);
         }
         if (cameraA3 != null) {
            cameras_.setCameraForAcquisition(Devices.Keys.CAMERA_A3, false);
         }
         if (cameraA4 != null) {
            cameras_.setCameraForAcquisition(Devices.Keys.CAMERA_A4, false);
         }
      } else {
         cameras_.setCameraForAcquisition(firstCameraKey, false);
         if (twoSided || acqBothCameras) {
            cameras_.setCameraForAcquisition(secondCameraKey, false);
         }
      }
      
      // restore exposure times of SPIM cameras
      try {
         if (acqSimultSideA) {
            core_.setExposure(cameraA1, prefs_.getFloat(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FIRST.toString(), 10f));
            if (camActiveA2) {
               core_.setExposure(cameraA2, prefs_.getFloat(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_SECOND.toString(), 10f));
            }
            if (camActiveA3) {
               core_.setExposure(cameraA3, prefs_.getFloat(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_THIRD.toString(), 10f));
            }
            if (camActiveA4) {
               core_.setExposure(cameraA4, prefs_.getFloat(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FOURTH.toString(), 10f));
            }
         } else {
            core_.setExposure(firstCamera, prefs_.getFloat(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_FIRST.toString(), 10f));
            if (twoSided || acqBothCameras) {
               core_.setExposure(secondCamera, prefs_.getFloat(MyStrings.PanelNames.SETTINGS.toString(),
                     Properties.Keys.PLUGIN_CAMERA_LIVE_EXPOSURE_SECOND.toString(), 10f));
            }
         }
         gui_.refreshGUIFromCache();
      } catch (Exception ex) {
         MyDialogUtils.showError("Could not restore exposure after acquisition", hideErrors);
      }
      
      // reset channel to original if we clobbered it
      if (acqSettings.useChannels) {
         multiChannelPanel_.setConfig(originalChannelConfig);
      }
      
      // clean up controller settings after acquisition
      // want to do this, even with demo cameras, so we can test everything else
      // TODO figure out if we really want to return piezos to 0 position (maybe center position,
      //   maybe not at all since we move when we switch to setup tab, something else??)
      // importantly do not move the piezo for SCOPE
      controller_.cleanUpControllerAfterAcquisition(acqSettings, (ASIdiSPIM.SCOPE ? false : true) );
      
      // if we did stage scanning restore its position and speed
      if (acqSettings.isStageScanning) {
         // check if we are raising the SPIM head to the load position between every acquisition
         final boolean returnToOriginalPosition = prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(), 
                    Properties.Keys.PLUGIN_RETURN_TO_ORIGINAL_POSITION_AFTER_STAGESCAN, false);

         try {
            // make sure stage scanning state machine is stopped, otherwise setting speed/position won't take
            props_.setPropValue(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_STATE,
                  Properties.Values.SPIM_IDLE);
            props_.setPropValue(Devices.Keys.XYSTAGE,
                  Properties.Keys.STAGESCAN_MOTOR_SPEED_X, origXSpeed);
            props_.setPropValue(Devices.Keys.XYSTAGE,
                  Properties.Keys.STAGESCAN_MOTOR_ACCEL_X, origXAccel);
         } catch (Exception ex) {
            MyDialogUtils.showError("Could not restore XY stage settings after acquisition", hideErrors);
         }
         
         if (returnToOriginalPosition) {
            try {
                core_.setXYPosition(devices_.getMMDevice(Devices.Keys.XYSTAGE), xyPosUm.x, xyPosUm.y);  
            } catch (Exception ex) {
                MyDialogUtils.showError("Could not restore XY stage position after acquisition", hideErrors);
            }
         }
      }
      
      if (acqSettings.isStageStepping) {
         positions_.setPosition(Devices.Keys.SUPPLEMENTAL_X, xSupPosUm);
      }
      
      if (acquisitionStatus_ != AcquisitionStatus.FATAL_ERROR) {
         updateAcquisitionStatus(AcquisitionStatus.DONE);
      }
      posUpdater_.pauseUpdates(false);
      if (testAcq && prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(),
            Properties.Keys.PLUGIN_TESTACQ_SAVE, false)) {
         String path = "";
         try {
            path = prefs_.getString(MyStrings.PanelNames.SETTINGS.toString(),
                  Properties.Keys.PLUGIN_TESTACQ_PATH, "");
            IJ.saveAs(acq_.getAcquisitionWindow().getImagePlus(), "raw", path);
            // TODO consider generating a short metadata file to assist in interpretation
         } catch (Exception ex) {
            MyDialogUtils.showError("Could not save raw data from test acquisition to path " + path, hideErrors);
         }
      }
      
      if (separateImageFilesOriginally) {
         ImageUtils.setImageStorageClass(TaggedImageStorageDiskDefault.class);
      }
      
      // restore camera
      try {
         core_.setCameraDevice(originalCamera);
      } catch (Exception ex) {
         MyDialogUtils.showError("Could not restore camera after acquisition", hideErrors);
      }
      
      if (liveModeOriginally) {
         gui_.enableLiveMode(true);
      }
      
      if (nonfatalError) {
         acquisitionStatus_ = AcquisitionStatus.NON_FATAL_ERROR;
         MyDialogUtils.showError("Missed some images during acquisition, see core log for details", hideErrors);
      }
      
      extraChannelOffset_ = 0.0;
      
      // execute any end-acquisition runnables
      for (Runnable r : runnablesEndAcquisition_) {
         try {
            r.run();
         } catch(Exception ex) {
            MyDialogUtils.showError("end acquisition runnable threw exception: " + r.toString(), hideErrors);
            return AcquisitionStatus.FATAL_ERROR;
         }
      }

      return AcquisitionStatus.DONE;  // TODO should we return acquisitionStatus_ instead?
   }
   
   /**
    * Checks that a camera is selected and can be used in the currently-selected triggering mode.
    * @param camKey
    * @return false if there is a problem => should abort acquisition
    */
   private boolean verifyCamera(Devices.Keys camKey) {
      if (!devices_.isValidMMDevice(camKey)) {
         MyDialogUtils.showError("Trying to use device " + camKey.toString() + " but no camera specified for that side.");
         return false;
      }
      Devices.Libraries camLib = devices_.getMMDeviceLibrary(camKey);
      if (!CameraModes.getValidModeKeys(camLib).contains(getSPIMCameraMode())) {
         MyDialogUtils.showError("Camera trigger mode set to " + getSPIMCameraMode().toString() + " but camera A doesn't support it.");
         return false;
      }
      // Hamamatsu only supports light sheet mode with CameraLink cameras.  It seems due to static architecture of getValidModeKeys
      //   there is no good way to tell earlier that light sheet mode isn't supported.  I don't like this but don't see another option.
      if (camLib == Devices.Libraries.HAMCAM && props_.getPropValueString(camKey, Properties.Keys.CAMERA_BUS).equals(Properties.Values.USB3)) {
         if (getSPIMCameraMode() == CameraModes.Keys.LIGHT_SHEET) {
            MyDialogUtils.showError("Hamamatsu only supports light sheet mode with CameraLink readout.");
            return false;
         }
      }
      return true;
   }
   
   /**
    * Make sure SPIM state machines are stopped, will wait for stage scan to finish
    * @param acqSettings
    * @throws Exception
    */
   private void stopSPIMStateMachines(AcquisitionSettings acqSettings) throws Exception {
      
      // stop micro-mirror cards
      if ((acqSettings.numSides > 1) || acqSettings.firstSideIsA) {
         props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SPIM_STATE,
               Properties.Values.SPIM_IDLE, true);
      }
      if ((acqSettings.numSides > 1) || !acqSettings.firstSideIsA) {
         props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SPIM_STATE,
               Properties.Values.SPIM_IDLE, true);
      }
      
      if (acqSettings.isStageScanning) {
         // give the stage scan 5 seconds to clean itself up, after which we stop it
         // once all images come in there is still a time when stage is moving back to its start/center position
         final int timeoutStageScanCleanupMs = 5000;
         final long deadline = System.currentTimeMillis() + timeoutStageScanCleanupMs;
         boolean done = false;
         while (!done) {
            done = (props_.getPropValueStringForceRefresh(Devices.Keys.XYSTAGE,
                  Properties.Keys.STAGESCAN_STATE)).equals(Properties.Values.SPIM_IDLE.toString());            
            if (!done && (System.currentTimeMillis() > deadline)) {
               // force-set to idle
               props_.setPropValue(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_STATE,
                     Properties.Values.SPIM_IDLE);
               ReportingUtils.logError("Force-set XY stage scan to IDLE state with stage speed "
                     + props_.getPropValueFloat(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_MOTOR_SPEED_X) + ".");
               done = true;
            }
            if (!done) {
               Thread.sleep(25);
               positions_.getUpdatedPosition(Devices.Keys.XYSTAGE, Directions.X);  // query position to debug
            }
         }
      }
   }
   
   /**
    * Waits for imageCache to be marked finished which means file saving is done.
    * Times out after 5 seconds of waiting.
    * @param cache
    * @throws Exception
    */
   private void waitForBlockingQueue(ImageCache cache) throws Exception {
      final int timeoutBlockingQueue = 35000;
      final long deadline = System.currentTimeMillis() + timeoutBlockingQueue; 
      boolean done = false;
      while (!done) {
         done = cache.isFinished();
         done = done || (System.currentTimeMillis() > deadline);
         Thread.sleep(25);
      }
   }

   /**
    * Called whenever position updater has refreshed positions, even when this tab isn't active.
    */
   @Override
   public final void updateStagePositions() {
      double xPos = positions_.getCachedPosition(Devices.Keys.XYSTAGE, Directions.X);
      double yPos = positions_.getCachedPosition(Devices.Keys.XYSTAGE, Directions.Y);
      // update overview display
      try {
         Rectangle camROI = core_.getROI();
         int width = (int) Math.round(camROI.width*overviewCompressX_/overviewDownsampleY_);
         int height =(int) Math.round(camROI.height/overviewDownsampleY_);
         int x = (int) Math.round((overviewMax_.x - xPos) / overviewPixelSize_ + overviewWidthExpansion_/2 - width/2);
         int y = (int) Math.round((yPos - overviewMin_.y) / overviewPixelSize_);
         overviewIP_.setRoi(x, y, width, height);
      } catch (Exception ex) {
         // do nothing
      }
      // update Z position for planar correction
      controller_.setPlanarZ(xPos, yPos);
      gridPanel_.updateStagePositions();
   }

   @Override
   public void saveSettings() {
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
      if (devices_.isDifferentPLogic()) {
         props_.setPropValue(Devices.Keys.PLOGIC_LASER, Properties.Keys.SAVE_CARD_SETTINGS,
               Properties.Values.DO_SSZ, true);
      }
   }

   /**
    * Gets called when this tab gets focus. Refreshes values from properties.
    */
   @Override
   public void gotSelected() {
      posUpdater_.pauseUpdates(true);
      props_.callListeners();
      // old joystick associations were cleared when leaving
      //   last tab so only do it if joystick settings need to be applied
      if (navigationJoysticksCB_.isSelected()) {
         updateJoysticks();
      }
      sliceFrameAdvanced_.setVisible(advancedSliceTimingCB_.isSelected());
      posUpdater_.pauseUpdates(false);
   }

   /**
    * called when tab looses focus.
    */
   @Override
   public void gotDeSelected() {
      // if we have been using navigation panel's joysticks need to unset them
      if (navigationJoysticksCB_.isSelected()) {
         if (ASIdiSPIM.getFrame() != null) {
            ASIdiSPIM.getFrame().getNavigationPanel().doJoystickSettings(false);
         }
      }
      sliceFrameAdvanced_.setVisible(false);
   }

   @Override
   public void devicesChangedAlert() {
      devices_.callListeners();
   }
   
   /**
    * Gets called when enclosing window closes
    */
   @Override
   public void windowClosing() {
      if (acquisitionRequested_.get()) {
         cancelAcquisition_.set(true);
         while (acquisitionRunning_.get()) {
            // spin wheels until we are done
         }
      }
      // close dependent windows
      sliceFrameAdvanced_.savePosition();
      sliceFrameAdvanced_.dispose();
      gridFrame_.savePosition();
      gridPanel_.windowClosing();
      gridFrame_.dispose();
   }
   
   @Override
   public void refreshDisplay() {
      updateDurationLabels();
   }
   
   @Override
   // Used to re-layout portion of window depending when camera mode changes, in
   //   particular light sheet mode needs different set of controls.
   public void cameraModeChange() {
      CameraModes.Keys key = getSPIMCameraMode();
      slicePanelContainer_.removeAll();
      slicePanelContainer_.add((key == CameraModes.Keys.LIGHT_SHEET) ?
         lightSheetPanel_ : normalPanel_, "growx");
      slicePanelContainer_.revalidate();
      slicePanelContainer_.repaint();
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
    * @param acq - MMAcquisition object to use (old way used acquisition name and then
    *  had to call deprecated function on every call, now just pass acquisition object
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
   private void addImageToAcquisition(MMAcquisition acq,
           int frame,
           int channel,
           int slice,
           int position,
           long ms,
           TaggedImage taggedImg,
           BlockingQueue<TaggedImage> bq) throws MMScriptException, InterruptedException {

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
         MDUtils.setChannelName(tags, channelNames_[channel]);
         MDUtils.setSliceIndex(tags, slice);
         MDUtils.setPositionIndex(tags, position);
         MDUtils.setElapsedTimeMs(tags, ms);
         MDUtils.setImageTime(tags, MDUtils.getCurrentTime());
         MDUtils.setZStepUm(tags, zStepUm_);
         
         // save cached positions of SPIM head for this stack
         tags.put("SPIM_Position_X", xPositionUm_);  // TODO consider computing accurate X position per slice for stage scanning data
         tags.put("SPIM_Position_Y", yPositionUm_);
         tags.put("SPIM_Position_Z", zPositionUm_);  // NB this is SPIM head position, not position in stack
  
         // these lines should no longer be required
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
   
   /**
    * Gets position of sample in XYZ, being XY stage and SPIM head. Used to mark metadata with center position
    * that gets updated with multiple positions. 
    */
   private void refreshXYZPositions() {
      xPositionUm_ = positions_.getUpdatedPosition(Devices.Keys.XYSTAGE, Directions.X);  // / will update cache for Y too
      yPositionUm_ = positions_.getCachedPosition(Devices.Keys.XYSTAGE, Directions.Y);
      zPositionUm_ = positions_.getUpdatedPosition(Devices.Keys.UPPERZDRIVE);
   }
   
   public JCheckBox getMultiplePositionsCheckBox() {
       return usePositionsCB_;
   }
   
   public void updateCheckBox(final JCheckBox checkBox, final boolean state) {
       if (state != checkBox.isSelected()) {
           checkBox.doClick();
       }
   }
   
   /***************** API  *******************/
   
   
   /**
    * @return true if an acquisition is currently underway
    *   (e.g. all checks passed, controller set up, MM acquisition object created, etc.)
    */
   public boolean isAcquisitionRunning() {
      return acquisitionRunning_.get();
   }
   
   /**
    * @return true if an acquisition has been requested by user.  Will
    *   also return true if acquisition is running.
    */
   public boolean isAcquisitionRequested() {
      return acquisitionRequested_.get();
   }
   
   /**
    * Stops the acquisition by setting an Atomic boolean indicating that we should
    *   halt.  Does nothing if an acquisition isn't running.
    */
   public void stopAcquisition() {
      if (isAcquisitionRequested()) {
         cancelAcquisition_.set(true);
      }
   }
   
   public AcquisitionStatus getAcquisitionStatus() {
      return acquisitionStatus_;
   }
   
   /**
    * @return pathname on filesystem to last completed acquisition
    *   (even if it was stopped pre-maturely).  Null if not saved to disk.
    */
   public String getLastAcquisitionPath() {
      return lastAcquisitionPath_;
   }

   public String getLastAcquisitionName() {
      return lastAcquisitionName_;
   }
   
   public ij.ImagePlus getLastAcquisitionImagePlus() throws ASIdiSPIMException {
      try {
         return gui_.getAcquisition(lastAcquisitionName_).getAcquisitionWindow().getImagePlus();
      } catch (MMScriptException e) {
         throw new ASIdiSPIMException(e);
      }
   }
   
   public String getSavingDirectoryRoot() {
      return rootField_.getText();
   }

   public void setSavingDirectoryRoot(String directory) throws ASIdiSPIMException {
      rootField_.setText(directory);
      try {
         rootField_.commitEdit();
      } catch (ParseException e) {
         throw new ASIdiSPIMException(e);
      }
   }

   public String getSavingNamePrefix() {
      return prefixField_.getText();
   }

   public void setSavingNamePrefix(String acqPrefix) throws ASIdiSPIMException {
      prefixField_.setText(acqPrefix);
      try {
         prefixField_.commitEdit();
      } catch (ParseException e) {
         throw new ASIdiSPIMException(e);
      }
   }

   public boolean getSavingSeparateFile() {
      return separateTimePointsCB_.isSelected();
   }

   public void setSavingSeparateFile(boolean separate) {
      separateTimePointsCB_.setSelected(separate);
   }

   public boolean getSavingSaveWhileAcquiring() {
      return saveCB_.isSelected();
   }

   public void setSavingSaveWhileAcquiring(boolean save) {
      saveCB_.setSelected(save);
   }

   public org.micromanager.asidispim.Data.AcquisitionModes.Keys getAcquisitionMode() {
      return (org.micromanager.asidispim.Data.AcquisitionModes.Keys) spimMode_.getSelectedItem();
   }

   public void setAcquisitionMode(org.micromanager.asidispim.Data.AcquisitionModes.Keys mode) {
      spimMode_.setSelectedItem(mode);
   }

   public boolean getTimepointsEnabled() {
      return useTimepointsCB_.isSelected();
   }

   public void setTimepointsEnabled(boolean enabled) {
      useTimepointsCB_.setSelected(enabled);
   }

   public int getNumberOfTimepoints() {
      return (Integer) numTimepoints_.getValue();
   }

   public void setNumberOfTimepoints(int numTimepoints) throws ASIdiSPIMException {
      if (MyNumberUtils.outsideRange(numTimepoints, 1, 100000)) {
         throw new ASIdiSPIMException("illegal value for number of time points");
      }
      numTimepoints_.setValue(numTimepoints);
   }
   
   // getTimepointInterval already existed

   public void setTimepointInterval(double intervalTimepoints) throws ASIdiSPIMException {
      if (MyNumberUtils.outsideRange(intervalTimepoints,  0.1, 32000)) {
         throw new ASIdiSPIMException("illegal value for time point interval");
      }
      acquisitionInterval_.setValue(intervalTimepoints);
   }

   public boolean getMultiplePositionsEnabled() {
      return usePositionsCB_.isSelected();
   }

   public void setMultiplePositionsEnabled(boolean enabled) {
      usePositionsCB_.setSelected(enabled);
   }

   public double getMultiplePositionsPostMoveDelay() {
      return PanelUtils.getSpinnerFloatValue(positionDelay_);
   }

   public void setMultiplePositionsDelay(double delayMs) throws ASIdiSPIMException {
      if (MyNumberUtils.outsideRange(delayMs, 0d, 10000d)) {
         throw new ASIdiSPIMException("illegal value for post move delay");
      }
      positionDelay_.setValue(delayMs);
   }

   public boolean getChannelsEnabled() {
      return multiChannelPanel_.isMultiChannel();
   }

   public void setChannelsEnabled(boolean enabled) {
      multiChannelPanel_.setPanelEnabled(enabled);
   }

   public String[] getAvailableChannelGroups() {
      return multiChannelPanel_.getAvailableGroups();
   }
   
   public String getChannelGroup() {
      return multiChannelPanel_.getChannelGroup();
   }

   public void setChannelGroup(String channelGroup) {
      String[] availableGroups = getAvailableChannelGroups();
      for (String group : availableGroups) {
         if (group.equals(channelGroup)) {
            multiChannelPanel_.setChannelGroup(channelGroup);
         }
      }
   }

   public String[] getAvailableChannels() {
      return multiChannelPanel_.getAvailableChannels();
   }

   public boolean getChannelEnabled(String channel) {
      ChannelSpec[] usedChannels = multiChannelPanel_.getUsedChannels();
      for (ChannelSpec spec : usedChannels) {
         if (spec.config_.equals(channel)) {
            return true;
         }
      }
      return false;
   }

   public void setChannelEnabled(String channel, boolean enabled) {
     multiChannelPanel_.setChannelEnabled(channel, enabled);
   }

   public double getChannelOffset(String channel) {
      return multiChannelPanel_.getChannelOffset(channel);
   }
   
   public double getChannelOffset() {
      try {
         final String currentChannelConfig = gui_.getMMCore().getCurrentConfigFromCache(multiChannelPanel_.getChannelGroup());
         return getChannelOffset(currentChannelConfig);
      } catch (Exception ex) {
         return 0.0;
      }
   }
   
   public org.micromanager.asidispim.Data.MultichannelModes.Keys getChannelChangeMode() {
      return multiChannelPanel_.getChannelMode();
   }
   
   public void setChannelChangeMode(org.micromanager.asidispim.Data.MultichannelModes.Keys mode) {
      multiChannelPanel_.setChannelMode(mode);
   }

   // getNumSides() already existed
   
   public void setVolumeNumberOfSides(int numSides) {
      if (numSides == 2) {
         numSides_.setSelectedIndex(1);
      } else {
         numSides_.setSelectedIndex(0);
      }
   }

   public void setFirstSideIsA(boolean firstSideIsA) {
      if (firstSideIsA) {
         firstSide_.setSelectedIndex(0);
      } else {
         firstSide_.setSelectedIndex(1);
      }
   }

   public double getVolumeDelayBeforeSide() {
      return PanelUtils.getSpinnerFloatValue(delaySide_);
   }

   public void setVolumeDelayBeforeSide(double delayMs) throws ASIdiSPIMException {
      if (MyNumberUtils.outsideRange(delayMs, 0d, 10000d)) {
         throw new ASIdiSPIMException("illegal value for delay before side");
      }
      delaySide_.setValue(delayMs);
   }

   public int getVolumeSlicesPerVolume() {
      return (Integer) numSlices_.getValue();
   }

   public void setVolumeSlicesPerVolume(int slices) throws ASIdiSPIMException {
      if (MyNumberUtils.outsideRange(slices, 1, 65000)) {
         throw new ASIdiSPIMException("illegal value for number of slices");
      }
      numSlices_.setValue(slices);
   }

   public double getVolumeSliceStepSize() {
      return PanelUtils.getSpinnerFloatValue(stepSize_);
   }

   public void setVolumeSliceStepSize(double stepSizeUm) throws ASIdiSPIMException {
      if (MyNumberUtils.outsideRange(stepSizeUm, 0d, 100d)) {
         throw new ASIdiSPIMException("illegal value for slice step size");
      }
      stepSize_.setValue(stepSizeUm);
   }

   public boolean getVolumeMinimizeSlicePeriod() {
      return minSlicePeriodCB_.isSelected();
   }

   public void setVolumeMinimizeSlicePeriod(boolean minimize) {
      minSlicePeriodCB_.setSelected(minimize);
   }

   public double getVolumeSlicePeriod() {
      return PanelUtils.getSpinnerFloatValue(desiredSlicePeriod_);
   }

   public void setVolumeSlicePeriod(double periodMs) throws ASIdiSPIMException {
      if (MyNumberUtils.outsideRange(periodMs, 1d, 1000d)) {
         throw new ASIdiSPIMException("illegal value for slice period");
      }
      desiredSlicePeriod_.setValue(periodMs);      
   }

   public double getVolumeSampleExposure() {
      return PanelUtils.getSpinnerFloatValue(desiredLightExposure_);
   }

   public void setVolumeSampleExposure(double exposureMs) throws ASIdiSPIMException {
      if (MyNumberUtils.outsideRange(exposureMs, 1.0, 1000.0)) {
         throw new ASIdiSPIMException("illegal value for sample exposure");
      }
      desiredLightExposure_.setValue(exposureMs);        
   }

   public boolean getAutofocusDuringAcquisition() {
      return useAutofocusCB_.isSelected();
   }

   public void setAutofocusDuringAcquisition(boolean enable) {
      useAutofocusCB_.setSelected(enable);
   }

   public double getEstimatedSliceDuration() {
      return sliceTiming_.sliceDuration;
   }

   public double getEstimatedVolumeDuration() {
      return computeActualVolumeDuration();
   }

   public double getEstimatedAcquisitionDuration() {
      return computeActualTimeLapseDuration();
   }

   public void attachRunnable(Runnable runnable, RunnableType type) {
      switch (type) {
      case ACQUISITION_START:
         runnablesStartAcquisition_.add(runnable);
         break;
      case ACQUISITION_END:
         runnablesEndAcquisition_.add(runnable);
         break;
      case TIMEPOINT_START:
         runnablesStartTimepoint_.add(runnable);
         break;
      case TIMEPOINT_END:
         runnablesEndTimepoint_.add(runnable);
         break;
      case POSITION_START:
         runnablesStartPosition_.add(runnable);
         break;
      case POSITION_END:
         runnablesEndPosition_.add(runnable);
         break;
      default:
         break;
      }
   }

   public void clearAllRunnables() {
      runnablesStartAcquisition_.clear();
      runnablesEndAcquisition_.clear();
      runnablesStartTimepoint_.clear();
      runnablesEndTimepoint_.clear();
      runnablesStartPosition_.clear();
      runnablesEndPosition_.clear();
   }
   
   public String getAcqName() {
	   return acqName_;
   }
}
