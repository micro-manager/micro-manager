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

package com.asiimaging.crisp.control;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.asiimaging.crisp.panels.StatusPanel;

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
        skipRefresh = 20;
        pollRateMs = 250;
        
        // created later
        pollTask = null;
        panel = null;
    }
    
    @Override
    public String toString() {
        return String.format("%s[]", 
            getClass().getSimpleName());
    }
    
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
        pollTask = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                //System.out.println(SwingUtilities.isEventDispatchThread());
                if (skipCounter > 0) {
                    skipCounter--;
                    panel.setStateLabelText("Calibrating..." + computeRemainingSeconds() + "s");
                } else {
                    panel.update();
                }
            }
        };
    }
    
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
    
    
//    * This method is called every timer tick, at the polling rate in milliseconds.
//    * The status panel is updated with new values queried from the CRISP unit.
//    * When CRISP enters the Log Cal state, the skipCounter is > 0.

    public void onLogCal() {
        // controller becomes unresponsive during loG_cal, skip polling a few times
        if (timer.isRunning()) {
            skipCounter = skipRefresh;
            timer.restart();
            panel.setStateLabelText("Calibrating..." + computeRemainingSeconds() + "s");
        }
    }
    
    private String computeRemainingSeconds() {
        return Float.toString((pollRateMs*skipCounter)/1000);
    }
    
    public int getPollRateMs() {
        return pollRateMs;
    }
    
    /**
     * Set the polling rate of the Swing timer.
     * 
     * @param rate the polling rate in milliseconds
     */
    public void setPollRateMs(final int rate) {
        timer.setDelay(rate);
        pollRateMs = rate;
    }
}

