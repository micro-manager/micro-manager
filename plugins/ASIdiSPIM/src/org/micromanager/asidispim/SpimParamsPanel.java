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
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JSeparator;
import javax.swing.JSpinner;

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

   Devices devices_;
   Properties props_;
   private final AcquisitionWrapperEngine acqEngine_;
   private final CMMCore core_;
   
   JSpinner numSlices_;
   JSpinner numScansPerSlice_;
   JSpinner lineScanPeriodA_;
   JSpinner numRepeats_;
   
   public SpimParamsPanel(Devices devices, Properties props) {
      super(new MigLayout(
            "",
            "[right]16[center]16[center]16[center]",
            "[]12[]"));
      devices_ = devices;
      props_ = props;
      acqEngine_ = MMStudioMainFrame.getInstance().getAcquisitionEngine();
      core_ = MMStudioMainFrame.getInstance().getCore();
      
      try {
         PanelUtils pu = new PanelUtils();
         JSpinner tmp_jsp;
         
         add(new JLabel("Number of sides:"), "split 2");
         tmp_jsp = pu.makeSpinnerInteger(1, 2, props_, devices_, Devices.Keys.GALVOA, Properties.Keys.SPIM_NUM_SIDES);
         add(tmp_jsp);

         add(new JLabel("First side:"), "align right");
         String[] ab = {Devices.Sides.A.toString(), Devices.Sides.B.toString()};
         JComboBox tmp_box = pu.makeDropDownBox(ab, props_, devices_, Devices.Keys.GALVOA, Properties.Keys.SPIM_FIRSTSIDE);
         // no listener here
         add(tmp_box, "wrap");

         add(new JLabel("Path A"), "cell 1 2");
         add(new JLabel("Path B"), "wrap");

         add(new JLabel("Number of repeats:"));
         numRepeats_ = pu.makeSpinnerInteger(1, 100, props_, devices_, Devices.Keys.GALVOA, Properties.Keys.SPIM_NUM_REPEATS);
         add(numRepeats_, "span 2, wrap");

         add(new JLabel("Number of slices:"));
         numSlices_ = pu.makeSpinnerInteger(1, 100, props_, devices_, Devices.Keys.GALVOA, Properties.Keys.SPIM_NUM_SLICES);
         add(numSlices_, "span 2, wrap");

         add(new JLabel("Lines scans per slice:"));
         numScansPerSlice_ = pu.makeSpinnerInteger(1, 1000, props_, devices_, Devices.Keys.GALVOA, Properties.Keys.SPIM_NUM_SCANSPERSLICE);
         add(numScansPerSlice_, "span 2, wrap");

         add(new JLabel("Line scan period (ms):"));
         lineScanPeriodA_ = pu.makeSpinnerInteger(1, 10000, props_, devices_, Devices.Keys.GALVOA, Properties.Keys.SPIM_LINESCAN_PERIOD);
         add(lineScanPeriodA_);
         // TODO remove this if only doing single-sided?? would have to add/remove dynamically which might be a pain
         tmp_jsp = pu.makeSpinnerInteger(1, 10000, props_, devices_, Devices.Keys.GALVOB, Properties.Keys.SPIM_LINESCAN_PERIOD);
         add(tmp_jsp, "wrap");

         add(new JLabel("Delay before each slice (ms):"));
         tmp_jsp = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_, Devices.Keys.GALVOA, Properties.Keys.SPIM_DELAY_SLICE);
         add(tmp_jsp, "span 2, wrap");

         add(new JLabel("Delay before each side (ms):"));
         tmp_jsp = pu.makeSpinnerFloat(0, 10000, 0.25, props_, devices_, Devices.Keys.GALVOA, Properties.Keys.SPIM_DELAY_SIDE);
         add(tmp_jsp, "span 2, wrap");
         
         add(new JSeparator(JSeparator.VERTICAL), "growy, cell 3 0 1 9");
         
         JButton buttonStart_ = new JButton("Start!");
         buttonStart_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               try {
                  // trigger cameras after getting acquisition engine configured
                  acqEngine_.enableFramesSetting(true);
                  acqEngine_.setFrames(
                        (Integer)numSlices_.getValue() * (Integer)numRepeats_.getValue(), // number of frames to capture
                        0); // 0ms exposure time means it will record as fast as possible
                  acqEngine_.enableMultiPosition(false);
                  acqEngine_.enableZSliceSetting(false);
                  acqEngine_.enableChannelsSetting(false);
                  acqEngine_.acquire();
                  core_.sleep(2000);  // seem to need a long delay for some reason, more than 1 sec
                  // make sure camera is set to external mode
                  // TODO make this cleaner, probably by factoring out camera into own class
                  props_.setPropValue(Devices.Keys.CAMERAA, Properties.Keys.TRIGGER_SOURCE, Properties.Values.EXTERNAL, true);
                  props_.setPropValue(Devices.Keys.CAMERAB, Properties.Keys.TRIGGER_SOURCE, Properties.Values.EXTERNAL, true);
                  // trigger controller
                  props_.setPropValue(Devices.Keys.PIEZOA, Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED, true);
                  props_.setPropValue(Devices.Keys.PIEZOB, Properties.Keys.SPIM_STATE, Properties.Values.SPIM_ARMED, true);
                  props_.setPropValue(Devices.Keys.GALVOA, Properties.Keys.SPIM_STATE, Properties.Values.SPIM_RUNNING, true);
                  // TODO generalize this for different ways of running SPIM
               } catch (MMException ex) {
                  ReportingUtils.showError(ex);
               }
            }
         });
         add(buttonStart_, "cell 4 0, span 2, center, wrap");
         
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
         
         

      } catch (Exception ex) {
         ReportingUtils.showError("Error creating \"SPIM Params\" tab.  Make sure to select devices in \"Devices\" first, then restart plugin");
      }

   }


   /**
    * Gets called when this tab gets focus.
    * Refreshes values from properties.
    */
   @Override
   public void gotSelected() {
      props_.callListeners();
   }
   
   @Override
   public void devicesChangedAlert() {
      devices_.callListeners();
   }


}
