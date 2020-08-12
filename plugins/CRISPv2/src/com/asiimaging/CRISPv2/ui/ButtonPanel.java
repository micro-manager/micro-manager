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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPanel;

import com.asiimaging.CRISPv2.CRISP;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class ButtonPanel extends JPanel {

	private final CRISP crisp;
	private Button buttonIdle;
	private Button buttonLogCal;
	private Button buttonDither;
	private Button buttonSetGain;
	private Button buttonResetOffsets;
	private Button buttonLock;
	private Button buttonUnlock;
	private Button buttonSave;
	//private Button buttonPlot;
	
	public ButtonPanel(final CRISP crispDevice, final String layout, final String cols, final String rows) {
		setLayout(new MigLayout(layout, cols, rows));
		crisp = crispDevice;
		
		// CRISP control buttons
		final int width = 100;
		final int height = 20;
		buttonIdle = new Button("1) Idle", width, height);
		buttonLogCal = new Button("2) Log Cal", width, height);
		buttonDither = new Button("3) Dither", width, height);
		buttonSetGain = new Button("4) Set Gain", width, height);
		buttonResetOffsets = new Button("Reset Offsets", width, height);
		buttonLock = new Button("Lock", width, height);
		buttonUnlock = new Button("Unlock", width, height);
		buttonSave = new Button("Save", width, height);
		//buttonPlot = new Button("Graph", width, height);
		
		// handle user events
		createEventHandlers();
		
		// add components to panel
		add(buttonIdle, "wrap");
		add(buttonLogCal, "wrap");
		add(buttonDither, "wrap");
		add(buttonSetGain, "wrap");
		add(buttonResetOffsets, "wrap");
		add(buttonLock, "wrap");
		add(buttonUnlock, "wrap");
		add(buttonSave, "wrap");
		//add(buttonPlot, "wrap");
	}
	
	/**
	 * Creates the event handlers for Button objects.
	 */
	private void createEventHandlers() {
		// step 1 in the calibration routine
//		buttonIdle.registerListener(new Method() {
//			@Override
//			public void run(EventObject event) {
//				crisp.setStateIdle();
//			}
//		});
		
		buttonIdle.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				crisp.setStateIdle();
			}
		});
		
		// step 2 in the calibration routine
		buttonLogCal.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				crisp.setStateLogCal();
			}
		});
		
		// step 3 in the calibration routine
		buttonDither.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				crisp.setStateDither();
			}
		});
		
		// step 4 in the calibration routine
		buttonSetGain.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				crisp.setStateGainCal();
			}
		});
		
		// reset the focus offset to zero for the present position
		buttonResetOffsets.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				crisp.resetOffsets();
			}
		});
		
		// locks the focal position
		buttonLock.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				crisp.lock();
			}
		});
		
		// unlocks the focal position
		buttonUnlock.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				crisp.unlock();
			}
		});
		
		// saves all CRISP related settings
		buttonSave.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				crisp.save();
			}
		});
		
		// plots the graph of the focus curve
//		buttonPlot.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent event) {
//				System.out.println("Plot Focus Curve Pressed");
//			}
//		});
	}
}
