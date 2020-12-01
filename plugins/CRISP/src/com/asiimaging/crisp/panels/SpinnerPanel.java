///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
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

package com.asiimaging.crisp.panels;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.asiimaging.crisp.control.CRISP;
import com.asiimaging.crisp.control.CRISPTimer;
import com.asiimaging.crisp.data.Defaults;
import com.asiimaging.crisp.data.Ranges;
import com.asiimaging.crisp.ui.Panel;
import com.asiimaging.crisp.ui.Spinner;

@SuppressWarnings("serial")
public class SpinnerPanel extends Panel {
    
    private JLabel labelDeviceAxis;
    private JLabel lblLEDIntensity;
    private JLabel lblObjectiveNA;
    private JLabel lblGain;
    private JLabel lblNumAverages;
    private JLabel lblLockRange;
    private JLabel lblPollRate;
    private Spinner spnLEDIntensity;
    private Spinner spnGain;
    private Spinner spnNumAverages;
    private Spinner spnObjectiveNA;
    private Spinner spnLockRange;
    private Spinner spnPollRate;
    private JCheckBox cbEnablePolling;
    
    private final CRISP crisp;
    private final CRISPTimer timer;
    
    public SpinnerPanel(final CRISP crisp, final CRISPTimer timer) {
        super();
        this.crisp = crisp;
        this.timer = timer;
    }
    
    public void createComponents() {
        // spinner labels
        labelDeviceAxis = new JLabel("Axis");
        lblLEDIntensity = new JLabel("LED Intensity [%]");
        lblObjectiveNA = new JLabel("Objective NA");
        lblGain = new JLabel("Loop Gain");
        lblNumAverages = new JLabel("Averaging");
        lblLockRange = new JLabel("Lock Range [mm]");
        lblPollRate = new JLabel("Polling Rate [ms]");
        
        // spinners to change CRISP values
        spnLEDIntensity = Spinner.createIntegerSpinnner(
            Defaults.LED_INTENSITY,
            Ranges.MIN_LED_INTENSITY,
            Ranges.MAX_LED_INTENSITY,
            1
        );
        
        spnGain = Spinner.createIntegerSpinnner(
            Defaults.GAIN,
            Ranges.MIN_GAIN,
            Ranges.MAX_GAIN,
            1
        );
        
        spnNumAverages = Spinner.createIntegerSpinnner(
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
            0.1f
        );
        
        spnPollRate = Spinner.createIntegerSpinnner(
            Defaults.POLL_RATE_MS,
            Ranges.MIN_POLL_RATE_MS,
            Ranges.MAX_POLL_RATE_MS,
            10
        );
        
        // enables or disables the CRISP polling timer
        cbEnablePolling = new JCheckBox("Polling Enabled", true);
        cbEnablePolling.setFocusPainted(false); // no highlight
        cbEnablePolling.setToolTipText("Enable or disable updating CRISP values.");
        
        // TODO: better tooltips
        // tooltips for the spinners
        lblPollRate.setToolTipText("The rate in milliseconds that CRISP is polled to update the status text.");
        lblObjectiveNA.setToolTipText("The numerical aperture of the objective.");
        lblGain.setToolTipText("");
        lblNumAverages.setToolTipText(""); 
        lblLockRange.setToolTipText("The range of the focus lock.");
        lblLEDIntensity.setToolTipText("The intensity of the LED.");
        
        // init event handlers
        registerEventHandlers();
        
        // add components to panel
        add(labelDeviceAxis, "right, span 2, wrap");
        add(lblLEDIntensity, "");
        add(spnLEDIntensity, "wrap");
        add(lblObjectiveNA, "");
        add(spnObjectiveNA, "wrap");
        add(lblGain, "");
        add(spnGain, "wrap");
        add(lblNumAverages, "");
        add(spnNumAverages, "wrap");
        add(lblLockRange, "");
        add(spnLockRange, "wrap");
        add(lblPollRate, "");
        add(spnPollRate, "wrap");
        add(cbEnablePolling, "");
    }
    
    /**
     * Create the event handlers for Spinner objects.
     */
    private void registerEventHandlers() {
        // changes the led intensity
        spnLEDIntensity.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                crisp.setLEDIntensity(spnLEDIntensity.getInt());
            }
        });
         
        // changes the gain multiplier
        spnGain.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                crisp.setGain(spnGain.getInt());
            }
         });
         
        // changes the number of samples to average
        spnNumAverages.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                crisp.setNumAverages(spnNumAverages.getInt());
            }
         });
         
        // set this value to the objectives numerical aperture
        spnObjectiveNA.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                crisp.setObjectiveNA(spnObjectiveNA.getFloat());
            }
        });
        
        // changes the CRISP lock range
        spnLockRange.addChangeListener(new ChangeListener() {
            @Override
             public void stateChanged(ChangeEvent event) {
                 crisp.setLockRange(spnLockRange.getFloat());
             }
         });
         
        // changes the polling rate for CRISP to update values
        spnPollRate.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                timer.setPollRateMs(spnPollRate.getInt());
            }
        });
        
        // check this box to update the status panel with CRISP values
        cbEnablePolling.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent event) {
                System.out.println("GERE");
                timer.setPollState(cbEnablePolling.isSelected());
            }
        });
    }
    
    /**
     * Updates the values of the Spinners by querying CRISP.
     * <p>
     * Note: Only happens once at application startup, no need for thread.
     */
    public void update() {
        spnGain.setInt(crisp.getGain());
        spnLEDIntensity.setInt(crisp.getLEDIntensity());
        spnNumAverages.setInt(crisp.getNumAverages());
        spnObjectiveNA.setFloat(crisp.getObjectiveNA());
        spnLockRange.setFloat(crisp.getLockRange());
    }
    
    // used to enable or disable spinners based on focus lock state
    public void setEnabledFocusLock(final boolean state) {
        lblLEDIntensity.setEnabled(state);
        spnLEDIntensity.setEnabled(state);
        lblObjectiveNA.setEnabled(state);
        spnObjectiveNA.setEnabled(state);
    }
    
    public void setAxisLabelText(final String text) {
        labelDeviceAxis.setText(text);
    }
    
    public void setPollingCheckBox(final boolean state) {
        cbEnablePolling.setSelected(state);
    }
    
    public boolean isPollingEnabled() {
        return cbEnablePolling.isSelected();
    }
    
    public Spinner getPollRateSpinner() {
        return spnPollRate;
    }
}