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


import javax.swing.JComboBox;
import javax.swing.JLabel;
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
   
   private final JComboBox spimSourceCB_;
   private final JSpinner spimSourcePosition_;
   private final JSpinner spimSourceIntensity_;
   
   /**
    * 
    * @param gui -implementation of the Micro-Manager ScriptInterface api
    */
   public LightSourcePanel(ScriptInterface gui, Devices devices, 
         Properties props, Prefs prefs) {    
      super (MyStrings.TabNames.LIGHTSOURCE.toString(), 
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
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.SOURCE_SPIM) + ":"));
      spimSourceCB_ = du.makeDeviceSelectionBox(new mmcorej.DeviceType[]
            {mmcorej.DeviceType.StateDevice, mmcorej.DeviceType.ShutterDevice}, Devices.Keys.SOURCE_SPIM); 
      add(spimSourceCB_, "wrap");
      
      // TODO add for each "channel" of acquisition once we have that feature
      add(new JLabel("SPIM source position:"));
      spimSourcePosition_ = pu.makeSpinnerInteger(0, 5,
            new Devices.Keys[]{Devices.Keys.PLUGIN},
            Properties.Keys.PLUGIN_SPIM_SOURCE_POSITION, 0);
      add(spimSourcePosition_, "wrap");
      
      add(new JLabel("SPIM source Intensity:"));
      spimSourceIntensity_ = pu.makeSpinnerFloat(0.0, 100.0, 1.0,
            new Devices.Keys[]{Devices.Keys.SOURCE_SPIM},
            Properties.Keys.PLUGIN_SPIM_SOURCE_INTENSITY, 0);
      add(spimSourceIntensity_, "wrap");
      spimSourceIntensity_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent ce) {
            float newVal = PanelUtils.getSpinnerFloatValue(spimSourceIntensity_);
            switch(devices_.getMMDeviceLibrary(Devices.Keys.SOURCE_SPIM)) {
            case TOPTICA_MLE:
               props_.setPropValue(Devices.Keys.SOURCE_SPIM,
                     Properties.Keys.TOPTICA_LASER_LEVEL,
                     newVal, true, new Object[]{(Integer) spimSourcePosition_.getValue()});
               break;
            default:
               break;
            }
         }
      });
      
      
      
      
      
      
   }//constructor
   
   
   
   
}
