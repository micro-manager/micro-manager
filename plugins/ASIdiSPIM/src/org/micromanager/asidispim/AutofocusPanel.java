///////////////////////////////////////////////////////////////////////////////
//FILE:          AutofocusPanel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2015
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.AutofocusUtils;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyNumberUtils;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.asidispim.api.ASIdiSPIMException;
import org.micromanager.asidispim.fit.Fitter;

/**
 *
 * @author nico
 * @author jon
 */
@SuppressWarnings("serial")
public class AutofocusPanel extends ListeningJPanel{
   final private Properties props_;
   final private Prefs prefs_;
   final private Devices devices_;
   
   private final JCheckBox autoUpdateOffset_;
   private final JLabel maxOffsetChangeSetupLabel1_;
   private final JSpinner maxOffsetChangeSetupSpinner_;
   private final JLabel maxOffsetChangeSetupLabel2_;
   private final JSpinner eachNTimePointsSpinner_;
   private final JSpinner correctMEachNTimePointsSpinner_;
   
   public AutofocusPanel(final ScriptInterface gui, final Devices devices, 
           final Properties props, final Prefs prefs, 
           final AutofocusUtils autofocus) {
      
      super(MyStrings.PanelNames.AUTOFOCUS.toString(),
              new MigLayout(
              "flowy",
              "[center]16[center]",
              "[top]8[top]8[top]" ) );
      prefs_ = prefs;
      props_ = props;
      devices_ = devices;
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);

      // start options panel
      final JPanel optionsPanel = new JPanel(new MigLayout(
            "",
            "[right]16[left]",
            "[]8[]"));
      optionsPanel.setBorder(PanelUtils.makeTitledBorder("General Autofocus Options"));
      
      // show images checkbox
      final JCheckBox showImagesCheckBox = pu.makeCheckBox("Show images",
              Properties.Keys.PLUGIN_AUTOFOCUS_SHOWIMAGES, panelName_, true);
      optionsPanel.add(showImagesCheckBox);
      
      // show plot checkbox
      final JCheckBox showPlotCheckBox = pu.makeCheckBox("Show plot",
              Properties.Keys.PLUGIN_AUTOFOCUS_SHOWPLOT, panelName_, true);
      optionsPanel.add(showPlotCheckBox, "wrap");
 
      // spinner with number of images:
      optionsPanel.add(new JLabel("Number of images:"));
      final JSpinner nrImagesSpinner = pu.makeSpinnerInteger(1, 1000,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_NRIMAGES, 21);
      optionsPanel.add(nrImagesSpinner, "wrap");
      
      // spinner with stepsize:
      optionsPanel.add(new JLabel("Step size [\u00B5m]:"));
      final JSpinner stepSizeSpinner = pu.makeSpinnerFloat(0.001, 100., 1.,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_STEPSIZE, 0.5);
      optionsPanel.add(stepSizeSpinner, "wrap");
      
      // scan either piezo or sheet; select which one
      optionsPanel.add(new JLabel("Mode:"));
      String[] scanOptions = getModeStrings();
      final JComboBox scanModeCB = pu.makeDropDownBox(scanOptions,
            Devices.Keys.PLUGIN, Properties.Keys.AUTOFOCUS_ACQUSITION_MODE,
            scanOptions[0]);
      optionsPanel.add(scanModeCB, "wrap");
      
      optionsPanel.add(new JLabel("Scoring algorithm:"));
      final JComboBox scoringAlgorithmCB = new JComboBox();
      for (String scoringAlgorithm : Fitter.getAlgorithms()) {
         scoringAlgorithmCB.addItem(scoringAlgorithm);
      }
      scoringAlgorithmCB.setSelectedItem(Fitter.getAlgorithmFromPrefCode(
            prefs_.getInt(panelName_,
            Properties.Keys.AUTOFOCUS_SCORING_ALGORITHM,
            Fitter.Algorithm.VOLATH.getPrefCode())).toString());
      scoringAlgorithmCB.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            prefs_.putInt(panelName_, Properties.Keys.AUTOFOCUS_SCORING_ALGORITHM,
                  Fitter.getPrefCodeFromString(scoringAlgorithmCB.getSelectedItem().toString()));
         }
      });
      optionsPanel.add(scoringAlgorithmCB, "wrap");
      
      optionsPanel.add(new JLabel("Fit using:"));
      final JComboBox fitFunctionSelection = new JComboBox();
      for (String fitFunction : Fitter.getFunctionTypes()) {
         fitFunctionSelection.addItem(fitFunction);
      }
      fitFunctionSelection.setSelectedItem(prefs_.getString(panelName_, 
              Prefs.Keys.AUTOFOCUSFITFUNCTION, 
              Fitter.getFunctionTypeAsString(Fitter.FunctionType.Gaussian)));
      fitFunctionSelection.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            prefs_.putString(panelName_, Prefs.Keys.AUTOFOCUSFITFUNCTION, 
                    (String) fitFunctionSelection.getSelectedItem());
         }
      });
      optionsPanel.add(fitFunctionSelection, "wrap");
            
      optionsPanel.add(new JLabel("<html>Minimum R<sup>2</sup></html>:"));
      final JSpinner minimumR2Spinner = pu.makeSpinnerFloat(0.0, 1.0, 0.01,
              Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_MINIMUMR2, 0.75);
      optionsPanel.add(minimumR2Spinner);
      
      // end options subpanel
      
      // start acquisition options panel
      final JPanel acqOptionsPanel = new JPanel(new MigLayout(
            "",
            "[right]8[center]8[left]",
            "[]8[]"));
      acqOptionsPanel.setBorder(PanelUtils.makeTitledBorder(
              "Autofocus Options During Acquisition"));
      
      // whether or not to run autofocus at the start of the acquisition
      final JCheckBox beforeStartCheckBox = pu.makeCheckBox("Autofocus before starting acquisition",
              Properties.Keys.PLUGIN_AUTOFOCUS_ACQBEFORESTART, panelName_, false);     
      acqOptionsPanel.add(beforeStartCheckBox, "center, span 3, wrap");
      
      // autofocus every nth image
      acqOptionsPanel.add(new JLabel("Autofocus every "));
      eachNTimePointsSpinner_ = pu.makeSpinnerInteger(1, 1000,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_EACHNIMAGES, 10);
      acqOptionsPanel.add(eachNTimePointsSpinner_);
      acqOptionsPanel.add(new JLabel( "time points"), "wrap");
      
      // autofocus using this channel
      // TODO: need to update combobox when the channel group changes
      String channelGroup_  = props_.getPropValueString(Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_MULTICHANNEL_GROUP);
      StrVector channels = gui.getMMCore().getAvailableConfigs(channelGroup_);
      final JComboBox channelSelect = pu.makeDropDownBox(channels.toArray(), 
              Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_CHANNEL, "");
      // make sure to explicitly set it to something so pref gets written
      channelSelect.setSelectedIndex(channelSelect.getSelectedIndex());
      acqOptionsPanel.add(new JLabel("Autofocus channel: "));
      acqOptionsPanel.add(channelSelect, "left, span 2, wrap");
      
      acqOptionsPanel.add(new JLabel("Max offset change:"));
      final JSpinner maxOffsetChangeSpinner = pu.makeSpinnerFloat(0, 10, 1,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_MAXOFFSETCHANGE, 2);
      acqOptionsPanel.add(maxOffsetChangeSpinner);
      acqOptionsPanel.add(new JLabel("\u00B5m (\u00B1)"), "left, wrap");
      
      final JPanel setupOptionsPanel = new JPanel(new MigLayout(
            "",
            "[right]8[center]8[left]",
            "[]8[]"));
      setupOptionsPanel.setBorder(PanelUtils.makeTitledBorder(
              "Autofocus Options During Setup"));

      autoUpdateOffset_ = pu.makeCheckBox("Automatically update offset if focus found",
            Properties.Keys.PLUGIN_AUTOFOCUS_AUTOUPDATE_OFFSET, panelName_, false);
      setupOptionsPanel.add(autoUpdateOffset_, "center, span 3, wrap");

      maxOffsetChangeSetupLabel1_ = new JLabel("Max offset change:"); 
      setupOptionsPanel.add(maxOffsetChangeSetupLabel1_);
      maxOffsetChangeSetupSpinner_ = pu.makeSpinnerFloat(0, 10, 1,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_MAXOFFSETCHANGE_SETUP, 5);
      setupOptionsPanel.add(maxOffsetChangeSetupSpinner_);
      maxOffsetChangeSetupLabel2_ = new JLabel("\u00B5m (\u00B1)");
      setupOptionsPanel.add(maxOffsetChangeSetupLabel2_, "left, wrap");
      
      final JComponent[] autoUpdateComponents = { maxOffsetChangeSetupLabel1_,
            maxOffsetChangeSetupSpinner_, maxOffsetChangeSetupLabel2_ };

      // only enable max change field when autoupdate is selected
      // the field's value is only used when autoupdate is selected
      PanelUtils.componentsSetEnabled(autoUpdateComponents, autoUpdateOffset_.isSelected());
      autoUpdateOffset_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            PanelUtils.componentsSetEnabled(autoUpdateComponents, autoUpdateOffset_.isSelected());
         }
      });
      
      final JPanel movementCorrectionsPanel = new JPanel(new MigLayout(
            "",
            "[right]8[center]8[left]",
            "[]8[]"));
      movementCorrectionsPanel.setBorder(PanelUtils.makeTitledBorder(
              "Movement correction options"));
      
      // autofocus every nth image
      movementCorrectionsPanel.add(new JLabel("Correct every "));
      correctMEachNTimePointsSpinner_ = pu.makeSpinnerInteger(1, 1000,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_EACHNIMAGES, 10);
      movementCorrectionsPanel.add(correctMEachNTimePointsSpinner_);
      movementCorrectionsPanel.add(new JLabel( "time points"), "wrap");
      
      // correct movement using this channel
      // TODO: need to update combobox when the channel group changes
      final JComboBox channelSelectCM = pu.makeDropDownBox(channels.toArray(), 
              Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_CHANNEL, "");
      // make sure to explicitly set it to something so pref gets written
      channelSelectCM.setSelectedIndex(channelSelectCM.getSelectedIndex());
      movementCorrectionsPanel.add(new JLabel("Channel: "));
      movementCorrectionsPanel.add(channelSelectCM, "left, span 2, wrap");
            
      movementCorrectionsPanel.add(new JLabel("Max distance:"));
      final JSpinner maxMovementChangeSpinner = pu.makeSpinnerFloat(1, 100, 1,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_MAXCHANGE, 25);
      movementCorrectionsPanel.add(maxMovementChangeSpinner);
      movementCorrectionsPanel.add(new JLabel("\u00B5m (\u00B1)"), "left, wrap");
                      
      movementCorrectionsPanel.add(new JLabel("Min movement:"));
      final JSpinner minMovementChangeSpinner = pu.makeSpinnerFloat(0, 10, 0.5,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_MINCHANGE, 1);
      movementCorrectionsPanel.add(minMovementChangeSpinner);
      movementCorrectionsPanel.add(new JLabel("\u00B5m (\u00B1)"), "left, wrap");
      
      
      // construct the main panel
      super.add(optionsPanel, "wrap");
      super.add(acqOptionsPanel, "flowy, split 2");
      super.add(setupOptionsPanel, "wrap");
      super.add(movementCorrectionsPanel);
   }  //constructor
   
   
   // not using the pref code for now, but now at least we have a way to refer
   //   to the possible options by the enum instead of a hard-coded string
   public static enum Modes {
      FIX_PIEZO("Fix piezo, sweep slice", 1),
      FIX_SLICE("Fix slice, sweep piezo", 2),
      NONE("None", 0);
      private final String text;
      private final int prefCode;
      Modes(String text, int prefCode) {
         this.text = text;
         this.prefCode = prefCode;
      }
      @Override
      public String toString() {
         return text;
      }
      public int getPrefCode() {
         return prefCode;
      }
   };
   
   private static String[] getModeStrings() {
      return new String[] {Modes.FIX_PIEZO.toString(), Modes.FIX_SLICE.toString()};
   }
   
   public Modes getModeFromString(String str) {
      if (str.equals(Modes.FIX_PIEZO.toString())) {
         return Modes.FIX_PIEZO;
      } else if (str.equals(Modes.FIX_SLICE.toString())) {
         return Modes.FIX_SLICE;
      } else {
         return Modes.NONE;
      }
   }
   
   public int getAutofocusTimepointInterval() {
      return (Integer)eachNTimePointsSpinner_.getValue();
   }

   public void setAutofocusTimepointInterval(int numTimepoints) throws ASIdiSPIMException {
      if (MyNumberUtils.outsideRange(numTimepoints, 1, 1000)) {
         throw new ASIdiSPIMException("illegal value for number of slices");
      }
      eachNTimePointsSpinner_.setValue(numTimepoints);
   }
   
   
}
