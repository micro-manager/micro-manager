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

package com.asiimaging.CRISPv2.ui;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.asiimaging.CRISPv2.CRISP;
import com.asiimaging.CRISPv2.data.DefaultSettings;
import com.asiimaging.CRISPv2.data.RangeSettings;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class SpinnerPanel extends JPanel {

	private final CRISP crisp;
	
	private JLabel labelDeviceAxis;
	private JLabel labelLED;
	private JLabel labelGain;
	private JLabel labelAverage;
	private JLabel labelNA;
	private JLabel labelLockRange;
	private JLabel labelPollingRate;
	private Spinner spinnerLED;
	private Spinner spinnerGain;
	private Spinner spinnerAverage;
	private Spinner spinnerNA;
	private Spinner spinnerLockRange;
	private Spinner spinnerPollingRate;
	private JCheckBox checkboxPollingEnabled;
	
	public SpinnerPanel(final CRISP crispDevice, final String layout, final String cols, final String rows) {
		setLayout(new MigLayout(layout, cols, rows));
		crisp = crispDevice;
		
		// spinner labels
		labelDeviceAxis = new JLabel("Axis");
		labelLED = new JLabel("LED Intensity");
		labelGain = new JLabel("Gain");
		labelAverage = new JLabel("Average");
		labelNA = new JLabel("Objective NA");
		labelLockRange = new JLabel("Lock Range [mm]");
		labelPollingRate = new JLabel("Polling Rate [ms]");
		
		// spinners to change CRISP values
		spinnerLED = new Spinner(
			DefaultSettings.LED_INTENSITY,
			RangeSettings.MIN_LED_INTENSITY,
			RangeSettings.MAX_LED_INTENSITY,
			1
		);
		
		spinnerGain = new Spinner(
			DefaultSettings.GAIN,
			RangeSettings.MIN_GAIN,
			RangeSettings.MAX_GAIN,
			1
		);
		
		spinnerAverage = new Spinner(
			DefaultSettings.NUM_AVERAGES,
			RangeSettings.MIN_NUM_AVERAGES,
			RangeSettings.MAX_NUM_AVERAGES,
			1
		);
		
		spinnerNA = new Spinner(
			DefaultSettings.OBJECTIVE_NA,
			RangeSettings.MIN_OBJECTIVE_NA,
			RangeSettings.MAX_OBJECTIVE_NA,
			0.01f
		);
		
		spinnerLockRange = new Spinner(
			DefaultSettings.LOCK_RANGE,
			RangeSettings.MIN_LOCK_RANGE,
			RangeSettings.MAX_LOCK_RANGE,
			0.1f
		);
		
		spinnerPollingRate = new Spinner(
			DefaultSettings.POLL_RATE_MS,
			RangeSettings.MIN_POLL_RATE_MS,
			RangeSettings.MAX_POLL_RATE_MS,
			10
		);
		
		// enables or disables the CRISP polling timer
		checkboxPollingEnabled = new JCheckBox("Polling Enabled", true);
		checkboxPollingEnabled.setFocusPainted(false); // no highlight
		checkboxPollingEnabled.setToolTipText("Enable or disable updating CRISP values.");
		
		// tooltips for the spinners
		spinnerPollingRate.setToolTipText("The rate in milliseconds that CRISP is polled to update the status text.");
		spinnerNA.setToolTipText("The numerical aperture of the objective.");
		spinnerGain.setToolTipText("");
		spinnerAverage.setToolTipText("");
		spinnerLockRange.setToolTipText("The range of the focus lock.");
		spinnerLED.setToolTipText("The intensity of the LED.");
		
		// set the size of the spinners
		final int width = 5;
		spinnerLED.setWidth(width);
		spinnerGain.setWidth(width);
		spinnerAverage.setWidth(width);
		spinnerNA.setWidth(width);
		spinnerLockRange.setWidth(width);
		
		// init event handlers
		createEventHandlers();
		
		// add components to panel
		add(labelDeviceAxis, "right, span 2, wrap");
		add(labelLED, "");
		add(spinnerLED, "wrap");
		add(labelGain, "");
		add(spinnerGain, "wrap");
		add(labelAverage, "");
		add(spinnerAverage, "wrap");
		add(labelNA, "");
		add(spinnerNA, "wrap");
		add(labelLockRange, "");
		add(spinnerLockRange, "wrap");
		add(labelPollingRate, "");
		add(spinnerPollingRate, "wrap");
		add(checkboxPollingEnabled, "");
	}
	
	/**
	 * Updates the values of Spinner objects by querying CRISP.
	 */
	public void update() {
		spinnerLED.setValue(crisp.getLEDIntensity());
		spinnerGain.setValue(crisp.getGainMultiplier());
		spinnerAverage.setValue(crisp.getNumberOfAverages());
		spinnerNA.setValue(crisp.getObjectiveNA());
		spinnerLockRange.setValue(crisp.getLockRange());
	}
	
	/**
	 * Create the event handlers for Spinner objects.
	 */
	private void createEventHandlers() {
		// changes the led intensity
		spinnerLED.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent event) {
				final int intensity = (Integer)spinnerLED.getValue();
				crisp.setLEDIntensity(intensity);
				//System.out.println("led spinner " + intensity);
			}
		});
		
		// changes the gain multiplier
		spinnerGain.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent event) {
				final int gain = (Integer)spinnerGain.getValue();
				crisp.setGainMultiplier(gain);
				//System.out.println("gain spinner " + gain);
			}
		});
		
		// changes the number of samples to average
		spinnerAverage.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent event) {
				final int averages = (Integer)spinnerAverage.getValue();
				crisp.setNumberOfAverages(averages);
				//System.out.println("average spinner " + averages);
			}
		});
		
		// set this value to the objectives numerical aperture
		spinnerNA.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent event) {
				final float objectiveNA = (Float)spinnerNA.getValue();
				crisp.setObjectiveNA(objectiveNA);
				//System.out.println("obj na spinner " + objectiveNA);
			}
		});
		
		// changes the CRISP lock range
		spinnerLockRange.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent event) {
				final float lockRange = (Float)spinnerLockRange.getValue();
				crisp.setLockRange(lockRange);
				//System.out.println("lock range spinner " + lockRange);
			}
		});
		
		// changes the polling rate for CRISP to update values
		spinnerPollingRate.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent event) {
				final int pollRate = (Integer)spinnerPollingRate.getValue();
				crisp.setPollRateMs(pollRate);
				//System.out.println("polling rate " + pollingRate);
			}
		});
		
		// check this box to update the status panel with CRISP values
		checkboxPollingEnabled.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				final boolean selected = checkboxPollingEnabled.isSelected();
				crisp.setPollingState(selected);
				//System.out.println("checkbox: " + selected);
			}
		});
	}
	
	public JLabel getAxisLabel() {
		return labelDeviceAxis;
	}
	
	// used to save user settings
	public Spinner getGainSpinner() {
		return spinnerGain;
	}
	
	public Spinner getAveragesSpinner() {
		return spinnerAverage;
	}
	
	public Spinner getNASpinner() {
		return spinnerNA;
	}
	
	public Spinner getLEDSpinner() {
		return spinnerLED;
	}
	
	public Spinner getPollRateSpinner() {
		return spinnerPollingRate;
	}
	
	public Spinner getLockRangeSpinner() {
		return spinnerLockRange;
	}
}
