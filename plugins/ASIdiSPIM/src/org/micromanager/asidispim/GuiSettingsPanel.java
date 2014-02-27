///////////////////////////////////////////////////////////////////////////////
//FILE:          GuiSettingsPanel.java
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


import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.asidispim.Utils.StagePositionUpdater;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class GuiSettingsPanel extends ListeningJPanel {
   
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   private final JSpinner positionRefreshInterval_;
   private final StagePositionUpdater stagePosUpdater_;
     
   /**
    * 
    * @param devices the (single) instance of the Devices class
    */
   public GuiSettingsPanel(Devices devices, Properties props, Prefs prefs,
         StagePositionUpdater stagePosUpdater) {    
      super ("GUI Settings", 
            new MigLayout(
              "", 
              "[right]",
              "[]16[]"));
     
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      stagePosUpdater_ = stagePosUpdater;
      
      panelName_ = super.panelName_;

      // copy plugin "property" values from prefs to props
      props_.setPropValue(Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL, // in seconds
            prefs_.getFloat(panelName_, Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL, 1));
      
      
      PanelUtils pu = new PanelUtils();
      add(new JLabel("Position refresh interval (s):"));
      positionRefreshInterval_ = pu.makeSpinnerFloat(0.5, 1000, 0.5, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.PLUGIN},
            Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL);
      ChangeListener listenerLast = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            stagePosUpdater_.start();
         }
      };
      pu.addListenerLast(positionRefreshInterval_, listenerLast);
      add(positionRefreshInterval_, "wrap");
      

   }
   
   @Override
   public void saveSettings() {
      // save 
      prefs_.putFloat(panelName_, Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL,
            props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL));
   }
   
   
}
