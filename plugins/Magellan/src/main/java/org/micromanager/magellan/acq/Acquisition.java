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

import org.micromanager.magellan.imagedisplay.DisplayPlus;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.micromanager.magellan.channels.ChannelSpec;
import org.micromanager.magellan.coordinates.AffineUtils;
import org.micromanager.magellan.coordinates.PositionManager;
import org.micromanager.magellan.coordinates.XYStagePosition;
import org.micromanager.magellan.json.JSONArray;
import org.micromanager.magellan.json.JSONObject;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.Log;
import org.micromanager.magellan.misc.MD;
import mmcorej.CMMCore;

/**
 * Abstract class that manages a generic acquisition. Subclassed into specific
 * types of acquisition
 */
public abstract class Acquisition {

   private static final int MAX_QUEUED_IMAGES_FOR_WRITE = 20;

   protected final double zStep_;
   protected double zOrigin_;
   protected volatile int minSliceIndex_ = 0, maxSliceIndex_ = 0;
   protected String xyStage_, zStage_;
   protected boolean zStageHasLimits_ = false;
   protected double zStageLowerLimit_, zStageUpperLimit_;
   protected AcquisitionEvent lastEvent_ = null;
   protected volatile boolean finished_ = false;
   private JSONObject summaryMetadata_;
   private String name_;
   private long startTime_ms_ = -1;
   private int overlapX_, overlapY_;
   private volatile boolean paused_ = false;
   protected ChannelSpec channels_;
   private MMImageCache imageCache_;
   private ThreadPoolExecutor savingExecutor_;
   private ExecutorService eventGenerator_;
   protected PositionManager posManager_;
   private MagellanEngine eng_;
   protected volatile boolean aborted_ = false;
   private MultiResMultipageTiffStorage storage_;
   private DisplayPlus display_;

   public Acquisition(double zStep, ChannelSpec channels) {
      eng_ = MagellanEngine.getInstance();
      xyStage_ = Magellan.getCore().getXYStageDevice();
      zStage_ = Magellan.getCore().getFocusDevice();
      channels_ = channels;
      //"postion" is not generic name..and as of right now there is now way of getting generic z positions
      //from a z deviec in MM
      String positionName = "Position";
      try {
         if (Magellan.getCore().hasProperty(zStage_, positionName)) {
            zStageHasLimits_ = Magellan.getCore().hasPropertyLimits(zStage_, positionName);
            if (zStageHasLimits_) {
               zStageLowerLimit_ = Magellan.getCore().getPropertyLowerLimit(zStage_, positionName);
               zStageUpperLimit_ = Magellan.getCore().getPropertyUpperLimit(zStage_, positionName);
            }
         }
      } catch (Exception ex) {
         Log.log("Problem communicating with core to get Z stage limits");
      }
      zStep_ = zStep;
   }

   protected void initialize(String dir, String name, double overlapPercent) {
      overlapX_ = (int) (Magellan.getCore().getImageWidth() * overlapPercent / 100);
      overlapY_ = (int) (Magellan.getCore().getImageHeight() * overlapPercent / 100);
      summaryMetadata_ = makeSummaryMD(name);
      storage_ = new MultiResMultipageTiffStorage(dir, summaryMetadata_);
      posManager_ = storage_.getPosManager();
      //storage class has determined unique acq name, so it can now be stored
      name_ = storage_.getUniqueAcqName();
      imageCache_ = new MMImageCache(storage_);
      imageCache_.setSummaryMetadata(summaryMetadata_);
      display_ = new DisplayPlus(imageCache_, this, summaryMetadata_, storage_);
      savingExecutor_ = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
              (Runnable r) -> new Thread(r, name_ + ": Saving executor"));
      eventGenerator_ = Executors.newSingleThreadExecutor((Runnable r) -> new Thread(r, name_ + ": Event generator"));
      //subclasses are resonsible for submitting event streams to begin acquisiton
   }

   /**
    * Called by acquisition subclasses to communicate with acquisition engine
    * returns a Future that can be gotten once the last image has written to
    * disk
    *
    * @param eventStream
    * @return
    */
   protected void submitEventStream(Stream<AcquisitionEvent> eventStream) {
      eventGenerator_.submit(() -> {
         //Submit stream to acqusition event for execution, getting a stream of Future<Future>
         //This won't actually do anything until the terminal operation on the stream has taken place

         Stream<Future<Future>> eventFutureStream = eng_.mapToAcquisition(eventStream);

         //Make sure events can't be submitted to the engine way faster than images can be written to disk
         eventFutureStream = eventFutureStream.map(new Function<Future<Future>, Future<Future>>() {
            @Override
            public Future<Future> apply(Future<Future> t) {
//               int queuedSavingImages = savingExecutor_.getQueue().size();
               while (savingExecutor_.getQueue().size() > MAX_QUEUED_IMAGES_FOR_WRITE) {
                  try {
                     Thread.sleep(2);
                  } catch (InterruptedException ex) {
                     throw new RuntimeException(ex); //must have beeen aborted
                  }
               }
               return t;
            }
         });

         //Wait around while pause is engaged
         eventFutureStream = eventFutureStream.map(new Function<Future<Future>, Future<Future>>() {
            @Override
            public Future<Future> apply(Future<Future> t) {
               while (paused_) {
                  try {
                     Thread.sleep(5);
                  } catch (InterruptedException ex) {
                     throw new RuntimeException(ex);
                  }
               }
               return t;
            }
         });

         //lazily iterate through them
         Stream<Future> imageSavedFutureStream = eventFutureStream.map((Future<Future> t) -> {
            try {
               return t.get();
            } catch (InterruptedException | ExecutionException ex) {
               t.cancel(true); //interrupt current event, which is especially important if it is an acquisition waiting event
               throw new RuntimeException(ex);
            }
         });
         //Iterate through and make sure images get saved 
         imageSavedFutureStream.forEach((Future t) -> {
            try {
               t.get();
            } catch (InterruptedException | ExecutionException ex) {
               throw new RuntimeException(ex);
            }
         });
      });
   }

   /**
    * Function for lazy conversion a stream of acquisition events to another
    * stream by applying a event2stream function to each element of the
    * inputStream
    *
    * @return
    */
   protected Function<Stream<AcquisitionEvent>, Stream<AcquisitionEvent>> stream2Stream(
           Function<AcquisitionEvent, Stream<AcquisitionEvent>> event2StreamFunction) {
      return (Stream<AcquisitionEvent> inputStream) -> {
         //make a stream builder
         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
         //apply the function to each element of the input stream, then send the resulting streams
         //to the builder
         inputStream.spliterator().forEachRemaining((AcquisitionEvent t) -> {
            Stream<AcquisitionEvent> subStream = event2StreamFunction.apply(t);
            subStream.spliterator().forEachRemaining(builder);
         });
         return builder.build();
      };
   }

   /**
    * Called by acquisition engine to save an image, returns a future that can
    * be gotten once that image has made it onto the disk
    */
   Future saveImage(MagellanTaggedImage image) {
      //The saving executor is essentially doing the work of making the image pyramid, while there
      //is a seperate internal executor in MultiResMultipageTiffStorage that does all the writing
      return savingExecutor_.submit(() -> {
         if (MagellanTaggedImage.isAcquisitionFinishedImage(image)) {
            eventGenerator_.shutdown();
            savingExecutor_.shutdown();
            //Dont wait for it to shutdown because it is the one executing this code
            imageCache_.finished();
            finished_ = true;
         } else {
            //this method doesnt return until all images have been writtent to disk
            imageCache_.putImage(image);
         }
      });
   }

   protected abstract JSONArray createInitialPositionList();

   public void abort() {
      //Do this on a seperate thread. Maybe this was to avoid deadlock?
      new Thread(new Runnable() {
         @Override
         public void run() {
            if (finished_) {
               //acq already aborted
               return;
            }
            aborted_ = true;
//            if (Acquisition.this.isPaused()) {
//               Acquisition.this.togglePaused();
//            }
            eventGenerator_.shutdownNow();
            try {
               //wait for it to exit
               while (!eventGenerator_.awaitTermination(5, TimeUnit.MILLISECONDS)) {
               }
            } catch (InterruptedException ex) {
               Log.log("Unexpected interrupt while trying to abort acquisition", true);
               //shouldn't happen
            }

            //signal acquisition engine to start finishing process
            Future<Future> endAcqFuture = eng_.mapToAcquisition(Stream.of(AcquisitionEvent.createAcquisitionFinishedEvent(Acquisition.this))).findFirst().get();
            Future imageSaverDoneFuture;
            try {
               imageSaverDoneFuture = endAcqFuture.get();
               imageSaverDoneFuture.get();
            } catch (InterruptedException ex) {
               Log.log("aborting acquisition interrupted");
            } catch (ExecutionException ex) {
               Log.log("Exception encountered when trying to end acquisition");
            }
         }
      }, "Aborting thread").start();
   }
   
      public static void addImageMetadata(JSONObject tags, AcquisitionEvent event, int timeIndex,
           int camChannelIndex, long elapsed_ms, double exposure) {
      //add tags
      try {
         long gridRow = event.xyPosition_.getGridRow();
         long gridCol = event.xyPosition_.getGridCol();
         MD.setPositionName(tags, "Grid_" + gridRow+ "_" + gridCol);
         MD.setPositionIndex(tags, event.positionIndex_);
         MD.setSliceIndex(tags, event.sliceIndex_);
         MD.setFrameIndex(tags, timeIndex);
         MD.setChannelIndex(tags, event.channelIndex_ + camChannelIndex);
         MD.setZPositionUm(tags, event.zPosition_);
         MD.setElapsedTimeMs(tags, elapsed_ms);
         MD.setImageTime(tags, (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss -")).format(Calendar.getInstance().getTime()));
         MD.setExposure(tags, exposure);
         MD.setGridRow(tags, gridRow);
         MD.setGridCol(tags, gridCol);
         MD.setStageX(tags, event.xyPosition_.getCenter().x);
         MD.setStageY(tags, event.xyPosition_.getCenter().y);
         //add data about surface
         //right now this only works for fixed distance from the surface
         if ((event.acquisition_ instanceof MagellanGUIAcquisition)
                 && ((MagellanGUIAcquisition) event.acquisition_).getSpaceMode() == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
            //add metadata about surface
            MD.setSurfacePoints(tags, ((MagellanGUIAcquisition) event.acquisition_).getFixedSurfacePoints());
         }
      } catch (Exception e) {
         Log.log("Problem adding image metadata");
         throw new RuntimeException();
      }
   }

   private JSONObject makeSummaryMD(String prefix) {
      //num channels is camera channels * acquisitionChannels
      int numChannels = this.getNumChannels();

      CMMCore core = Magellan.getCore();
      JSONObject summary = new JSONObject();
      MD.setAcqDate(summary, getCurrentDateAndTime());
      //Actual number of channels is equal or less than this field
      MD.setNumChannels(summary, numChannels);

      MD.setZCTOrder(summary, false);
      MD.setPixelTypeFromByteDepth(summary, (int) Magellan.getCore().getBytesPerPixel());
      MD.setBitDepth(summary, (int) Magellan.getCore().getImageBitDepth());
      MD.setWidth(summary, (int) Magellan.getCore().getImageWidth());
      MD.setHeight(summary, (int) Magellan.getCore().getImageHeight());
      MD.setSavingPrefix(summary, prefix);
      JSONArray initialPosList = this.createInitialPositionList();
      MD.setInitialPositionList(summary, initialPosList);
      MD.setPixelSizeUm(summary, core.getPixelSizeUm());
      MD.setZStepUm(summary, this.getZStep());
      MD.setIntervalMs(summary, this instanceof MagellanGUIAcquisition ? ((MagellanGUIAcquisition) this).getTimeInterval_ms() : 0);
      MD.setPixelOverlapX(summary, this.getOverlapX());
      MD.setPixelOverlapY(summary, this.getOverlapY());
      MD.setExploreAcq(summary, this instanceof ExploreAcquisition);
      //affine transform
      String pixelSizeConfig;
      try {
         pixelSizeConfig = core.getCurrentPixelSizeConfig();
      } catch (Exception ex) {
         Log.log("couldn't get affine transform");
         throw new RuntimeException();
      }
      AffineTransform at = AffineUtils.getAffineTransform(pixelSizeConfig, 0, 0);
      if (at == null) {
         Log.log("No affine transform found for pixel size config: " + pixelSizeConfig
                 + "\nUse \"Calibrate\" button on main Magellan window to configure\n\n");
         throw new RuntimeException();
      }
      MD.setAffineTransformString(summary, AffineUtils.transformToString(at));
      JSONArray chNames = new JSONArray();
      JSONArray chColors = new JSONArray();
      String[] names = this.getChannelNames();
      Color[] colors = this.getChannelColors();
      for (int i = 0; i < numChannels; i++) {
         chNames.put(names[i]);
         chColors.put(colors[i].getRGB());
      }
      MD.setChannelNames(summary, chNames);
      MD.setChannelColors(summary, chColors);
      try {
         MD.setCoreXY(summary, Magellan.getCore().getXYStageDevice());
         MD.setCoreFocus(summary, Magellan.getCore().getFocusDevice());
      } catch (Exception e) {
         Log.log("couldn't get XY or Z stage from core");
      }
      return summary;
   }

   /**
    * Build a lazy stream of events based on the hierarchy of acquisition
    * functions
    *
    * @param acqFunctions
    * @return
    */
   protected Stream<AcquisitionEvent> makeEventStream(List<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions) {
      //Make a composed function that expands every level of the acquisition tree as needed
      AcquisitionEventIterator iterator = new AcquisitionEventIterator(new AcquisitionEvent(this), acqFunctions);
      Stream<AcquisitionEvent> targetStream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
      return targetStream;
   }

   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels(ChannelSpec channels) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            int channelIndex_ = 0;

            @Override
            public boolean hasNext() {
               while (channelIndex_ < channels.getNumActiveChannels() && !channels.getActiveChannelSetting(channelIndex_).uniqueEvent_) {
                  channelIndex_++;
                  if (channelIndex_ >= channels.getNumActiveChannels()) {
                     return false;
                  }
               }
               return channelIndex_ < channels.getNumActiveChannels();
            }

            @Override
            public AcquisitionEvent next() {
               AcquisitionEvent channelEvent = event.copy();
               channelEvent.channelIndex_ = channelIndex_;
               channelEvent.zPosition_ += channels.getActiveChannelSetting(channelIndex_).offset_;
               channelIndex_++;
               return channelEvent;
            }
         };
      };
   }

   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack(int startSliceIndex, int stopSliceIndex) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {

            private int sliceIndex_ = startSliceIndex;

            @Override
            public boolean hasNext() {
               return sliceIndex_ < stopSliceIndex;
            }

            @Override
            public AcquisitionEvent next() {
               double zPos = sliceIndex_ * zStep_ + zOrigin_;
               AcquisitionEvent sliceEvent = event.copy();
               sliceEvent.sliceIndex_ = sliceIndex_;
               //Do plus equals here in case z positions have been modified by another function (e.g. channel specific focal offsets)
               sliceEvent.zPosition_ += zPos;
               sliceIndex_++;
               return sliceEvent;
            }
         };
      };
   }

   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions(
           int[] positionIndices, List<XYStagePosition> positions) {
      return (AcquisitionEvent event) -> {
         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
         for (int index = 0; index < positionIndices.length; index++) {
            AcquisitionEvent posEvent = event.copy();
            posEvent.positionIndex_ = positionIndices[index];
            posEvent.xyPosition_ = positions.get(posEvent.positionIndex_);
            builder.accept(posEvent);
         }
         return builder.build().iterator();
      };
   }

   public String getXYStageName() {
      return xyStage_;
   }

   public String getZStageName() {
      return zStage_;
   }

   /**
    * indices are 1 based and positive
    *
    * @param sliceIndex -
    * @param frameIndex -
    * @return
    */
   public double getZCoordinateOfDisplaySlice(int displaySliceIndex) {
      displaySliceIndex += minSliceIndex_;
      return zOrigin_ + zStep_ * displaySliceIndex;
   }

   public int getDisplaySliceIndexFromZCoordinate(double z) {
      return (int) Math.round((z - zOrigin_) / zStep_) - minSliceIndex_;
   }

   /**
    * Return the maximum number of possible channels for the acquisition, not
    * all of which are neccessarily active
    *
    * @return
    */
   public int getNumChannels() {
      return channels_.getNumActiveChannels();
   }

   public ChannelSpec getChannels() {
      return channels_;
   }

   public int getNumSlices() {
      return maxSliceIndex_ - minSliceIndex_ + 1;
   }

   public int getMinSliceIndex() {
      return minSliceIndex_;
   }

   public int getMaxSliceIndex() {
      return maxSliceIndex_;
   }

   public boolean isFinished() {
      return finished_;
   }

   public long getStartTime_ms() {
      return startTime_ms_;
   }

   public void setStartTime_ms(long time) {
      startTime_ms_ = time;
   }

   public int getOverlapX() {
      return overlapX_;
   }

   public int getOverlapY() {
      return overlapY_;
   }

   public void waitUntilClosed() {
      try {
         //wait for it to exit
         while (!eventGenerator_.awaitTermination(5, TimeUnit.MILLISECONDS)) {
         }
         while (!savingExecutor_.awaitTermination(5, TimeUnit.MILLISECONDS)) {
         }
      } catch (InterruptedException ex) {
         Log.log("Unexpected interrupt while trying to abort acquisition", true);
         //shouldn't happen
      }
   }

   public String getName() {
      return name_;
   }

   public double getZStep() {
      return zStep_;
   }

   public boolean isPaused() {
      return paused_;
   }

   public void togglePaused() {
      paused_ = !paused_;
   }

   public String[] getChannelNames() {
      return channels_.getActiveChannelNames();
   }

   public Color[] getChannelColors() {
      return channels_.getActiveChannelColors();
   }

   private static String getCurrentDateAndTime() {
      DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      Calendar calobj = Calendar.getInstance();
      return df.format(calobj.getTime());
   }

   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }
   
   public boolean anythingAcquired() {
      return !storage_.imageKeys().isEmpty();
   }

   /**
    * Used to tell the multiresoltuion storage to create more downsampled levels for higher zoom
    * Explore acquisitions use this
    * @param index 
    */
   public void addResolutionsUpTo(int index) {
      savingExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            try {
               storage_.addResolutionsUpTo(index);
            } catch (InterruptedException | ExecutionException ex) {
               ex.printStackTrace();
               Log.log(ex);
            }
            display_.updateDisplay(true);
         }
      });
   }
}
