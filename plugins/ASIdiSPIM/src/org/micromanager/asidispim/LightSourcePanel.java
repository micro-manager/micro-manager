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

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.DeviceUtils;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;

import mmcorej.CMMCore;
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
   private final CMMCore core_;
   
   private final JComboBox spimSourceCB_;
   private final JSpinner spimSourcePosition_;
   
   /**
    * 
    * @param gui -implementation of the Micro-Manager ScriptInterface api
    */
   public LightSourcePanel(ScriptInterface gui, Devices devices, 
         Properties props, Prefs prefs) {    
      super ("Light", 
            new MigLayout(
              "", 
              "[right]16[left]",
              "[]16[]"));
      
      gui_ = gui;
      core_ = gui_.getMMCore();
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      
      DeviceUtils du = new DeviceUtils(gui, devices, props);
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.SOURCE_SPIM) + ":"));
      spimSourceCB_ = du.makeDeviceSelectionBox(new mmcorej.DeviceType[]
            {mmcorej.DeviceType.StateDevice, mmcorej.DeviceType.ShutterDevice}, Devices.Keys.SOURCE_SPIM); 
//      spimSourceCB_.addActionListener(new DevicesPanel.DeviceBoxListener(Devices.Keys.SOURCE_SPIM, spimSourceCB_));
      add(spimSourceCB_, "wrap");
      
      // TODO add for each "channel" of acquisition once we have that feature
      add(new JLabel("SPIM source position:"));
      PanelUtils pu = new PanelUtils(gui_, prefs_);
      spimSourcePosition_ = pu.makeSpinnerInteger(0, 5, props_, devices_,
            new Devices.Keys[]{Devices.Keys.PLUGIN},
            Properties.Keys.PLUGIN_SPIM_SOURCE_POSITION, 0);
      add(spimSourcePosition_, "wrap");
      
      
   }//constructor
   
   
   
   
}
