/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.panels;

import com.asiimaging.crisp.data.Defaults;
import com.asiimaging.crisp.data.Ranges;
import com.asiimaging.devices.crisp.CRISP;
import com.asiimaging.devices.crisp.CRISPSettings;
import com.asiimaging.devices.crisp.CRISPTimer;
import com.asiimaging.crisp.utils.DialogUtils;
import com.asiimaging.ui.Button;
import com.asiimaging.ui.CheckBox;
import com.asiimaging.ui.ComboBox;
import com.asiimaging.ui.Panel;
import com.asiimaging.ui.Spinner;

import javax.swing.JLabel;
import java.util.Objects;

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
        init();
    }
    
    private void init() {
        // spinner labels
        lblDeviceAxis = new JLabel("Axis");
        lblLEDIntensity = new JLabel("LED Intensity [%]");
        lblObjectiveNA = new JLabel("Objective NA");
        lblLoopGain = new JLabel("Loop Gain");
        lblNumAverages = new JLabel("Averaging");
        lblUpdateRateMs = new JLabel("Update Rate [ms]");
        lblLockRange = new JLabel("Lock Range [mm]");
        lblPollRate = new JLabel("Polling Rate [ms]");
        
        // spinners to change CRISP values
        spnLEDIntensity = Spinner.createIntegerSpinner(
            Defaults.LED_INTENSITY,
            Ranges.MIN_LED_INTENSITY,
            Ranges.MAX_LED_INTENSITY,
            1
        );

        spnLoopGain = Spinner.createIntegerSpinner(
            Defaults.LOOP_GAIN,
            Ranges.MIN_LOOP_GAIN,
            Ranges.MAX_LOOP_GAIN,
            1
        );

        spnUpdateRateMs = Spinner.createIntegerSpinner(
            Defaults.UPDATE_RATE_MS,
            Ranges.MIN_UPDATE_RATE_MS,
            Ranges.MAX_UPDATE_RATE_MS,
            1
        );

        spnNumAverages = Spinner.createIntegerSpinner(
            Defaults.NUM_AVERAGES,
            Ranges.MIN_NUM_AVERAGES,
            Ranges.MAX_NUM_AVERAGES,
            1
        );
        
        spnObjectiveNA = Spinner.createFloatSpinner(
            Defaults.OBJECTIVE_NA,
            Ranges.MIN_OBJECTIVE_NA,
            Ranges.MAX_OBJECTIVE_NA,
            0.01f
        );
        
        spnLockRange = Spinner.createFloatSpinner(
            Defaults.LOCK_RANGE,
            Ranges.MIN_LOCK_RANGE,
            Ranges.MAX_LOCK_RANGE,
            0.05f
        );
        
        spnPollRate = Spinner.createIntegerSpinner(
            Defaults.POLL_RATE_MS,
            Ranges.MIN_POLL_RATE_MS,
            Ranges.MAX_POLL_RATE_MS,
            50
        );

        // enable or disable the CRISP polling timer
        chkEnablePolling = new CheckBox("Polling Enabled", true);
        chkEnablePolling.setToolTipText("Enable or disable updating CRISP values.");

        // select the settings to use
        lblSelectSettings = new JLabel("Settings:");
        final String[] labels = {CRISPSettings.DEFAULT_PROFILE_NAME};
        cmbSelectSettings = new ComboBox(labels, labels[0], 100, 20);
        
        // increase and decrease number of software settings
        btnAddNewSettings = new Button("+", 40, 20);
        btnRemoveSettings = new Button("-", 40, 20);

        // TODO: better tooltips
        // tooltips for the spinners
        lblPollRate.setToolTipText("The rate in milliseconds that CRISP is polled to update the status text.");
        lblObjectiveNA.setToolTipText("The numerical aperture of the objective.");
        lblLoopGain.setToolTipText("");
        lblNumAverages.setToolTipText("");
        lblUpdateRateMs.setToolTipText("Set the update rate for CRISP trajectory.");
        lblLockRange.setToolTipText("The range of the focus lock.");
        lblLEDIntensity.setToolTipText("The intensity of the LED.");
    
        btnAddNewSettings.setToolTipText("Add new software settings profile.");
        btnRemoveSettings.setToolTipText("Delete the last software settings profile.");
        
        // init event handlers
        registerEventHandlers();
        
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
    private void registerEventHandlers() {
        // changes the LED intensity
        spnLEDIntensity.registerListener(event -> {
            final int ledIntensity = spnLEDIntensity.getInt();
            crisp.getSettings().setLEDIntensity(ledIntensity);
            crisp.setLEDIntensity(ledIntensity);
        });

        // changes the gain multiplier
        spnLoopGain.registerListener(event -> {
            final int gain = spnLoopGain.getInt();
            crisp.getSettings().setGain(gain);
            crisp.setGain(gain);
        });

        // changes the update rate in milliseconds
        spnUpdateRateMs.registerListener(event -> {
            final int updateRateMs = spnUpdateRateMs.getInt();
            crisp.getSettings().setUpdateRateMs(updateRateMs);
            crisp.setUpdateRateMs(updateRateMs);
        });

        // changes the number of samples to average
        spnNumAverages.registerListener(event -> {
            final int numAverages = spnNumAverages.getInt();
            crisp.getSettings().setNumAverages(numAverages);
            crisp.setNumAverages(numAverages);
        });
         
        // set this value to the objective numerical aperture
        spnObjectiveNA.registerListener(event -> {
            final float objectiveNA = spnObjectiveNA.getFloat();
            crisp.getSettings().setObjectiveNA(objectiveNA);
            crisp.setObjectiveNA(objectiveNA);
        });
        
        // changes the CRISP lock range
        spnLockRange.registerListener(event -> {
            final float lockRange = spnLockRange.getFloat();
            crisp.getSettings().setLockRange(lockRange);
            crisp.setLockRange(lockRange);
        });
         
        // changes the polling rate for CRISP to update values
        spnPollRate.registerListener(event -> {
            timer.setPollRateMs(spnPollRate.getInt());
        });

        // check this box to update the status panel with CRISP values
        chkEnablePolling.registerListener(event -> {
            timer.setPollState(chkEnablePolling.isSelected());
        });

        // select which software settings to use
        cmbSelectSettings.registerListener(event -> {
            final int index = cmbSelectSettings.getSelectedIndex();
            crisp.setSettingsIndex(index);
            updateSpinnersFromSettings(crisp.getSettingsByIndex(index));
//             System.out.println(index);
//             System.out.println(crisp.getSettingsByIndex(index));
//             System.out.println(crisp.getSettingsFromDevice());
        });

        // increase the number of available software settings
        btnAddNewSettings.registerListener(event -> {
            final String name = crisp.addSettings();
            cmbSelectSettings.addItem(name);
        });

        // remove the last software settings profile
        btnRemoveSettings.registerListener(event -> {
            // if the last item is selected and the remove button is clicked that will
            // cause the ComboBox to automatically select the item above it which will
            // fire the ActionListener of cmbSelectSettings and change CRISP settings

            final int numSettings = crisp.getNumSettings();
            final int selectedIndex = cmbSelectSettings.getSelectedIndex();
            final String lastProfileName = cmbSelectSettings.getItemAt(cmbSelectSettings.getItemCount()-1);

            // prompt the user and make sure it's ok to change settings
            if (numSettings > 1 && numSettings == selectedIndex+1) {
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

        });
        
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
     * <p>
     * Note: Only happens once at application startup, no need for thread.
     */
    public void update() {
        spnLoopGain.setInt(crisp.getGain());
        spnLEDIntensity.setInt(crisp.getLEDIntensity());
        spnUpdateRateMs.setInt(crisp.getUpdateRateMs());
        spnNumAverages.setInt(crisp.getNumAverages());
        spnObjectiveNA.setFloat(crisp.getObjectiveNA());
        spnLockRange.setFloat(crisp.getLockRange());
    }

    /**
     * Updates SpinnerPanel spinners with the values from {@code CRISPSettings} object.
     * <p>
     * This causes the spinner ActionListeners to fire and set values on the CRISP device.
     *
     * @param settings the {@code CRISPSettings} to update
     */
    public void updateSpinnersFromSettings(final CRISPSettings settings) {
        spnLoopGain.setInt(settings.getGain());
        spnLEDIntensity.setInt(settings.getLEDIntensity());
        spnUpdateRateMs.setInt(settings.getUpdateRateMs());
        spnNumAverages.setInt(settings.getNumAverages());
        spnObjectiveNA.setFloat(settings.getObjectiveNA());
        spnLockRange.setFloat(settings.getLockRange());
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
     * Firmware version 3.38 is required for Tiger.
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
