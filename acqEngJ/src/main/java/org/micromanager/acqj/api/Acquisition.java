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
package org.micromanager.acqj.api;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acqj.internal.acqengj.Engine;

/**
 * Abstract class that manages a generic acquisition. Subclassed into specific
 * types of acquisition. Minimal set of assumptions that mirror those in the
 * core. For example, assumes one Z stage, one XY stage, one channel group, etc
 */
public class Acquisition implements AcquisitionInterface {

   private static final int IMAGE_QUEUE_SIZE = 10;

   public static final int BEFORE_HARDWARE_HOOK = 0;
   public static final int AFTER_HARDWARE_HOOK = 1;

   protected String xyStage_, zStage_;
   protected boolean zStageHasLimits_ = false;
   protected double zStageLowerLimit_, zStageUpperLimit_;
   protected AcquisitionEvent lastEvent_ = null;
   protected volatile boolean finished_, completed_ = false;
   private JSONObject summaryMetadata_;
   private final String name_, dir_;
   private long startTime_ms_ = -1;
   private volatile boolean paused_ = false;
   private CopyOnWriteArrayList<String> channelNames_ = new CopyOnWriteArrayList<String>();
   protected volatile Future<Future> acqFinishedFuture_;
   protected DataSink dataSink_;
   protected CMMCore core_;
   private CopyOnWriteArrayList<AcquisitionHook> beforeHardwareHooks_ = new CopyOnWriteArrayList<AcquisitionHook>();
   private CopyOnWriteArrayList<AcquisitionHook> afterHardwareHooks_ = new CopyOnWriteArrayList<AcquisitionHook>();
   private CopyOnWriteArrayList<TaggedImageProcessor> imageProcessors_ = new CopyOnWriteArrayList<TaggedImageProcessor>();
   private LinkedBlockingDeque<TaggedImage> firstDequeue_
           = new LinkedBlockingDeque<TaggedImage>(IMAGE_QUEUE_SIZE);
   private ConcurrentHashMap<TaggedImageProcessor, LinkedBlockingDeque<TaggedImage>> processorOutputQueues_
           = new ConcurrentHashMap<TaggedImageProcessor, LinkedBlockingDeque<TaggedImage>>();

   public Acquisition(String dir, String name, DataSink sink) {
      core_ = Engine.getCore();
      name_ = name;
      dir_ = dir;
      dataSink_ = sink;
      initialize();
   }

   public void abort() {
      if (this.isPaused()) {
         this.togglePaused();
      }
      acqFinishedFuture_ = Engine.getInstance().finishAcquisition(this);
   }

   public void addToSummaryMetadata(JSONObject summaryMetadata) {
   }

   ;

   public void addToImageMetadata(JSONObject tags) {
   }

   ;

   public void submitEventIterator(Iterator<AcquisitionEvent> evt) {
      Engine.getInstance().submitEventIterator(evt, this);
   }

   @Override
   public void start() {
      //TODO could avoid startin this here if not going to be using saving  
      ThreadPoolExecutor savingAndProcessingExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
              (Runnable r) -> new Thread(r, "Acquisition image processing and saving thread"));

      savingAndProcessingExecutor.submit(() -> {
         try {
            while (true) {
               boolean storageFinished;
               if (imageProcessors_.isEmpty()) {
                  TaggedImage img = firstDequeue_.takeFirst();
                  storageFinished = saveImage(img);
               } else {
                  LinkedBlockingDeque<TaggedImage> dequeue = processorOutputQueues_.get(
                          imageProcessors_.get(imageProcessors_.size() - 1));
                  TaggedImage img = dequeue.takeFirst();
                  storageFinished = saveImage(img);
               }
               if (storageFinished) {
                  savingAndProcessingExecutor.shutdown();
                  for (TaggedImageProcessor p : imageProcessors_) {
                     p.close();
                  }
                  completed_ = true;
                  return;
               }
            }
         } catch (InterruptedException e) {
            //this should never happen
         } catch (Exception ex) {
            System.err.println(ex);
            ex.printStackTrace();
         }
      });
   }

   @Override
   public void addImageProcessor(TaggedImageProcessor p) {
      imageProcessors_.add(p);
      processorOutputQueues_.put(p, new LinkedBlockingDeque<TaggedImage>(IMAGE_QUEUE_SIZE));

      if (imageProcessors_.size() == 1) {
         p.setDequeues(firstDequeue_, processorOutputQueues_.get(p));
      } else {
         p.setDequeues(processorOutputQueues_.get(imageProcessors_.size() - 2),
                 processorOutputQueues_.get(imageProcessors_.size() - 1));
      }
   }

   @Override
   public void addHook(AcquisitionHook h, int type) {
      if (type == BEFORE_HARDWARE_HOOK) {
         beforeHardwareHooks_.add(h);
      } else if (type == AFTER_HARDWARE_HOOK) {
         afterHardwareHooks_.add(h);
      }
   }

   @Override
   public void close() {
      try {
         //wait for event generation to shut down
         if (acqFinishedFuture_ != null) {
            while (!acqFinishedFuture_.isDone()) {
               try {
                  Thread.sleep(1);
               } catch (InterruptedException ex) {
                  throw new RuntimeException("Interrupted while waiting to cancel");
               }
            }
            //wait for final signal to be sent to saving class..cant do much byond that
            Future executionFuture = acqFinishedFuture_.get();
            while (!executionFuture.isDone()) {
               try {
                  Thread.sleep(1);
               } catch (InterruptedException ex) {
                  throw new RuntimeException("Interrupted while waiting to cancel");
               }
            }
            executionFuture.get();
         }
      } catch (InterruptedException ex) {
         throw new RuntimeException(ex);
      } catch (ExecutionException ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * 1) Get the names or core devices to be used in acquistion 2) Create
    * Summary metadata 3) Initialize data sink
    */
   private void initialize() {
      xyStage_ = core_.getXYStageDevice();
      zStage_ = core_.getFocusDevice();
      //"postion" is not generic name..and as of right now there is now way of getting generic z positions
      //from a z deviec in MM
      String positionName = "Position";
      try {
         if (core_.hasProperty(zStage_, positionName)) {
            zStageHasLimits_ = core_.hasPropertyLimits(zStage_, positionName);
            if (zStageHasLimits_) {
               zStageLowerLimit_ = core_.getPropertyLowerLimit(zStage_, positionName);
               zStageUpperLimit_ = core_.getPropertyUpperLimit(zStage_, positionName);
            }
         }
      } catch (Exception ex) {
         throw new RuntimeException("Problem communicating with core to get Z stage limits");
      }
      JSONObject summaryMetadata = AcqEngMetadata.makeSummaryMD(name_, this);
      addToSummaryMetadata(summaryMetadata);

      try {
         //keep local copy for viewer
         summaryMetadata_ = new JSONObject(summaryMetadata.toString());
      } catch (JSONException ex) {
         System.err.print("Couldn't copy summaary metadata");
         ex.printStackTrace();
      }
      dataSink_.initialize(this, summaryMetadata);
   }

   public void onDataSinkClosing() {
      dataSink_ = null;
   }

   /**
    * Called by acquisition engine to save an image, shoudn't return until it as
    * been written to disk
    */
   private synchronized boolean saveImage(TaggedImage image) {
      if (image.tags == null && image.pix == null) {
         dataSink_.finished();
         acqFinishedFuture_ = null;
         return true;
      } else {
         //Now that all data processors have run, the channel index can be inferred
         //based on what channels show up at runtime
         String channelName = AcqEngMetadata.getChannelName(image.tags);
         if (!channelNames_.contains(channelName)) {
            channelNames_.add(channelName);
         }
         AcqEngMetadata.setAxisPosition(image.tags, AcqEngMetadata.CHANNEL_AXIS,
                 channelNames_.indexOf(channelName));
         //this method doesnt return until all images have been writtent to disk
         dataSink_.putImage(image);
         return false;
      }
   }

   public String getXYStageName() {
      return xyStage_;
   }

   public String getZStageName() {
      return zStage_;
   }

   public boolean isComplete() {
      return completed_;
   }

   public long getStartTime_ms() {
      return startTime_ms_;
   }

   public void setStartTime_ms(long time) {
      startTime_ms_ = time;
   }

   public boolean isPaused() {
      return paused_;
   }

   public synchronized void togglePaused() {
      paused_ = !paused_;
   }

   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   public boolean anythingAcquired() {
      return dataSink_ == null ? true : dataSink_.anythingAcquired();
   }

   public Iterable<AcquisitionHook> getBeforeHardwareHooks() {
      return beforeHardwareHooks_;
   }

   public Iterable<AcquisitionHook> getAfterHardwareHooks() {
      return afterHardwareHooks_;
   }

   public void addToOutput(TaggedImage ti) throws InterruptedException {
      firstDequeue_.putLast(ti);
   }

   public void markFinished() {
      finished_ = true;
   }

   public boolean isMarkedFinished() {
      return finished_;
   }
}
