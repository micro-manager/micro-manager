/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class Engine {

   CMMCore core_ = null;
   public double lastWakeTime_;
   ArrayList<Runnable> taskSequence_;
   private boolean stopRequested_ = false;
   private boolean isRunning_ = false;
   private Lock pauseLock = new ReentrantLock();
   public boolean autoShutterSelected_;
   public TaggedImageQueue imageReceivingQueue_;
   private long startTimeNs_;
   private Engine this_;

   public Engine(CMMCore core, TaggedImageQueue imageReceivingQueue) {
      core_ = core;
      imageReceivingQueue_ = imageReceivingQueue;
   }

   public void setupStandardSequence(SequenceSettings settings) {
      try {
         ArrayList<ImageRequest> requestSequence = SequenceGenerator.generateSequence(settings, core_.getExposure());
         taskSequence_ = SequenceGenerator.makeTaskSequence(this, requestSequence);
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
            CoreState originalState = getCoreState();
            core_.setAutoShutter(false);
            
            stopRequested_ = false;
            for (Runnable task : taskSequence_) {
               if (!stopHasBeenRequested()) {
                  pauseLock.lock();
                  try {
                     task.run();
                  } finally {
                     pauseLock.unlock();
                  }
               } else {
                  break;
               }
            }
            core_.setAutoShutter(autoShutterSelected_);

            this_.setCoreState(originalState);

            setRunning(false);
            try {
               imageReceivingQueue_.put(TaggedImageQueue.POISON);
            } catch (InterruptedException ex) {
               ReportingUtils.showError(ex);
            }
         }
      }.start();
   }


   public class CoreState {
      Configuration systemState;
      double zPosition = 0;
      boolean zSaved = false;
   }

   public CoreState getCoreState() {
      CoreState state = new CoreState();
      state.systemState = core_.getSystemStateCache();
      try {
        state.zPosition = core_.getPosition(core_.getFocusDevice());
        state.zSaved = true;
      } catch (Exception ex) {
        state.zSaved = false;
        ReportingUtils.logError(ex);
      }
      return state;
   }

   public void setCoreState(CoreState state) {
      core_.setSystemState(state.systemState);
      if (state.zSaved)
         try {
         core_.setPosition(core_.getFocusDevice(), state.zPosition);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public synchronized void pause() {
      pauseLock.lock();
   }

   public synchronized void resume() {
      pauseLock.unlock();
   }

   public synchronized void stop() {
      stopRequested_ = true;
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
