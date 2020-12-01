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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.asiimaging.crisp.control.CRISP;
import com.asiimaging.crisp.control.CRISPTimer;
import com.asiimaging.crisp.ui.Button;
import com.asiimaging.crisp.ui.Panel;

@SuppressWarnings("serial")
public class ButtonPanel extends Panel {

    private Button btnIdle;
    private Button btnLogCal;
    private Button btnDither;
    private Button btnSetGain;
    private Button btnReset;
    private Button btnSave;
    private Button btnLock;
    private Button btnUnlock;
    
    private final CRISP crisp;
    private final CRISPTimer timer;
    private final SpinnerPanel panel;
    
    public ButtonPanel(final CRISP crisp, final CRISPTimer timer, final SpinnerPanel panel) {
        super();
        this.crisp = crisp;
        this.timer = timer;
        this.panel = panel;
    }
    
    public void createComponents() {
        // CRISP control buttons
        Button.setDefaultSize(120, 30);
        btnIdle = new Button("1: Idle");
        btnLogCal = new Button("2: Log Cal");
        btnDither = new Button("3: Dither");
        btnSetGain = new Button("4: Set Gain");
        btnReset = new Button("Reset Offsets");
        btnSave = new Button("Save Settings");
        
        Button.setDefaultSize(120, 60);
        btnLock = new Button("Lock");
        btnUnlock = new Button("Unlock");
        btnLock.setBoldFont(14);
        btnUnlock.setBoldFont(14);
        
        // handle user events
        registerEventHandlers();
        
        // add components to panel
        add(btnIdle, "wrap");
        add(btnLogCal, "wrap");
        add(btnDither, "wrap");
        add(btnSetGain, "wrap");
        add(btnReset, "wrap");
        add(btnSave, "wrap");
        add(btnLock, "gaptop 60, wrap");
        add(btnUnlock, "wrap");
    }

    public void setCalibrationButtonStates(final boolean state) {
        btnIdle.setEnabled(state);
        btnLogCal.setEnabled(state);
        btnDither.setEnabled(state);
        btnSetGain.setEnabled(state);
    }
    
    /**
     * Creates the event handlers for Button objects.
     */
    private void registerEventHandlers() {
        
        // step 1 in the calibration routine
        btnIdle.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                crisp.setStateIdle();
                panel.setEnabledFocusLock(true);
            }
        });
        
        // step 2 in the calibration routine
        btnLogCal.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                crisp.setStateLogCal(timer);
            }
        });
        
        // step 3 in the calibration routine
        btnDither.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                crisp.setStateDither();
            }
        });
        
        // step 4 in the calibration routine
        btnSetGain.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                crisp.setStateGainCal();
            }
        });
        
        // reset the focus offset to zero for the present position
        btnReset.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                crisp.setResetOffsets();
            }
        });
        
        // locks the focal position
        btnLock.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                crisp.lock();
                panel.setEnabledFocusLock(false);
                setCalibrationButtonStates(false);
            }
        });
        
        // unlocks the focal position
        btnUnlock.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                crisp.unlock();
                panel.setEnabledFocusLock(true);
                setCalibrationButtonStates(true);
            }
        });
        
        // saves all CRISP related settings
        btnSave.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                crisp.save();
            }
        });
    }
}

