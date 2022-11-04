/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.ui;

import javax.swing.JFrame;

import com.asiimaging.tirf.model.TIRFControlModel;
import com.asiimaging.tirf.TIRFControlPlugin;
import com.asiimaging.tirf.model.data.Icons;
import com.asiimaging.tirf.ui.panels.ButtonPanel;
import com.asiimaging.tirf.ui.panels.TabPanel;
import com.asiimaging.tirf.model.devices.Scanner;
import com.asiimaging.tirf.ui.components.Label;

import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;
import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.events.LiveModeEvent;
import org.micromanager.internal.utils.WindowPositioning;

import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class TIRFControlFrame extends JFrame {

    public static final boolean DEBUG = false;

    private final Studio studio;

    private TabPanel tabPanel;
    private ButtonPanel buttonPanel;

    private TIRFControlModel model;

    public TIRFControlFrame(final Studio studio) {
        this.studio = studio;

        // register for events
        studio.events().registerForEvents(this);

        // window closing method
        registerWindowEventHandlers();

        // save/load window position
        WindowPositioning.setUpBoundsMemory(this, this.getClass(), this.getClass().getSimpleName());

    }

    public void createUserInterface() {
        setTitle(TIRFControlPlugin.menuName);
        setResizable(false);

        // use MigLayout as the layout manager
        setLayout(new MigLayout(
             "insets 20 30 20 20",
            "[]20[]",
            "[]10[]"
        ));

        final Label lblTitle = new Label(TIRFControlPlugin.menuName);
        lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));

        // ui elements
        tabPanel = new TabPanel(model, this);
        buttonPanel = new ButtonPanel(model, this);

        // add ui elements to the panel
        add(lblTitle, "wrap");
        add(tabPanel, "wrap");
        add(buttonPanel, "");

        pack(); // set the window size automatically
        setIconImage(Icons.MICROSCOPE.getImage());

        // clean up resources when the frame is closed
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // show the frame
        setVisible(true);
        toFront();
    }

    /**
     * This method is called when the frame closes.
     */
    private void registerWindowEventHandlers() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                model.getUserSettings().save(model);
                model.getScanner().setBeamEnabled(false);
                model.getScanner().setFastCirclesState(Scanner.Values.FAST_CIRCLES_STATE.OFF);
                //System.out.println("settings saved => plugin closed!");
            }
        });
    }

    // TODO: turn off external triggering when using live mode
    /**
     * Enable or disable the live mode window.
     */
    public void toggleLiveMode() {
        // set to internal trigger mode
        if (model.getCamera().isTriggerModeExternal()) {
            model.getCamera().setTriggerModeInternal();
        }
        // toggle live mode
        if (studio.live().isLiveModeOn()) {
            studio.live().setLiveModeOn(false);
            // close the live mode window if it exists
            if (studio.live().getDisplay() != null) {
                studio.live().getDisplay().close();
            }
        } else {
            studio.live().setLiveModeOn(true);
        }
    }

    public void setModel(final TIRFControlModel model) {
        this.model = model;
    }

    public TabPanel getTabPanel() {
        return tabPanel;
    }

    public ButtonPanel getButtonPanel() {
        return buttonPanel;
    }

    @Subscribe
    public void liveModeListener(LiveModeEvent event) {
        if (!studio.live().isLiveModeOn()) {
            buttonPanel.getLiveModeButton().setState(false);
        }
    }

    // Note: this does not work for all cameras
    @Subscribe
    public void onExposureChanged(ExposureChangedEvent event) {
        tabPanel.getScannerTab().updateFastCirclesHzLabel();
    }

}