/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2024, Applied Scientific Instrumentation
 */

package com.asiimaging.devices.crisp;

import com.asiimaging.crisp.panels.StatusPanel;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingWorker;

/**
 * A SwingTimer that helps update the ui with values queried from CRISP.
 */
public class CRISPTimer {

   private int pollRateMs;
   private int skipCounter;
   private int skipRefresh;

   private StatusPanel panel;
   private final CRISP crisp;
   private final AtomicBoolean isPolling;
   private SwingWorker<Void, Void> worker;

   public CRISPTimer(final CRISP crisp) {
      this.crisp = Objects.requireNonNull(crisp);
      isPolling = new AtomicBoolean();
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
   public void setStatusPanel(final StatusPanel panel) {
      this.panel = panel;
   }

   /**
    * Create the SwingWorker that is called at the polling rate in milliseconds.
    */
   private void createPollingWorker() {
      worker = new SwingWorker<Void, Void>() {
         @Override
         protected Void doInBackground() {
            long startTime = System.nanoTime();
            while (isPolling.get()) {
               final long endTime = System.nanoTime();
               final double elapsedMs = (endTime - startTime) / 1_000_000.0;
               if (elapsedMs > pollRateMs) {
                  if (skipCounter > 0) {
                     skipCounter--;
                     panel.setStateLabelText("Calibrating..." + computeRemainingSeconds() + "s");
                  } else {
                     panel.update();
                  }
                  //System.out.println("tick: " + elapsedMs + " ms");
                  startTime = System.nanoTime();
               }
            }
            return null;
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
    * Start polling CRISP.
    */
   public void start() {
      isPolling.set(true);
      if (crisp.isTiger()) {
         crisp.setRefreshPropertyValues(true);
      }
      createPollingWorker();
      worker.execute();
   }

   /**
    * Stop polling CRISP.
    */
   public void stop() {
      if (crisp.isTiger()) {
         crisp.setRefreshPropertyValues(false);
      }
      isPolling.set(false);
   }

   /**
    * Called in the CRISP setStateLogCal method to skip timer updates.
    */
   public void onLogCal() {
      // controller becomes unresponsive during loG_cal => skip polling a few times
      skipCounter = skipRefresh;
      panel.setStateLabelText("Calibrating..." + computeRemainingSeconds() + "s");
   }

   /**
    * Returns the remaining time that polling updates will be skipped in seconds.
    *
    * @return the number of seconds remaining
    */
   private String computeRemainingSeconds() {
      return Integer.toString((pollRateMs * skipCounter) / 1000);
   }

   /**
    * Set the polling rate of the Swing timer.
    *
    * @param rate the polling rate in milliseconds
    */
   public void setPollRateMs(final int rate) {
      skipRefresh = Math.round(2500.0f / rate);
      pollRateMs = rate;
   }

}

