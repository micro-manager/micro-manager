///////////////////////////////////////////////////////////////////////////////
//FILE:          LightSourcePanel.java
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


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.DeviceUtils;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class LightSourcePanel extends ListeningJPanel {
   private final ScriptInterface gui_;
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   
   private final JPanel spimPanel_;
   
   private final JComboBox spimSourceCB_;
   private final JSpinner spimSourcePosition_;
   private final JSpinner spimSourceIntensity_;
   private final JCheckBox spimEnabled_;
   
   /**
    * 
    * @param gui -implementation of the Micro-Manager ScriptInterface api
    */
   public LightSourcePanel(ScriptInterface gui, Devices devices, 
         Properties props, Prefs prefs) {    
      super (MyStrings.PanelNames.LIGHTSOURCE.toString(), 
            new MigLayout(
              "", 
              "[right]16[left]",
              "[]16[]"));
      
      gui_ = gui;
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      
      DeviceUtils du = new DeviceUtils(gui, devices, props);
      PanelUtils pu = new PanelUtils(gui_, prefs_, props_, devices_);
      
      // begin SPIM source panel
      
      spimPanel_ = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]8[]"));
      
      spimPanel_.setBorder(PanelUtils.makeTitledBorder("SPIM Light Source"));
      
      spimPanel_.add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.SOURCE_SPIM) + ":"));
      spimSourceCB_ = du.makeDeviceSelectionBox(new mmcorej.DeviceType[]
            {mmcorej.DeviceType.StateDevice, mmcorej.DeviceType.ShutterDevice}, Devices.Keys.SOURCE_SPIM); 
      spimPanel_.add(spimSourceCB_, "wrap");
      
      // TODO add for each "channel" of acquisition once we have that feature
      // TODO implement combobox with choices for particular light source??
      // TODO implement updateFromProperties-type function when this is changed
      spimPanel_.add(new JLabel("Position:"));
      spimSourcePosition_ = pu.makeSpinnerInteger(1, 4,
            new Devices.Keys[]{Devices.Keys.PLUGIN},
            Properties.Keys.PLUGIN_SPIM_SOURCE_POSITION, 0);
      spimPanel_.add(spimSourcePosition_, "wrap");
      
      spimPanel_.add(new JLabel("Intensity:"));
      spimSourceIntensity_ = pu.makeSpinnerFloat(0.0, 100.0, 1.0,
            new Devices.Keys[]{Devices.Keys.SOURCE_SPIM},
            Properties.Keys.PLUGIN_SPIM_SOURCE_INTENSITY, 0);
      spimPanel_.add(spimSourceIntensity_, "wrap");
      // TODO make this function in separate utility file for light sources
      spimSourceIntensity_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent ce) {
            float newVal = PanelUtils.getSpinnerFloatValue(spimSourceIntensity_);
            switch(devices_.getMMDeviceLibrary(Devices.Keys.SOURCE_SPIM)) {
            case TOPTICA_MLE:
               props_.setPropValue(Devices.Keys.SOURCE_SPIM,
                     Properties.Keys.TOPTICA_LASER_LEVEL,
                     newVal, true,
                     ((Integer)spimSourcePosition_.getValue()).toString());
               break;
            default:
               break;
            }
         }
      });
      
      spimEnabled_ = new JCheckBox("Source on");
      spimEnabled_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            switch(devices_.getMMDeviceLibrary(Devices.Keys.SOURCE_SPIM)) {
            case TOPTICA_MLE:
               int newVal = spimEnabled_.isSelected() ? 1 : 0;
               props_.setPropValue(Devices.Keys.SOURCE_SPIM,
                     Properties.Keys.TOPTICA_LASER_EMISSION,
                     newVal, true,
                     ((Integer)spimSourcePosition_.getValue()).toString());
               break;
            default:
               break;
            }
         }
      }); 

      spimPanel_.add(spimEnabled_, "center, span 2");
      
      
      // end SPIM source panel
      
      
      
      add(spimPanel_, "spany2, top");
      
      
      
   }//constructor
   
   
   
}
