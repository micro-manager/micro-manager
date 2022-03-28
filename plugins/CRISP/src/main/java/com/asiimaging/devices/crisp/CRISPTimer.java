/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.devices.crisp;

import java.awt.event.ActionListener;
import javax.swing.Timer;

import com.asiimaging.crisp.panels.StatusPanel;

/**
 * A SwingTimer that helps update the ui with values queried from CRISP.
 * 
 */
public class CRISPTimer {

    private int pollRateMs;
    private int skipCounter;
    private int skipRefresh;
    private ActionListener pollTask;

    private Timer timer;
    private StatusPanel panel;
    private final CRISP crisp;

    public CRISPTimer(final CRISP crisp) {
        this.crisp = crisp;
        skipCounter = 0;
        skipRefresh = 10; // this value changes based on pollRateMs
        pollRateMs = 250;
    }
    
    @Override
    public String toString() {
        return String.format("%s[pollRateMs=%s]", getClass().getSimpleName(), pollRateMs);
    }

    /**
     * Create the task to be run every tick of the timer.
     *
     * @param panel the Panel to update
     */
    public void createTimerTask(final StatusPanel panel) {
        this.panel = panel;
        createPollingTask();
        timer = new Timer(pollRateMs, pollTask);
    }
    
    /**
     * Create the event handler that is called at the polling rate in milliseconds.
     * <p>
     * The polling task runs on the Event Dispatch Thread. 
     */
    private void createPollingTask() {
        pollTask = event -> {
            if (skipCounter > 0) {
                skipCounter--;
                panel.setStateLabelText("Calibrating..." + computeRemainingSeconds() + "s");
            } else {
                panel.update();
            }
        };
    }

    /**
     * Starts or stops the timer based on state variable.
     *
     * @param state true to start the timer
     */
    public void setPollState(final boolean state) {
        if (state) {
            start();
        } else {
            stop();
        }
    }
    
    /**
     * Start the Swing timer.
     */
    public void start() {
        timer.start();
        if (crisp.isTiger()) {
            crisp.setRefreshPropertyValues(true);
        }
    }
    
    /**
     * Stop the Swing timer.
     */
    public void stop() {
        if (crisp.isTiger()) {
            crisp.setRefreshPropertyValues(false);
        }
        timer.stop();
    }

    /**
     * Called in the CRISP setStateLogCal method to skip timer updates.
     */
    public void onLogCal() {
        // controller becomes unresponsive during loG_cal => skip polling a few times
        if (timer.isRunning()) {
            skipCounter = skipRefresh;
            timer.restart();
            panel.setStateLabelText("Calibrating..." + computeRemainingSeconds() + "s");
        }
    }

    /**
     * Returns the remaining time that polling updates will be skipped in seconds.
     *
     * @return the number of seconds remaining
     */
    private String computeRemainingSeconds() {
        return Integer.toString((pollRateMs*skipCounter)/1000);
    }
    
    /**
     * Set the polling rate of the Swing timer.
     * 
     * @param rate the polling rate in milliseconds
     */
    public void setPollRateMs(final int rate) {
        skipRefresh = Math.round(2500.0f/rate);
        pollRateMs = rate;
        timer.setDelay(rate);
    }

}

