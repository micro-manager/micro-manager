/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import mmcorej.CMMCore;
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
   private boolean isFinished_ = false;
   private Lock pauseLock = new ReentrantLock();

   public Engine(CMMCore core) {
      core_ = core;
   }

   public void setupStandardSequence(SequenceSettings settings) {
      try {
         ArrayList<ImageRequest> requestSequence = SequenceGenerator.generateSequence(settings, core_.getExposure());
         taskSequence_ = SequenceGenerator.makeTaskSequence(this, requestSequence);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public synchronized void start() {
      setFinished(false);
      new Thread() {

         @Override
         public void run() {
            stopRequested_ = false;
            for (Runnable task : taskSequence_) {
               if (!stopHasBeenRequested()) {
                  pauseLock.lock();
                  try {
                     task.run();
                  } finally {
                     pauseLock.unlock();
                  }
               }
            }
            Engine.this.isFinished_ = true;
         }
      }.start();
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

   public synchronized boolean isFinished() {
      return isFinished_;
   }

   private synchronized void setFinished(boolean state) {
      isFinished_ = state;
   }
}
