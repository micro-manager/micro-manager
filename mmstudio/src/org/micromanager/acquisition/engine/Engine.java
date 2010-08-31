/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import org.micromanager.api.EngineTask;
import java.util.ArrayList;
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
   ArrayList<EngineTask> taskSequence_;
   private boolean stopRequested_ = false;
   private boolean isRunning_ = false;
   public boolean autoShutterSelected_;
   public TaggedImageQueue imageReceivingQueue_;
   private long startTimeNs_;
   private Engine this_;
   private SequenceSettings settings_;
   private boolean isPaused_ = false;
   private EngineTask currentTask_;

   public Engine(CMMCore core, TaggedImageQueue imageReceivingQueue) {
      core_ = core;
      imageReceivingQueue_ = imageReceivingQueue;
   }

   public void setupStandardSequence(SequenceSettings settings) {
      try {
         ArrayList<ImageRequest> requestSequence = SequenceGenerator.generateSequence(settings, core_.getExposure());
         taskSequence_ = SequenceGenerator.makeTaskSequence(this, requestSequence);
         settings_ = settings;
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public synchronized long getStartTimeNs() {
      return startTimeNs_;
   }
   
   public synchronized void start() {
      setRunning(true);
      this_ = this;

      new Thread() {
         @Override
         public void run() {
            startTimeNs_ = System.nanoTime();
            autoShutterSelected_ = core_.getAutoShutter();

            core_.setAutoShutter(false);

            double originalZ = Double.MIN_VALUE;
            if (settings_.slices.size() > 0) {
               try {
                  originalZ = core_.getPosition(core_.getFocusDevice());
               } catch (Exception ex) {
                  ReportingUtils.showError(ex);
               }
            }

            stopRequested_ = false;
            for (EngineTask task : taskSequence_) {
               while (isPaused() && !stopHasBeenRequested()) {
                  JavaUtils.sleep(10);
               }
               if (!stopHasBeenRequested() && !isPaused()) {
                  setCurrentTask(task);
                  task.run();
               } else {
                  break;
               }
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
