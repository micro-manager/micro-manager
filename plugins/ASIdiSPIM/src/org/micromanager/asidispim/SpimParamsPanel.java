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

import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.text.NumberFormatter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

   private final JSpinner numSlices_;
   private final JSpinner numSides_;
   private final JComboBox firstSide_;
   private final JSpinner numScansPerSlice_;
   private final JSpinner lineScanPeriod_;
   private final JSpinner numRepeats_;
   private final JSpinner delaySlice_;
   private final JSpinner delaySide_;
   private final JTextField acqExposure_;

   private final CameraSubPanel cameraPanel_;

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

      PanelUtils pu = new PanelUtils();

      add(new JLabel("Number of sides:"), "split 2");
      numSides_ = pu.makeSpinnerInteger(1, 2, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_NUM_SIDES);
      add(numSides_);

      add(new JLabel("First side:"), "align right");
      String[] ab = {Devices.Sides.A.toString(), Devices.Sides.B.toString()};
      firstSide_ = pu.makeDropDownBox(ab, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB}, 
            Properties.Keys.SPIM_FIRSTSIDE);
      // no listener here
      add(firstSide_, "wrap");

      add(new JLabel("Number of repeats:"));
      numRepeats_ = pu.makeSpinnerInteger(1, 100, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB}, 
            Properties.Keys.SPIM_NUM_REPEATS);
      add(numRepeats_, "wrap");

      add(new JLabel("Number of slices:"));
      numSlices_ = pu.makeSpinnerInteger(1, 100, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_NUM_SLICES);
      add(numSlices_, "wrap");

      add(new JLabel("Lines scans per slice:"));
      numScansPerSlice_ = pu.makeSpinnerInteger(1, 1000, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_NUM_SCANSPERSLICE);
      add(numScansPerSlice_, "wrap");

      add(new JLabel("Line scan period (ms):"));
      lineScanPeriod_ = pu.makeSpinnerInteger(1, 10000, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_LINESCAN_PERIOD);
      add(lineScanPeriod_, "wrap");

      add(new JLabel("Delay before each slice (ms):"));
      delaySlice_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_,
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_SLICE);
      add(delaySlice_, "wrap");

      add(new JLabel("Delay before each side (ms):"));
      delaySide_ = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SPIM_DELAY_SIDE);
      add(delaySide_, "wrap");
      
      add(new JSeparator(JSeparator.VERTICAL), "growy, cell 3 0 1 9");

      JButton buttonStart_ = new JButton("Start acquisition!");
      buttonStart_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
//            double oldExposure = 0;
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
               cameras_.enableLiveMode(false);
//               oldExposure = core_.getExposure();
               core_.setExposure(NumberUtils.displayStringToDouble(acqExposure_.getText()));
               cameras_.setSPIMCameraTriggerMode(Cameras.TriggerModes.EXTERNAL);
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
               core_.sleep(2000);  // seem to need a long delay for some reason, more than 1 sec
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
//               core_.setExposure(oldExposure); // don't do this until we make sure acquisition is complete
            } catch (MMException ex) {
               ReportingUtils.showError(ex);
            } catch (Exception e1) {
               ReportingUtils.showError(e1);
            }
         }
      });
      add(buttonStart_, "cell 4 0, span 2, center, wrap");
      
      add(new JLabel("SPIM exposure time (ms):"), "cell 4 1");
      acqExposure_ = new JFormattedTextField(new NumberFormatter());
      acqExposure_.setColumns(5);
      acqExposure_.setText(prefs_.getString(super.panelName_, Prefs.Keys.SPIM_EXPOSURE, "10"));
      add(acqExposure_, "wrap");

      cameraPanel_ = new CameraSubPanel(cameras_, devices_, this.panelName_, Devices.Sides.NONE, prefs_, false);
      add(cameraPanel_, "cell 4 2, center, span 2");
      
      add(new JLabel("This camera is used for acquisition"), "cell 4 3, span 2");

      JButton buttonSaveSettings_ = new JButton("Save controller settings");
      buttonSaveSettings_.setToolTipText("Saves settings to piezo and galvo cards");
      buttonSaveSettings_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SAVE_CARD_SETTINGS, Properties.Values.DO_SSZ, true);
            props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SAVE_CARD_SETTINGS, Properties.Values.DO_SSZ, true);
            props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SAVE_CARD_SETTINGS, Properties.Values.DO_SSZ, true);
            props_.setPropValue(Devices.Keys.GALVOB, Properties.Keys.SAVE_CARD_SETTINGS, Properties.Values.DO_SSZ, true);
         }
      });
      add(buttonSaveSettings_, "cell 4 8, span 2, center, wrap");

   }//end constructor
   
   @Override
   public void saveSettings() {
      prefs_.putString(super.panelName_, Prefs.Keys.SPIM_EXPOSURE, acqExposure_.getText());
   }

   /**
    * Gets called when this tab gets focus.
    * Refreshes values from properties.
    */
   @Override
   public void gotSelected() {
      props_.callListeners();
      cameraPanel_.gotSelected();
   }
   
   @Override
   public void devicesChangedAlert() {
      devices_.callListeners();
   }


}
