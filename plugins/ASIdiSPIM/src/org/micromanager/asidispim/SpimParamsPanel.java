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
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.MMException;
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
import java.util.TimerTask;
import java.util.Timer;  // note different from javax.swing.Timer

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

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
//   private final ScriptInterface gui_;

   private final JSpinner numSlices_;
   private final JSpinner numSides_;
   private final JComboBox firstSide_;
   private final JSpinner numScansPerSlice_;
   private final JSpinner lineScanPeriod_;
   private final JSpinner numRepeats_;
   private final JSpinner delaySlice_;
   private final JSpinner delaySide_;
   private final JSpinner numAcquisitions_;   
   private final JSpinner acquisitionInterval_;
//   private final JTextField acqExposure_;
   private final JButton buttonStart_;
   private final JButton buttonStop_;

   private final JPanel acqPanel_;
   private final JPanel loopPanel_;
   private final JPanel camPanel_;
   
   private final CameraSubPanel cameraPanel_;
   
   private final JLabel numAcquisitionsDoneLabel_;
   private int numAcquisitionsDone_;
   Timer acqTimer_;
   AcquisitionTask acqTask_;
   
   public SpimParamsPanel(Devices devices, Properties props, Cameras cameras, Prefs prefs) {
      super("SPIM Params", 
            new MigLayout(
                  "",
                  "[right]16[center]16[center]16[center]16[center]",
                  "[]12[]"));
      devices_ = devices;
      props_ = props;
      cameras_ = cameras;
      prefs_ = prefs;
      acqEngine_ = MMStudioMainFrame.getInstance().getAcquisitionEngine();
      core_ = MMStudioMainFrame.getInstance().getCore();
      numAcquisitionsDone_ = 0;

      PanelUtils pu = new PanelUtils();
      
      acqPanel_ = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]12[]"));
      
      TitledBorder myBorder = BorderFactory.createTitledBorder("Acquisition Settings");
      myBorder.setTitleJustification(TitledBorder.CENTER);
      acqPanel_.setBorder(myBorder);

      acqPanel_.add(new JLabel("Number of sides:"));
      numSides_ = pu.makeSpinnerInteger(1, 2, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_NUM_SIDES);
      acqPanel_.add(numSides_, "wrap");

      acqPanel_.add(new JLabel("First side:"));
      String[] ab = {Devices.Sides.A.toString(), Devices.Sides.B.toString()};
      firstSide_ = pu.makeDropDownBox(ab, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB}, 
            Properties.Keys.SPIM_FIRSTSIDE);
      acqPanel_.add(firstSide_, "wrap");

      acqPanel_.add(new JLabel("Number of volumes per acquisition:"));
      numRepeats_ = pu.makeSpinnerInteger(1, 32000, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB}, 
            Properties.Keys.SPIM_NUM_REPEATS);
      acqPanel_.add(numRepeats_, "wrap");

      acqPanel_.add(new JLabel("Number of slices per volume:"));
      numSlices_ = pu.makeSpinnerInteger(1, 1000, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_NUM_SLICES);
      acqPanel_.add(numSlices_, "wrap");

      acqPanel_.add(new JLabel("Lines scans per slice:"));
      numScansPerSlice_ = pu.makeSpinnerInteger(1, 1000, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_NUM_SCANSPERSLICE);
      acqPanel_.add(numScansPerSlice_, "wrap");

      acqPanel_.add(new JLabel("Line scan period (ms):"));
      lineScanPeriod_ = pu.makeSpinnerInteger(1, 10000, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_LINESCAN_PERIOD);
      acqPanel_.add(lineScanPeriod_, "wrap");

      acqPanel_.add(new JLabel("Delay before each slice (ms):"));
      delaySlice_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_,
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_SLICE);
      acqPanel_.add(delaySlice_, "wrap");

      acqPanel_.add(new JLabel("Delay before each side (ms):"));
      delaySide_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_SIDE);
      acqPanel_.add(delaySide_, "wrap");
      
      JButton buttonSaveSettings_ = new JButton("Save settings");
      buttonSaveSettings_.setToolTipText("Saves settings to piezo and galvo cards on controller");
      buttonSaveSettings_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SAVE_CARD_SETTINGS, Properties.Values.DO_SSZ, true);
            props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SAVE_CARD_SETTINGS, Properties.Values.DO_SSZ, true);
            props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SAVE_CARD_SETTINGS, Properties.Values.DO_SSZ, true);
            props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SAVE_CARD_SETTINGS, Properties.Values.DO_SSZ, true);
         }
      });
      acqPanel_.add(buttonSaveSettings_, "span 2, center, wrap");
      
      // add acquisition sub-panel to tabbed pane
      add(acqPanel_, "span 2 10, wrap");
      
      // end acquisition sub-panel
      
      // start repeat sub-panel
      
      loopPanel_ = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]12[]"));
      
      myBorder = BorderFactory.createTitledBorder("Repeat Settings");
      myBorder.setTitleJustification(TitledBorder.CENTER);
      loopPanel_.setBorder(myBorder);
      
      loopPanel_.add(new JLabel("Number of acquisitions:"));
      numAcquisitions_ = pu.makeSpinnerInteger(1, 32000, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.PLUGIN},
            Properties.Keys.PLUGIN_NUM_ACQUISITIONS);
      loopPanel_.add(numAcquisitions_, "wrap");
      
      loopPanel_.add(new JLabel("Acquisition interval (s):"));
      acquisitionInterval_ = pu.makeSpinnerFloat(1, 32000, 0.1, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.PLUGIN},
            Properties.Keys.PLUGIN_ACQUISITION_PERIOD);
      loopPanel_.add(acquisitionInterval_, "wrap");
      
      add(loopPanel_, "span 2 3, wrap");
      
      // end repeat sub-panel
      
      // start camera sub-panel
      
      camPanel_ = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]12[]"));
      
      myBorder = BorderFactory.createTitledBorder("Camera Settings");
      myBorder.setTitleJustification(TitledBorder.CENTER);
      camPanel_.setBorder(myBorder);
      
      cameraPanel_ = new CameraSubPanel(cameras_, devices_, this.panelName_, Devices.Sides.NONE, prefs_, false);
      camPanel_.add(cameraPanel_, "center, span 2");
      
//      camPanel.add(new JLabel("Exposure (ms):"));
//      acqExposure_ = new JFormattedTextField(new NumberFormatter());
//      acqExposure_.setColumns(5);
//      acqExposure_.setText(prefs_.getString(super.panelName_, Prefs.Keys.SPIM_EXPOSURE, "10"));
//      camPanel.add(acqExposure_, "wrap");
      
      add(camPanel_, "cell 2 4, span 2 2, wrap");
      
      acqTimer_ = null;
      acqTask_ = null;

      buttonStart_ = new JButton("Start!");
      buttonStart_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            numAcquisitionsDone_ = 0;
            acqTimer_ = new Timer(true); // once cancelled we need to create a new one
            acqTask_ = new AcquisitionTask();
            float f = 0;
            try {
               f = (float)((Double)acquisitionInterval_.getValue()).doubleValue();
            } catch (Exception ex) {
               f = ((Float)acquisitionInterval_.getValue()).floatValue();
            }
            int rate = (int)(f*1000); 
            acqTimer_.scheduleAtFixedRate(acqTask_, 0, rate);
            buttonStart_.setEnabled(false);
            buttonStop_.setEnabled(true);
         }
      });
      add(buttonStart_, "cell 2 7, center");
      
      buttonStop_ = new JButton("Stop!");
      buttonStop_.setEnabled(false);
      buttonStop_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            acqTimer_.cancel();
            buttonStop_.setEnabled(false);
            buttonStart_.setEnabled(true);
         }
      });
      add(buttonStop_, "cell 3 7, center");
      
      numAcquisitionsDoneLabel_ = new JLabel("");
      updateAcquisitionCountLabel();
      add(numAcquisitionsDoneLabel_, "cell 3 8, center");
      
            
   }//end constructor
   

   private void updateAcquisitionCountLabel() {
      numAcquisitionsDoneLabel_.setText( "Current acquisition: " +
            NumberUtils.intToDisplayString(numAcquisitionsDone_));
   }
   
   
   private class AcquisitionTask extends TimerTask {
      @Override
      public void run() {
         if (numAcquisitionsDone_ >= ((Integer)numAcquisitions_.getValue()).intValue()) {
            buttonStop_.doClick();
         } else {
            startAcquisition();
            numAcquisitionsDone_++;
         }
         updateAcquisitionCountLabel();
      }
   }

   
   /**
    * Performs an acquisition using the Micro-manager AcquisitionEngine object
    */
   private void startAcquisition() {
    if (acqEngine_.isAcquisitionRunning()) {
       ReportingUtils.showError("Already running another acquisition!");
       return;
    }
    try {
       // set up cameras
       if (!cameras_.isCurrentCameraValid()) {
          ReportingUtils.showError("No camera set for acquisition!");
          return;
       }
       // TODO warn user if acquisition interval is too low
       cameras_.enableLiveMode(false);
//       core_.setExposure(NumberUtils.displayStringToDouble(acqExposure_.getText()));
       // get acquisition engine configured
       // patterned after code in mmstudio\src\org\micromanager\AcqControlDlg.java
       acqEngine_.clear();
       acqEngine_.setUpdateLiveWindow(false);
       acqEngine_.enableCustomTimeIntervals(false);
       acqEngine_.enableFramesSetting(true);
       acqEngine_.enableMultiPosition(false);
       acqEngine_.enableZSliceSetting(false);
       acqEngine_.enableChannelsSetting(false);
       acqEngine_.enableAutoFocus(false);
       acqEngine_.setFrames(
             (Integer)numSlices_.getValue() * (Integer)numRepeats_.getValue(), // number of frames to capture
             0); // 0ms exposure time means it will record as fast as possible
       acqEngine_.acquire();
       core_.sleep(1000);  // seem to need a long delay for some reason, more than 1 sec
       // get controller ready
       props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.BEAM_ENABLED, Properties.Values.NO, true);
       props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.BEAM_ENABLED, Properties.Values.NO, true);
       props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SPIM_NUM_SLICES, (Integer)numSlices_.getValue(), true);
       props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SPIM_NUM_SLICES, (Integer)numSlices_.getValue(), true);
       props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED, true);
       props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED, true);
       // trigger controller
       // TODO generalize this for different ways of running SPIM
       props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SPIM_STATE, Properties.Values.SPIM_RUNNING, true);
       // TODO figure out how to wait until acquisition is done to continue
       // clean up
    } catch (MMException ex) {
       ReportingUtils.showError(ex);
    } catch (Exception e1) {
       ReportingUtils.showError(e1);
    }
 
   }
   
   @Override
   public void saveSettings() {
//      prefs_.putString(super.panelName_, Prefs.Keys.SPIM_EXPOSURE, acqExposure_.getText());
   }

   /**
    * Gets called when this tab gets focus.
    * Refreshes values from properties.
    */
   @Override
   public void gotSelected() {
      props_.callListeners();
      cameraPanel_.gotSelected();
      cameras_.enableLiveMode(false);
      // would like to close liveMode window if we can!
      cameras_.setSPIMCameraTriggerMode(Cameras.TriggerModes.EXTERNAL);
   }
   
   /**
    * called when tab looses focus.
    */
   @Override
   public void gotDeSelected() {
      // need to make sure we switch back to internal mode for everything
      cameras_.setSPIMCameraTriggerMode(Cameras.TriggerModes.INTERNAL);
   }
   
   @Override
   public void devicesChangedAlert() {
      devices_.callListeners();
   }


}
