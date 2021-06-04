/**
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.panels;

import java.util.Objects;

import com.asiimaging.crisp.device.CRISP;
import com.asiimaging.crisp.device.CRISPTimer;
import com.asiimaging.ui.Button;
import com.asiimaging.ui.Panel;

/**
 * The ui panel that has buttons for calibrating CRISP.
 */
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
        this.crisp = Objects.requireNonNull(crisp);
        this.timer = Objects.requireNonNull(timer);
        this.panel = Objects.requireNonNull(panel);
        init();
    }
    
    private void init() {
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
        btnIdle.registerListener(event -> {
            crisp.setStateIdle();
            panel.setEnabledFocusLockSpinners(true);
        });
        
        // step 2 in the calibration routine
        btnLogCal.registerListener(event -> {
            crisp.setStateLogCal(timer);
        });
        
        // step 3 in the calibration routine
        btnDither.registerListener(event -> {
            crisp.setStateDither();
        });
        
        // step 4 in the calibration routine
        btnSetGain.registerListener(event -> {
            crisp.setStateGainCal();
        });

        // reset the focus offset to zero for the present position
        btnReset.registerListener(event -> {
            crisp.setResetOffsets();
        });
        
        // locks the focal position
        btnLock.registerListener(event -> {
            crisp.lock();
            panel.setEnabledFocusLockSpinners(false);
            setCalibrationButtonStates(false);
        });

        // unlocks the focal position
        btnUnlock.registerListener(event -> {
            crisp.unlock();
            panel.setEnabledFocusLockSpinners(true);
            setCalibrationButtonStates(true);
        });
        
        // saves all CRISP related settings
        btnSave.registerListener(event -> {
            crisp.save();
        });
    }
}

