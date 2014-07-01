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


import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.asidispim.Utils.StagePositionUpdater;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;

import net.miginfocom.swing.MigLayout;

import org.json.JSONException;
import org.json.JSONObject;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.micromanager.api.ScriptInterface;
import org.micromanager.api.ImageCache;
import org.micromanager.api.MMTags;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.DefaultTaggedImageSink;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.imageDisplay.VirtualAcquisitionDisplay;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.MMScriptException;

/**
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
   private final CMMCore core_;
   private final ScriptInterface gui_;
   private final JSpinner numSlices_;
   private final JSpinner numSides_;
   private final JComboBox firstSide_;
   private final JSpinner numScansPerSlice_;
   private final JSpinner lineScanPeriod_;
   private final JSpinner delayScan_;
   private final JSpinner delayLaser_;
   private final JSpinner delayCamera_;
   private final JSpinner durationLaser_;
   private final JSpinner delaySide_;
   private final JSpinner numAcquisitions_;
   private final JSpinner acquisitionInterval_;
   private final JButton buttonStart_;
   private final JButton buttonStop_;
   private final JPanel volPanel_;
   private final JPanel slicePanel_;
   private final JPanel repeatPanel_;
   private final JPanel savePanel_;
   private final JTextField rootField_;
   private final JTextField nameField_;
   private final JLabel numTimePointsDoneLabel_;
   private int numTimePointsDone_;
   private final JLabel nextTimePointLabel_;
   private AtomicBoolean stop_ = new AtomicBoolean(false);
   private final StagePositionUpdater stagePosUpdater_;
   private final JFormattedTextField exposureField_;
   private final JFormattedTextField stepSizeField_;
   private final JCheckBox separateTimePointsCB_;
   private final JCheckBox saveCB_;
   
   //private static final String ZSTEPTAG = "z-step_um";
   private static final String ELAPSEDTIME = "ElapsedTime-ms";

   public AcquisitionPanel(ScriptInterface gui, 
           Devices devices, 
           Properties props, 
           Cameras cameras, 
           Prefs prefs, 
           StagePositionUpdater stagePosUpdater) {
      super("Acquisition",
              new MigLayout(
              "",
              "[right]16[center]16[center]16[center]16[center]",
              "[]8[]"));
      gui_ = gui;
      devices_ = devices;
      props_ = props;
      cameras_ = cameras;
      prefs_ = prefs;
      stagePosUpdater_ = stagePosUpdater;
      core_ = gui_.getMMCore();
      numTimePointsDone_ = 0;

      PanelUtils pu = new PanelUtils(gui_, prefs_);
           
      
      // start volume sub-panel

      volPanel_ = new JPanel(new MigLayout(
              "",
              "[right]16[center]",
              "[]8[]"));

      volPanel_.setBorder(PanelUtils.makeTitledBorder("Volume Settings"));

      volPanel_.add(new JLabel("Number of sides:"));
      numSides_ = pu.makeSpinnerInteger(1, 2, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_NUM_SIDES);
      volPanel_.add(numSides_, "wrap");

      volPanel_.add(new JLabel("First side:"));
      String[] ab = {Devices.Sides.A.toString(), Devices.Sides.B.toString()};
      firstSide_ = pu.makeDropDownBox(ab, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_FIRSTSIDE);
      volPanel_.add(firstSide_, "wrap");

      volPanel_.add(new JLabel("Number of slices per volume:"));
      numSlices_ = pu.makeSpinnerInteger(1, 1000, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_NUM_SLICES);
      volPanel_.add(numSlices_, "wrap");
      
      volPanel_.add(new JLabel("Stepsize [um]:"));
      stepSizeField_ = pu.makeFloatEntryField(panelName_, "StepSize", 1.0, 6);
      volPanel_.add(stepSizeField_, "wrap");

      volPanel_.add(new JLabel("Delay before each side (ms):"));
      delaySide_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_DELAY_SIDE);
      volPanel_.add(delaySide_, "wrap");

      // end volume sub-panel
      
      
      // start slice sub-panel

      slicePanel_ = new JPanel(new MigLayout(
              "",
              "[right]16[center]",
              "[]8[]"));

      slicePanel_.setBorder(PanelUtils.makeTitledBorder("Slice Settings"));

      slicePanel_.add(new JLabel("Delay before starting scan (ms):"));
      delayScan_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_SCAN);
      slicePanel_.add(delayScan_, "wrap");

      slicePanel_.add(new JLabel("Lines scans per slice:"));
      numScansPerSlice_ = pu.makeSpinnerInteger(1, 1000, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_NUM_SCANSPERSLICE);
      slicePanel_.add(numScansPerSlice_, "wrap");

      slicePanel_.add(new JLabel("Line scan period (ms):"));
      lineScanPeriod_ = pu.makeSpinnerInteger(1, 10000, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_LINESCAN_PERIOD);
      slicePanel_.add(lineScanPeriod_, "wrap");
      
      slicePanel_.add(new JSeparator(), "span 2, wrap");
      
      slicePanel_.add(new JLabel("Delay before laser (ms):"));
      delayLaser_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_LASER);
      slicePanel_.add(delayLaser_, "wrap");
      
      slicePanel_.add(new JLabel("Laser duration (ms):"));
      durationLaser_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DURATION_LASER);
      slicePanel_.add(durationLaser_, "span 2, wrap");
      
      slicePanel_.add(new JSeparator(), "wrap");

      slicePanel_.add(new JLabel("Delay before camera (ms):"));
      delayCamera_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_,
            new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_CAMERA);
      slicePanel_.add(delayCamera_, "wrap");
      
      slicePanel_.add(new JLabel("Exposure (ms)"));
      exposureField_ = new JFormattedTextField();
      try {
         exposureField_.setValue((Double) core_.getExposure());
      } catch (Exception ex) {
         gui_.showError(ex, "Error getting exposure time from core");
      }
      exposureField_.setColumns(6);
      slicePanel_.add(exposureField_, "wrap");

      // end slice sub-panel
      

      // start repeat sub-panel

      repeatPanel_ = new JPanel(new MigLayout(
              "",
              "[right]16[center]",
              "[]8[]"));

      repeatPanel_.setBorder(PanelUtils.makeTitledBorder("Repeat Settings"));

      repeatPanel_.add(new JLabel("Time points:"));
      // create plugin "property" for number of acquisitions
      props_.setPropValue(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_NUM_ACQUISITIONS,
              prefs_.getInt(panelName_, Properties.Keys.PLUGIN_NUM_ACQUISITIONS, 1));
      numAcquisitions_ = pu.makeSpinnerInteger(1, 32000, props_, devices_,
              new Devices.Keys[]{Devices.Keys.PLUGIN},
              Properties.Keys.PLUGIN_NUM_ACQUISITIONS);
      repeatPanel_.add(numAcquisitions_, "wrap");

      repeatPanel_.add(new JLabel("Interval (s):"));
      // create plugin "property" for acquisition interval
      props_.setPropValue(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_ACQUISITION_INTERVAL,
              prefs_.getFloat(panelName_, Properties.Keys.PLUGIN_ACQUISITION_INTERVAL, 60));
      acquisitionInterval_ = pu.makeSpinnerFloat(1, 32000, 0.1, props_, devices_,
              new Devices.Keys[]{Devices.Keys.PLUGIN},
              Properties.Keys.PLUGIN_ACQUISITION_INTERVAL);
      repeatPanel_.add(acquisitionInterval_, "wrap");

      // end repeat sub-panel
      
      
      // start savePanel
      int textFieldWidth = 20;
      savePanel_ = new JPanel(new MigLayout(
              "",
              "[right]16[center]16[left]",
              "[]8[]"));
      savePanel_.setBorder(PanelUtils.makeTitledBorder("Data saving Settings"));
      
      separateTimePointsCB_ = new JCheckBox("Separate viewer for each time point");
      separateTimePointsCB_.setSelected(prefs_.getBoolean(panelName_, 
              Properties.Keys.PLUGIN_SEPARATE_VIEWERS_FOR_TIMEPOINTS, false));
      savePanel_.add(separateTimePointsCB_, "span 3, left, wrap");
      
      saveCB_ = new JCheckBox("Save while acquiring");
      saveCB_.setSelected(prefs_.getBoolean(panelName_, 
              Properties.Keys.PLUGIN_SAVE_WHILE_ACQUIRING, false));
      
      savePanel_.add(saveCB_, "span 3, left, wrap");

      JLabel dirRootLabel = new JLabel ("Directory root");
      savePanel_.add(dirRootLabel);

      rootField_ = new JTextField();
      rootField_.setText( prefs_.getString(panelName_, 
              Properties.Keys.PLUGIN_DIRECTORY_ROOT, "") );
      rootField_.setColumns(textFieldWidth);
      savePanel_.add(rootField_);

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
      namePrefixLabel.setText("Name prefix");
      savePanel_.add(namePrefixLabel);

      nameField_ = new JTextField("acq");
      nameField_.setText( prefs_.getString(panelName_,
              Properties.Keys.PLUGIN_NAME_PREFIX, "acq"));
      nameField_.setColumns(textFieldWidth);
      savePanel_.add(nameField_, "wrap");
      
      final JComponent[] saveComponents = { browseRootButton, rootField_, 
                                            dirRootLabel };
      setDataSavingComponents(saveComponents);
      
      saveCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setDataSavingComponents(saveComponents);
         }
      });
      
      // end save panel
      

      buttonStart_ = new JButton("Start!");
      buttonStart_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            //if (!cameras_.isCurrentCameraValid()) {
            //   ReportingUtils.showError("Must set valid camera for acquisition!");
            //   return;
            //}
            stop_.set(false);

            class acqThread extends Thread {
               acqThread(String threadName) {
                  super(threadName);
               }

               @Override
               public void run() {
                  buttonStart_.setEnabled(false);
                  buttonStop_.setEnabled(true);
                  runAcquisition();
                  buttonStop_.setEnabled(false);
                  buttonStart_.setEnabled(true);
               }
            }            
            acqThread acqt = new acqThread("diSPIM Acquisition");
            acqt.start();          
         }
      });

      buttonStop_ = new JButton("Stop!");
      buttonStop_.setEnabled(false);
      buttonStop_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            stop_.set(true);
            buttonStop_.setEnabled(false);
            buttonStart_.setEnabled(true);
         }
      });

      numTimePointsDoneLabel_ = new JLabel("");
      updateTimePointsDoneLabel(0);
      nextTimePointLabel_ = new JLabel("");

      // set up tabbed pane
      add(slicePanel_, "spany 2, top");
      add(volPanel_, "spany2, top");
      add(repeatPanel_, "top, wrap");
      add(savePanel_, "wrap");
      add(buttonStart_, "cell 0 2, split 2, center");
      add(buttonStop_, "center");
      add(numTimePointsDoneLabel_, "center");
      add(nextTimePointLabel_, "center");

   }//end constructor


   private void updateTimePointsDoneLabel(int timePointsDone) {
      numTimePointsDoneLabel_.setText("Current time point: "
              + NumberUtils.intToDisplayString(timePointsDone));
   }

   private void setDataSavingComponents(JComponent[] saveComponents) {
      if (saveCB_.isSelected()) {
         for (JComponent c : saveComponents) {
            c.setEnabled(true);
         }
      } else {
         for (JComponent c : saveComponents) {
            c.setEnabled(false);
         }
      }
   }

   /**
    * Implementation of acquisition that orchestrates image
    * acquisition itself rather than using the acquisition engine
    *
    * @return
    */
   private boolean runAcquisition() {
      if (gui_.isAcquisitionRunning()) {
         gui_.showError("An acquisition is already running");
         return false;
      }

      // check input for sanity
      float lineScanTime = ((Integer) lineScanPeriod_.getValue()
              * (Integer) numScansPerSlice_.getValue());
      try {
         lineScanTime += (Float) delayScan_.getValue();
      } catch (ClassCastException cce) {
         lineScanTime += (Double) delayScan_.getValue();
      }
      double exposure = (Double) exposureField_.getValue();
      if (exposure > lineScanTime) {
         gui_.showError("Exposure time is longer than time needed for a line scan. " +
                 "\n" + "This will result in dropped frames. " + "\n" +
                 "Please change input");
         return false;
      }
      
      boolean liveMode = gui_.isLiveModeOn();
      gui_.enableLiveMode(false);
      // TODO: get camera trigger mode and reset after acquisition
      cameras_.setSPIMCameraTriggerMode(Cameras.TriggerModes.EXTERNAL_START);

      String cameraA = devices_.getMMDevice(Devices.Keys.CAMERAA);
      String cameraB = devices_.getMMDevice(Devices.Keys.CAMERAB);
      String firstSide = (String) firstSide_.getSelectedItem();
      String firstCamera, secondCamera;
      if (firstSide.equals("A")) {
         firstCamera = cameraA;
         secondCamera = cameraB;
      } else {
         firstCamera = cameraB;
         secondCamera = cameraA;
      }

      // TODO: get these from the UI
      boolean show = true;
      boolean save = saveCB_.isSelected();
      boolean singleTimePoints = separateTimePointsCB_.isSelected();
      String rootDir = rootField_.getText();

      int nrRepeats = (Integer) numAcquisitions_.getValue();
      int nrFrames = 1;
      if (!singleTimePoints) {
         nrFrames = nrRepeats;
         nrRepeats = 1;
      }

      long timeBetweenFramesMs = Math.round(
              PanelUtils.getSpinnerValue(acquisitionInterval_) * 1000d);
      int nrSides = (Integer) numSides_.getValue();  // TODO: multi-channel
      int nrSlices = (Integer) numSlices_.getValue();
      int nrPos = 1;

      boolean autoShutter = core_.getAutoShutter();
      boolean shutterOpen = false;

      // Sanity checks
      if (firstCamera == null) {
         gui_.showError("Please set up a camera first on the Devices Panel");
         return false;
      }
      if (nrSides == 2 && secondCamera == null) {
         gui_.showError("2 Sides requested, but second camera is not configured."
                 + "\nPlease configure the Imaging Path B camera on the Devices Panel");
         return false;
      }

      long acqStart = System.currentTimeMillis();

      try {
         // empty out circular buffer
         while (core_.getRemainingImageCount() > 0) {
            core_.popNextImage();
         }
      } catch (Exception ex) {
         gui_.showError(ex, "Error emptying out the circular buffer");
         return false;
      }

      // Something is not thread safe, so disable the stageposupdater
      stagePosUpdater_.setAcqRunning(true);

      for (int tp = 0; tp < nrRepeats && !stop_.get(); tp++) {
         BlockingQueue<TaggedImage> bq = new LinkedBlockingQueue<TaggedImage>(10);
         String acqName = gui_.getUniqueAcquisitionName(nameField_.getText());
         if (singleTimePoints) {
            acqName = gui_.getUniqueAcquisitionName(nameField_.getText() + "-" + tp);
         }
         try {
            gui_.openAcquisition(acqName, rootDir, nrFrames, nrSides, nrSlices, nrPos,
                    show, save);
            core_.setExposure(firstCamera, exposure);
            if (secondCamera != null) {
               core_.setExposure(secondCamera, exposure);
            }
            gui_.setChannelName(acqName, 0, firstCamera);
            if (nrSides == 2 && secondCamera != null) {
               gui_.setChannelName(acqName, 1, secondCamera);
            }
            gui_.initializeAcquisition(acqName, (int) core_.getImageWidth(),
                    (int) core_.getImageHeight(), (int) core_.getBytesPerPixel(),
                    (int) core_.getImageBitDepth());
            MMAcquisition acq = gui_.getAcquisition(acqName);
            
            // Dive into MM internals since script interface does not support pipelines
            ImageCache imageCache = acq.getImageCache();
            VirtualAcquisitionDisplay vad = acq.getAcquisitionWindow();
            imageCache.addImageCacheListener(vad);

            // Start pumping images into the ImageCache
            DefaultTaggedImageSink sink = new DefaultTaggedImageSink(bq, imageCache);
            sink.start();

            // If the interval between frames is shorter than the time to acquire
            // them, we can switch to hardware based solution.  Not sure how important 
            // that feature is, so leave it out for now.
            for (int f = 0; f < nrFrames && !stop_.get(); f++) {
               long acqNow = System.currentTimeMillis();
               long delay = acqStart + f * timeBetweenFramesMs - acqNow;
               while (delay > 0 && !stop_.get()) {
                  nextTimePointLabel_.setText("Next time point in "
                          + NumberUtils.intToDisplayString((int) (delay / 1000))
                          + " seconds");
                  long sleepTime = Math.min(1000, delay);
                  Thread.sleep(sleepTime);
                  acqNow = System.currentTimeMillis();
                  delay = acqStart + f * timeBetweenFramesMs - acqNow;
               }

               nextTimePointLabel_.setText("");
               updateTimePointsDoneLabel(numTimePointsDone_++);

               core_.startSequenceAcquisition(firstCamera, nrSlices, 0, true);
               if (nrSides == 2) {
                  core_.startSequenceAcquisition(secondCamera, nrSlices, 0, true);
               }

               // get controller armed
               // Need to calculate the sheet amplitude based on settings 
               // in the Setup panels
               // We get these through the preferences

               int numSlices = (Integer) numSlices_.getValue();
               float piezoAmplitude = (float) ( (numSlices - 1) * 
                       (Double) stepSizeField_.getValue());
               
               float sheetARate = prefs_.getFloat(
                       Properties.Keys.PLUGIN_SETUP_PANEL_NAME.toString() + Devices.Sides.A, 
                       Properties.Keys.PLUGIN_RATE_PIEZO_SHEET, -80);
               // catch divide by 0 errors
               float sheetAmplitudeA = piezoAmplitude / sheetARate;
               float sheetBRate = prefs_.getFloat(
                       Properties.Keys.PLUGIN_SETUP_PANEL_NAME.toString() + Devices.Sides.B, 
                       Properties.Keys.PLUGIN_RATE_PIEZO_SHEET, -80);
               float sheetAmplitudeB = piezoAmplitude / sheetBRate ;
               
               props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SA_AMPLITUDE_Y_DEG,
                       sheetAmplitudeA);
               props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.BEAM_ENABLED,
                       Properties.Values.NO, true);
               props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SA_AMPLITUDE_Y_DEG,
                       sheetAmplitudeB);
               props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.BEAM_ENABLED,
                       Properties.Values.NO, true);
               props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SPIM_NUM_SLICES,
                       (Integer) numSlices_.getValue(), true);
               props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SA_AMPLITUDE,
                       piezoAmplitude );
               props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SPIM_NUM_SLICES,
                       (Integer) numSlices_.getValue(), true);
               props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SA_AMPLITUDE,
                       piezoAmplitude );
               props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SPIM_STATE,
                       Properties.Values.SPIM_ARMED, true);
               props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SPIM_STATE,
                       Properties.Values.SPIM_ARMED, true);

               // deal with shutter
               if (autoShutter) {
                  core_.setAutoShutter(false);
                  shutterOpen = core_.getShutterOpen();
                  if (!shutterOpen) {
                     core_.setShutterOpen(true);
                  }
               }

               // trigger controller
               // TODO generalize this for different ways of running SPIM
               props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SPIM_STATE,
                       Properties.Values.SPIM_RUNNING, true);

               // get images from camera and stick into acquisition
               // Wait for first image to create ImageWindow, so that we can be sure about image size
               long start = System.currentTimeMillis();
               long now = start;
               long timeout = 10000;
               while (core_.getRemainingImageCount() == 0 && (now - start < timeout)) {
                  now = System.currentTimeMillis();
                  Thread.sleep(5);
               }
               if (now - start >= timeout) {
                  throw new Exception("Camera did not send image within a reasonable time");
               }

               // run the loop that takes images from the cameras and puts them 
               // into the acquisition
               int[] frNumber = new int[2];
               boolean done = false;
               long timeout2;
               try {
                  timeout2 = Math.max(10000, Math.round((Float) delaySide_.getValue()
                          + nrSlices * nrSides * lineScanTime));
               } catch (ClassCastException cce) {
                  timeout2 = Math.max(10000, Math.round((Double) delaySide_.getValue()
                          + nrSlices * nrSides * lineScanTime));
               }
               start = System.currentTimeMillis();
               while ((core_.getRemainingImageCount() > 0
                       || core_.isSequenceRunning(firstCamera)
                       || (secondCamera != null && core_.isSequenceRunning(secondCamera)))
                       && !stop_.get() && !done) {
                  now = System.currentTimeMillis();
                  if (core_.getRemainingImageCount() > 0) {
                     TaggedImage timg = core_.popNextTaggedImage();
                     String camera = (String) timg.tags.get("Camera");
                     int ch = 0;
                     if (camera.equals(secondCamera)) {
                        ch = 1;
                     }
                     addImageToAcquisition(acqName, f, ch, frNumber[ch], 0,
                             now - acqStart, timg, bq);
                     frNumber[ch]++;
                  } else {
                     Thread.sleep(1);
                  }
                  if (frNumber[0] == frNumber[1] && frNumber[0] == nrSlices) {
                     done = true;
                  }
                  if (now - start >= timeout2) {
                     gui_.logError("No image arrived withing a reasonable period");
                     // stop_.set(true);
                     done = true;
                  }
               }

               if (core_.isSequenceRunning(firstCamera)) {
                  core_.stopSequenceAcquisition(firstCamera);
               }
               if (secondCamera != null && core_.isSequenceRunning(secondCamera)) {
                  core_.stopSequenceAcquisition(secondCamera);
               }
               if (autoShutter) {
                  core_.setAutoShutter(true);

                  if (!shutterOpen) {
                     core_.setShutterOpen(false);
                  }
               }
            }
         } catch (MMScriptException mex) {
            gui_.showError(mex);
         } catch (Exception ex) {
            gui_.showError(ex);
         } finally {
            try {
               if (core_.isSequenceRunning(firstCamera)) {
                  core_.stopSequenceAcquisition(firstCamera);
               }
               if (secondCamera != null && core_.isSequenceRunning(secondCamera)) {
                  core_.stopSequenceAcquisition(secondCamera);
               }
               if (autoShutter) {
                  core_.setAutoShutter(true);

                  if (!shutterOpen) {
                     core_.setShutterOpen(false);
                  }
               }
               
               // the controller will end with both beams disabled and scan off so reflect that here
               props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.BEAM_ENABLED,
                     Properties.Values.NO, true);
               props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.BEAM_ENABLED,
                     Properties.Values.NO, true);
               props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SA_MODE_X,
                     Properties.Values.SAM_DISABLED, true);
               props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SA_MODE_X,
                     Properties.Values.SAM_DISABLED, true);
               
               nextTimePointLabel_.setText("Acquisition finished");
               numTimePointsDone_ = 0;
               updateTimePointsDoneLabel(numTimePointsDone_);
               stagePosUpdater_.setAcqRunning(false);
               bq.add(TaggedImageQueue.POISON);
               gui_.closeAcquisition(acqName);
               gui_.logMessage("Acquisition took: " + 
                       (System.currentTimeMillis() - acqStart) + "ms");
               
            } catch (Exception ex) {
               // exception while stopping sequence acquisition, not sure what to do...
               gui_.showError(ex, "Problem while finsihing acquisition");
            }
         }

      }

      // return camera trigger mode 
      cameras_.setSPIMCameraTriggerMode(Cameras.TriggerModes.INTERNAL);
      gui_.enableLiveMode(liveMode);

      return true;
   }
   

   @Override
   public void saveSettings() {
      prefs_.putInt(panelName_, Properties.Keys.PLUGIN_NUM_ACQUISITIONS,
              props_.getPropValueInteger(Devices.Keys.PLUGIN,
              Properties.Keys.PLUGIN_NUM_ACQUISITIONS));
      prefs_.putFloat(panelName_, Properties.Keys.PLUGIN_ACQUISITION_INTERVAL,
              props_.getPropValueFloat(Devices.Keys.PLUGIN,
              Properties.Keys.PLUGIN_ACQUISITION_INTERVAL));
      prefs_.putBoolean(panelName_, Properties.Keys.PLUGIN_SAVE_WHILE_ACQUIRING,
              saveCB_.isSelected());
      prefs_.putString(panelName_, Properties.Keys.PLUGIN_DIRECTORY_ROOT,
              rootField_.getText());
      prefs_.putString(panelName_, Properties.Keys.PLUGIN_NAME_PREFIX,
              nameField_.getText());
      prefs_.putBoolean(panelName_,
              Properties.Keys.PLUGIN_SEPARATE_VIEWERS_FOR_TIMEPOINTS,
              separateTimePointsCB_.isSelected());

      // save controller settings
      props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SAVE_CARD_SETTINGS,
              Properties.Values.DO_SSZ, true);
      props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SAVE_CARD_SETTINGS,
              Properties.Values.DO_SSZ, true);
      props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SAVE_CARD_SETTINGS,
              Properties.Values.DO_SSZ, true);
      props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SAVE_CARD_SETTINGS,
              Properties.Values.DO_SSZ, true);

   }

   /**
    * Gets called when this tab gets focus. Refreshes values from properties.
    */
   @Override
   public void gotSelected() {
      props_.callListeners();
   }

   /**
    * called when tab looses focus.
    */
   @Override
   public void gotDeSelected() {

   }

   @Override
   public void devicesChangedAlert() {
      devices_.callListeners();
   }

   private void setRootDirectory(JTextField rootField) {
      File result = FileDialogs.openDir(null,
              "Please choose a directory root for image data",
              MMStudioMainFrame.MM_DATA_SET);
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
    * @throws org.micromanager.utils.MMScriptException
    */
   public void addImageToAcquisition(String name,
           int frame,
           int channel,
           int slice,
           int position,
           long ms,
           TaggedImage taggedImg,
           BlockingQueue<TaggedImage> bq) throws MMScriptException {

      MMAcquisition acq = gui_.getAcquisition(name);

      // check position, for multi-position data set the number of declared 
      // positions should be at least 2
      if (acq.getPositions() <= 1 && position > 0) {
         throw new MMScriptException("The acquisition was opened as a single position data set.\n"
                 + "Open acqusition with two or more positions in order to crate a multi-position data set.");
      }

      // check position, for multi-position data set the number of declared 
      // positions should be at least 2
      if (acq.getChannels() <= channel) {
         throw new MMScriptException("This acquisition was opened with " + acq.getChannels() + " channels.\n"
                 + "The channel number must not exceed declared number of positions.");
      }


      JSONObject tags = taggedImg.tags;

      if (!acq.isInitialized()) {
         throw new MMScriptException("Error in the ASIdiSPIM logic.  Acquisition should have been initialized");
      }

      // create required coordinate tags
      try {
         tags.put(MMTags.Image.FRAME_INDEX, frame);
         tags.put(MMTags.Image.FRAME, frame);
         tags.put(MMTags.Image.CHANNEL_INDEX, channel);
         tags.put(MMTags.Image.SLICE_INDEX, slice);
         tags.put(MMTags.Image.POS_INDEX, position);
         tags.put(MMTags.Image.ELAPSED_TIME_MS, ms);

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

      bq.add(taggedImg);
   }
   
   
}
