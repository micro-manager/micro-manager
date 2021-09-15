/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

import javax.swing.JFrame;
import javax.swing.JLabel;

import com.asiimaging.crisp.device.CRISP;
import com.asiimaging.crisp.device.CRISPTimer;
import com.asiimaging.crisp.device.ControllerType;
import com.asiimaging.ui.Panel;
import org.micromanager.Studio;

import com.asiimaging.crisp.data.Icons;
import com.asiimaging.crisp.panels.ButtonPanel;
import com.asiimaging.crisp.panels.PlotPanel;
import com.asiimaging.crisp.panels.SpinnerPanel;
import com.asiimaging.crisp.panels.StatusPanel;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

/**
 * The main frame that opens when the plugin is selected in Micro-Manager.
 */
public class CRISPFrame extends JFrame {
    
    // DEBUG => flag to turn on debug mode when editing the ui
    // use "debug" in MigLayout to see layout constraints
    public static final boolean DEBUG = false;

    private final CMMCore core;
    private final Studio studio;

    private Panel leftPanel;
    private Panel rightPanel;

    private PlotPanel plotPanel;
    private ButtonPanel buttonPanel;
    private StatusPanel statusPanel;
    private SpinnerPanel spinnerPanel;
    
    private final CRISP crisp;
    private final CRISPTimer timer;
    private final UserSettings settings;


    private static class DeviceNotFoundException extends Exception {
        public DeviceNotFoundException() {
            super("This plugin requires an ASI CRISP Autofocus device.\n"
                + "Add CRISP from the ASIStage or ASITiger device adapter"
                + " in the Hardware Configuration Wizard.");
        }
    }

    /**
     * Construct the main frame of the user interface.
     *
     * @param studio the {@link Studio} instance
     * @throws Exception device not detected
     */
    public CRISPFrame(final Studio studio) throws Exception {
        this.studio = Objects.requireNonNull(studio);
        this.core = studio.core();
        
        crisp = new CRISP(studio);
        timer = new CRISPTimer(crisp);

        // some ui panels require both crisp and the timer
        createUserInterface();

        // created after ui because spinnerPanel populates software settings
        settings = new UserSettings(studio, crisp, timer, this);
        
        // only call after the required objects exist
        // required objects: crisp, timer, and settings
        detectDevice();

        // window closing handler
        registerWindowEventHandlers();
    }

    /**
     * Detects the CRISP device, updates panels, and starts the timer.
     * 
     * @throws Exception no device found
     */
    private void detectDevice() throws Exception {
        Objects.requireNonNull(crisp);
        Objects.requireNonNull(timer);
        Objects.requireNonNull(settings);
        
        // exit on no device
        if (!crisp.detectDevice()) {
            throw new DeviceNotFoundException();
        }

        // query values from CRISP and update the ui
        spinnerPanel.setAxisLabelText(crisp.getAxisString());
        
        // the timer task updates the status panel
        timer.createTimerTask(statusPanel);

        // load settings before the polling check
        // but after we created the timer task we need
        settings.load();

        // update panels after we load the settings
        // spinner ActionListeners update the default CRISPSettings
        spinnerPanel.update();
        statusPanel.update();

        // start the timer if polling CheckBox enabled
        if (spinnerPanel.isPollingEnabled()) {
            timer.start();
        }
        
        // disable spinners if already focus locked
        if (crisp.isFocusLocked()) {
            spinnerPanel.setEnabledFocusLockSpinners(false);
            buttonPanel.setCalibrationButtonStates(false);
        }
        
        // TODO: better method for parsing version strings
        // disable update rate spinner if using old firmware
        final String version = crisp.getFirmwareVersion();
        if (crisp.getDeviceType() == ControllerType.TIGER) {
            if (!version.startsWith("3.38")) {
                spinnerPanel.setEnabledUpdateRateSpinner(false);
            }
        } else {
            if (!version.startsWith("USB-9.2n")) {
                spinnerPanel.setEnabledUpdateRateSpinner(false);
            }
        }
        
        // TODO: support this feature on Tiger
        plotPanel.disableFocusCurveButtonTiger();
    }
    
    /**
     * Create the user interface for the plugin.
     */
    private void createUserInterface() {
        Objects.requireNonNull(crisp);
        Objects.requireNonNull(timer);

        // frame settings
        setTitle(CRISPPlugin.menuName);
        setResizable(false);

        // use MigLayout as the layout manager
        setLayout(new MigLayout(
            "insets 20 10 10 10",
            "",
            ""
        ));

        // draw the title in bold
        final JLabel lblTitle = new JLabel(CRISPPlugin.menuName);
        lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        
        Panel.setDefaultMigLayout(
            "",
            "[]10[]",
            "[]10[]"
        );

        // create panels and layouts for ui elements
        spinnerPanel = new SpinnerPanel(crisp, timer);
        buttonPanel = new ButtonPanel(crisp, timer, spinnerPanel);

        statusPanel = new StatusPanel(crisp, new MigLayout(
            "",
            "40[]10[]",
            "[]10[]"
        ));

        plotPanel = new PlotPanel(studio, this, new MigLayout(
            "",
            "[]20[]",
            ""
        ));

        // main layout panels
        leftPanel = Panel.createFromMigLayout(
            "",
            "[]10[]",
            "[]50[]"
        );
        rightPanel = Panel.createFromMigLayout(
            "",
            "",
            ""
        );

        // color the panels to make editing the ui easier
        if (DEBUG) {
            leftPanel.setBackground(Color.RED);
            rightPanel.setBackground(Color.BLUE);
            plotPanel.setBackground(Color.YELLOW);
        }

        // add sub panels to the main layout panels
        leftPanel.add(spinnerPanel, "wrap");
        leftPanel.add(statusPanel, "center");
        rightPanel.add(buttonPanel, "");
        
        // add ui components to the frame
        add(lblTitle, "span, center, wrap");
        add(leftPanel, "");
        add(rightPanel, "wrap");
        add(plotPanel, "span 2");

        pack(); // set the window size automatically
        setIconImage(Icons.MICROSCOPE.getImage());
        
        // clean up resources when the frame is closed
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
    
    /**
     * This method is called when the main frame closes.
     *
     * <p>Stop the timer from polling and save settings.
     */
    private void registerWindowEventHandlers() {
        Objects.requireNonNull(timer);
        Objects.requireNonNull(settings);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                timer.stop();
                settings.save();
            }
        });
    }

    public SpinnerPanel getSpinnerPanel() {
        return spinnerPanel;
    }

    public CRISPTimer getTimer() {
        return timer;
    }

    public CRISP getCRISP() {
        return crisp;
    }
}
