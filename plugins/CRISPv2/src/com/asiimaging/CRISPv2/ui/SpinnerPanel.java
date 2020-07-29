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
import com.asiimaging.CRISPv2.DefaultSettings;

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
	
	public SpinnerPanel(final CRISP crispDevice, final String layoutConstraints, final String columnConstraints, final String rowConstraints) {
		setLayout(new MigLayout(layoutConstraints, columnConstraints, rowConstraints));
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
		spinnerLED = new Spinner(50, 0, 100, 1);
		spinnerGain = new Spinner(10, 0, 100, 1);
		spinnerAverage = new Spinner(10, 0, 100, 1);
		spinnerNA = new Spinner(0.65f, 0.0f, 1.4f, 0.01f);
		spinnerLockRange = new Spinner(1.0f, 0.0f, 2.0f, 0.1f);
		spinnerPollingRate = new Spinner(120, DefaultSettings.MIN_CRISP_POLL_RATE_MS, DefaultSettings.MAX_CRISP_POLL_RATE_MS, 10);
		
		// starts and stops the timer
		checkboxPollingEnabled = new JCheckBox("Polling Enabled", true);
		checkboxPollingEnabled.setFocusPainted(false);
		checkboxPollingEnabled.setToolTipText("Enable or disable updating CRISP values.");
		
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
				System.out.println("led spinner " + intensity);
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
		
		// set this value to the objective numerical aperture
		spinnerNA.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent event) {
				final float objNA = (Float)spinnerNA.getValue();
				crisp.setObjectiveNA(objNA);
				//System.out.println("obj na spinner " + objNA);
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
				final int pollingRate = (Integer)spinnerPollingRate.getValue();
				crisp.setPollingRate(pollingRate);
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
	
	/**
	 * 
	 * @return
	 */
	public JLabel getAxisLabel() {
		return labelDeviceAxis;
	}
}
