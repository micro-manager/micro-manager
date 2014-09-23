package org.micromanager;

import java.util.ArrayList;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.micromanager.acquisition.ReaderRAM;

import org.micromanager.data.DefaultCoords;
import org.micromanager.data.DefaultDatastore;
import org.micromanager.data.DefaultImage;

import org.micromanager.imagedisplay.dev.TestDisplay;

import org.micromanager.internalinterfaces.LiveModeListener;

import org.micromanager.MMStudio;

import org.micromanager.utils.ReportingUtils;

/**
 * This class is responsible for all logic surrounding live mode.
 */
public class LiveMode {
   private CMMCore core_;
   private TestDisplay display_;
   private DefaultDatastore store_;
   private ArrayList<LiveModeListener> listeners_;
   private boolean isOn_ = false;
   // Suspended means that we *would* be running except we temporarily need
   // to halt for the duration of some action (e.g. changing the exposure
   // time). See setSuspended().
   private boolean isSuspended_ = false;
   private boolean shouldStopGrabberThread_ = false;
   private Thread grabberThread_;

   public LiveMode() {
      core_ = MMStudio.getInstance().getMMCore();
      store_ = new DefaultDatastore();
      store_.setReader(new ReaderRAM(store_));
   }

   public void setLiveMode(boolean isOn) {
      if (isOn_ == isOn) {
         return;
      }
      isOn_ = isOn;
      if (isOn_) {
         startLiveMode();
      }
      else {
         stopLiveMode();
      }
   }

   /**
    * If live mode needs to temporarily stop for some action (e.g. changing
    * the exposure time), then clients can blindly call setSuspended(true)
    * to stop it and then setSuspended(false) to resume-only-if-necessary.
    */
   public void setSuspended(boolean shouldSuspend) {
      if (shouldSuspend && isOn_) {
         // Need to stop now.`
         setLiveMode(false);
         isSuspended_ = true;
      }
      else if (!shouldSuspend && isSuspended_) {
         // Need to resume now.
         setLiveMode(true);
         isSuspended_ = false;
      }
   }

   private void startLiveMode() {
      // First, ensure that any extant grabber thread is dead.
      stopLiveMode();
      shouldStopGrabberThread_ = false;
      if (display_ == null || display_.getIsClosed()) {
         // We need to recreate the display.
         display_ = new TestDisplay(store_);
      }
      grabberThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            grabImages();
         }
      });
      grabberThread_.start();
      try {
         core_.startContinuousSequenceAcquisition(0);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't start live mode sequence acquisition");
      }
   }

   private void stopLiveMode() {
      if (grabberThread_ != null) {
         shouldStopGrabberThread_ = true;
         try {
            grabberThread_.join();
         }
         catch (InterruptedException e) {
            ReportingUtils.logError(e, "Interrupted while waiting for grabber thread to end");
         }
      }
   }

   /**
    * This function is expected to run in its own thread. It continuously
    * polls the core for new images, which then get inserted into the 
    * Datastore (which in turn propagates them to the display).
    */
   private void grabImages() {
      while (!shouldStopGrabberThread_) {
         try {
            TaggedImage tagged = core_.getLastTaggedImage();
            DefaultImage image = new DefaultImage(tagged);
            store_.putImage(image);
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Exception in image grabber thread.");
         }
      }
   }
}
