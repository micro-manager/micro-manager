/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2024, Applied Scientific Instrumentation
 */

package com.asiimaging.crisp.panels;

import com.asiimaging.crisp.data.Defaults;
import com.asiimaging.crisp.utils.DialogUtils;
import com.asiimaging.devices.crisp.CRISP;
import com.asiimaging.devices.crisp.CRISPSettings;
import com.asiimaging.devices.crisp.CRISPTimer;
import com.asiimaging.devices.crisp.PropName;
import com.asiimaging.ui.Button;
import com.asiimaging.ui.CheckBox;
import com.asiimaging.ui.ComboBox;
import com.asiimaging.ui.Panel;
import com.asiimaging.ui.Spinner;
import java.util.Objects;
import javax.swing.JLabel;

/**
 * The ui panel for setting CRISP settings.
 */
public class SpinnerPanel extends Panel {

   private JLabel lblDeviceAxis;
   private JLabel lblLEDIntensity;
   private JLabel lblObjectiveNA;
   private JLabel lblLoopGain;
   private JLabel lblNumAverages;
   private JLabel lblUpdateRateMs;
   private JLabel lblLockRange;
   private JLabel lblPollRate;
   private JLabel lblSelectSettings;
   private Spinner spnLEDIntensity;
   private Spinner spnLoopGain;
   private Spinner spnNumAverages;
   private Spinner spnUpdateRateMs;
   private Spinner spnObjectiveNA;
   private Spinner spnLockRange;
   private Spinner spnPollRate;
   private CheckBox chkEnablePolling;
   private ComboBox cmbSelectSettings;
   private Button btnAddNewSettings;
   private Button btnRemoveSettings;

   private final CRISP crisp;
   private final CRISPTimer timer;

   public SpinnerPanel(final CRISP crisp, final CRISPTimer timer) {
      this.crisp = Objects.requireNonNull(crisp);
      this.timer = Objects.requireNonNull(timer);
      createUserInterface();
      createEventHandlers();
   }

   /**
    * Create the user interface.
    */
   private void createUserInterface() {
      // spinner labels
      lblDeviceAxis = new JLabel("Axis");
      lblLEDIntensity = new JLabel("LED Intensity [%]");
      lblObjectiveNA = new JLabel("Objective NA");
      lblLoopGain = new JLabel("Loop Gain");
      lblNumAverages = new JLabel("Averaging");
      lblUpdateRateMs = new JLabel("Update Rate [ms]");
      lblLockRange = new JLabel("Lock Range [mm]");
      lblPollRate = new JLabel("Polling Rate [ms]");

      // get property limits
      final int lowerLimitLED = (int)crisp.getPropertyLowerLimit(PropName.LED_INTENSITY);
      final int upperLimitLED = (int)crisp.getPropertyUpperLimit(PropName.LED_INTENSITY);
      final int lowerLimitGain = (int)crisp.getPropertyLowerLimit(PropName.GAIN);
      final int upperLimitGain = (int)crisp.getPropertyUpperLimit(PropName.GAIN);
      final int lowerLimitUpdateRate = (int)crisp.getPropertyLowerLimit(PropName.UPDATE_RATE_MS);
      final int upperLimitUpdateRate = (int)crisp.getPropertyUpperLimit(PropName.UPDATE_RATE_MS);
      final int lowerLimitAverages = (int)crisp.getPropertyLowerLimit(PropName.NUMBER_OF_AVERAGES);
      final int upperLimitAverages = (int)crisp.getPropertyUpperLimit(PropName.NUMBER_OF_AVERAGES);
      final double lowerLimitObjNA = crisp.getPropertyLowerLimit(PropName.OBJECTIVE_NA);
      final double upperLimitObjNA = crisp.getPropertyUpperLimit(PropName.OBJECTIVE_NA);

      // spinners to change CRISP state
      spnLEDIntensity = Spinner.createIntegerSpinner(
              Defaults.LED_INTENSITY, lowerLimitLED, upperLimitLED, 1);
      spnLoopGain = Spinner.createIntegerSpinner(
              Defaults.LOOP_GAIN, lowerLimitGain, upperLimitGain, 1);
      spnUpdateRateMs = Spinner.createIntegerSpinner(
              Defaults.UPDATE_RATE_MS, lowerLimitUpdateRate, upperLimitUpdateRate, 1);
      spnNumAverages = Spinner.createIntegerSpinner(
              Defaults.NUM_AVERAGES, lowerLimitAverages, upperLimitAverages, 1);
      spnObjectiveNA = Spinner.createDoubleSpinner(
              Defaults.OBJECTIVE_NA, lowerLimitObjNA, upperLimitObjNA, 0.01);

      // Note: no property limits in the Device Adapter
      spnLockRange = Spinner.createDoubleSpinner(Defaults.LOCK_RANGE, 0.0, 2.0, 0.05);

      // poll rate in milliseconds
      spnPollRate = Spinner.createIntegerSpinner(Defaults.POLL_RATE_MS, 50, 5000, 50);

      // enable or disable the CRISP polling timer
      chkEnablePolling = new CheckBox("Polling Enabled", true);
      chkEnablePolling.setToolTipText("Check to enable updating the status text.");

      // select the settings to use
      lblSelectSettings = new JLabel("Settings:");
      final String[] labels = {CRISPSettings.DEFAULT_PROFILE_NAME};
      cmbSelectSettings = new ComboBox(labels, labels[0], 100, 20);

      // increase and decrease number of software settings profiles
      btnAddNewSettings = new Button("+", 40, 20);
      btnRemoveSettings = new Button("-", 40, 20);

      // spinner tooltips
      // "Property:" is the associated Micro-Manager property name
      // "Serial command:" is the serial command sent to the controller
      lblLEDIntensity.setToolTipText("<html>Set the intensity of the LED.<br>" +
              "Property: <b>LED Intensity</b><br>Serial command: <b>UL X</b></html>");
      lblObjectiveNA.setToolTipText("<html>Set the objective numerical aperture.<br>" +
              "Property: <b>Objective NA</b><br>Serial command: <b>LR Y</b></html>");
      lblLoopGain.setToolTipText("<html>Set the loop gain.<br>" +
              "Property: <b>GainMultiplier</b><br>Serial command: <b>LR T</b></html>");
      lblNumAverages.setToolTipText("<html>Set the number of averages.<br>" +
              "Property: <b>Number of Averages</b><br>Serial command: <b>RT F</b></html>");
      lblUpdateRateMs.setToolTipText("<html>Set the trajectory update rate in milliseconds.<br>" +
              "Property: <b>Number of Skips</b><br>Serial command: <b>UL Y</b></html>");
      lblLockRange.setToolTipText("<html>Set the max focus lock range in millimeters.<br>" +
              "Property: <b>Max Lock Range(mm)</b><br>Serial command: <b>LR Z</b></html>");

      lblPollRate.setToolTipText(
              "The rate in milliseconds that the device is polled to update the status text.");

      // software settings profiles tooltips
      lblSelectSettings.setToolTipText("The software settings profiles are saved in the plugin settings.");
      cmbSelectSettings.setToolTipText("Select the software settings profile.");
      btnAddNewSettings.setToolTipText("Add a new software settings profile.");
      btnRemoveSettings.setToolTipText("Remove the last software settings profile.");

      // add components to panel
      add(lblDeviceAxis, "right, span 2, wrap");
      add(lblLEDIntensity, "");
      add(spnLEDIntensity, "wrap");
      add(lblObjectiveNA, "");
      add(spnObjectiveNA, "wrap");
      add(lblLoopGain, "");
      add(spnLoopGain, "wrap");
      add(lblNumAverages, "");
      add(spnNumAverages, "wrap");
      add(lblUpdateRateMs, "");
      add(spnUpdateRateMs, "wrap");
      add(lblLockRange, "");
      add(spnLockRange, "wrap");
      add(lblPollRate, "");
      add(spnPollRate, "wrap");
      add(chkEnablePolling, "wrap");
      add(lblSelectSettings, "wrap");
      add(cmbSelectSettings, "wrap");
      add(btnAddNewSettings, "split 2");
      add(btnRemoveSettings, "split 2");
   }

   /**
    * Create the event handlers for Spinner objects.
    */
   private void createEventHandlers() {
      // changes the LED intensity
      spnLEDIntensity.registerListener(event -> {
         final int ledIntensity = spnLEDIntensity.getInt();
         crisp.getSettings().setLEDIntensity(ledIntensity);
         crisp.setLEDIntensity(ledIntensity);
      });

      // set this value to the objective numerical aperture
      spnObjectiveNA.registerListener(event -> {
         final double objectiveNA = spnObjectiveNA.getDouble();
         crisp.getSettings().setObjectiveNA(objectiveNA);
         crisp.setObjectiveNA(objectiveNA);
      });

      // changes the gain multiplier
      spnLoopGain.registerListener(event -> {
         final int gain = spnLoopGain.getInt();
         crisp.getSettings().setGain(gain);
         crisp.setGain(gain);
      });

      // changes the number of samples to average
      spnNumAverages.registerListener(event -> {
         final int numAverages = spnNumAverages.getInt();
         crisp.getSettings().setNumAverages(numAverages);
         crisp.setNumAverages(numAverages);
      });

      // changes the update rate in milliseconds
      spnUpdateRateMs.registerListener(event -> {
         final int updateRateMs = spnUpdateRateMs.getInt();
         crisp.getSettings().setUpdateRateMs(updateRateMs);
         crisp.setUpdateRateMs(updateRateMs);
      });

      // changes the CRISP lock range
      spnLockRange.registerListener(event -> {
         final double lockRange = spnLockRange.getDouble();
         crisp.getSettings().setLockRange(lockRange);
         crisp.setLockRange(lockRange);
      });

      // changes the polling rate to update values on the StatusPanel
      spnPollRate.registerListener(event ->
              timer.setPollRateMs(spnPollRate.getInt()));

      // check this box to update the status panel with CRISP values
      chkEnablePolling.registerListener(event ->
              timer.setPollState(chkEnablePolling.isSelected()));

      // select the software settings profile
      cmbSelectSettings.registerListener(event ->
              setSoftwareSettings(cmbSelectSettings.getSelectedIndex()));

      // add a new software settings profile
      btnAddNewSettings.registerListener(event -> addSoftwareSettings());

      // remove the last software settings profile
      btnRemoveSettings.registerListener(event -> removeSoftwareSettings());
   }

   /**
    * Select the software settings profile by index.
    *
    * @param index the profile index
    */
   private void setSoftwareSettings(final int index) {
      crisp.setSettingsIndex(index);
      updateSpinnersFromSettings(crisp.getSettingsByIndex(index));
      // System.out.println(index);
      // System.out.println(crisp.getSettingsByIndex(index));
      // System.out.println(crisp.getSettingsFromDevice());
   }

   /**
    * Increase the number of software settings profiles.
    */
   private void addSoftwareSettings() {
      final String name = crisp.addSettings();
      cmbSelectSettings.addItem(name);
   }

   /**
    * Remove the last software settings profile.
    *
    * <p>This method will not remove the last settings object.
    */
   private void removeSoftwareSettings() {
      // if the last item is selected and the remove button is clicked that will
      // cause the ComboBox to automatically select the item above it which will
      // fire the ActionListener of cmbSelectSettings and change CRISP settings

      final int numSettings = crisp.getNumSettings();
      final int selectedIndex = cmbSelectSettings.getSelectedIndex();
      final String lastProfileName =
              cmbSelectSettings.getItemAt(cmbSelectSettings.getItemCount() - 1);

      // prompt the user and make sure it's ok to change settings
      if (numSettings > 1 && numSettings == selectedIndex + 1) {
         // if the last item is selected and we click remove
         final int result = DialogUtils.showConfirmDialog(
                 btnRemoveSettings, "Settings",
                 "Are you sure you want to remove \"" + lastProfileName + "\"? \n\n"
                         + "This will cause the settings profile to change."
         );
         if (result == 1) {
            return; // no button pressed => early exit
         }
      } else if (numSettings > 1) {
         // if we click remove and have more than one profile
         final int result = DialogUtils.showConfirmDialog(
                 btnRemoveSettings, "Settings",
                 "Are you sure you want to remove \"" + lastProfileName + "\"?"
         );
         if (result == 1) {
            return; // no button pressed => early exit
         }
      }

      // make sure we don't delete the last CRISPSettings object
      if (crisp.removeSettings()) {
         cmbSelectSettings.removeItemAt(crisp.getNumSettings());
      } else {
         DialogUtils.showMessage(
                 btnRemoveSettings,
                 "Settings",
                 "Unable to delete the default settings."
         );
      }
   }

   /**
    * Update the software settings ComboBox when loading settings in UserSettings.
    */
   public void updateSoftwareSettingsComboBox() {
      final int numSettings = crisp.getNumSettings();
      if (numSettings > 1) {
         for (int i = 1; i < numSettings; i++) {
            cmbSelectSettings.addItem(CRISPSettings.NAME_PREFIX + i);
         }
      }
   }

   /**
    * Updates the values of the Spinners by querying CRISP.
    *
    * <p>Note: Only happens once at application startup, no need for thread.
    */
   public void update() {
      spnLoopGain.setInt(crisp.getGain());
      spnLEDIntensity.setInt(crisp.getLEDIntensity());
      spnUpdateRateMs.setInt(crisp.getUpdateRateMs());
      spnNumAverages.setInt(crisp.getNumAverages());
      spnObjectiveNA.setDouble(crisp.getObjectiveNA());
      spnLockRange.setDouble(crisp.getLockRange());
   }

   /**
    * Updates SpinnerPanel spinners with the values from {@code CRISPSettings} object.
    *
    * <p>This causes the spinner ActionListeners to fire and set values on the CRISP device.
    *
    * @param settings the {@code CRISPSettings} to update
    */
   public void updateSpinnersFromSettings(final CRISPSettings settings) {
      spnLoopGain.setInt(settings.getGain());
      spnLEDIntensity.setInt(settings.getLEDIntensity());
      spnUpdateRateMs.setInt(settings.getUpdateRateMs());
      spnNumAverages.setInt(settings.getNumAverages());
      spnObjectiveNA.setDouble(settings.getObjectiveNA());
      spnLockRange.setDouble(settings.getLockRange());
   }

   /**
    * Enable or disable the LED Intensity and Objective NA spinners.
    *
    * @param state false to disable the elements
    */
   public void setEnabledFocusLockSpinners(final boolean state) {
      lblLEDIntensity.setEnabled(state);
      spnLEDIntensity.setEnabled(state);
      lblObjectiveNA.setEnabled(state);
      spnObjectiveNA.setEnabled(state);
   }

   /**
    * Used when detecting the CRISP device.
    *
    * @param text the text for the label
    */
   public void setAxisLabelText(final String text) {
      lblDeviceAxis.setText(text);
   }

   /**
    * Used when loading settings.
    *
    * @param state the state of the CheckBox
    */
   public void setPollingCheckBox(final boolean state) {
      chkEnablePolling.setSelected(state);
   }

   /**
    * Enable or disable the UpdateRateMs spinner.
    * <p>Firmware versions required:
    * <p>Tiger v3.38 and MS2000 v9.2n
    *
    * @param state true or false
    */
   public void setEnabledUpdateRateSpinner(final boolean state) {
      lblUpdateRateMs.setEnabled(state);
      spnUpdateRateMs.setEnabled(state);
   }

   public boolean isPollingEnabled() {
      return chkEnablePolling.isSelected();
   }

   public Spinner getPollRateSpinner() {
      return spnPollRate;
   }

}
