/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.micromanager.api.EngineTask;
import java.util.concurrent.TimeUnit;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.utils.AutofocusManager;
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
   public BlockingQueue<TaggedImage> imageReceivingQueue_;
   private long startTimeNs_;
   private SequenceSettings acquisitionSettings_;
   private boolean isPaused_ = false;
   private EngineTask currentTask_;
   private BlockingQueue<ImageRequest> requestQueue_;
   private final AutofocusManager afMgr_;

   public Engine(CMMCore core, AutofocusManager afMgr,
           BlockingQueue<ImageRequest> requestQueue,
           SequenceSettings acquisitionSettings) {
      core_ = core;
      imageReceivingQueue_ = new LinkedBlockingQueue<TaggedImage>();
      afMgr_ = afMgr;
      requestQueue_ = requestQueue;
      acquisitionSettings_ = acquisitionSettings;
   }

   public synchronized long getStartTimeNs() {
      return startTimeNs_;
   }

   public BlockingQueue<TaggedImage> getOutputChannel() {
      return imageReceivingQueue_;
   }

   private class EngineThread extends Thread {

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
         if (acquisitionSettings_.slices.size() > 0) {
            try {
               originalZ = core_.getPosition(core_.getFocusDevice());
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }

         stopRequested_ = false;
         ImageRequest request = null;
         EngineTask task = null;

         for (;;) {
            do {
               try {
                  request = requestQueue_.poll(30, TimeUnit.MILLISECONDS);
               } catch (InterruptedException ex) {
                  ReportingUtils.logError(ex);
                  request = null;
               }
            } while (request == null && !stopHasBeenRequested());

            while (isPaused() && !stopHasBeenRequested()) {
               JavaUtils.sleep(10);
            }

            if ((request.stop == true) || stopHasBeenRequested()) {
               break;
            } else {
               task = new ImageTask(Engine.this, request);
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

         if (acquisitionSettings_.slices.size() > 0 && originalZ != Double.MIN_VALUE) {
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
   }

   public void start() {
      setRunning(true);

      Thread engineThread = new EngineThread();
      //We want high performance:
      //if (Runtime.getRuntime().availableProcessors() > 1)
      engineThread.setPriority(Thread.MAX_PRIORITY);
      engineThread.start();
   }

   private void setCurrentTask(EngineTask task) {
      currentTask_ = task;
   }

   private synchronized boolean isPaused() {
      return isPaused_;
   }

   public synchronized void pause() {
      isPaused_ = true;
     // currentTask_.requestPause();
   }

   public synchronized void resume() {
      isPaused_ = false;
    //  currentTask_.requestResume();
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

   public AutofocusManager getAutofocusManager() {
      return afMgr_;
   }
}
