///////////////////////////////////////////////////////////////////////////////
//FILE:          SpimParamsPanel.java
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

import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.AcquisitionWrapperEngine;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.border.TitledBorder;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFormattedTextField;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;
import org.micromanager.asidispim.Utils.StagePositionUpdater;
import org.micromanager.utils.MMScriptException;

/**
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class SpimParamsPanel extends ListeningJPanel implements DevicesListenerInterface {

   private final Devices devices_;
   private final Properties props_;
   private final Cameras cameras_;
   private final Prefs prefs_;
   private final AcquisitionWrapperEngine acqEngine_;
   private final CMMCore core_;
   private final ScriptInterface gui_;
   private final JSpinner numSlices_;
   private final JSpinner numSides_;
   private final JComboBox firstSide_;
   private final JSpinner numScansPerSlice_;
   private final JSpinner lineScanPeriod_;
   private final JSpinner delaySlice_;
   private final JSpinner delaySide_;
   private final JSpinner numAcquisitions_;
   private final JSpinner acquisitionInterval_;
   private final JButton buttonStart_;
   private final JButton buttonStop_;
   private final JPanel acqPanel_;
   private final JPanel loopPanel_;
   private final JLabel numTimePointsDoneLabel_;
   private int numTimePointsDone_;
   private final JLabel nextTimePointLabel_;
   private String acqName_;
   private AtomicBoolean stop_ = new AtomicBoolean(false);
   private final StagePositionUpdater stagePosUpdater_;
   private final JFormattedTextField exposureField_;
   
   private static final String ZSTEPTAG = "z-step_um";

   public SpimParamsPanel(Devices devices, Properties props, Cameras cameras, 
           Prefs prefs, StagePositionUpdater stagePosUpdater) {
      super("Acquisition",
              new MigLayout(
              "",
              "[right]16[center]16[center]16[center]16[center]",
              "[]12[]"));
      devices_ = devices;
      props_ = props;
      cameras_ = cameras;
      prefs_ = prefs;
      stagePosUpdater_ = stagePosUpdater;
      acqEngine_ = MMStudioMainFrame.getInstance().getAcquisitionEngine();
      core_ = MMStudioMainFrame.getInstance().getCore();
      gui_ = MMStudioMainFrame.getInstance();
      numTimePointsDone_ = 0;
      acqName_ = "Acq";

      PanelUtils pu = new PanelUtils();

      acqPanel_ = new JPanel(new MigLayout(
              "",
              "[right]16[center]",
              "[]12[]"));

      acqPanel_.setBorder(makeTitledBorder("Acquisition Settings"));

      acqPanel_.add(new JLabel("Number of sides:"));
      numSides_ = pu.makeSpinnerInteger(1, 2, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_NUM_SIDES);
      acqPanel_.add(numSides_, "wrap");

      acqPanel_.add(new JLabel("First side:"));
      String[] ab = {Devices.Sides.A.toString(), Devices.Sides.B.toString()};
      firstSide_ = pu.makeDropDownBox(ab, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_FIRSTSIDE);
      acqPanel_.add(firstSide_, "wrap");

      // acqPanel_.add(new JLabel("Number of volumes per acquisition:"));
      // numRepeats_ = pu.makeSpinnerInteger(1, 32000, props_, devices_,
      //        new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
      //        Properties.Keys.SPIM_NUM_REPEATS);
      // acqPanel_.add(numRepeats_, "wrap");

      acqPanel_.add(new JLabel("Number of slices per volume:"));
      numSlices_ = pu.makeSpinnerInteger(1, 1000, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_NUM_SLICES);
      acqPanel_.add(numSlices_, "wrap");

      acqPanel_.add(new JLabel("Lines scans per slice:"));
      numScansPerSlice_ = pu.makeSpinnerInteger(1, 1000, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_NUM_SCANSPERSLICE);
      acqPanel_.add(numScansPerSlice_, "wrap");

      acqPanel_.add(new JLabel("Line scan period (ms):"));
      lineScanPeriod_ = pu.makeSpinnerInteger(1, 10000, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_LINESCAN_PERIOD);
      acqPanel_.add(lineScanPeriod_, "wrap");

      acqPanel_.add(new JLabel("Delay before each slice (ms):"));
      delaySlice_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_DELAY_SLICE);
      acqPanel_.add(delaySlice_, "wrap");

      acqPanel_.add(new JLabel("Delay before each side (ms):"));
      delaySide_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_,
              new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
              Properties.Keys.SPIM_DELAY_SIDE);
      acqPanel_.add(delaySide_, "wrap");
      
      acqPanel_.add(new JLabel("Exposure (ms)"));
      exposureField_ = new JFormattedTextField();
      try {
         exposureField_.setValue((Double) core_.getExposure());
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Error getting exposure time from core");
      }
      exposureField_.setColumns(6);
      acqPanel_.add(exposureField_, "wrap");

      // end acquisition sub-panel


      // start repeat sub-panel

      loopPanel_ = new JPanel(new MigLayout(
              "",
              "[right]16[center]",
              "[]12[]"));

      loopPanel_.setBorder(makeTitledBorder("Repeat Settings"));

      loopPanel_.add(new JLabel("Time points:"));
      // create plugin "property" for number of acquisitions
      props_.setPropValue(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_NUM_ACQUISITIONS,
              prefs_.getInt(panelName_, Properties.Keys.PLUGIN_NUM_ACQUISITIONS, 1));
      numAcquisitions_ = pu.makeSpinnerInteger(1, 32000, props_, devices_,
              new Devices.Keys[]{Devices.Keys.PLUGIN},
              Properties.Keys.PLUGIN_NUM_ACQUISITIONS);
      loopPanel_.add(numAcquisitions_, "wrap");

      loopPanel_.add(new JLabel("Interval (s):"));
      // create plugin "property" for acquisition interval
      props_.setPropValue(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_ACQUISITION_INTERVAL,
              prefs_.getFloat(panelName_, Properties.Keys.PLUGIN_ACQUISITION_INTERVAL, 60));
      acquisitionInterval_ = pu.makeSpinnerFloat(1, 32000, 0.1, props_, devices_,
              new Devices.Keys[]{Devices.Keys.PLUGIN},
              Properties.Keys.PLUGIN_ACQUISITION_INTERVAL);
      loopPanel_.add(acquisitionInterval_, "wrap");

      // end repeat sub-panel

      buttonStart_ = new JButton("Start!");
      buttonStart_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (!cameras_.isCurrentCameraValid()) {
               ReportingUtils.showError("Must set valid camera for acquisition!");
               return;
            }
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
      add(acqPanel_, "dock west");
      add(loopPanel_, "cell 2 0 2 3");
      add(buttonStart_, "cell 2 7, center");
      add(buttonStop_, "cell 3 7, center");
      add(numTimePointsDoneLabel_, "cell 2 8 2 1, center");
      add(nextTimePointLabel_, "cell 2 9 2 1, center");

   }//end constructor

   private TitledBorder makeTitledBorder(String title) {
      TitledBorder myBorder = BorderFactory.createTitledBorder(
              BorderFactory.createLineBorder(ASIdiSPIM.borderColor), title);
      myBorder.setTitleJustification(TitledBorder.CENTER);
      return myBorder;
   }

   private void updateTimePointsDoneLabel(int timePointsDone) {
      numTimePointsDoneLabel_.setText("Current time point: "
              + NumberUtils.intToDisplayString(timePointsDone));
   }


   /**
    * Alternative implementation of acquisition that orchestrates image
    * acquisition itself rather than using the acquisition engine
    *
    * @return
    */
   private boolean runAcquisition() {
      if (acqEngine_.isAcquisitionRunning()) {
         ReportingUtils.showError("An acquisition is already running");
         return false;
      }

      // check input for sanity
      float lineScanTime = (Float) delaySlice_.getValue()
              + ((Integer) lineScanPeriod_.getValue()
              * (Integer) numScansPerSlice_.getValue());
      double exposure = (Double) exposureField_.getValue();
      if (exposure > lineScanTime) {
         ReportingUtils.showError("Exposure time is longer than time needed for a line scan. " +
                 "\n" + "This will result in dropped frames. " + "\n" +
                 "Please change input");
         return false;
      }
      
      boolean liveMode = gui_.isLiveModeOn();
      gui_.enableLiveMode(false);
      // TODO: get camera trigger mode and reset after acquisition
      cameras_.setSPIMCameraTriggerMode(Cameras.TriggerModes.EXTERNAL);

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
      String acqName = gui_.getUniqueAcquisitionName(acqName_);
      String rootDir = "";  
      
      int nrFrames = (Integer) numAcquisitions_.getValue();
      long timeBetweenFramesMs = Math.round ( 
              PanelUtils.getSpinnerValue(acquisitionInterval_) * 1000d);
      int nrSides = (Integer) numSides_.getValue();  // TODO: multi-channel
      int nrSlices = (Integer) numSlices_.getValue();
      int nrPos = 1;
      boolean show = true;
      boolean save = false;
      boolean autoShutter = core_.getAutoShutter();
      boolean shutterOpen = false;

      // Sanity checks
      if (firstCamera == null) {
         ReportingUtils.showError("Please set up a camera first on the Devices Panel");
         return false;
      }
      if (nrSides == 1 && secondCamera == null) {
         ReportingUtils.showError("2 Sides requested, but second camera not configured." +
                 "\nPlease configure the Side B camera on the Devices Panel");
         return false;
      }

      try {
         // empty out circular buffer
         while (core_.getRemainingImageCount() > 0) {
            core_.popNextImage();
         }
         // Something is not thread safe, so disable the stageposupdater
         stagePosUpdater_.setAcqRunning(true);

         gui_.openAcquisition(acqName, rootDir, nrFrames, nrSides, nrSlices, nrPos,
                 show, save);
         core_.setExposure(cameraA, exposure);
         core_.setExposure(cameraB, exposure);
         gui_.setChannelName(acqName, 0, firstCamera);
         if (nrSides == 2 && secondCamera != null) {
            gui_.setChannelName(acqName, 1, secondCamera);
         }
         long acqStart = System.currentTimeMillis();

         // If the interval between frames is shorter than the time to acquire
         // them, we can switch to hardware based solution.  Not sure how important 
         // that feature is, so leave it out for now.
         for (int f = 0; f < nrFrames && !stop_.get(); f++) {
            long acqNow = System.currentTimeMillis();
            long delay = acqStart + f * timeBetweenFramesMs - acqNow;
            while (delay > 0 && !stop_.get()) {
               nextTimePointLabel_.setText("Next time point in " + 
                       NumberUtils.intToDisplayString((int) (delay/1000)) +
                       " seconds");
               long sleepTime = Math.min(1000, delay);
               Thread.sleep(sleepTime);
               acqNow = System.currentTimeMillis();
               delay = acqStart + f * timeBetweenFramesMs - acqNow;
            }
            
            nextTimePointLabel_.setText("");
            updateTimePointsDoneLabel(numTimePointsDone_++);
            
            // Not sure what to do with camera and # of sides selection
            // simply start sequence acquisition on both cameras 
            core_.startSequenceAcquisition(firstCamera, nrSlices, 0, true);
            if (nrSides == 2) {
               core_.startSequenceAcquisition(secondCamera, nrSlices, 0, true);
            }

            // get controller armed
            props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.BEAM_ENABLED,
                    Properties.Values.NO, true);
            props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.BEAM_ENABLED,
                    Properties.Values.NO, true);
            props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SPIM_NUM_SLICES,
                    (Integer) numSlices_.getValue(), true);
            props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SPIM_NUM_SLICES,
                    (Integer) numSlices_.getValue(), true);
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
            long timeout2 = Math.max(10000, Math.round ( (Float) delaySide_.getValue() + 
                    nrSlices * nrSides * lineScanTime) );
            start = System.currentTimeMillis();
            while ((core_.getRemainingImageCount() > 0 || core_.isSequenceRunning(cameraA)
                    || core_.isSequenceRunning(cameraB)) && !stop_.get() && !done) {
               //int nrImg = core_.getRemainingImageCount();
               //ReportingUtils.logMessage("Images in C++ buffer: " + nrImg);
               if (core_.getRemainingImageCount() > 0) {
                  TaggedImage timg = core_.popNextTaggedImage();
                  String camera = (String) timg.tags.get("Camera");
                  int ch = 0;
                  if (camera.equals(secondCamera)) {
                     ch = 1;
                  }
                  gui_.addImageToAcquisition(acqName, f, ch, frNumber[ch], 0, timg);
                  frNumber[ch]++;
               } else {
                  Thread.sleep(5);
               }
               if (frNumber[0] == frNumber[1] && frNumber[0] == nrSlices) {
                  done = true;
               }
               now = System.currentTimeMillis();
               if (now - start >= timeout2) {
                  ReportingUtils.showError("No image arrived withing a reasonable period");
                  stop_.set(true);
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
         ReportingUtils.showError(mex);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
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
            nextTimePointLabel_.setText("Acquisition finished");
            numTimePointsDone_ = 0;
            updateTimePointsDoneLabel(numTimePointsDone_);
            stagePosUpdater_.setAcqRunning(false);
            gui_.closeAcquisition(acqName);
         } catch (Exception ex) {
            // exception while stopping sequence acquisition, not sure what to do...
            ReportingUtils.showError(ex, "Problem while finsihing acquisition");
         }

      }
       
      cameras_.setSPIMCameraTriggerMode(Cameras.TriggerModes.INTERNAL);
      gui_.enableLiveMode(liveMode);

      return true;
   }

 

   @Override
   public void saveSettings() {
//      prefs_.putString(panelName_, Prefs.Keys.SPIM_EXPOSURE, acqExposure_.getText());
      prefs_.putInt(panelName_, Properties.Keys.PLUGIN_NUM_ACQUISITIONS,
              props_.getPropValueInteger(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_NUM_ACQUISITIONS));
      prefs_.putFloat(panelName_, Properties.Keys.PLUGIN_ACQUISITION_INTERVAL,
              props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_ACQUISITION_INTERVAL));
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
      // cameraPanel_.gotSelected();
      // cameras_.enableLiveMode(false);
      // would like to close liveMode window if we can!
      // cameras_.setSPIMCameraTriggerMode(Cameras.TriggerModes.EXTERNAL);
   }

   /**
    * called when tab looses focus.
    */
   @Override
   public void gotDeSelected() {
      // need to make sure we switch back to internal mode for everything
      // cameras_.setSPIMCameraTriggerMode(Cameras.TriggerModes.INTERNAL);
   }

   @Override
   public void devicesChangedAlert() {
      devices_.callListeners();
   }
}
