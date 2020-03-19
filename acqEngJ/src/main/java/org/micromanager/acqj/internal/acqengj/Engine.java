///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, Berkeley, 2018
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
package org.micromanager.acqj.internal.acqengj;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
import org.micromanager.acqj.api.ImageAcqTuple;
import org.micromanager.acqj.api.TaggedImageProcessor;
import org.micromanager.acqj.api.AcqEngineJ;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.api.ChannelSetting;
import org.micromanager.acqj.api.AcquisitionHook;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DoubleVector;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import org.micromanager.acqj.api.AcqEngMetadata;

public class Engine implements AcqEngineJ {

   private static final int IMAGE_QUEUE_SIZE = 10;
   private static final int HARDWARE_ERROR_RETRIES = 6;
   private static final int DELAY_BETWEEN_RETRIES_MS = 5;
   private static CMMCore core_;
   private static Engine singleton_;
   private AcquisitionEvent lastEvent_ = null;
   private final ExecutorService acqExecutor_;
   private final ThreadPoolExecutor savingAndProcessingExecutor_;
//   private AcqDurationEstimator acqDurationEstiamtor_; //get information about how much time different hardware moves take
   private LinkedList<AcquisitionEvent> eventQueue_ = new LinkedList<AcquisitionEvent>();
   private ExecutorService eventGeneratorExecutor_;
   private volatile Future savingProcessingFuture_;
   private LinkedBlockingDeque<ImageAcqTuple> firstDequeue_
           = new LinkedBlockingDeque<ImageAcqTuple>(IMAGE_QUEUE_SIZE);
   private ConcurrentHashMap<TaggedImageProcessor, LinkedBlockingDeque<ImageAcqTuple>> processorOutputQueues_ = new ConcurrentHashMap<TaggedImageProcessor, LinkedBlockingDeque<ImageAcqTuple>>();
   private CopyOnWriteArrayList<TaggedImageProcessor> imageProcessors_ = new CopyOnWriteArrayList<TaggedImageProcessor>();
   private CopyOnWriteArrayList<AcquisitionHook> beforeHardwareHooks_ = new CopyOnWriteArrayList<AcquisitionHook>();
   private CopyOnWriteArrayList<AcquisitionHook> afterHardwareHooks_ = new CopyOnWriteArrayList<AcquisitionHook>();
   private CopyOnWriteArrayList<AcquisitionHook> afterSaveHooks_ = new CopyOnWriteArrayList<AcquisitionHook>();

   public Engine(CMMCore core) {
      singleton_ = this;
      core_ = core;
//      acqDurationEstiamtor_ = acqDurationEstiamtor;
      acqExecutor_ = Executors.newSingleThreadExecutor(r -> {
         return new Thread(r, "Acquisition Engine Thread");
      });
      savingAndProcessingExecutor_ = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
              (Runnable r) -> new Thread(r, "AcqEngine image processing and saving thread"));
      eventGeneratorExecutor_ = Executors.newSingleThreadExecutor((Runnable r) -> new Thread(r, "Acq Eng event generator"));
      restartSavingAndProcessing();
   }

   public static CMMCore getCore() {
      return core_;
   }

   public static Engine getInstance() {
      return singleton_;
   }

   private void restartSavingAndProcessing() {
      savingProcessingFuture_ = savingAndProcessingExecutor_.submit(() -> {
         while (true) {
            try {
               if (imageProcessors_.isEmpty()) {
                  ImageAcqTuple imgAcq = firstDequeue_.takeFirst();
                  TaggedImage img = imgAcq.img_;
                  AcquisitionBase acq = imgAcq.acq_;
//                  if (!acq.saveToDisk()) {
//                     throw new RuntimeException("Must implement an image processor if not saving to disk");
//                  }
                  acq.saveImage(img);                 
                  for (AcquisitionHook h : afterSaveHooks_) {
                     h.run(acq, img);
                  }
               } else {
                  LinkedBlockingDeque<ImageAcqTuple> dequeue = processorOutputQueues_.get(imageProcessors_.get(imageProcessors_.size() - 1));
                  ImageAcqTuple imgAcq = dequeue.takeFirst();
                  TaggedImage img = imgAcq.img_;
                  AcquisitionBase acq = imgAcq.acq_;
//                  if (!acq.saveToDisk()) {
//                     throw new RuntimeException("Terminal image processor shouldn't send image to otputqueue if saving to "
//                             + "disk not desired");
//                  }
                  acq.saveImage(img);
                  for (AcquisitionHook h : afterSaveHooks_) {
                     h.run(acq, img);
                  }
               }

            } catch (Exception ex) {
               System.err.println(ex);
               ex.printStackTrace();
            } finally {
               restartSavingAndProcessing();
            }
         }
      });
   }

   public void addAcquisitionHook(int type, AcquisitionHook hook) {
      if (type == BEFORE_HARDWARE_HOOK) {
         beforeHardwareHooks_.add(hook);
      } else if (type == AFTER_HARDWARE_HOOK) {
         afterHardwareHooks_.add(hook);
      } else if (type == AFTER_SAVE_HOOK) {
         afterSaveHooks_.add(hook);
      } else {
         throw new RuntimeException("Unknown acquisition hook type");
      }
   }

   public void clearAcquisitionHooks() {
      beforeHardwareHooks_.clear();
      afterHardwareHooks_.clear();
      afterSaveHooks_.clear();
   }

   public synchronized void addImageProcessor(TaggedImageProcessor p) {
      imageProcessors_.add(p);
      processorOutputQueues_.put(p, new LinkedBlockingDeque<ImageAcqTuple>(IMAGE_QUEUE_SIZE));

      if (imageProcessors_.size() == 1) {
         p.setDequeues(firstDequeue_, processorOutputQueues_.get(p));
      } else {
         p.setDequeues(processorOutputQueues_.get(imageProcessors_.size() - 2),
                 processorOutputQueues_.get(imageProcessors_.size() - 1));
      }
   }

   public void clearImageProcessors() {
      imageProcessors_.clear();
      processorOutputQueues_.clear();
   }

   public Future<Future> finishAcquisition(AcquisitionBase acq) {
      return eventGeneratorExecutor_.submit(() -> {
         Future f = acqExecutor_.submit(() -> {
            try {
               eventQueue_.clear();
               executeAcquisitionEvent(AcquisitionEvent.createAcquisitionFinishedEvent(acq));
               while (!acq.isComplete()) {
                  Thread.sleep(1);
               }
            } catch (InterruptedException ex) {
               throw new RuntimeException(ex);
            }
         });
         return f;
      });
   }

   /**
    * Submit a stream of events which will get lazily processed and combined
    * into sequence events as needed. Block until all events executed
    *
    * @param eventIterator Iterator of acquisition events instructing what to
    * acquire
    * @param acq the acquisition
    * @return a Future that can be gotten when the event iteration is finished,
    */
   public Future submitEventIterator(Iterator<AcquisitionEvent> eventIterator, AcquisitionBase acq) {
      return eventGeneratorExecutor_.submit(() -> {

         while (eventIterator.hasNext()) {
            AcquisitionEvent event = eventIterator.next();

            //Wait here is acquisition is paused
            while (event.acquisition_.isPaused()) {
               try {
                  Thread.sleep(5);
               } catch (InterruptedException ex) {
                  throw new RuntimeException(ex);

               }
            }
            try {
               Future imageAcquiredFuture = processAcquistionEvent(event);
               imageAcquiredFuture.get();
            } catch (InterruptedException ex) {
               //cancelled
               return;
            } catch (ExecutionException ex) {
               //some problem with acuisition, abort and propagate exception
               acq.abort();
               throw new RuntimeException(ex);
            }
         }
         try {
            Future lastImageFuture = processAcquistionEvent(AcquisitionEvent.createAcquisitionSequenceEndEvent(acq));
            lastImageFuture.get();
         } catch (InterruptedException ex) {
            //cancelled
            return;
         } catch (ExecutionException ex) {
            //some problem with acuisition, propagate exception
            ex.printStackTrace();
            throw new RuntimeException(ex);
         }

      });
   }

   /**
    * Coalesce acquisition event with others in sequence in applicable,
    * otherwise dispatch it to executor, get future for image/sequence fully
    * acquired and dispatched to subsequent processing/saving
    *
    * @return
    */
   private Future processAcquistionEvent(AcquisitionEvent event) throws ExecutionException {
      Future imageAcquiredFuture = acqExecutor_.submit(() -> {
         try {
            if (eventQueue_.isEmpty() && !event.isAcquisitionSequenceEndEvent()) {
               eventQueue_.add(event);
            } else if (isSequencable(eventQueue_.getLast(), event, eventQueue_.size() + 1)) {
               eventQueue_.add(event); //keep building up events to sequence
            } else {
               // merge the sequence of events to one and send it out
               AcquisitionEvent sequenceEvent = mergeSequenceEvent(eventQueue_);
               eventQueue_.clear();
               //Add in the start of the new sequence
               if (!event.isAcquisitionSequenceEndEvent()) {
                  eventQueue_.add(event);
               }
               executeAcquisitionEvent(sequenceEvent);
            }
         } catch (InterruptedException e) {
            if (core_.isSequenceRunning()) {
               try {
                  core_.stopSequenceAcquisition();
               } catch (Exception ex) {
                  throw new RuntimeException(ex);

               }
            }
            throw new RuntimeException("Acquisition canceled");
         }
         return null;
      });
      return imageAcquiredFuture;
   }

   /**
    * If acq finishing, return a Future that can be gotten when whatever sink it
    * goes to is done. Otherwise return null, since individual images can
    * undergo abitrary duplication/deleting dependending on image processors
    *
    * @param event
    * @return
    * @throws InterruptedException
    */
   private void executeAcquisitionEvent(final AcquisitionEvent event) throws InterruptedException {
      while (System.currentTimeMillis() < event.getMinimumStartTime()) {
         try {
            Thread.sleep(1);
         } catch (InterruptedException e) {
            //Abort while waiting for next time point
            return;
         }
      }
      if (event.isAcquisitionFinishedEvent()) {
         //signal to finish saving thread and mark acquisition as finished
         firstDequeue_.putLast(new ImageAcqTuple(new TaggedImage(null, null), event.acquisition_));
      } else {
         for (AcquisitionHook h : beforeHardwareHooks_) {
            h.run(event);
         }
         updateHardware(event);
         for (AcquisitionHook h : afterHardwareHooks_) {
            h.run(event);
         }
         acquireImages(event);
         //pause here while hardware is doing stuff
         while (core_.isSequenceRunning()) {
            Thread.sleep(2);
         }
         try {
            core_.stopSequenceAcquisition();
         } catch (Exception ex) {
            throw new RuntimeException("Couldn't stop sequence acquisition");
         }
      }
      return;
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
   private void acquireImages(final AcquisitionEvent event) throws InterruptedException, HardwareControlException {

      double startTime = System.currentTimeMillis();
      loopHardwareCommandRetries(new Runnable() {
         @Override
         public void run() {
            try {
               core_.startSequenceAcquisition(1, 0, true);
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

      for (int i = 0; i < (event.getSequence() == null ? 1 : event.getSequence().size()); i++) {
         double exposure;
         try {
            exposure = event.acquisition_.getChannels() == null || event.acquisition_.getChannels().getNumChannels() == 0
                    ? core_.getExposure() : event.acquisition_.getChannels().getChannelSetting(event.getChannelName()).exposure_;
         } catch (Exception ex) {
            throw new RuntimeException("Couldnt get exposure form core");
         }
         for (int camIndex = 0; camIndex < core_.getNumberOfCameraChannels(); camIndex++) {
            TaggedImage ti = null;
            while (ti == null) {
               try {
                  ti = core_.popNextTaggedImage(camIndex);
               } catch (Exception ex) {
               }
            }

            AcqEngMetadata.addImageMetadata(ti.tags, event, camIndex, 
                    currentTime - event.acquisition_.getStartTime_ms(), exposure);
            event.acquisition_.addToImageMetadata(ti.tags);
            firstDequeue_.putLast(new ImageAcqTuple(ti, event.acquisition_));
//            System.out.println("dequeue size: " + firstDequeue_.size());
         }

         //keep track of how long it takes to acquire an image for acquisition duration estimation
//         try {
//            acqDurationEstiamtor_.storeImageAcquisitionTime(
//                    exposure, System.currentTimeMillis() - startTime);
//         } catch (Exception ex) {
//            throw new RuntimeException(ex);
//         }
      }
   }

   private void updateHardware(final AcquisitionEvent event) throws InterruptedException, HardwareControlException {
      //Get the hardware specific to this acquisition
      final String xyStage = event.acquisition_.getXYStageName();
      final String zStage = event.acquisition_.getZStageName();
      //prepare sequences if applicable
      if (event.getSequence() != null) {
         try {
            DoubleVector zSequence = new DoubleVector();
            DoubleVector xSequence = new DoubleVector();
            DoubleVector ySequence = new DoubleVector();
            DoubleVector exposureSequence_ms = new DoubleVector();
            String group = event.getSequence().get(0).acquisition_.getChannels().getChannelSetting(event.getChannelName()).group_;
            Configuration config = core_.getConfigData(group, event.getSequence().get(0).acquisition_.getChannels().getConfigName(0));
            LinkedList<StrVector> propSequences = new LinkedList<StrVector>();
            for (AcquisitionEvent e : event.getSequence()) {
               zSequence.add(event.getZPosition());
               xSequence.add(event.getXY().getCenter().x);
               ySequence.add(event.getXY().getCenter().y);
               exposureSequence_ms.add(event.acquisition_.getChannels().getChannelSetting(event.getChannelName()).exposure_);
               //et sequences for all channel properties
               for (int i = 0; i < config.size(); i++) {
                  PropertySetting ps = config.getSetting(i);
                  String deviceName = ps.getDeviceLabel();
                  String propName = ps.getPropertyName();
                  if (e == event.getSequence().get(0)) { //first property
                     propSequences.add(new StrVector());
                  }
                  Configuration channelPresetConfig = core_.getConfigData(group,
                          event.acquisition_.getChannels().getChannelSetting(event.getChannelName()).config_);
                  String propValue = channelPresetConfig.getSetting(deviceName, propName).getPropertyValue();
                  propSequences.get(i).add(propValue);
               }
            }
            //Now have built up all the sequences, apply them
            if (event.isExposureSequenced()) {
               core_.loadExposureSequence(core_.getCameraDevice(), exposureSequence_ms);
            }
            if (event.isXYSequenced()) {
               core_.loadXYStageSequence(xyStage, xSequence, ySequence);
            }
            if (event.isZSequenced()) {
               core_.loadStageSequence(zStage, zSequence);
            }
            if (event.isChannelSequenced()) {
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
               if (event.isZSequenced()) {
                  core_.startStageSequence(zStage);
               } else if (lastEvent_ == null || event.getZPosition() != 
                       lastEvent_.getZPosition() || !event.getXY().equals(lastEvent_.getXY())) {
                  //wait for it to not be busy (is this even needed?)   
                  while (core_.deviceBusy(zStage)) {
                     Thread.sleep(1);
                  }
                  //Move Z
                  core_.setPosition(zStage, event.getZPosition());
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
//      acqDurationEstiamtor_.storeZMoveTime(System.currentTimeMillis() - startTime);

      /////////////////////////////XY Stage////////////////////////////////////////////////////
      if (event.getXY() != null) {
         startTime = System.currentTimeMillis();
         loopHardwareCommandRetries(new Runnable() {
            @Override
            public void run() {
               try {
                  if (event.isXYSequenced()) {
                     core_.startXYStageSequence(xyStage);
                  } else if (lastEvent_ == null || !event.getXY().equals(lastEvent_.getXY())) {
                     //wait for it to not be busy (is this even needed?)   
                     while (core_.deviceBusy(xyStage)) {
                        Thread.sleep(1);
                     }
                     //Move XY
                     core_.setXYPosition(xyStage, event.getXY().getCenter().x, event.getXY().getCenter().y);
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
//         acqDurationEstiamtor_.storeXYMoveTime(System.currentTimeMillis() - startTime);
      }
      /////////////////////////////Channels//////////////////////////////////////////////////
      startTime = System.currentTimeMillis();
      loopHardwareCommandRetries(new Runnable() {
         @Override
         public void run() {
            try {
               if (event.isChannelSequenced()) {
                  //Channels
                  String group = event.acquisition_.getChannels().getChannelSetting(event.getChannelName()).group_;
                  Configuration config = core_.getConfigData(group,
                          event.acquisition_.getChannels().getChannelSetting(event.getChannelName()).config_);
                  for (int i = 0; i < config.size(); i++) {
                     PropertySetting ps = config.getSetting(i);
                     String deviceName = ps.getDeviceLabel();
                     String propName = ps.getPropertyName();
                     core_.startPropertySequence(deviceName, propName);
                  }
               } else if (event.acquisition_.getChannels() != null && event.acquisition_.getChannels().getNumChannels() != 0 && (lastEvent_ == null
                       || event.getChannelName() != null && lastEvent_.getChannelName() != null
                       && !event.getChannelName().equals(lastEvent_.getChannelName()) && event.acquisition_.getChannels() != null)) {
                  final ChannelSetting setting = event.acquisition_.getChannels().getChannelSetting(event.getChannelName());
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
//      acqDurationEstiamtor_.storeChannelSwitchTime(System.currentTimeMillis() - startTime);

      /////////////////////////////Camera exposure//////////////////////////////////////////////
      startTime = System.currentTimeMillis();
      loopHardwareCommandRetries(new Runnable() {
         @Override
         public void run() {
            try {
               if (event.isExposureSequenced()) {
                  core_.startExposureSequence(core_.getCameraDevice());
               } else if (event.acquisition_.getChannels() != null && event.acquisition_.getChannels().getNumChannels() != 0
                       && (lastEvent_ == null || lastEvent_.acquisition_ != event.acquisition_
                       || ((lastEvent_.acquisition_.getChannels().getChannelSetting(lastEvent_.getChannelName()).exposure_
                       != event.acquisition_.getChannels().getChannelSetting(event.getChannelName()).exposure_)))) {
                  core_.setExposure(event.acquisition_.getChannels().getChannelSetting(event.getChannelName()).exposure_);
               }
            } catch (Exception ex) {
               throw new HardwareControlException(ex.getMessage());
            }

         }
      }, "Changing exposure");

      //keep track of last event to know what state the hardware was in without having to query it
      lastEvent_ = event.getSequence() == null ? event : event.getSequence().get(event.getSequence().size() - 1);

   }

   private void loopHardwareCommandRetries(Runnable r, String commandName) throws InterruptedException, HardwareControlException {
      for (int i = 0; i < HARDWARE_ERROR_RETRIES; i++) {
         try {
            r.run();
            return;
         } catch (Exception e) {
            e.printStackTrace();

            System.out.println(getCurrentDateAndTime() + ": Problem "
                    + commandName + "\n Retry #" + i + " in " + DELAY_BETWEEN_RETRIES_MS + " ms");
            Thread.sleep(DELAY_BETWEEN_RETRIES_MS);
         }
      }
      throw new HardwareControlException(commandName + " unsuccessful");
   }

   private static String getCurrentDateAndTime() {
      DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      Calendar calobj = Calendar.getInstance();
      return df.format(calobj.getTime());

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
         if (e1.getChannelName() != null && e2.getChannelName() != null && !e1.getChannelName().equals(e2.getChannelName())) {
            //check all properties in the channel
            String group = e1.acquisition_.getChannels().getChannelGroup();

            Configuration config1 = core_.getConfigData(group,
                    e1.acquisition_.getChannels().getChannelSetting(e1.getChannelName()).config_);
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
         //TODO arbitrary additional properties in acq event
         
         
         //z stage
         if (e1.getZPosition() != e2.getZPosition()) {
            if (!core_.isStageSequenceable(core_.getFocusDevice())) {
               return false;
            }
            if (core_.getStageSequenceMaxLength(core_.getFocusDevice()) > newSeqLength) {
               return false;
            }
         }
         //xy stage
         if (!e1.getXY().equals(e2.getXY()) ) {
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
         if (e1.getTIndex() != e2.getTIndex()) {
            if (e1.getMinimumStartTime() != e2.getMinimumStartTime()) {
               return false;
            }
         }
         return true;
      } catch (Exception ex) {
         throw new RuntimeException(ex);
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
}

class HardwareControlException extends RuntimeException {

   public HardwareControlException() {
      super();
   }

   public HardwareControlException(String s) {
      super(s);
   }
}
