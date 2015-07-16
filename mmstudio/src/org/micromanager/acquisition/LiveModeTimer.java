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

import ij.gui.ImageWindow;

import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.MMStudio;
import org.micromanager.SnapLiveManager;
import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 * This class extends the java swing timer.  It periodically retrieves images
 * from the core and displays them in the live window
 * 
 * @author Henry Pinkard
 */
public class LiveModeTimer {
   private VirtualAcquisitionDisplay win_;
   private CMMCore core_;
   private MMStudio studio_;
   private SnapLiveManager snapLiveManager_;
   private int multiChannelCameraNrCh_;

   private Thread displayThread_;

   private long fpsTimer_; // Guarded by this
   private long fpsCounter_; // Guarded by this
   private long imageNumber_; // Guarded by this
   private long oldImageNumber_; // Guarded by this

   private long fpsInterval_ = 5000;
   private final NumberFormat format_;
   private boolean running_ = false;
   private Runnable task_;
   private final MMStudio.DisplayImageRoutine displayImageRoutine_;
   private LinkedBlockingQueue<TaggedImage> imageQueue_;
   private static int mCamImageCounter_ = 0;
   private boolean multiCam_ = false;

   // Helper class to start and stop timer task atomically.
   private class TimerController {
      private Timer timer_;
      private final Object timerLock_ = new Object();
      private boolean timerTaskShouldStop_ = true; // Guarded by timerLock_
      private boolean timerTaskIsBusy_ = false; // Guarded by timerLock_
      public void start(final Runnable task, long interval) {
         synchronized (timerLock_) {
            if (timer_ != null) {
               return;
            }
            timer_ = new Timer("Live mode timer");
            timerTaskShouldStop_ = false;
            TimerTask timerTask = new TimerTask() {
               @Override
               public void run() {
                  synchronized (timerLock_) {
                     if (timerTaskShouldStop_) {
                        return;
                     }
                     timerTaskIsBusy_ = true;
                  }
                  try {
                     task.run();
                  }
                  finally {
                     synchronized (timerLock_) {
                        timerTaskIsBusy_ = false;
                        timerLock_.notifyAll();
                     }
                  }
               }
            };
            timer_.schedule(timerTask, 0, interval);
         }
      }

      public void stop() {
         synchronized (timerLock_) {
            if (timer_ == null || timerTaskShouldStop_) {
               return;
            }

            timerTaskShouldStop_ = true;
            timer_.cancel();
         }
      }

      public void waitForCompletion() {
         synchronized (timerLock_) {
            while (timerTaskIsBusy_) {
               try {
                  timerLock_.wait();
               }
               catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
               }
            }
            timer_ = null;
         }
      }
   }
   private final TimerController timerController_ = new TimerController();

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
      studio_ = MMStudio.getInstance();
      snapLiveManager_ = studio_.getSnapLiveManager();
      core_ = studio_.getCore();
      format_ = NumberFormat.getInstance();
      format_.setMaximumFractionDigits(0x1);
      mCamImageCounter_ = 0;
      displayImageRoutine_ = new MMStudio.DisplayImageRoutine() {
         @Override
         public void show(final TaggedImage ti) {
            // Ensure the image has summary metadata set properly, as
            // AcquisitionManager relies on it (e.g. in addToAlbum(), when the
            // "snap to album" button is clicked).
            try {
               MDUtils.setSummary(ti.tags,
                     MMStudio.getInstance().getAcquisitionWithName(SnapLiveManager.SIMPLE_ACQ).getSummaryMetadata());
            }
            catch (Exception e) {
               ReportingUtils.logError(e, "Error setting summary metadata");
            }

            try {
               if (multiCam_) {
                  mCamImageCounter_++;
                  if (mCamImageCounter_ < multiChannelCameraNrCh_) {
                     studio_.normalizeTags(ti);
                     studio_.addImage(SnapLiveManager.SIMPLE_ACQ, ti, false, false);
                     return;
                  } else { // completes the set
                     mCamImageCounter_ = 0;
                  }              
               }
               ImageWindow window = snapLiveManager_.getSnapLiveWindow();
               if (!CanvasPaintPending.isMyPaintPending(
                        window.getCanvas(), this) ) {
                  CanvasPaintPending.setPaintPending(
                        window.getCanvas(), this);
                  studio_.normalizeTags(ti);
                  studio_.addImage(SnapLiveManager.SIMPLE_ACQ, ti, true, true);
                  studio_.updateLineProfile();
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

      core_.clearCircularBuffer();
      try {
         core_.startContinuousSequenceAcquisition(0);
      }
      catch (Exception e) {
         ReportingUtils.showError("Unable to start the sequence acquisition: " + e);
         throw(e);
      }
      setType();
      long period = getInterval();

      // Wait for first image to create ImageWindow, so that we can be sure about image size
      long start = System.currentTimeMillis();
      long now = start;
      // Give 10s extra for the camera to transfer the image to us.
      long timeout = period + 10000;
      while (core_.getRemainingImageCount() == 0 && (now - start < timeout) ) {
         now = System.currentTimeMillis();
         Thread.sleep(5);
      }
      if (now - start >= timeout) {
         throw new Exception("Camera did not send image within " + timeout + "ms");
      }

      TaggedImage timg = core_.getLastTaggedImage();

      // With first image acquired, create the display
      snapLiveManager_.validateDisplayAndAcquisition(timg);
      win_ = snapLiveManager_.getSnapLiveDisplay();
      long firstImageSequenceNumber = MDUtils.getSequenceNumber(timg.tags);

      synchronized (this) {
         fpsCounter_ = 0;
         fpsTimer_ = System.nanoTime();
         imageNumber_ = firstImageSequenceNumber;
         oldImageNumber_ = firstImageSequenceNumber;
      }

      imageQueue_ = new LinkedBlockingQueue<TaggedImage>(10);
      // XXX The logic here is very weird. We add this first image only if we
      // are using a single camera, because the single camera timer code checks
      // and eliminates duplicates of the same frame. For multi camera, we do
      // not add the image, since no checks for duplicates are performed
      // (which is a bug that needs to be fixed).
      if (!multiCam_) {
         imageQueue_.put(timg);
      }

      timerController_.start(task_, period);

      win_.getImagePlus().getWindow().toFront();
      running_ = true;
      displayThread_ = studio_.runDisplayThread(imageQueue_,
            displayImageRoutine_);
   }

   public void stop() {
      stop(true);
   }

   private void stop(boolean firstAttempt) {
      ReportingUtils.logMessage("Stop called in LiveModeTimer, " +
            (firstAttempt ? "first" : "second") + " attempt");

      // To safely shut down live mode, we need to stop (1) image acquisition,
      // (2) transfer of images from the Core to image queue (live mode timer),
      // and (3) transfer of images from the image processor output to the
      // display (display thread). Furthermore, we need to wait for all of
      // these to complete, so that whatever comes next (e.g. MDA) does not
      // see any resources in an inconsistent or busy state.

      // (1) Stop image acquisition (with up to one retry, FWIW)
      try {
         if (core_.isSequenceRunning()) {
            core_.stopSequenceAcquisition();
         }
         running_ = false;
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
         if (firstAttempt) {
            final long RETRY_DELAY_MS = 1000;
            ReportingUtils.logMessage(
                  "Will try again to stop live acquisition after " +
                  RETRY_DELAY_MS + " ms");
            new Timer().schedule(new TimerTask() {
               @Override
               public void run() {
                  stop(false);
               }
            }, RETRY_DELAY_MS);
         }
      }

      // (2) Stop transfering images from Core to image queue
      // Indicate end of image stream by sending POISON. Note that POISON might
      // be enqueued before the final image (since we have not yet waited for
      // the timer to finish), but that is not a problem because the queue
      // simply gets discarded.
      timerController_.stop();
      // Note: This will block if downstream (image processors and display
      // thread) do not consume images.
      ReportingUtils.logMessage("Waiting for timer to stop");
      timerController_.waitForCompletion();
      ReportingUtils.logMessage("Finished waiting for timer to stop");

      // Now nobody is enqueuing images to imageQueue_, so we can be sure
      // that the POISON we enqueue will not be followed by any images.
      try {
         if (imageQueue_ != null) {
            imageQueue_.put(TaggedImageQueue.POISON);
            imageQueue_ = null; // Prevent further attempts to send POISON
         }
      } catch (InterruptedException ex) {
           Thread.currentThread().interrupt();
      }

      // (3) Now the display thread should exit on its own when it sees the
      // POISON.

      // Finally, make sure we block until the POISON passes through the image
      // processors, so that no images get stranded.
      ReportingUtils.logMessage("Waiting for live mode display thread");
      try {
         displayThread_.join();
      }
      catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      ReportingUtils.logMessage("Finished waiting for live mode display thread");
      displayThread_ = null;
   }

   /**
    * Keep track of the last imagenumber, added by the circular buffer
    * that we have seen here
    * 
    * @param imageNumber the new imagenumber
    * @return true if imagenumber was incremented
    */
   private synchronized boolean setImageNumber(long imageNumber)
   {
      if (imageNumber > imageNumber_) {
         imageNumber_ = imageNumber;
         return true;
      }
      return false;
   }

   /**
    * Updates the fps timer (how fast does the camera pump images into the 
    * circular buffer) and display fps (how fast do we display the images)
    * It is called from tasks that are doing the actual image drawing
    */
   private synchronized void updateFPS() {
      if (!running_)
         return;
      if (imageNumber_ == oldImageNumber_) {
         return;
      }
      try {
         fpsCounter_++;
         long now = System.nanoTime();
         long diffMs = (now - fpsTimer_) / 1000000;
         if (diffMs > fpsInterval_) {
            double d = diffMs / 1000.0;
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
   private Runnable singleCameraLiveTask() {
      return new Runnable() {
         @Override
         public void run() {
            if (core_.getRemainingImageCount() == 0) {
               return;
            }
            if (win_.windowClosed()) //check is user closed window             
            {
               SwingUtilities.invokeLater(new Runnable() {
                  @Override public void run() { snapLiveManager_.setLiveMode(false); }
               });
            } else {
               try {
                  TaggedImage ti = core_.getLastTaggedImage();
                  // if we have already shown this image, do not do it again.
                  long imageNumber = MDUtils.getSequenceNumber(ti.tags);
                  if (setImageNumber(imageNumber)) {
                     imageQueue_.put(ti);
                  }
               } catch (final Exception ex) {
                  ReportingUtils.logMessage("Stopping live mode because of error...");
                  SwingUtilities.invokeLater(new Runnable() {
                     @Override public void run() {
                        snapLiveManager_.setLiveMode(false);
                        ReportingUtils.showError(ex);
                     }
                  });
               }
            }
         }
      };
   }

   
   private Runnable multiCamLiveTask() {
      return new Runnable() {
         @Override
         public void run() {
            if (core_.getRemainingImageCount() == 0) {
               return;
            }
            if (win_.windowClosed() || !studio_.acquisitionExists(SnapLiveManager.SIMPLE_ACQ)) {
               SwingUtilities.invokeLater(new Runnable() {
                  @Override public void run() { snapLiveManager_.setLiveMode(false); }
               });
            } else {
               try {
                  String camera = core_.getCameraDevice();
                  Set<String> cameraChannelsAcquired = new HashSet<String>();
                  for (int i = 0; i < 2 * multiChannelCameraNrCh_; ++i) {
                     TaggedImage ti = core_.getNBeforeLastTaggedImage(i);
                     String channelName;
                     if (ti.tags.has(camera + "-CameraChannelName")) {
                        channelName = ti.tags.getString(camera + "-CameraChannelName");
                        if (!cameraChannelsAcquired.contains(channelName)) {
                           MDUtils.setChannelName(ti.tags, channelName);
                           int ccIndex = ti.tags.getInt(camera + "-CameraChannelIndex");
                           MDUtils.setChannelIndex(ti.tags, ccIndex);
                           if (ccIndex == 0) {
                              // XXX We do keep track of the image number, but
                              // we are currently ignoring the check for new
                              // images here (see the single camera version).
                              // When fixing this, make sure to handle the
                              // first frames correctly, even for very slow
                              // acquisitions (see begin()).
                              setImageNumber(MDUtils.getSequenceNumber(ti.tags));
                           }
                           imageQueue_.put(ti);
                           cameraChannelsAcquired.add(channelName);
                        }
                        if (cameraChannelsAcquired.size() == multiChannelCameraNrCh_) {
                           break;
                        }
                     }
                  }
               } catch (final Exception exc) {
                  ReportingUtils.logMessage("Stopping live mode because of error...");
                  SwingUtilities.invokeLater(new Runnable() {
                     @Override public void run() {
                        snapLiveManager_.setLiveMode(false);
                        ReportingUtils.showError(exc);
                     }
                  });
               }
            }
         }
      };
   }

}
