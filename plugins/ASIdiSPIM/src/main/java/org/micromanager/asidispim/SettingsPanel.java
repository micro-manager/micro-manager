///////////////////////////////////////////////////////////////////////////////
//FILE:          SettingsPanel.java
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

import org.micromanager.asidispim.data.CameraModes;
import org.micromanager.asidispim.data.Devices;
import org.micromanager.asidispim.data.MyStrings;
import org.micromanager.asidispim.data.Prefs;
import org.micromanager.asidispim.data.Properties;
import org.micromanager.asidispim.utils.ListeningJPanel;
import org.micromanager.asidispim.utils.PanelUtils;
import org.micromanager.asidispim.utils.StagePositionUpdater;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class SettingsPanel extends ListeningJPanel {
   
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   private final StagePositionUpdater stagePosUpdater_;
   
   /**
    * 
    * @param devices the (single) instance of the Devices class
    * @param props 
    * @param prefs
    * @param stagePosUpdater
    */
   public SettingsPanel(Devices devices, Properties props, 
         Prefs prefs, StagePositionUpdater stagePosUpdater) {    
      super (MyStrings.PanelNames.SETTINGS.toString(), 
            new MigLayout(
              "", 
              "[right]16[center]16[center]",
              "[]16[]"));
     
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      stagePosUpdater_ = stagePosUpdater;
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);

      
      // start GUI panel
      
      final JPanel guiPanel = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]8[]"));
      guiPanel.setBorder(PanelUtils.makeTitledBorder("GUI"));
      
      final JCheckBox activeTimerCheckBox = pu.makeCheckBox("Update axis positions continually",
            Properties.Keys.PREFS_ENABLE_POSITION_UPDATES, panelName_, true); 
      ActionListener ae = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) { 
            if (activeTimerCheckBox.isSelected()) {
               stagePosUpdater_.start();
            } else {
               stagePosUpdater_.stop();
            }
         }
      };
      activeTimerCheckBox.addActionListener(ae);
      // programmatically click twice to make sure the action handler is called;
      //   it is not called by setSelected unless there is a change in the value
      activeTimerCheckBox.doClick();
      activeTimerCheckBox.doClick();
      guiPanel.add(activeTimerCheckBox, "center, span 2, wrap");
      
      guiPanel.add(new JLabel("Position refresh interval (s):"));
      final JSpinner positionRefreshInterval = pu.makeSpinnerFloat(0.5, 1000, 0.5,
            Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL, 1);
      ChangeListener listenerLast = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            stagePosUpdater_.restartIfRunning();
            prefs_.putFloat(panelName_, Properties.Keys.PLUGIN_POSITION_REFRESH_INTERVAL,
                  PanelUtils.getSpinnerFloatValue(positionRefreshInterval));
         }
      };
      pu.addListenerLast(positionRefreshInterval, listenerLast);
      guiPanel.add(positionRefreshInterval, "wrap");
      
      final JCheckBox ignoreScannerMissing = pu.makeCheckBox("Ignore missing scanner (for debug)",
            Properties.Keys.PREFS_IGNORE_MISSING_SCANNER, panelName_, false);
      guiPanel.add(ignoreScannerMissing, "center, span 2, wrap");
      
      // end GUI subpanel
      
      // start scanner panel
      
      final JPanel scannerPanel = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]8[]"));
      scannerPanel.setBorder(PanelUtils.makeTitledBorder("Scanner"));

      scannerPanel.add(new JLabel("Filter freq, sheet axis [kHz]:"));
      final JSpinner scannerFilterX = pu.makeSpinnerFloat(0.1, 5, 0.1,
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SCANNER_FILTER_X, 0.8);
      scannerPanel.add(scannerFilterX, "wrap");
      
      scannerPanel.add(new JLabel("Filter freq, slice axis [kHz]:"));
      final JSpinner scannerFilterY = pu.makeSpinnerFloat(0.1, 5, 0.1,
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SCANNER_FILTER_Y, 0.4);
      scannerPanel.add(scannerFilterY, "wrap");
      
      final JCheckBox scanOppositeDirectionsCB = pu.makeCheckBox("Scan beam opposite directions each slice",
            Properties.Keys.PREFS_SCAN_OPPOSITE_DIRECTIONS, panelName_, false);
      scannerPanel.add(scanOppositeDirectionsCB, "center, span 2, wrap");
      
      // end scanner panel
      
      
      // start camera panel
      
      final JPanel cameraPanel = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]8[]"));
      cameraPanel.setBorder(PanelUtils.makeTitledBorder("Camera"));
      CameraModes camModeObject = new CameraModes(devices_, props_, prefs_);
      JComboBox camModeCB = camModeObject.getComboBox();
      cameraPanel.add(camModeCB);
      
      // end camera panel
      
      
      // start stage scan panel
      
      final JPanel stageScanPanel = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]8[]"));
      stageScanPanel.setBorder(PanelUtils.makeTitledBorder("Stage scanning"));

      stageScanPanel.add(new JLabel("Motor acceleration time [ms]:"));
      final JSpinner stageAccelTime = pu.makeSpinnerFloat(10, 1000, 10,
            Devices.Keys.XYSTAGE,
            Properties.Keys.STAGESCAN_MOTOR_ACCEL, 50);
      stageScanPanel.add(stageAccelTime, "wrap");
      
      // end stage scan panel
      
      // construct main panel
      add(guiPanel);
      add(scannerPanel);
      add(cameraPanel, "wrap");
      add(stageScanPanel, "growx");
      
      
   }
   
   @Override
   public void saveSettings() {

   }
   
   
}
