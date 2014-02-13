///////////////////////////////////////////////////////////////////////////////
//FILE:          LiveModeTimer.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2011
//
// COPYRIGHT:    University of California, San Francisco, 2011
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
//
package org.micromanager.acquisition;

import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 * This class extends the java swing timer.  It periodically retrieves images
 * from the core and displays them in the live window
 * 
 * @author Henry Pinkard
 */
public class LiveModeTimer {

   private static final String ACQ_NAME = MMStudioMainFrame.SIMPLE_ACQ;
   private VirtualAcquisitionDisplay win_;
   private CMMCore core_;
   private MMStudioMainFrame gui_;
   private int multiChannelCameraNrCh_;
   private long fpsTimer_;
   private long fpsCounter_;
   private long imageNumber_;
   private long oldImageNumber_;
   private long fpsInterval_ = 5000;
   private final NumberFormat format_;
   private boolean running_ = false;
   private Timer timer_;
   private TimerTask task_;
   private final MMStudioMainFrame.DisplayImageRoutine displayImageRoutine_;
   private LinkedBlockingQueue<TaggedImage> imageQueue_;
   private static int mCamImageCounter_ = 0;
   private boolean multiCam_ = false;
   private AtomicBoolean timerLock_;
   
   
   /**
    * The LivemodeTimer constructor defines a DisplayImageRoutine that 
    * synchronizes image display with the "paint" function (currently execute
    * by the ImageCanvas of ImageJ).  
    * 
    * The multiCamLiveTask needs extra synchronization at this point.
    * The multiCamLiveTask generates tagged images in groups of
    * multiChannelCameraNrCh_, however, we only want to update
    * the display (which is costly) when we have the whole group.
    */
   
   public LiveModeTimer() {
      timerLock_ = new AtomicBoolean(false);
      gui_ = MMStudioMainFrame.getInstance();
      core_ = gui_.getCore();
      format_ = NumberFormat.getInstance();
      format_.setMaximumFractionDigits(0x1);
      mCamImageCounter_ = 0;
      displayImageRoutine_ = new MMStudioMainFrame.DisplayImageRoutine() {
         @Override
         public void show(final TaggedImage ti) {
            try {
               if (multiCam_) {
                  mCamImageCounter_++;
                  if (mCamImageCounter_ < multiChannelCameraNrCh_) {
                     gui_.normalizeTags(ti);
                     gui_.addImage(ACQ_NAME, ti, false, false);
                     return;
                  } else { // completes the set
                     mCamImageCounter_ = 0;
                  }              
               }
               if (!CanvasPaintPending.isMyPaintPending(
                        gui_.getImageWin().getCanvas(), this) ) {
                  CanvasPaintPending.setPaintPending(
                        gui_.getImageWin().getCanvas(), this);
                  gui_.normalizeTags(ti);
                  gui_.addImage(ACQ_NAME, ti, true, true);
                  gui_.updateLineProfile();
                  updateFPS();
               }
            } catch (MMScriptException e) {
               ReportingUtils.logError(e);
            }
         }
      };
   }

   /**
    * Determines the optimum interval for the live mode timer task to happen
    * Also sets variable fpsInterval_
    */
   private long getInterval() {
      double interval = 20;
      try {
         interval = Math.max(core_.getExposure(), interval);
      } catch (Exception e) {
         ReportingUtils.logError("Unable to get exposure from core");
      }
      fpsInterval_ =  (long) (20 *  interval);
      if (fpsInterval_ < 1000)
         fpsInterval_ = 1000;
      
      return (int) interval;
   }

   /**
    * Determines whether we are dealing with multiple cameras
    */
   private void setType() {
      multiChannelCameraNrCh_ = (int) core_.getNumberOfCameraChannels();
      if (multiChannelCameraNrCh_ == 1) {
         task_ = singleCameraLiveTask();
         multiCam_ = false;
      } else {
         task_ = multiCamLiveTask();
         multiCam_ = true;
      }
   }

   public boolean isRunning() {
      return running_;
   }

   @SuppressWarnings("SleepWhileInLoop")
   public void begin() throws Exception {
         if(running_) {
            return;
         }
         timer_ = new Timer("Live mode timer");
         
         core_.clearCircularBuffer();
            
         core_.startContinuousSequenceAcquisition(0);
         setType();
         long delay = getInterval();

         // Wait for first image to create ImageWindow, so that we can be sure about image size
         long start = System.currentTimeMillis();
         long now = start;
         long timeout = Math.min(10000, delay * 150);
         while (core_.getRemainingImageCount() == 0 && (now - start < timeout) ) {
            now = System.currentTimeMillis();
            Thread.sleep(5);
         }
         if (now - start >= timeout) {
            throw new Exception("Camera did not send image within a reasonable time");
         }
                    
         TaggedImage timg = core_.getLastTaggedImage();

         // With first image acquired, create the display
         gui_.checkSimpleAcquisition();
         win_ = MMStudioMainFrame.getSimpleDisplay();
         
         fpsCounter_ = 0;
         fpsTimer_ = System.currentTimeMillis();
         imageNumber_ = timg.tags.getLong("ImageNumber");
         oldImageNumber_ = imageNumber_;

         imageQueue_ = new LinkedBlockingQueue<TaggedImage>(10);
         timer_.schedule(task_, 0, delay);
         win_.liveModeEnabled(true);
         
         win_.getImagePlus().getWindow().toFront();
         running_ = true;
         gui_.runDisplayThread(imageQueue_, displayImageRoutine_);
   }

   
   public void stop() {
      stop(true);
   }
   
   private void stop(boolean firstAttempt) {
      ReportingUtils.logMessage("Stop called in LivemodeTimer, " + firstAttempt);
      // Before putting the poison image in the queue, we have to be sure that 
      // no new images will be inserted after the Poison
      // doing so results in a very bad state with hanging image processors
      if (timer_ != null) {
         timer_.cancel();
      }
      // wait for timer to exit
      while (timerLock_.get()) {
      }
      try {
         if (imageQueue_ != null)
            imageQueue_.put(TaggedImageQueue.POISON);
      } catch (InterruptedException ex) {
           ReportingUtils.logError(ex); 
      }      
      try {
         if (core_.isSequenceRunning())
            core_.stopSequenceAcquisition();
         if (win_ != null) {
            win_.liveModeEnabled(false);
         }
         running_ = false;
      } catch (Exception ex) {
         try {
         } catch (Exception e) {
            ReportingUtils.showError("Error closing shutter");
         }
         ReportingUtils.showError(ex);
         //Wait 1 s and try to stop again
         if (firstAttempt) {
            final Timer delayStop = new Timer();
            delayStop.schedule( new TimerTask() {
               @Override
               public void run() {
                  stop(false);
               }},1000);   
         }
      } 
   }
   
   /**
    * Keep track of the last imagenumber, added by the circular buffer
    * that we have seen here
    * 
    * @param imageNumber 
    */
   private synchronized void setImageNumber(long imageNumber) 
   {
      imageNumber_ = imageNumber;
   }
           

   /**
    * Updates the fps timer (how fast does the camera pump images into the 
    * circular buffer) and display fps (how fast do we display the images)
    * It is called from tasks that are doing the actual image drawing
    * 
    */
   public synchronized void updateFPS() {
      if (!running_)
         return;
      try {
         fpsCounter_++;
         long now = System.currentTimeMillis();
         long diff = now - fpsTimer_;
         if (diff > fpsInterval_) {
            double d = diff/ 1000.0;
            double fps = fpsCounter_ / d;
            double dfps = (imageNumber_ - oldImageNumber_) / d;
            win_.displayStatusLine("fps: " + format_.format(dfps) +
                    ", display fps: " + format_.format(fps));
            fpsCounter_ = 0;
            fpsTimer_ = now;
            oldImageNumber_ = imageNumber_;
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   /**
    * Task executed to display live images when using a single camera
    * 
    * @return 
    */
   private TimerTask singleCameraLiveTask() {
      return new TimerTask() {
         @Override
         public void run() {
            if (core_.getRemainingImageCount() == 0) {
               return;
            }
            if (win_.windowClosed()) //check is user closed window             
            {
               gui_.enableLiveMode(false);
            } else {
               try {
                  timerLock_.set(true);
                  TaggedImage ti = core_.getLastTaggedImage();
                  // if we have already shown this image, do not do it again.
                  long imageNumber = ti.tags.getLong("ImageNumber");
                  if (imageNumber > imageNumber_ ) {
                     setImageNumber(imageNumber);
                     imageQueue_.put(ti);
                  }
               } catch (Exception ex) {
                  ReportingUtils.logMessage("Stopping live mode because of error...");
                  gui_.enableLiveMode(false);
                  ReportingUtils.showError(ex);
               } finally {
                  timerLock_.set(false);
               }
            }
         }
      };
   }

   
   private TimerTask multiCamLiveTask() {
      return new TimerTask() {
         @Override
         public void run() {
            if (core_.getRemainingImageCount() == 0) {
               return;
            }
            if (win_.windowClosed() || !gui_.acquisitionExists(MMStudioMainFrame.SIMPLE_ACQ)) {
               gui_.enableLiveMode(false);  //disable live if user closed window
            } else {
               try {
                  timerLock_.set(true);
                  String camera = core_.getCameraDevice();
                  Set<String> cameraChannelsAcquired = new HashSet<String>();
                  for (int i = 0; i < 2 * multiChannelCameraNrCh_; ++i) {
                     TaggedImage ti = core_.getNBeforeLastTaggedImage(i);
                     String channelName;
                     if (ti.tags.has(camera + "-CameraChannelName")) {
                        channelName = ti.tags.getString(camera + "-CameraChannelName");
                        if (!cameraChannelsAcquired.contains(channelName)) {
                           ti.tags.put("Channel", channelName);
                           int ccIndex = ti.tags.getInt(camera + "-CameraChannelIndex");
                           ti.tags.put("ChannelIndex", ccIndex);
                           if (ccIndex == 0) {
                              setImageNumber(ti.tags.getLong("ImageNumber"));
                           }
                           imageQueue_.put(ti);
                           cameraChannelsAcquired.add(channelName);
                        }
                        if (cameraChannelsAcquired.size() == multiChannelCameraNrCh_) {
                           break;
                        }
                     }
                  }
               } catch (Exception exc) {
                  ReportingUtils.logMessage("Stopping live mode because of error...");
                  gui_.enableLiveMode(false);
                  ReportingUtils.showError(exc);
               } finally {
                  timerLock_.set(false);
               }
            }
         }
      };
   }

}
