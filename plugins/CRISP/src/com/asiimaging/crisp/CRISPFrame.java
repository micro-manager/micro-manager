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

package com.asiimaging.crisp;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMFrame;

import com.asiimaging.crisp.control.CRISP;
import com.asiimaging.crisp.control.CRISPTimer;
import com.asiimaging.crisp.data.Icons;
import com.asiimaging.crisp.panels.ButtonPanel;
import com.asiimaging.crisp.panels.PlotPanel;
import com.asiimaging.crisp.panels.SpinnerPanel;
import com.asiimaging.crisp.panels.StatusPanel;
import com.asiimaging.crisp.ui.Panel;
import com.asiimaging.crisp.utils.ObjectUtils;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class CRISPFrame extends MMFrame {
    
    // flag to turn on debug mode when editing the ui
    // use "debug" in MigLayout to see layout constraints
    private final static boolean DEBUG = false;
    
    @SuppressWarnings("unused")
    private final CMMCore core;
    private final ScriptInterface gui;
    
    private final CRISP crisp;
    private final CRISPTimer timer;
    private UserSettings settings;
    
    private JLabel lblTitle;
    private Panel leftPanel;
    private Panel rightPanel;
    
    private PlotPanel plotPanel;
    private ButtonPanel buttonPanel;
    private StatusPanel statusPanel;
    private SpinnerPanel spinnerPanel;

    private class DeviceNotFoundException extends Exception {
        public DeviceNotFoundException() {
            super("This plugin requires an ASI CRISP Autofocus device.\n"
                + "Add CRISP from the ASIStage or ASITiger device adapter"
                + " in the Hardware Configuration Wizard.");
        }
    }
    
    /**
     * The main frame of the user interface.
     * 
     * @param app
     * @throws Exception
     */
    public CRISPFrame(final ScriptInterface app) throws Exception {
        core = app.getMMCore();
        gui = app;
        
        crisp = new CRISP(gui);
        timer = new CRISPTimer(crisp);
        
        // some ui panels require both crisp and timer
        createUserInterface();
        settings = new UserSettings(
            timer,
            spinnerPanel
        );
        
        // only call after the required objects exist
        // required objects: crisp, timer, and settings
        detectDevice();
        registerWindowEventHandlers();
    }

    /**
     * Detects the CRISP device, updates panels, and starts the timer.
     * 
     * @throws Exception no device found
     */
    private void detectDevice() throws Exception {
        ObjectUtils.requireNonNull(crisp);
        ObjectUtils.requireNonNull(timer);
        ObjectUtils.requireNonNull(settings);
        
        // exit on no device
        if (!crisp.detectDevice()) {
            throw new DeviceNotFoundException();
        }
        
        // query values from CRISP and update the ui
        spinnerPanel.setAxisLabelText(crisp.getAxisString());
        spinnerPanel.update();
        statusPanel.update();
        
        // have the timer task update the status panel
        timer.createTimerTask(statusPanel);
        
        // load settings before the polling check
        settings.load();
        
        // start the timer if polling enabled
        if (spinnerPanel.isPollingEnabled()) {
            timer.start();
        }
        
        // disable spinners if focus already locked
        if (crisp.isFocusLocked()) {
            spinnerPanel.setEnabledFocusLock(false);
        }
        
        // TODO: enabled this feature on Tiger
        plotPanel.disableFocusCurveButton();
    }
    
    /**
     * Create the user interface for the plugin.
     */
    private void createUserInterface() {
        ObjectUtils.requireNonNull(crisp);
        ObjectUtils.requireNonNull(timer);
        
        setTitle(CRISPPlugin.menuName);
        loadAndRestorePosition(200, 200);
        setResizable(false);

        // use MigLayout as the layout manager
        setLayout(new MigLayout(
            "insets 20 10 10 10",
            "",
            ""
        ));
        
        // draw the title in bold
        lblTitle = new JLabel(CRISPPlugin.menuName);
        lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        
        Panel.setMigLayoutDefaults(
            "",
            "[]10[]",
            "[]10[]"
        );
        
        // create panels and layouts for ui elements
        plotPanel = new PlotPanel(crisp, this);
        spinnerPanel = new SpinnerPanel(crisp, timer);
        buttonPanel = new ButtonPanel(crisp, timer, spinnerPanel);
 
        statusPanel = new StatusPanel(crisp);
        statusPanel.setMigLayout(
            "",
            "40[]10[]",
            "[]10[]"
        );
        
        // now that the layouts are setup
        plotPanel.createComponents();
        spinnerPanel.createComponents();
        buttonPanel.createComponents();
        statusPanel.createComponents();
        
        // main layout panels
        leftPanel = new Panel();
        rightPanel = new Panel();

        // color the panels to make editing the ui easy
        if (DEBUG) {
            leftPanel.setBackground(Color.RED);
            rightPanel.setBackground(Color.BLUE);
            plotPanel.setBackground(Color.YELLOW);
        }
        
        // set panel layouts
        leftPanel.setMigLayout(
            "",
            "[]10[]",
            "[]50[]"
        );
        rightPanel.setMigLayout(
            "",
            "",
            ""
        );
        plotPanel.setMigLayout(
            "",
            "20[]20[]",
            ""
        );
        
        // add subpanels to the main layout panels
        leftPanel.add(spinnerPanel, "wrap");
        leftPanel.add(statusPanel, "center");
        rightPanel.add(buttonPanel, "");
        
        // add swing components to the frame
        add(lblTitle, "span, center, wrap");
        add(leftPanel, "");
        add(rightPanel, "wrap");
        add(plotPanel, "span 2");
        
        pack(); // set the window size automatically
        setIconImage(Icons.MICROSCOPE_ICON.getImage());
        
        // clean up resources when the frame is closed
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
    
    /**
     * Create a {@code windowClosing} event handler.
     * <p>
     * Stop the Swing timer from polling CRISP and 
     * save the user's settings.
     */
    private void registerWindowEventHandlers() {
        ObjectUtils.requireNonNull(timer);
        ObjectUtils.requireNonNull(settings);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                timer.stop();
                settings.save();
            }
        });
    }
}
