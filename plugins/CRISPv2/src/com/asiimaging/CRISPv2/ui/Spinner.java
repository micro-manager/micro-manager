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

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

@SuppressWarnings("serial")
public class Spinner extends JSpinner {

	public Spinner(final Integer start, final Integer min, final Integer max, final Integer step) {
		super(new SpinnerNumberModel(start, min, max, step));
	}
	
	public Spinner(final Float start, final Float min, final Float max, final Float step) {
		super(new SpinnerNumberModel(start, min, max, step));
	}
	
	public Spinner(final Double start, final Double min, final Double max, final Double step) {
		super(new SpinnerNumberModel(start, min, max, step));
	}
	
	/**
	 * Sets the width of the Spinner component.
	 * 
	 * @param width The width of the textbox.
	 */
	public void setWidth(final int width) {
		final JComponent editor = getEditor();
		final JFormattedTextField textField = ((JSpinner.NumberEditor)editor).getTextField();
		textField.setColumns(width);
	}
}
