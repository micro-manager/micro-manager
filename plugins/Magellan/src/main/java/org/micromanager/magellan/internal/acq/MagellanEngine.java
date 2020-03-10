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
package org.micromanager.magellan.internal.acq;

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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.micromanager.magellan.internal.channels.ChannelSetting;
import org.micromanager.magellan.internal.misc.Log;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DoubleVector;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;

/**
 * Desired characteristics: 1) Lazy evaluation of acquisition events (in case
 * the number is enourmous, so they can be processed as acq is running). Also so
 * that acquisition settings can be changed during acquisition, and the
 * acquisition will adapt. 2) The ability to monitor the progress of certain
 * parts of acq event (as its acquired, when it reaches disk, if there was an
 * exception along the way) 3) black box optimization of acquisition events, so
 * sequence acquisitions can run super fast without caller having to worry about
 * the details
 *
 */
public class MagellanEngine {

   private static final int MAX_QUEUED_IMAGES_FOR_WRITE = 20;
   private static final int HARDWARE_ERROR_RETRIES = 6;
   private static final int DELAY_BETWEEN_RETRIES_MS = 5;
   private static CMMCore core_;
   private static MagellanEngine singleton_;
   private AcquisitionEvent lastEvent_ = null;
   private final ExecutorService acqExecutor_;
   private final ThreadPoolExecutor savingExecutor_;
   private AcqDurationEstimator acqDurationEstiamtor_; //get information about how much time different hardware moves take
   private LinkedList<AcquisitionEvent> eventQueue_ = new LinkedList<AcquisitionEvent>();
   private ExecutorService eventGeneratorExecutor_;

   public MagellanEngine(CMMCore core, AcqDurationEstimator acqDurationEstiamtor) {
      singleton_ = this;
      core_ = core;
      acqDurationEstiamtor_ = acqDurationEstiamtor;
      acqExecutor_ = Executors.newSingleThreadExecutor(r -> {
         return new Thread(r, "Magellan Acquisition Engine Thread");
      });
      savingExecutor_ = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
              (Runnable r) -> new Thread(r, "Magellan engine image saving thread"));
      eventGeneratorExecutor_ = Executors.newSingleThreadExecutor((Runnable r) -> new Thread(r, "Magellan engine vent generator"));
      //subclasses are resonsible for submitting event streams to begin acquisiton
   }

   public static MagellanEngine getInstance() {
      return singleton_;
   }

   void runOnSavingThread(Runnable r) {
      savingExecutor_.submit(r);
   }

   private int getNumImagesWaitingToSave() {
      return savingExecutor_.getQueue().size();
   }

   /**
    * Check if two events can be sequenced into one
    *
    * @param e1
    * @param e2
    * @return
    */
   private static boolean isSequencable(AcquisitionEvent e1, AcquisitionEvent e2, int newSeqLength) {
      try {
         if (e2.isAcquisitionSequenceEndEvent() || e2.isAcquisitionFinishedEvent()) {
            return false;
         }

         //check all properties in channel
         if (e1.channelName_ != null && e2.channelName_ != null && !e1.channelName_.equals(e2.channelName_)) {
            //check all properties in the channel
            String group = e1.acquisition_.channels_.getChannelGroup();

            Configuration config1 = core_.getConfigData(group,
                    e1.acquisition_.channels_.getChannelSetting(e1.channelName_).config_);
            for (int i = 0; i < config1.size(); i++) {
               PropertySetting ps = config1.getSetting(i);
               String deviceName = ps.getDeviceLabel();
               String propName = ps.getPropertyName();
               if (!core_.isPropertySequenceable(deviceName, propName)) {
                  return false;
               }
               if (core_.getPropertySequenceMaxLength(deviceName, propName) < newSeqLength) {
                  return false;
               }
            }

         }
         //check all properties in z stage
         //TODO: linear sequence?
         if (e1.zPosition_ != e2.zPosition_) {
            if (!core_.isStageSequenceable(core_.getFocusDevice())) {
               return false;
            }
            if (core_.getStageSequenceMaxLength(core_.getFocusDevice()) > newSeqLength) {
               return false;
            }
         }
         //xy stage
         if (e1.xyPosition_ != e2.xyPosition_) {
            if (!core_.isXYStageSequenceable(core_.getXYStageDevice())) {
               return false;
            }
            if (core_.getXYStageSequenceMaxLength(core_.getXYStageDevice()) > newSeqLength) {
               return false;
            }
         }
         //camera
         if (!core_.isExposureSequenceable(core_.getCameraDevice())) {
            return false;
         }
         if (core_.getExposureSequenceMaxLength(core_.getCameraDevice()) > newSeqLength) {
            return false;
         }
         //timelapse
         if (e1.timeIndex_ != e2.timeIndex_) {
            if (e1.miniumumStartTime_ != e2.miniumumStartTime_) {
               return false;
            }
         }
         return true;
      } catch (Exception ex) {
         Log.log("Problem getting channel config for sequencing");
         Log.log(ex);
      } finally {
         return false;
      }
   }

   private AcquisitionEvent mergeSequenceEvent(List<AcquisitionEvent> eventList) {
      if (eventList.size() == 1) {
         return eventList.get(0);
      }
      return new AcquisitionEvent(eventList);
   }

   public void finishAcquisition(Acquisition acq) {
      try {
         Future<Future> f = acqExecutor_.submit(new Callable<Future>() {
            @Override
            public Future call() throws InterruptedException {
               return executeAcquisitionEvent(AcquisitionEvent.createAcquisitionFinishedEvent(acq));
            }
         });
         Future savingDone = f.get();
         if (savingDone != null) { //Could be null if acq aborted
            savingDone.get();
         }
         savingDone.get();
      } catch (NullPointerException e) {
         Log.log(e);
         throw new RuntimeException();
      } catch (ExecutionException | InterruptedException ex) {
         ex.printStackTrace();
         Log.log("Exception while waiting for acquisition finish");
      }
   }

   public Future submitToEventExecutor(Runnable r) {
      return eventGeneratorExecutor_.submit(r);
   }

   /**
    * Submit a stream of events which will get lazily processed and combined
    * into sequence events as needed
    *
    * @param eventStream
    * @return
    */
   public Future submitEventStream(Stream<AcquisitionEvent> eventStream, Acquisition acq) {
      return eventGeneratorExecutor_.submit(() -> {
         Stream<AcquisitionEvent> streamWithEnd = Stream.concat(eventStream, Stream.of(AcquisitionEvent.createAcquisitionSequenceEndEvent(acq)));

         //Wait around while pause is engaged
         Stream<AcquisitionEvent> streamWithPause = streamWithEnd.map(new Function<AcquisitionEvent, AcquisitionEvent>() {
            @Override
            public AcquisitionEvent apply(AcquisitionEvent t) {
               while (t.acquisition_.isPaused()) {
                  try {
                     Thread.sleep(5);
                  } catch (InterruptedException ex) {
                     Log.log(ex);
                     throw new RuntimeException(ex);
                  }
               }
               return t;
            }
         });

         //Make sure events can't be submitted to the engine way faster than images can be written to disk
         Stream<AcquisitionEvent> rateLimitedStream = streamWithPause.map(new Function<AcquisitionEvent, AcquisitionEvent>() {
            @Override
            public AcquisitionEvent apply(AcquisitionEvent t) {
               while (MagellanEngine.getInstance().getNumImagesWaitingToSave() > MAX_QUEUED_IMAGES_FOR_WRITE) {
                  try {
                     Thread.sleep(2);
                  } catch (InterruptedException ex) {
                     ex.printStackTrace();
                     throw new RuntimeException(ex); //must have beeen aborted
                  }
               }
               return t;
            }
         });

         //Lazily map the optimized acquisition events to a stream of futures
         Stream<Future<Future>> eventProcessedFutureStream = rateLimitedStream.map((AcquisitionEvent event) -> {
            Future<Future> imageAcquiredFuture = acqExecutor_.submit(new Callable() {
               @Override
               public Future<Future> call() {
                  try {
                     if (eventQueue_.isEmpty() && !event.isAcquisitionSequenceEndEvent()) {
                        eventQueue_.add(event);
                        return null;
                     } else if (isSequencable(eventQueue_.getLast(), event, eventQueue_.size() + 1)) {
                        eventQueue_.add(event); //keep building up events to sequence                     
                        return null;
                     } else {
                        // merge the sequence of events to one and send it out
                        AcquisitionEvent sequenceEvent = mergeSequenceEvent(eventQueue_);
                        eventQueue_.clear();
                        //Add in the start of the new sequence
                        if (!event.isAcquisitionSequenceEndEvent()) {
                           eventQueue_.add(event);
                        }
                        Future saveFuture = executeAcquisitionEvent(sequenceEvent);
                        if (sequenceEvent.afterImageSavedHook_ != null) {
                           try {
                              saveFuture.get();
                           } catch (InterruptedException | ExecutionException ex) {
                              throw new RuntimeException(ex);
                           }
                           sequenceEvent.afterImageSavedHook_.run(sequenceEvent);
                           return null;
                        }
                        return saveFuture;
                     }
                  } catch (InterruptedException e) {
                     if (core_.isSequenceRunning()) {
                        try {
                           core_.stopSequenceAcquisition();
                        } catch (Exception ex) {
                           Log.log("exception while tryign to stop sequence acquistion");
                        }
                     }

                     throw new RuntimeException("Acquisition canceled");
                  }
               }
            });
            return imageAcquiredFuture;
         });

         //lazily iterate through them
         Stream<Future> imageSavedFutureStream = eventProcessedFutureStream.map((Future<Future> t) -> {
            try {
               if (t != null) {
                  return t.get();
               }
               return null;
            } catch (InterruptedException e) {
               //Acquisition aborted
               boolean sucess = t.cancel(true); //interrupt current event, which is especially important if it is an acquisition waiting event
               eventQueue_.clear();
               //this exception is needed to make everything stop
               throw new RuntimeException("Acquisition cancelled");
            } catch (ExecutionException ex) {
               t.cancel(true); //interrupt current event. Neccessary?
               Log.log(ex);
               ex.printStackTrace();
               throw new RuntimeException(ex);
            }
         });
         //Iterate through and make sure images get saved 
         imageSavedFutureStream.forEach((Future t) -> {
            try {
               if (t != null) { //null if it was combined as part of a sequence
                  t.get();
               }
            } catch (InterruptedException e) {
               //forget any acquisition events on abort
               eventQueue_.clear();
               throw new RuntimeException("Acquistion aborted");
            } catch (ExecutionException ex) {
               ex.printStackTrace();
               Log.log(ex);
               throw new RuntimeException(ex);
            }
         });

      });
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
         try {
            Thread.sleep(1);
         } catch (InterruptedException e) {
            //Abort while waiting for next time point
            return null;
         }
      }
      if (event.isAcquisitionFinishedEvent()) {
         //signal to MagellanTaggedImageSink to finish saving thread and mark acquisition as finished
         return savingExecutor_.submit(new Runnable() {
            @Override
            public void run() {
               event.acquisition_.saveImage(new TaggedImage(null, null));
            }
         });
      } else {
         if (event.beforeHardwareHook_ != null) {
            event.beforeHardwareHook_.run(event);
         }
         updateHardware(event);
         if (event.afterHardwareHook_ != null) {
            event.afterHardwareHook_.run(event);
         }
         Future lastImageSavedFuture = acquireImages(event);
         //pause here while hardware is doing stuff
         while (core_.isSequenceRunning()) {
            Thread.sleep(2);
         }
         return lastImageSavedFuture;
      }
   }

   /**
    * Acquire 1 or more images in a sequence and signal for them to be saved.
    *
    * @param event
    * @return a Future that can be gotten when the last image in the sequence is
    * saved
    * @throws InterruptedException
    * @throws HardwareControlException
    */
   private Future acquireImages(final AcquisitionEvent event) throws InterruptedException, HardwareControlException {

      double startTime = System.currentTimeMillis();
      loopHardwareCommandRetries(new Runnable() {
         @Override
         public void run() {
            try {
//               Magellan.getCore().snapImage();

               core_.startSequenceAcquisition(1, 0, false);
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

      // Submit a task to asynchronously get all images from the core and save them
      return savingExecutor_.submit(() -> {
         ArrayList<TaggedImage> images = new ArrayList<TaggedImage>();
         for (int i = 0; i < (event.sequence_ == null ? 1 : event.sequence_.size()); i++) {
            double exposure;
            try {
               exposure = event.acquisition_.channels_ == null || event.acquisition_.channels_.getNumChannels() == 0
                       ? core_.getExposure() : event.acquisition_.channels_.getChannelSetting(event.channelName_).exposure_;
            } catch (Exception ex) {
               throw new RuntimeException("Couldnt get exposure form core");
            }
            for (int c = 0; c < core_.getNumberOfCameraChannels(); c++) {
               TaggedImage ti = null;
               while (ti == null) {
                  try {
                     ti = core_.popNextTaggedImage(c);
                  } catch (Exception ex) {
                     try {
                        Thread.sleep(1);
                     } catch (InterruptedException ex1) {
                        Log.log("Unexpected interrupt on image savigng thread");
                     }
                  }
               }

               event.acquisition_.addImageMetadata(ti.tags, event, event.timeIndex_, c, currentTime - event.acquisition_.getStartTime_ms(),
                       exposure, core_.getNumberOfCameraChannels() > 1);
               images.add(ti);
            }

            //send to storage
            for (int c = 0; c < images.size(); c++) {
               event.acquisition_.saveImage(images.get(c));
            }
            //keep track of how long it takes to acquire an image for acquisition duration estimation
            try {

               acqDurationEstiamtor_.storeImageAcquisitionTime(
                       exposure, System.currentTimeMillis() - startTime);
            } catch (Exception ex) {
               Log.log(ex);
            }
         }
      });
   }

   private void updateHardware(final AcquisitionEvent event) throws InterruptedException, HardwareControlException {
      //Get the hardware specific to this acquisition
      final String xyStage = event.acquisition_.getXYStageName();
      final String zStage = event.acquisition_.getZStageName();
      //prepare sequences if applicable
      if (event.sequence_ != null) {
         try {
            DoubleVector zSequence = new DoubleVector();
            DoubleVector xSequence = new DoubleVector();
            DoubleVector ySequence = new DoubleVector();
            DoubleVector exposureSequence_ms = new DoubleVector();
            String group = event.sequence_.get(0).acquisition_.channels_.getChannelSetting(event.channelName_).group_;
            Configuration config = core_.getConfigData(group, event.sequence_.get(0).acquisition_.channels_.getConfigName(0));
            LinkedList<StrVector> propSequences = new LinkedList<StrVector>();
            for (AcquisitionEvent e : event.sequence_) {
               zSequence.add(event.zPosition_);
               xSequence.add(event.xyPosition_.getCenter().x);
               ySequence.add(event.xyPosition_.getCenter().y);
               exposureSequence_ms.add(event.acquisition_.channels_.getChannelSetting(event.channelName_).exposure_);
               //et sequences for all channel properties
               for (int i = 0; i < config.size(); i++) {
                  PropertySetting ps = config.getSetting(i);
                  String deviceName = ps.getDeviceLabel();
                  String propName = ps.getPropertyName();
                  if (e == event.sequence_.get(0)) { //first property
                     propSequences.add(new StrVector());
                  }
                  Configuration channelPresetConfig = core_.getConfigData(group,
                          event.acquisition_.channels_.getChannelSetting(event.channelName_).config_);
                  String propValue = channelPresetConfig.getSetting(deviceName, propName).getPropertyValue();
                  propSequences.get(i).add(propValue);
               }
            }
            //Now have built up all the sequences, apply them
            if (event.exposureSequenced_) {
               core_.loadExposureSequence(core_.getCameraDevice(), exposureSequence_ms);
            }
            if (event.xySequenced_) {
               core_.loadXYStageSequence(xyStage, xSequence, ySequence);
            }
            if (event.zSequenced_) {
               core_.loadStageSequence(zStage, zSequence);
            }
            if (event.channelSequenced_) {
               for (int i = 0; i < config.size(); i++) {
                  PropertySetting ps = config.getSetting(i);
                  String deviceName = ps.getDeviceLabel();
                  String propName = ps.getPropertyName();
                  core_.loadPropertySequence(deviceName, propName, propSequences.get(i));
               }
            }
            core_.prepareSequenceAcquisition(core_.getCameraDevice());

         } catch (Exception ex) {
            throw new HardwareControlException(ex.getMessage());
         }
      }

      //compare to last event to see what needs to change
      if (lastEvent_ != null && lastEvent_.acquisition_ != event.acquisition_) {
         lastEvent_ = null; //update all hardware if switching to a new acquisition
      }
      /////////////////////////////Z stage////////////////////////////////////////////
      double startTime = System.currentTimeMillis();
      loopHardwareCommandRetries(new Runnable() {
         @Override
         public void run() {
            try {
               if (event.zSequenced_) {
                  core_.startStageSequence(zStage);
               } else if (lastEvent_ == null || event.zPosition_ != lastEvent_.zPosition_ || event.positionIndex_ != lastEvent_.positionIndex_) {
                  //wait for it to not be busy (is this even needed?)   
                  while (core_.deviceBusy(zStage)) {
                     Thread.sleep(1);
                  }
                  //Move Z
                  core_.setPosition(zStage, event.zPosition_);
                  //wait for move to finish
                  while (core_.deviceBusy(zStage)) {
                     Thread.sleep(1);
                  }
               }
            } catch (Exception ex) {
               throw new HardwareControlException(ex.getMessage());
            }

         }
      }, "Moving Z device");
      acqDurationEstiamtor_.storeZMoveTime(System.currentTimeMillis() - startTime);

      /////////////////////////////XY Stage////////////////////////////////////////////////////
      if (event.xyPosition_ != null) {
         startTime = System.currentTimeMillis();
         loopHardwareCommandRetries(new Runnable() {
            @Override
            public void run() {
               try {
                  if (event.xySequenced_) {
                     core_.startXYStageSequence(xyStage);
                  } else if (lastEvent_ == null || event.positionIndex_ != lastEvent_.positionIndex_) {
                     //wait for it to not be busy (is this even needed?)   
                     while (core_.deviceBusy(xyStage)) {
                        Thread.sleep(1);
                     }
                     //Move XY
                     core_.setXYPosition(xyStage, event.xyPosition_.getCenter().x, event.xyPosition_.getCenter().y);
                     //wait for move to finish
                     while (core_.deviceBusy(xyStage)) {
                        Thread.sleep(1);
                     }
                  }
               } catch (Exception ex) {
                  ex.printStackTrace();
                  throw new HardwareControlException(ex.getMessage());
               }

            }
         }, "Moving XY stage");
         acqDurationEstiamtor_.storeXYMoveTime(System.currentTimeMillis() - startTime);
      }
      /////////////////////////////Channels//////////////////////////////////////////////////
      startTime = System.currentTimeMillis();
      loopHardwareCommandRetries(new Runnable() {
         @Override
         public void run() {
            try {
               if (event.channelSequenced_) {
                  //Channels
                  String group = event.acquisition_.channels_.getChannelSetting(event.channelName_).group_;
                  Configuration config = core_.getConfigData(group,
                          event.acquisition_.channels_.getChannelSetting(event.channelName_).config_);
                  for (int i = 0; i < config.size(); i++) {
                     PropertySetting ps = config.getSetting(i);
                     String deviceName = ps.getDeviceLabel();
                     String propName = ps.getPropertyName();
                     core_.startPropertySequence(deviceName, propName);
                  }
               } else if (event.acquisition_.channels_ != null && event.acquisition_.channels_.getNumChannels() != 0 && (lastEvent_ == null
                       || event.channelName_ != null && lastEvent_.channelName_ != null
                       && !event.channelName_.equals(lastEvent_.channelName_) && event.acquisition_.channels_ != null)) {
                  final ChannelSetting setting = event.acquisition_.channels_.getChannelSetting(event.channelName_);
                  if (setting.use_ && setting.config_ != null) {
                     //set exposure
                     core_.setExposure(setting.exposure_);
                     //set other channel props
                     core_.setConfig(setting.group_, setting.config_);
                     core_.waitForConfig(setting.group_, setting.config_);
                  }
               }
            } catch (Exception ex) {
               ex.printStackTrace();
               throw new HardwareControlException(ex.getMessage());
            }

         }
      }, "Changing channels");
      acqDurationEstiamtor_.storeChannelSwitchTime(System.currentTimeMillis() - startTime);

      /////////////////////////////Camera exposure//////////////////////////////////////////////
      startTime = System.currentTimeMillis();
      loopHardwareCommandRetries(new Runnable() {
         @Override
         public void run() {
            try {
               if (event.exposureSequenced_) {
                  core_.startExposureSequence(core_.getCameraDevice());
               } else if (event.acquisition_.channels_ != null && event.acquisition_.channels_.getNumChannels() != 0
                       && (lastEvent_ == null || lastEvent_.acquisition_ != event.acquisition_
                       || ((lastEvent_.acquisition_.channels_.getChannelSetting(lastEvent_.channelName_).exposure_
                       != event.acquisition_.channels_.getChannelSetting(event.channelName_).exposure_)))) {
                  core_.setExposure(event.acquisition_.channels_.getChannelSetting(event.channelName_).exposure_);
               }
            } catch (Exception ex) {
               throw new HardwareControlException(ex.getMessage());
            }

         }
      }, "Changing exposure");

      //keep track of last event to know what state the hardware was in without having to query it
      lastEvent_ = event.sequence_ == null ? event : event.sequence_.get(event.sequence_.size() - 1);

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
}

class HardwareControlException extends RuntimeException {

   public HardwareControlException() {
      super();
   }

   public HardwareControlException(String s) {
      super(s);
   }
}
