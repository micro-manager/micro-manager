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

import java.awt.Dimension;

import javax.swing.JLabel;

import com.asiimaging.crisp.control.CRISP;
import com.asiimaging.crisp.ui.Panel;

/**
 * This panel displays the values being queried from CRISP by the polling task.
 */
@SuppressWarnings("serial")
public class StatusPanel extends Panel {
    
    private JLabel lblState;
    private JLabel lblStateValue;
    private JLabel lblError;
    private JLabel lblErrorValue;
    private JLabel lblSNR;
    private JLabel lblSNRValue;
    private JLabel lblAGC;
    private JLabel lblAGCValue;
    private JLabel lblSum;
    private JLabel lblSumValue;
    private JLabel lblOffset;
    private JLabel lblOffsetValue;
    
    private final CRISP crisp;
    
    public StatusPanel(final CRISP crisp) {
        super();
        this.crisp = crisp;
    }
    
    public void createComponents() {
        // labels to display the names of value labels 
        lblState = new JLabel("CRISP State:");
        lblError = new JLabel("Error #:");
        lblSNR = new JLabel("SNR [dB]:");
        lblAGC = new JLabel("AGC:");
        lblSum = new JLabel("Sum:");
        lblOffset = new JLabel("Offset:");
        
        // labels to display feedback to the user
        lblStateValue = new JLabel("State");
        lblErrorValue = new JLabel("###");
        lblSNRValue = new JLabel("###");
        lblAGCValue = new JLabel("###");
        lblSumValue = new JLabel("###");
        lblOffsetValue = new JLabel("###");
        
        // prevent text labels from jumping around during calibration
        lblErrorValue.setMinimumSize(new Dimension(85, 10));
        
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

// TODO: something more efficient than launching a thread every call
// Note: Maybe a dedicated thread in CRISPTimer?
//    public void update2() {
//        final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
//            String state = "";
//            String snr = "";
//            String agc = "";
//            String sum = "";
//            String offset = "";
//            String ditherError = "";
//            
//            @Override
//            protected Void doInBackground() throws Exception {
//                state = crisp.getState();
//                ditherError = crisp.getDitherError();
//                snr = crisp.getSNR();
//                agc = crisp.getAGC();
//                sum = crisp.getSum();
//                offset = crisp.getOffsetString();
//                return null;
//            }
//            
//            @Override
//            protected void done() {
//                setLabelText(lblStateValue, state);
//                setLabelText(lblErrorValue, ditherError);
//                setLabelText(lblSNRValue, snr);
//                setLabelText(lblAGCValue, agc);
//                setLabelText(lblSumValue, sum);
//                setLabelText(lblOffsetValue, offset);
//            }
//        };
//        worker.execute();
//    }
    
    // TODO: finish docs
    /**
     * Sets the JLabel text if the String is not empty.
     * 
     * Note: This method depends on 
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
     * 
     * @param text
     */
    public void setStateLabelText(final String text) {
        lblStateValue.setText(text);
    }
}
