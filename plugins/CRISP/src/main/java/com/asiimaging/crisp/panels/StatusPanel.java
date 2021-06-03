/**
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.panels;

import java.awt.Dimension;
import java.util.Objects;
import javax.swing.JLabel;

import com.asiimaging.crisp.device.CRISP;
import com.asiimaging.ui.Panel;
import net.miginfocom.swing.MigLayout;

/**
 * This panel displays the values being queried from CRISP by the polling task.
 */
public class StatusPanel extends Panel {

    private JLabel lblStateValue;
    private JLabel lblErrorValue;
    private JLabel lblSNRValue;
    private JLabel lblAGCValue;
    private JLabel lblSumValue;
    private JLabel lblOffsetValue;
    
    private final CRISP crisp;
    
    public StatusPanel(final CRISP crisp, final MigLayout layout) {
        super(layout);
        this.crisp = Objects.requireNonNull(crisp);
        init();
    }
    
    private void init() {
        // labels to display the names of value labels 
        final JLabel lblState = new JLabel("CRISP State:");
        final JLabel lblError = new JLabel("Error #:");
        final JLabel lblSNR = new JLabel("SNR [dB]:");
        final JLabel lblAGC = new JLabel("AGC:");
        final JLabel lblSum = new JLabel("Sum:");
        final JLabel lblOffset = new JLabel("Offset:");
        
        // labels to show values to the user
        lblStateValue = new JLabel("State");
        lblErrorValue = new JLabel("###");
        lblSNRValue = new JLabel("###");
        lblAGCValue = new JLabel("###");
        lblSumValue = new JLabel("###");
        lblOffsetValue = new JLabel("###");
        
        // prevent text labels from jumping around during calibration
        lblErrorValue.setMinimumSize(new Dimension(90, 10));
        
        // add components to the panel
        add(lblState, "");
        add(lblStateValue, "wrap");
        add(lblError, "");
        add(lblErrorValue, "wrap");
        add(lblSNR, "");
        add(lblSNRValue, "wrap");
        add(lblAGC, "");
        add(lblAGCValue, "wrap");
        add(lblSum, "");
        add(lblSumValue, "wrap");
        add(lblOffset, "");
        add(lblOffsetValue, "wrap");
    }

    // NOTE: this method always takes about 250 ms!!!
    // TODO: this method is slowing down the UI, fix it.
    /**
     * Updates the user interface with new values queried from CRISP.
     * This method is called every tick of the polling task, except 
     * after CRISP enters the Log Cal state for several ticks.
     */
    public void update() {
        setLabelText(lblStateValue, crisp.getState());
        setLabelText(lblErrorValue, crisp.getDitherError());
        setLabelText(lblSNRValue, crisp.getSNR());
        setLabelText(lblAGCValue, crisp.getAGC());
        setLabelText(lblSumValue, crisp.getSum());
        setLabelText(lblOffsetValue, crisp.getOffsetString());
    }

    /**
     * Sets the JLabel text if the String is not empty.
     *
     * @param text The string to check and update the label with.
     * @param label The label to update.
     */
    private void setLabelText(final JLabel label, final String text) {
        if (text.isEmpty()) {
            label.setText("read error");
        } else {
            label.setText(text);
        }
    }
    
    /**
     * Used during the log_cal step.
     *
     * @param text the text String
     */
    public void setStateLabelText(final String text) {
        lblStateValue.setText(text);
    }
}
