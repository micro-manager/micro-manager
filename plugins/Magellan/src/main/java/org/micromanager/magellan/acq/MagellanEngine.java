///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
package org.micromanager.magellan.acq;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.micromanager.magellan.channels.ChannelSetting;
import org.micromanager.magellan.json.JSONException;
import org.micromanager.magellan.json.JSONObject;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.Log;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;

/**
 * Desired characteristics: 
 * 1) Lazy evaluation of acquisition events (in case
 * the number is enourmous, so they can be processed as acq is running). Also so
 * that acquisition settings can be changed during acquisition, and the
 * acquisition will adapt. 
 * 2) The ability to monitor the progress of certain
 * parts of acq event (as its acquired, when it reaches disk, if there was an
 * exception along the way) 
 * 3) black box optimization of acquisition events, so sequence acquisitions 
 * can run super fast without caller having to worry about the details
 *
 */
public class MagellanEngine {


   private static final int HARDWARE_ERROR_RETRIES = 6;
   private static final int DELAY_BETWEEN_RETRIES_MS = 5;
   private static CMMCore core_;
   private static MagellanEngine singleton_;
   private AcquisitionEvent lastEvent_ = null;
   private final ExecutorService acqExecutor_;
   private AcqDurationEstimator acqDurationEstiamtor_; //get information about how much time different hardware moves take

   public MagellanEngine(CMMCore core, AcqDurationEstimator acqDurationEstiamtor) {
      singleton_ = this;
      core_ = core;
      acqDurationEstiamtor_ = acqDurationEstiamtor;
      acqExecutor_ = Executors.newSingleThreadExecutor(r -> {
         return new Thread(r, "Magellan Acquisition Engine Thread");
      });
   }

   public static MagellanEngine getInstance() {
      return singleton_;
   }

   /**
    * Modify the first acquisition event in the list to do an entire sequence
    */
   private boolean accumulate(LinkedList<AcquisitionEvent> accumulator, AcquisitionEvent event) {
      //For now, every event is different
      accumulator.clear();
      return true;
      //TODO: if they can be merged, add to accumulator, modify the first one in the list as needed, and return false
   }

   /**
    *
    * Submit a stream of acquisition events for acquisition.
    *
    *
    * @return
    */
   public Stream<Future<Future>> mapToAcquisition(Stream<AcquisitionEvent> eventStream) {

      //Lazily optimize the stream of events for sequence acquisition
      //this might be a bit against the rules of streams because technically it has a side effect...
      LinkedList<AcquisitionEvent> accumulator = new LinkedList<AcquisitionEvent>();
      eventStream = eventStream.filter((AcquisitionEvent t) -> {
         return accumulate(accumulator, t);
      });

      //TODO: some processing to make things optimized when sequenceable hardware is present
      //Lazily map the optimized acquisition events to a stream of futures
      Stream<Future<Future>> futureStream = eventStream.map((AcquisitionEvent event) -> {
         Future<Future> imageAcquiredFuture = acqExecutor_.submit(new Callable() {
            @Override
            public Object call() throws Exception {
               return executeAcquisitionEvent(event);
            }
         });
         return imageAcquiredFuture;
      });

      return futureStream;
   }

   /**
    * Returns a future that returns when the image has been successfully written
    * to disk
    *
    * @param event
    * @return
    * @throws InterruptedException
    */
   private Future executeAcquisitionEvent(final AcquisitionEvent event) throws InterruptedException {
      while (System.currentTimeMillis() < event.miniumumStartTime_) {
         Thread.sleep(1);
      }
      if (event.isAcquisitionFinishedEvent()) {
         //signal to MagellanTaggedImageSink to finish saving thread and mark acquisition as finished
         return event.acquisition_.saveImage(MagellanTaggedImage.createAcquisitionFinishedImage());
      } else {
         updateHardware(event);
         return acquireImage(event);
      }
   }

   private Future acquireImage(final AcquisitionEvent event) throws InterruptedException, HardwareControlException {
      double startTime = System.currentTimeMillis();
      loopHardwareCommandRetries(new Runnable() {
         @Override
         public void run() {
            try {
               Magellan.getCore().snapImage();
            } catch (Exception ex) {
               throw new HardwareControlException(ex.getMessage());
            }
         }
      }, "snapping image");

      //get elapsed time
      final long currentTime = System.currentTimeMillis();
      if (event.acquisition_.getStartTime_ms() == -1) {
         //first image, initialize
         event.acquisition_.setStartTime_ms(currentTime);
      }

      ArrayList<MagellanTaggedImage> images = new ArrayList<MagellanTaggedImage>();
      for (int c = 0; c < core_.getNumberOfCameraChannels(); c++) {
         TaggedImage ti = null;
         try {
            ti = core_.getTaggedImage(c);
         } catch (Exception ex) {
            throw new HardwareControlException(ex.getMessage());
         }
         MagellanTaggedImage img = convertTaggedImage(ti);
         event.acquisition_.addImageMetadata(img.tags, event, event.timeIndex_, c, currentTime - event.acquisition_.getStartTime_ms(),
                 event.acquisition_.channels_.getActiveChannelSetting(event.channelIndex_).exposure_);
         images.add(img);
      }

      //send to storage
      Future imageSavedFuture = null;
      for (int c = 0; c < images.size(); c++) {
         imageSavedFuture = event.acquisition_.saveImage(images.get(c));
      }
      //keep track of how long it takes to acquire an image for acquisition duration estimation
      try {
         acqDurationEstiamtor_.storeImageAcquisitionTime(
                 event.acquisition_.channels_.getActiveChannelSetting(event.channelIndex_).exposure_, System.currentTimeMillis() - startTime);
      } catch (Exception ex) {
         Log.log(ex);
      }
      //Return the last image, which should not allow result to be gotten until all previous ones have been saved
      return imageSavedFuture;
   }

   private void updateHardware(final AcquisitionEvent event) throws InterruptedException, HardwareControlException {
      //compare to last event to see what needs to change
      if (lastEvent_ != null && lastEvent_.acquisition_ != event.acquisition_) {
         lastEvent_ = null; //update all hardware if switching to a new acquisition
      }
      //Get the hardware specific to this acquisition
      final String xyStage = event.acquisition_.getXYStageName();
      final String zStage = event.acquisition_.getZStageName();

      //move Z before XY 
      /////////////////////////////Z stage/////////////////////////////
      if (lastEvent_ == null || event.zPosition_ != lastEvent_.zPosition_ || event.positionIndex_ != lastEvent_.positionIndex_) {
         double startTime = System.currentTimeMillis();
         //wait for it to not be busy (is this even needed?)
         loopHardwareCommandRetries(new Runnable() {
            @Override
            public void run() {
               try {
                  while (core_.deviceBusy(zStage)) {
                     Thread.sleep(1);
                  }
               } catch (Exception ex) {
                  throw new HardwareControlException(ex.getMessage());
               }
            }
         }, "waiting for Z stage to not be busy");
         //move Z stage
         loopHardwareCommandRetries(new Runnable() {
            @Override
            public void run() {
               try {
                  core_.setPosition(zStage, event.zPosition_);
               } catch (Exception ex) {
                  throw new HardwareControlException(ex.getMessage());
               }
            }
         }, "move Z device");
         //wait for it to not be busy (is this even needed?)
         loopHardwareCommandRetries(new Runnable() {
            @Override
            public void run() {
               try {
                  while (core_.deviceBusy(zStage)) {
                     Thread.sleep(1);
                  }
               } catch (Exception ex) {
                  throw new HardwareControlException(ex.getMessage());
               }
            }
         }, "waiting for Z stage to not be busy");
         try {
            acqDurationEstiamtor_.storeZMoveTime(System.currentTimeMillis() - startTime);
         } catch (Exception ex) {
            Log.log(ex);
         }
      }

      /////////////////////////////XY Stage/////////////////////////////
      if (lastEvent_ == null || event.positionIndex_ != lastEvent_.positionIndex_) {
         double startTime = System.currentTimeMillis();
         //wait for it to not be busy (is this even needed??)
         loopHardwareCommandRetries(new Runnable() {
            @Override
            public void run() {
               try {
                  while (core_.deviceBusy(xyStage)) {
                     Thread.sleep(1);
                  }
               } catch (Exception ex) {
                  throw new HardwareControlException(ex.getMessage());
               }
            }
         }, "waiting for XY stage to not be busy");
         //move to new position
         loopHardwareCommandRetries(new Runnable() {
            @Override
            public void run() {
               try {
                  core_.setXYPosition(xyStage, event.xyPosition_.getCenter().x, event.xyPosition_.getCenter().y);
               } catch (Exception ex) {
                  throw new HardwareControlException(ex.getMessage());
               }
            }
         }, "moving XY stage");
         //wait for it to not be busy (is this even needed??)
         loopHardwareCommandRetries(new Runnable() {
            @Override
            public void run() {
               try {
                  while (core_.deviceBusy(xyStage)) {
                     Thread.sleep(1);
                  }
               } catch (Exception ex) {
                  throw new HardwareControlException(ex.getMessage());
               }
            }
         }, "waiting for XY stage to not be busy");
         try {
            acqDurationEstiamtor_.storeXYMoveTime(System.currentTimeMillis() - startTime);
         } catch (Exception ex) {
            Log.log(ex);
         }
      }

      /////////////////////////////Channels/////////////////////////////
      if (lastEvent_ == null || event.channelIndex_ != lastEvent_.channelIndex_
              && event.acquisition_.channels_ != null && event.acquisition_.channels_.getNumActiveChannels() != 0) {
         double startTime = System.currentTimeMillis();
         try {
            final ChannelSetting setting = event.acquisition_.channels_.getActiveChannelSetting(event.channelIndex_);
            if (setting.use_ && setting.config_ != null) {
               loopHardwareCommandRetries(new Runnable() {
                  @Override
                  public void run() {
                     try {
                        //set exposure
                        core_.setExposure(setting.exposure_);
                        //set other channel props
                        core_.setConfig(setting.group_, setting.config_);
                        core_.waitForConfig(setting.group_, setting.config_);
                     } catch (Exception ex) {
                        throw new HardwareControlException(ex.getMessage());
                     }
                  }
               }, "Set channel group");
            }

         } catch (Exception ex) {
            Log.log("Couldn't change channel group");
         }
         try {
            acqDurationEstiamtor_.storeChannelSwitchTime(System.currentTimeMillis() - startTime);
         } catch (Exception ex) {
            Log.log(ex);
         }
      }
      lastEvent_ = event;
   }

   private void loopHardwareCommandRetries(Runnable r, String commandName) throws InterruptedException, HardwareControlException {
      for (int i = 0; i < HARDWARE_ERROR_RETRIES; i++) {
         try {
            r.run();
            return;
         } catch (Exception e) {
            e.printStackTrace();
            Log.log(getCurrentDateAndTime() + ": Problem " + commandName + "\n Retry #" + i + " in " + DELAY_BETWEEN_RETRIES_MS + " ms", true);
            Thread.sleep(DELAY_BETWEEN_RETRIES_MS);
         }
      }
      Log.log(commandName + " unsuccessful", true);
      throw new HardwareControlException();
   }

   private static String getCurrentDateAndTime() {
      DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      Calendar calobj = Calendar.getInstance();
      return df.format(calobj.getTime());
   }

   private static MagellanTaggedImage convertTaggedImage(TaggedImage img) {
      try {
         return new MagellanTaggedImage(img.pix, new JSONObject(img.tags.toString()));
      } catch (JSONException ex) {
         Log.log("Couldn't convert JSON metadata");
         throw new RuntimeException();
      }
   }

}

class HardwareControlException extends RuntimeException {

   public HardwareControlException() {
      super();
   }

   public HardwareControlException(String s) {
      super(s);
   }
}
