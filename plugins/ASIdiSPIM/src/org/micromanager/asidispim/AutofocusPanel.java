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


import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.AutofocusUtils;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;

/**
 *
 * @author nico
 */
@SuppressWarnings("serial")
public class AutofocusPanel extends ListeningJPanel{
   final private Properties props_;
   final private Prefs prefs_;
   final private Devices devices_;
   final private AutofocusUtils autofocus_;
   
   private final JPanel optionsPanel_;
   
   public AutofocusPanel(Devices devices, Properties props, Prefs prefs, 
           AutofocusUtils autofocus) {
            super(MyStrings.PanelNames.AUTOFOCUS.toString(),
              new MigLayout(
              "",
              "[center]8[center]",
              "[]16[]16[]"));
      prefs_ = prefs;
      autofocus_ = autofocus;
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
              Properties.Keys.PLUGIN_AUTOFOCUS_DEBUG, panelName_, true);
      debugCheckBox.addItemListener(new ItemListener() {
         @Override
         public void itemStateChanged(ItemEvent e) {
            autofocus_.setDebug(debugCheckBox.isSelected());
         }
      });
      optionsPanel_.add(debugCheckBox, "center, span 2, wrap");
      autofocus_.setDebug(debugCheckBox.isSelected());
 
      // spinner with number of images:
      optionsPanel_.add(new JLabel("Number of Images:"));
      final JSpinner nrImagesSpinner = pu.makeSpinnerInteger(1, 1000,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_NRIMAGES, 10);
      ChangeListener listenerLast = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            prefs_.putInt(panelName_, Properties.Keys.PLUGIN_AUTOFOCUS_NRIMAGES,
                  (Integer) nrImagesSpinner.getValue());
            autofocus_.setNumberOfImages((Integer) nrImagesSpinner.getValue());
         }
      };
      pu.addListenerLast(nrImagesSpinner, listenerLast);
      autofocus_.setNumberOfImages((Integer) nrImagesSpinner.getValue());
      optionsPanel_.add(nrImagesSpinner, "wrap");
      
      // spinner with stepsize:
      optionsPanel_.add(new JLabel("Step size [\u00B5m]:"));
      final JSpinner stepSizeSpinner = pu.makeSpinnerFloat(0.001, 100., 1.,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_AUTOFOCUS_STEPSIZE, 10);
      listenerLast = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            float val = PanelUtils.getSpinnerFloatValue(stepSizeSpinner);
            prefs_.putFloat(panelName_, Properties.Keys.PLUGIN_AUTOFOCUS_STEPSIZE,
                  val);
         }
      };
      pu.addListenerLast(stepSizeSpinner, listenerLast);
      optionsPanel_.add(stepSizeSpinner, "wrap");
      // end options subpanel
      
      
      // construct the main panel
      add(optionsPanel_);
       
   }
   
}
