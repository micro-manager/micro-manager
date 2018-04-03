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

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.ParseException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.DefaultFormatter;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ImageJUtils;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.asidispim.Utils.StagePositionUpdater;
import org.micromanager.utils.FileDialogs;

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
   
   private final JFormattedTextField rawPath_;
   private final JSpinner liveScanMs_;
   
   /**
    * 
    * @param gui Micro-Manager api
    * @param devices the (single) instance of the Devices class
    * @param props Plugin-wide properties
    * @param prefs Plugin-wide preferences
    * @param stagePosUpdater Can query the controller for stage positionns
    */
   public SettingsPanel(final ScriptInterface gui, Devices devices, Properties props, 
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
      final JSpinner scannerFilterX = pu.makeSpinnerFloat(0.1, 1, 0.1,
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SCANNER_FILTER_X, 0.8);
      scannerPanel.add(scannerFilterX, "wrap");
      
      scannerPanel.add(new JLabel("Filter freq, slice axis [kHz]:"));
      final JSpinner scannerFilterY = pu.makeSpinnerFloat(0.1, 1, 0.1,
            new Devices.Keys [] {Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SCANNER_FILTER_Y, 0.4);
      scannerPanel.add(scannerFilterY, "wrap");
      
      scannerPanel.add(new JLabel("Live scan period [ms]:"));
      liveScanMs_ = pu.makeSpinnerInteger(1, 10000,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_CAMERA_LIVE_SCAN, 10);
      liveScanMs_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent arg0) {
            int scan = (Integer) liveScanMs_.getValue(); 
            props_.setPropValue(new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
                  Properties.Keys.SA_PERIOD_X, scan, true);
         }
      });
      // set it the first time
      props_.setPropValue(new Devices.Keys[]{Devices.Keys.GALVOA, Devices.Keys.GALVOB},
            Properties.Keys.SA_PERIOD_X, (Integer) liveScanMs_.getValue(), true);
      scannerPanel.add(liveScanMs_, "wrap");
      
      // end scanner panel
      
      // start acquisition panel
      
      final JPanel acqusitionPanel = new JPanel(new MigLayout(
            "",
            "[left]",
            "[]8[]"));
      acqusitionPanel.setBorder(PanelUtils.makeTitledBorder("Acquisition"));

      final JCheckBox acqSettingsWrite = pu.makeCheckBox("Write file with acquisition settings",
            Properties.Keys.PLUGIN_WRITE_ACQ_SETTINGS_FILE, panelName_, false);
      acqusitionPanel.add(acqSettingsWrite, "wrap");
      
      final JCheckBox acqBothCamerasSimult = pu.makeCheckBox("Acquire from both cameras simultaneously",
            Properties.Keys.PLUGIN_ACQUIRE_BOTH_CAMERAS_SIMULT, panelName_, false);
      acqusitionPanel.add(acqBothCamerasSimult, "wrap");
      
      // end acquisiton panel
      
      
      // start test acquisition panel
      
      final JPanel testAcqPanel = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]8[]"));
      testAcqPanel.setBorder(PanelUtils.makeTitledBorder("Test Acquisition"));
      
      final JCheckBox testAcqSave = pu.makeCheckBox("Save test acquisition as raw data",
            Properties.Keys.PLUGIN_TESTACQ_SAVE, panelName_, false);
      testAcqPanel.add(testAcqSave, "span 2, wrap");
      
      DefaultFormatter formatter = new DefaultFormatter();
      rawPath_ = new JFormattedTextField(formatter);
      rawPath_.setText( prefs_.getString(panelName_, 
              Properties.Keys.PLUGIN_TESTACQ_PATH, "") );
      rawPath_.addPropertyChangeListener(new PropertyChangeListener() {
         // will respond to commitEdit() as well as GUI edit on commit
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            prefs_.putString(panelName_, Properties.Keys.PLUGIN_TESTACQ_PATH,
                  rawPath_.getText());
         }
      });
      rawPath_.setColumns(20);
      testAcqPanel.add(rawPath_);

      JButton browseFileButton = new JButton();
      browseFileButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            File result = FileDialogs.openFile(null,
                  "Please choose a file for raw image data",
                  MMStudio.MM_DATA_SET);
            if (result != null) {
               rawPath_.setText(result.getAbsolutePath());
               try {
                  rawPath_.commitEdit();
               } catch (ParseException ex) {
                  MyDialogUtils.showError("Invalid file selected for test acquisition raw data");
               }
            }
         }
      });
      browseFileButton.setMargin(new Insets(2, 5, 2, 5));
      browseFileButton.setText("...");
      testAcqPanel.add(browseFileButton, "wrap");
      
      // end test acquisition panel

      
      // start stage scan panel
      
      final JPanel stageScanPanel = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]8[]"));
      stageScanPanel.setBorder(PanelUtils.makeTitledBorder("Stage scanning"));
      
      // TODO create method to determine this instead of separate code here and in AcquisitionPanel
      if (devices_.isTigerDevice(Devices.Keys.XYSTAGE)
            && props_.hasProperty(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_NUMLINES)) {
         stageScanPanel.add(new JLabel("Relative acceleration time:"));
         final JSpinner stageAccelFactor = pu.makeSpinnerFloat(0.1, 10, 1,
               Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_STAGESCAN_ACCEL_FACTOR, 1);
         stageScanPanel.add(stageAccelFactor, "wrap");
         if (props_.hasProperty(Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_OVERSHOOT_DIST)) {  // present in 3.17 and above
            stageScanPanel.add(new JLabel("Scan overshoot distance [" + "\u00B5"+ "m]:"));
            final JSpinner scanOvershootDistance = pu.makeSpinnerInteger(0, 1000,
                  Devices.Keys.XYSTAGE, Properties.Keys.STAGESCAN_OVERSHOOT_DIST, 0);
            stageScanPanel.add(scanOvershootDistance, "wrap");
         }
      } else {
         stageScanPanel.add(new JLabel("Stage scanning not supported by your"), "left, wrap");
         stageScanPanel.add(new JLabel("Tiger firmware.  See http://dispim.org"), "left, wrap");
         stageScanPanel.add(new JLabel("for further information."), "left, wrap");
      }
      
      // end stage scan panel
      
      
      // start ImageJ settings panel
      
      final JPanel imageJPanel = new JPanel(new MigLayout(
            "",
            "[right]16[center]",
            "[]8[]"));
      imageJPanel.setBorder(PanelUtils.makeTitledBorder("ImageJ"));
      
      final JCheckBox useToolset = pu.makeCheckBox("Load diSPIM toolset on launch",
            Properties.Keys.PLUGIN_USE_TOOLSET, panelName_, true);
      useToolset.setToolTipText("places icons in ImageJ toolbar for quick access of commonly-used image manipulation tasks");
      if (useToolset.isSelected()) {
         ImageJUtils.loadToolset();
      }
      imageJPanel.add(useToolset, "span 2, wrap");
      
      // end ImageJ settings panel
      
      
      
      // construct main panel
      super.add(guiPanel);
      super.add(scannerPanel);
      super.add(acqusitionPanel, "wrap");
      super.add(testAcqPanel);
      super.add(stageScanPanel, "growx");
      super.add(imageJPanel, "growx");
      

      
   }
   
   @Override
   public void saveSettings() {

   }
   
   
}
