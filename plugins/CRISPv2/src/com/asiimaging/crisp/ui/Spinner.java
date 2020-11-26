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

package com.asiimaging.crisp.ui;

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

@SuppressWarnings("serial")
public class Spinner extends JSpinner {

    private static int defaultSize = 5;
    
    private Spinner() {
    }
    
    private Spinner(final Integer start, final Integer min, final Integer max, final Integer step) {
        super(new SpinnerNumberModel(start, min, max, step));
        setColumnSize(defaultSize);
    }
    
    private Spinner(final Float start, final Float min, final Float max, final Float step) {
        super(new SpinnerNumberModel(start, min, max, step));
        setColumnSize(defaultSize);
    }
    
    private Spinner(final Double start, final Double min, final Double max, final Double step) {
        super(new SpinnerNumberModel(start, min, max, step));
        setColumnSize(defaultSize);
    }
    
    public static Spinner createFloatSpinner(
            final Float start, 
            final Float min, 
            final Float max, 
            final Float step) {
        return new Spinner(start, min, max, step);
    }
    
    public static Spinner createIntegerSpinnner(
            final Integer start, 
            final Integer min, 
            final Integer max, 
            final Integer step) {
        return new Spinner(start, min, max, step);
    }
    
    public static Spinner createDoubleSpinnner(
            final Double start, 
            final Double min, 
            final Double max, 
            final Double step) {
        return new Spinner(start, min, max, step);
    }
    
    /**
     * Sets the width of the Spinner component.
     * 
     * @param width The width of the textbox.
     */
    public void setColumnSize(final int width) {
        final JComponent editor = getEditor();
        final JFormattedTextField textField = ((JSpinner.NumberEditor)editor).getTextField();
        textField.setColumns(width);
    }
    
    public int getInt() {
        return (Integer)getValue();
    }
    
    public float getFloat() {
        return (Float)getValue();
    }
    
    public double getDouble() {
        return (Double)getValue();
    }
    
    public void setInt(final int n) {
        setValue(n);
    }
    
    public void setFloat(final float n) {
        setValue(n);
    }
    
    public void setDouble(final double n) {
        setValue(n);
    }
}
