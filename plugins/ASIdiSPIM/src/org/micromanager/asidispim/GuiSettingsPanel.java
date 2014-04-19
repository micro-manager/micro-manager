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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;
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

import org.micromanager.api.ScriptInterface;

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
   private final JSpinner scannerFilterX_;
   private final JSpinner scannerFilterY_;
   private final StagePositionUpdater stagePosUpdater_;
   private final ScriptInterface gui_;
     
   /**
    * 
    * @param devices the (single) instance of the Devices class
    */
   public GuiSettingsPanel(ScriptInterface gui, Devices devices, Properties props, Prefs prefs,
         StagePositionUpdater stagePosUpdater) {    
      super ("GUI Settings", 
            new MigLayout(
              "", 
              "[right]",
              "[]16[]"));
     
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      gui_ = gui;
      stagePosUpdater_ = stagePosUpdater;
      
      // copy plugin "property" values from prefs to props
      props_.setPropValue(Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL, // in seconds
            prefs_.getFloat(panelName_, Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL, 1));
      
      
      PanelUtils pu = new PanelUtils(gui_);
      
      final JCheckBox activeTimerCheckBox = new JCheckBox("Update positions continually");
      ActionListener ae = new ActionListener() {
         public void actionPerformed(ActionEvent e) { 
            if (activeTimerCheckBox.isSelected()) {
               stagePosUpdater_.start();
            } else {
               stagePosUpdater_.stop();
            }
            prefs_.putBoolean(panelName_, Prefs.Keys.ENABLE_POSITION_UPDATES, activeTimerCheckBox.isSelected());
         }
      }; 
      activeTimerCheckBox.addActionListener(ae);
      activeTimerCheckBox.setSelected(prefs_.getBoolean(panelName_, Prefs.Keys.ENABLE_POSITION_UPDATES, true));
      // programmatically click twice to make sure the action handler is called;
      //   it is not called by setSelected unless there is a change in the value
      activeTimerCheckBox.doClick();
      activeTimerCheckBox.doClick();
      add(activeTimerCheckBox, "center, span 2, wrap");
      
      add(new JLabel("Position refresh interval (s):"));
      positionRefreshInterval_ = pu.makeSpinnerFloat(0.5, 1000, 0.5, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.PLUGIN},
            Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL);
      ChangeListener listenerLast = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            if (stagePosUpdater_.isRunning()) {
               // restart, doing this grabs the interval from the plugin property
               stagePosUpdater_.start();
            }
         }
      };
      pu.addListenerLast(positionRefreshInterval_, listenerLast);
      add(positionRefreshInterval_, "wrap");
      
      
      add(new JSeparator(JSeparator.VERTICAL), "growy, cell 2 0 1 9");
      
      add(new JLabel("Scanner filter freq, sheet axis (kHz):"), "cell 3 0");
      scannerFilterX_ = pu.makeSpinnerFloat(0.1, 5, 0.1, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SCANNER_FILTER_X);
      add(scannerFilterX_, "wrap");
      
      add(new JLabel("Scanner filter freq, slice axis (kHz):"), "cell 3 1");
      scannerFilterY_ = pu.makeSpinnerFloat(0.1, 5, 0.1, props_, devices_, 
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SCANNER_FILTER_Y);
      add(scannerFilterY_, "wrap");
      
      
      
      
   }
   
   @Override
   public void saveSettings() {
      // save 
      prefs_.putFloat(panelName_, Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL,
            props_.getPropValueFloat(Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL));
   }
   
   
}
