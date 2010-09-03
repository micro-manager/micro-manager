/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.api.EngineTask;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import mmcorej.CMMCore;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class Engine {

   CMMCore core_ = null;
   public double lastWakeTime_;
   private boolean stopRequested_ = false;
   private boolean isRunning_ = false;
   public boolean autoShutterSelected_;
   public TaggedImageQueue imageReceivingQueue_;
   private long startTimeNs_;
   private Engine this_;
   private SequenceSettings settings_;
   private boolean isPaused_ = false;
   private EngineTask currentTask_;
   private LinkedBlockingQueue<EngineTask> taskQueue_;

   public Engine(CMMCore core, TaggedImageQueue imageReceivingQueue) {
      core_ = core;
      imageReceivingQueue_ = imageReceivingQueue;
   }

   public void setupStandardSequence(SequenceSettings settings) {
      try {
         SequenceGenerator generator = new SequenceGenerator();
         taskQueue_ = generator.generateSequence(this, settings, core_.getExposure());
         settings_ = settings;
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public synchronized long getStartTimeNs() {
      return startTimeNs_;
   }
   
   public void start() {
      setRunning(true);
      this_ = this;

      new Thread() {
         @Override
         public void run() {
            startTimeNs_ = System.nanoTime();
            autoShutterSelected_ = core_.getAutoShutter();

            boolean shutterWasOpen = false;
            core_.setAutoShutter(false);
            try {
               shutterWasOpen = core_.getShutterOpen();
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
            double originalZ = Double.MIN_VALUE;
            if (settings_.slices.size() > 0) {
               try {
                  originalZ = core_.getPosition(core_.getFocusDevice());
               } catch (Exception ex) {
                  ReportingUtils.showError(ex);
               }
            }

            stopRequested_ = false;
            EngineTask task = null;
            for (;;) {

               do {
                  try {
                     task = taskQueue_.poll(30, TimeUnit.MILLISECONDS);
                  } catch (InterruptedException ex) {
                     ReportingUtils.logError(ex);
                     task = null;
                  }
               } while (task == null && !stopHasBeenRequested());

               while (isPaused() && !stopHasBeenRequested()) {
                  JavaUtils.sleep(10);
               }

               if (task instanceof StopTask || stopHasBeenRequested()) {
                  break;
               } else {
                  setCurrentTask(task);
                  task.run();
               }
            
            }
            
            try {
               core_.setShutterOpen(shutterWasOpen);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
            core_.setAutoShutter(autoShutterSelected_);

            if (settings_.slices.size() > 0 && originalZ != Double.MIN_VALUE) {
               try {
                  core_.setPosition(core_.getFocusDevice(), originalZ);
               } catch (Exception ex) {
                  ReportingUtils.logError(ex);
               }
            }


            setRunning(false);
            try {
               imageReceivingQueue_.put(TaggedImageQueue.POISON);
            } catch (InterruptedException ex) {
               ReportingUtils.showError(ex);
            }
         }


      }.start();
   }

   private void setCurrentTask(EngineTask task) {
      currentTask_ = task;
   }

   private synchronized boolean isPaused() {
      return isPaused_;
   }

   public synchronized void pause() {
      isPaused_ = true;
      currentTask_.requestPause();
   }

   public synchronized void resume() {
      isPaused_ = false;
      currentTask_.requestResume();
   }

   public synchronized void stop() {
      stopRequested_ = true;
      currentTask_.requestStop();
   }

   public synchronized boolean stopHasBeenRequested() {
      return stopRequested_;
   }

   public synchronized boolean isRunning() {
      return isRunning_;
   }

   private synchronized void setRunning(boolean state) {
      isRunning_ = state;
   }
}
