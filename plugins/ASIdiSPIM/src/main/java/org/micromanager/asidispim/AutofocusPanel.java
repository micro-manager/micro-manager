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

import org.micromanager.AutofocusPlugin;
import org.micromanager.AutofocusManager;
import org.micromanager.Studio;

import org.micromanager.asidispim.api.ASIdiSPIMException;
import org.micromanager.asidispim.data.Devices;
import org.micromanager.asidispim.data.MyStrings;
import org.micromanager.asidispim.data.Prefs;
import org.micromanager.asidispim.data.Properties;
import org.micromanager.asidispim.utils.AutofocusUtils;
import org.micromanager.asidispim.utils.ListeningJPanel;
import org.micromanager.asidispim.utils.PanelUtils;
import org.micromanager.asidispim.fit.Fitter;
import org.micromanager.asidispim.utils.MyDialogUtils;
import org.micromanager.asidispim.utils.MyNumberUtils;

import org.micromanager.internal.utils.MMException;

/**
 *
 * @author nico
 */
@SuppressWarnings("serial")
public class AutofocusPanel extends ListeningJPanel{

   final private Studio gui_;
   final private Properties props_;
   final private Prefs prefs_;
   final private Devices devices_;
   
   private final JPanel optionsPanel_;
   private final JPanel acqOptionsPanel_;
   private final JPanel setupOptionsPanel_;
   private final JCheckBox autoUpdateOffset_;
   private final JLabel maxOffsetChangeSetupLabel1_;
   private final JSpinner maxOffsetChangeSetupSpinner_;
   private final JLabel maxOffsetChangeSetupLabel2_;
   private final JSpinner eachNTimePointsSpinner_;
   
   public AutofocusPanel(final Studio gui, final Devices devices, 
           final Properties props, final Prefs prefs, 
           final AutofocusUtils autofocus) {
      
      super(MyStrings.PanelNames.AUTOFOCUS.toString(),
              new MigLayout(
              "",
              "[center]8[center]",
              "[]16[]16[]"));
      gui_ = gui;
      prefs_ = prefs;
      props_ = props;
      devices_ = devices;
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);

      // start options panel
      optionsPanel_ = new JPanel(new MigLayout(
            "",
            "[right]16[left]",
            "[]8[]"));
     
      // show images checkbox
      final JCheckBox showImagesCheckBox = pu.makeCheckBox("Show images",
              Properties.Keys.PLUGIN_AUTOFOCUS_SHOWIMAGES, panelName_, false);
      optionsPanel_.add(showImagesCheckBox);

      optionsPanel_.setBorder(PanelUtils.makeTitledBorder("Autofocus Options", 
              optionsPanel_));
      
      // show plot checkbox
      final JCheckBox showPlotCheckBox = pu.makeCheckBox("Show plot",
              Properties.Keys.PLUGIN_AUTOFOCUS_SHOWPLOT, panelName_, false);
      optionsPanel_.add(showPlotCheckBox, "wrap");
 
      // spinner with number of images:
      optionsPanel_.add(new JLabel("Number of images:"));
      final JSpinner nrImagesSpinner = pu.makeSpinnerInteger(1, 1000,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_NRIMAGES, 21);
      optionsPanel_.add(nrImagesSpinner, "wrap");
      
      // spinner with stepsize:
      optionsPanel_.add(new JLabel("Step size [\u00B5m]:"));
      final JSpinner stepSizeSpinner = pu.makeSpinnerFloat(0.001, 100., 1.,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_STEPSIZE, 0.5);
      optionsPanel_.add(stepSizeSpinner, "wrap");
      
      // scan either piezo or sheet; select which one
      optionsPanel_.add(new JLabel("Mode:"));
      String[] scanOptions = getModeStrings();
      final JComboBox scanModeCB = pu.makeDropDownBox(scanOptions,
            Devices.Keys.PLUGIN, Properties.Keys.AUTOFOCUS_ACQUSITION_MODE,
            scanOptions[0]);
      optionsPanel_.add(scanModeCB, "wrap");
      
      optionsPanel_.add(new JLabel("Scoring algorithm:"));
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
            // push this change to the autofocus device now so that it is clear
            // to the semi-knowledgeable user what will happen
            try {
               AutofocusManager afManager = gui_.getAutofocusManager();
               afManager.setAutofocusMethodByName("OughtaFocus");
               AutofocusPlugin afDevice = afManager.getAutofocusMethod();

               if (afDevice == null) {
                  throw new ASIdiSPIMException("Please define autofocus method in the Autofocus panel");
               }

               // select the appropriate algorithm
               afDevice.setPropertyValue("Maximize",
                       Fitter.getAlgorithmFromPrefCode(
                               prefs_.getInt(MyStrings.PanelNames.AUTOFOCUS.toString(),
                                       Properties.Keys.AUTOFOCUS_SCORING_ALGORITHM,
                                       Fitter.Algorithm.VOLATH.getPrefCode())).toString());
            } catch (ASIdiSPIMException se) {
               MyDialogUtils.showError(se.getMessage());
            } catch (Exception ex) {
               MyDialogUtils.showError("Failed to select the Oughtafocus plugin.  Is it present in the mmautofocus directory?");
            }
         }
      });
      optionsPanel_.add(scoringAlgorithmCB, "wrap");
      
      optionsPanel_.add(new JLabel("Fit using:"));
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
      optionsPanel_.add(fitFunctionSelection, "wrap");
            
      optionsPanel_.add(new JLabel("<html>Minimum R<sup>2</sup></html>:"));
      final JSpinner minimumR2Spinner = pu.makeSpinnerFloat(0.0, 1.0, 0.01,
              Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_MINIMUMR2, 0.75);
      optionsPanel_.add(minimumR2Spinner);
      
      // end options subpanel
      
      // start acquisition options panel
      acqOptionsPanel_ = new JPanel(new MigLayout(
            "",
            "[right]8[center]8[left]",
            "[]8[]"));
      acqOptionsPanel_.setBorder(PanelUtils.makeTitledBorder(
              "Autofocus Options during Acquisition", acqOptionsPanel_));
      
      // whether or not to run autofocus at the start of the acquisition
      final JCheckBox beforeStartCheckBox = pu.makeCheckBox("Autofocus before starting acquisition",
              Properties.Keys.PLUGIN_AUTOFOCUS_ACQBEFORESTART, panelName_, false);     
      acqOptionsPanel_.add(beforeStartCheckBox, "center, span 3, wrap");
      
      // autofocus every nth image
      acqOptionsPanel_.add(new JLabel("Autofocus every "));
      eachNTimePointsSpinner_ = pu.makeSpinnerInteger(1, 1000,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_EACHNIMAGES, 10);
      acqOptionsPanel_.add(eachNTimePointsSpinner_);
      acqOptionsPanel_.add(new JLabel( "time points"), "wrap");
      
      // autofocus using this channel
      // TODO: need to update combobox when the channel group changes
      String channelGroup_  = props_.getPropValueString(Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_MULTICHANNEL_GROUP);
      StrVector channels = gui.core().getAvailableConfigs(channelGroup_);
      final JComboBox channelSelect = pu.makeDropDownBox(channels.toArray(), 
              Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_CHANNEL, "");
      // make sure to explicitly set it to something so pref gets written
      channelSelect.setSelectedIndex(channelSelect.getSelectedIndex());
      acqOptionsPanel_.add(new JLabel("Autofocus Channel: "));
      acqOptionsPanel_.add(channelSelect, "left, span 2, wrap");
      
      acqOptionsPanel_.add(new JLabel("Max offset change:"));
      final JSpinner maxOffsetChangeSpinner = pu.makeSpinnerFloat(0, 10, 1,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_MAXOFFSETCHANGE, 2);
      acqOptionsPanel_.add(maxOffsetChangeSpinner);
      acqOptionsPanel_.add(new JLabel("\u00B5m (\u00B1)"), "left, wrap");
      
      setupOptionsPanel_ = new JPanel(new MigLayout(
            "",
            "[right]8[center]8[left]",
            "[]8[]"));
      setupOptionsPanel_.setBorder(PanelUtils.makeTitledBorder(
              "Options During Setup", setupOptionsPanel_));

      autoUpdateOffset_ = pu.makeCheckBox("Automatically update offset if focus found",
            Properties.Keys.PLUGIN_AUTOFOCUS_AUTOUPDATE_OFFSET, panelName_, false);
      setupOptionsPanel_.add(autoUpdateOffset_, "center, span 3, wrap");

      maxOffsetChangeSetupLabel1_ = new JLabel("Max offset change:"); 
      setupOptionsPanel_.add(maxOffsetChangeSetupLabel1_);
      maxOffsetChangeSetupSpinner_ = pu.makeSpinnerFloat(0, 10, 1,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_MAXOFFSETCHANGE_SETUP, 5);
      setupOptionsPanel_.add(maxOffsetChangeSetupSpinner_);
      maxOffsetChangeSetupLabel2_ = new JLabel("\u00B5m (\u00B1)");
      setupOptionsPanel_.add(maxOffsetChangeSetupLabel2_, "left, wrap");
      
      final JComponent[] autoUpdateComponents = { maxOffsetChangeSetupLabel1_,
            maxOffsetChangeSetupSpinner_, maxOffsetChangeSetupLabel2_ };

      // only enable max change field when autoupdate is selected
      // the field's value is only used when autoupdate is selected
      componentsSetEnabled(autoUpdateComponents, autoUpdateOffset_.isSelected());
      autoUpdateOffset_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            componentsSetEnabled(autoUpdateComponents, autoUpdateOffset_.isSelected());
         }
      });
      
      
      // construct the main panel
      super.add(optionsPanel_);
      super.add(acqOptionsPanel_);
      super.add(setupOptionsPanel_);
   }//constructor
   
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
