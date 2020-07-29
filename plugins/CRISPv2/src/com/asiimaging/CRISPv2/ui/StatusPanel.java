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

import javax.swing.JLabel;
import javax.swing.JPanel;

import com.asiimaging.CRISPv2.CRISP;

import net.miginfocom.swing.MigLayout;

/**
 * This panel displays the values being queried from CRISP by the polling task.
 */
@SuppressWarnings("serial")
public class StatusPanel extends JPanel {
	
	private final CRISP crisp;
	private JLabel labelState;
	private JLabel labelStateValue;
	private JLabel labelError;
	private JLabel labelErrorValue;
	private JLabel labelSNR;
	private JLabel labelSNRValue;
	private JLabel labelAGC;
	private JLabel labelAGCValue;
	private JLabel labelSum;
	private JLabel labelSumValue;
	private JLabel labelOffset;
	private JLabel labelOffsetValue;
	
	public StatusPanel(final CRISP crispDevice, final String layout, final String cols, final String rows) {
		setLayout(new MigLayout(layout, cols, rows));
		
		// reference used to query values 
		crisp = crispDevice;
		
		// labels to display the names of value labels 
		labelState = new JLabel("CRISP State:");
		labelError = new JLabel("Error #:");
		labelSNR = new JLabel("SNR:");
		labelAGC = new JLabel("AGC:");
		labelSum = new JLabel("Sum:");
		labelOffset = new JLabel("Offset:");
		
		// labels to display feedback to the user
		labelStateValue = new JLabel("State");
		labelErrorValue = new JLabel("###");
		labelSNRValue = new JLabel("SNR");
		labelAGCValue = new JLabel("AGC");
		labelSumValue = new JLabel("###");
		labelOffsetValue = new JLabel("###");
		
		// add components to the panel
		add(labelState, "");
		add(labelStateValue, "wrap");
		add(labelError, "");
		add(labelErrorValue, "wrap");
		add(labelSNR, "");
		add(labelSNRValue, "wrap");
		add(labelAGC, "");
		add(labelAGCValue, "wrap");
		add(labelSum, "");
		add(labelSumValue, "wrap");
		add(labelOffset, "");
		add(labelOffsetValue, "wrap");
	}
	
	/**
	 * Set the layout using MigLayout.
	 * 
	 * @param layout The layout constraints.
	 * @param columns The column constraints.
	 * @param rows The row constraints.
	 */
	public void setMigLayout(final String layout, final String cols, final String rows) {
		setLayout(new MigLayout(layout, cols, rows));
	}
	
	/**
	 * Updates the user interface with new values queried from CRISP.
	 * This method is called every tick of the polling task, except 
	 * after CRISP enters the Log Cal state for several ticks.
	 */
	public void update() {	
		setLabelText(crisp.getState(),       labelStateValue);
		setLabelText(crisp.getDitherError(), labelErrorValue);
		setLabelText(crisp.getSNR(),         labelSNRValue);
		setLabelText(crisp.getAGC(),         labelAGCValue);
		setLabelText(crisp.getSum(),         labelSumValue);
		setLabelText(crisp.getOffset(),      labelOffsetValue);
	}

	/**
	 * Sets the JLabel text if the String is not empty.
	 * 
	 * @param text The string to check and update the label with.
	 * @param label The label to update.
	 */
	private void setLabelText(final String text, final JLabel label) {
		if (text.isEmpty()) {
			label.setText("read error");
		} else {
			label.setText(text);
		}
	}
	
	/**
	 * This is used in the polling task to set the alternative text 
	 * during the CRISP calibration routine.
	 * 
	 * @return The JLabel that displays the current CRISP state.
	 */
	public JLabel getStateLabel() {
		return labelStateValue;
	}
}
