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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import mmcorej.StrVector;

import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;

import org.micromanager.asidispim.data.Devices;
import org.micromanager.asidispim.data.MyStrings;
import org.micromanager.asidispim.data.Prefs;
import org.micromanager.asidispim.data.Properties;
import org.micromanager.asidispim.utils.AutofocusUtils;
import org.micromanager.asidispim.utils.ListeningJPanel;
import org.micromanager.asidispim.utils.PanelUtils;
import org.micromanager.asidispim.fit.Fitter;

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
            "[right]16[center]",
            "[]8[]"));
      optionsPanel_.setBorder(PanelUtils.makeTitledBorder("Autofocus Options"));
      
      // debug checkbox
      final JCheckBox debugCheckBox = pu.makeCheckBox("Show images",
              Properties.Keys.PLUGIN_AUTOFOCUS_DEBUG, panelName_, false);     
      optionsPanel_.add(debugCheckBox, "center, span 2, wrap");
 
      // spinner with number of images:
      optionsPanel_.add(new JLabel("Number of Images:"));
      final JSpinner nrImagesSpinner = pu.makeSpinnerInteger(1, 1000,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_NRIMAGES, 10);
      optionsPanel_.add(nrImagesSpinner, "wrap");
      
      // spinner with stepsize:
      optionsPanel_.add(new JLabel("Step size [\u00B5m]:"));
      final JSpinner stepSizeSpinner = pu.makeSpinnerFloat(0.001, 100., 1.,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_STEPSIZE, 10);
      optionsPanel_.add(stepSizeSpinner, "wrap");
      
      optionsPanel_.add(new JLabel("Algorithm:"));
      JButton afcButton = new JButton();
            afcButton.setFont(new Font("Arial", Font.PLAIN, 10));
      afcButton.setMargin(new Insets(0, 0, 0, 0));
      afcButton.setIconTextGap(4);
      Icon wrench = new ImageIcon(getClass().getResource(
              "/org/micromanager/icons/wrench_orange.png"));
      afcButton.setIcon(wrench);
      afcButton.setMinimumSize(new Dimension(30, 15));
      afcButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            gui_.getAutofocusManager().showOptionsDialog();
         }
      });
      optionsPanel_.add(afcButton, "wrap");
      
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
            
      optionsPanel_.add(new JLabel("Minimum R²:"));
      final JSpinner minimumR2Spinner = pu.makeSpinnerFloat(0.0, 1.0, 0.01,
              Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_MINIMUMR2, 0.75);
      optionsPanel_.add(minimumR2Spinner);
      
      // end options subpanel
      
      // start acquisiion optoinspanel
      acqOptionsPanel_ = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]8[]"));
      acqOptionsPanel_.setBorder(PanelUtils.makeTitledBorder(
              "Autofocus Options during Acquisition"));
      
      // whether or not to run autofocus at the start of the acquisition
      final JCheckBox beforeStartCheckBox = pu.makeCheckBox("Autofocus before starting acquisition",
              Properties.Keys.PLUGIN_AUTOFOCUS_ACQBEFORESTART, panelName_, false);     
      acqOptionsPanel_.add(beforeStartCheckBox, "center, span 3, wrap");
      
      // autofocus every nth image
      acqOptionsPanel_.add(new JLabel("Autofocus every "));
      final JSpinner eachNTimePointsSpinner = pu.makeSpinnerInteger(1, 1000,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_EACHNIMAGES, 10);
      acqOptionsPanel_.add(eachNTimePointsSpinner);
      acqOptionsPanel_.add(new JLabel( "Time points"), "wrap");
      
      // autofocus using this channel
      // TODO: need to update when the channel group changes
      String channelGroup_  = props_.getPropValueString(Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_MULTICHANNEL_GROUP);
      StrVector channels = gui.core().getAvailableConfigs(channelGroup_);
      final JComboBox channelSelect = pu.makeDropDownBox(channels.toArray(), 
              Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_AUTOFOCUS_CHANNEL, "");
      acqOptionsPanel_.add(new JLabel("Autofocus Channel: "));
      acqOptionsPanel_.add(channelSelect, "wrap");

      
      // construct the main panel
      add(optionsPanel_);
      add (acqOptionsPanel_);
       
      
      
   }
   
}
