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
package org.micromanager.plugins.magellan.acq;

import org.micromanager.plugins.magellan.autofocus.CrossCorrelationAutofocus;
import org.micromanager.plugins.magellan.bidc.FrameIntegrationMethod;
import org.micromanager.plugins.magellan.coordinates.XYStagePosition;
import ij.IJ;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.micromanager.plugins.magellan.bidc.JavaLayerImageConstructor;
import org.micromanager.plugins.magellan.channels.ChannelSetting;
import org.micromanager.plugins.magellan.coordinates.AffineUtils;
import java.awt.geom.Point2D;
import org.micromanager.plugins.magellan.json.JSONArray;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.surfacesandregions.Point3d;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceChangedListener;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceInterpolator;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceManager;

/**
 *
 * @author Henry
 */
public class FixedAreaAcquisition extends Acquisition implements SurfaceChangedListener {

   private static final int EVENT_QUEUE_CAP = 25;

   private final int spaceMode_;
   final private FixedAreaAcquisitionSettings settings_;
   private List<XYStagePosition> positions_;
   private long nextTimePointStartTime_ms_;
   private ParallelAcquisitionGroup acqGroup_;
   //barrier to wait for event generation at successive time points
   //signals come from 1) event genreating thread 2) Parallel acq group
   private volatile CountDownLatch startNextTPLatch_ = new CountDownLatch(1);
   //barrier to wait for all images to be written before starting nex time point stuff
   //signals come from 1) event generating thread 2) tagged iamge sink
   private volatile CountDownLatch tpImagesFinishedWritingLatch_ = new CountDownLatch(1);
   //executor service to wait for next execution
   final private ScheduledExecutorService waitForNextTPSerivice_ = Executors.newScheduledThreadPool(1);
   final private ExecutorService eventGenerator_;
   final private CrossCorrelationAutofocus autofocus_;
   private int maxSliceIndex_ = 0, minSliceIndex_ = 0;
   private double zOrigin_;
   private final boolean burstMode_;
   private final boolean towardsSampleIsPositive_;
   private volatile boolean acqSettingsUpdated_ = false;
   private volatile boolean tpImagesFinishedWriting_ = false;
   private final Object tpFinishedLockObject_ = new Object();
   

   /**
    * Acquisition with fixed XY positions (although they can potentially all be
    * translated between time points Supports time points Z stacks that can
    * change at positions between time points
    *
    * Acquisition engine manages a thread that reads events, fixed area
    * acquisition has another thread that generates events
    *
    * @param settings
    * @param acqGroup
    * @throws java.lang.Exception
    */
   public FixedAreaAcquisition(FixedAreaAcquisitionSettings settings, ParallelAcquisitionGroup acqGroup) throws Exception {
      super(settings.zStep_, settings.channels_);
      SurfaceManager.getInstance().registerSurfaceChangedListener(this);
      acqGroup_ = acqGroup;
      settings_ = settings;
      spaceMode_ = settings.spaceMode_;
      try {
         int dir = Magellan.getCore().getFocusDirection(zStage_);
         if (dir > 0) {
            towardsSampleIsPositive_ = true;
         } else if (dir < 0) {
            towardsSampleIsPositive_ = false;
         } else {
            throw new Exception();
         }
      } catch (Exception e) {
         Log.log("Couldn't get focus direction of Z drive. Configre using Tools--Hardware Configuration Wizard");
         throw new RuntimeException();
      }
      eventGenerator_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, settings_.name_ + ": Event generator");
         }
      });
      setupXYPositions();
      initialize(settings.dir_, settings.name_, settings.tileOverlap_);
      createEventGenerator();
      if (settings_.autofocusEnabled_) {
         //convert channel name to channel index
         int cIndex = getAutofocusChannelIndex();
         autofocus_ = new CrossCorrelationAutofocus(this, cIndex, settings_.autofocusMaxDisplacemnet_um_,
                 settings_.setInitialAutofocusPosition_ ? settings_.initialAutofocusPosition_
                         : Magellan.getCore().getPosition(settings_.autoFocusZDevice_));
      } else {
         autofocus_ = null;
      }
      //if a 2D, single xy position, no covariants, no autofocus: activate burst mode
      burstMode_ = getFilterType() == FrameIntegrationMethod.BURST_MODE;
      if (burstMode_) {
         Log.log("Burst mode activated");
      }
   }

   @Override
   public void SurfaceChanged(SurfaceInterpolator surface) {
      boolean updateNeeded = false;
      if (spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK && settings_.fixedSurface_ == surface) {
         updateNeeded = true;
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
              && (settings_.topSurface_ == surface || settings_.bottomSurface_ == surface)) {
         updateNeeded = true;
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {

      } else if (spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D) {

      } else {
         //no space mode
      }

      if (updateNeeded) {
         acqSettingsUpdated();
      }
   }
   
   public synchronized void acqSettingsUpdated() {
      acqSettingsUpdated_ = true;
      //restart event generation in case event generator is waiting at end of timepoint
      tpImagesFinishedWritingLatch_.countDown();
   }

   public boolean burstModeActive() {
      return burstMode_;
   }

   private void setupXYPositions() {
      try {
         //get XY positions
         if (spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
            positions_ = settings_.footprint_.getXYPositions(settings_.tileOverlap_);
         } else if (spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
            positions_ = settings_.useTopOrBottomFootprint_ == FixedAreaAcquisitionSettings.FOOTPRINT_FROM_TOP
                    ? settings_.topSurface_.getXYPositions(settings_.tileOverlap_) : settings_.bottomSurface_.getXYPositions(settings_.tileOverlap_);
         } else if (spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
            positions_ = settings_.footprint_.getXYPositions(settings_.tileOverlap_);
         } else if (spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D) {
            positions_ = settings_.footprint_.getXYPositions(settings_.tileOverlap_);
         } else {
            //no space mode, use current stage positon
            positions_ = new ArrayList<XYStagePosition>();
            int fullTileWidth = JavaLayerImageConstructor.getInstance().getImageWidth();
            int fullTileHeight = JavaLayerImageConstructor.getInstance().getImageHeight();
            int tileWidthMinusOverlap = fullTileWidth - this.getOverlapX();
            int tileHeightMinusOverlap = fullTileHeight - this.getOverlapY();
            Point2D.Double currentStagePos = Magellan.getCore().getXYStagePosition(xyStage_);
            positions_.add(new XYStagePosition(currentStagePos, tileWidthMinusOverlap, tileHeightMinusOverlap, fullTileWidth, fullTileHeight, 0, 0,
                    AffineUtils.getAffineTransform(Magellan.getCore().getCurrentPixelSizeConfig(),
                            currentStagePos.x, currentStagePos.y)));
         }
      } catch (Exception e) {
         Log.log("Problem with Acquisition's XY positions. Check acquisition settings");
         throw new RuntimeException();
      }
   }

   private int getAutofocusChannelIndex() {
      int index = 0;
      for (ChannelSetting channel : channels_) {
         if (channel.name_.equals(settings_.autofocusChannelName_)) {
            Log.log("Autofocus channel index: " + index, true);
            return index;
         }
         index++;
      }
      Log.log("Warning: couldn't find autofocus channel index", true);
      return index;
   }
   
   @Override
   public int getMinSliceIndex() {
      return  minSliceIndex_ ;
   }

   @Override
   public int getMaxSliceIndex() {
      return  maxSliceIndex_ ;
   }

   
   public FixedAreaAcquisitionSettings getSettings() {
      return settings_;
   }

   public double getTimeInterval_ms() {
      return settings_.timePointInterval_ * (settings_.timeIntervalUnit_ == 1 ? 1000 : (settings_.timeIntervalUnit_ == 2 ? 60000 : 1));
   }

   public long getNumRows() {
      long maxIndex = 0;
      synchronized (positions_) {
         for (XYStagePosition p : positions_) {
            maxIndex = Math.max(maxIndex, p.getGridRow());
         }
      }
      return maxIndex + 1;
   }

   public long getNumColumns() {
      long maxIndex = 0;
      for (XYStagePosition p : positions_) {
         maxIndex = Math.max(maxIndex, p.getGridCol());
      }
      return maxIndex + 1;
   }

   public long getNextWakeTime_ms() {
      return nextTimePointStartTime_ms_;
   }

   /**
    * abort acquisition. Block until successfully finished
    */
   public void abort() {
      new Thread(new Runnable() {
         @Override
         public void run() {
            if (finished_) {
               //acq already aborted
               return;
            }
            if (FixedAreaAcquisition.this.isPaused()) {
               FixedAreaAcquisition.this.togglePaused();
            }
            //interrupt event generating thread
            eventGenerator_.shutdownNow();
            waitForNextTPSerivice_.shutdownNow();
            try {
               //wait for it to exit        
               while (!eventGenerator_.isTerminated()) {
                  Thread.sleep(5);
               }
            } catch (InterruptedException ex) {
               IJ.log("Unexpected interrupt whil trying to abort acquisition");
               //shouldn't happen
            }
            //clear any pending events, specific to this acqusition (since parallel acquisitions
            //share their event queue                           
            while (events_.pollLast() != null) {
            }
            try {
               //not a big deal if an extra one of these is added since sink will shut down on the first one
               events_.put(AcquisitionEvent.createAcquisitionFinishedEvent(FixedAreaAcquisition.this));
               //make sure engine doesnt get stuck

            } catch (InterruptedException ex) {
               Log.log("Unexpected interrupted exception while trying to abort"); //shouldnt happen
            }

            //singal aborted will wait for the image sink to die so this function doesnt return until abort complete
            acqGroup_.signalAborted(FixedAreaAcquisition.this);

            //make sure parallel group doesnt hang waiting to signal this acq to start next TP
            startNextTPLatch_.countDown();
         }
      }, "Aborting thread").start();
   }

   public void signalReadyForNextTP() {
      //called by event generating thread and and parallel manager thread to
      //ensure enough time has passed to start next TP and that parallel group allows it 
      startNextTPLatch_.countDown();
   }

   /**
    * Called by image sink at the end of writing images of each time point or
    * when image sink is finishing, either due to an abort or the end of the
    * final timepoint
    */
   public void imagesAtTimepointFinishedWriting() {
      synchronized (tpFinishedLockObject_) {
         tpImagesFinishedWriting_ = true;
         tpImagesFinishedWritingLatch_.countDown();
      }
   }

   /**
    *
    * @throws InterruptedException if acq aborted while waiting for next TP
    */
   private void pauseUntilReadyForTP() throws InterruptedException {
      //Pause here bfore next time point starts
      Log.log(getName() + " pausing before TP", false);
      long timeUntilNext = nextTimePointStartTime_ms_ - System.currentTimeMillis();
      if (timeUntilNext > 0) {
         Log.log(getName() + " time before next greater than 0", false);
         //wait for enough time to pass and parallel group to signal ready
         Log.log(getName() + " scheduling wiat for next TP", false);
         ScheduledFuture future = waitForNextTPSerivice_.schedule(new Runnable() {

            @Override
            public void run() {
               try {
                  Log.log(getName() + " awaiting next TP latch", false);
                  startNextTPLatch_.await();
                  Log.log(getName() + " finished awaiting TP latch", false);
                  startNextTPLatch_ = new CountDownLatch(1);
               } catch (InterruptedException ex) {
                  throw new RuntimeException(); //propogate interrupt due to abort
               }
            }
         }, timeUntilNext, TimeUnit.MILLISECONDS);
         try {
            future.get();
         } catch (ExecutionException ex) {
            throw new InterruptedException(); //acq aborted         
         }
      } else {
         //already enough time passed, just wait for go-ahead from parallel group
         Log.log(getName() + " already enought time passed for TP, awaiting next TP singal", false);
         startNextTPLatch_.await();
         startNextTPLatch_ = new CountDownLatch(1);
      }
   }

   private void createEventGenerator() {
      Log.log("Create event generator started", false);
      eventGenerator_.submit(new Runnable() {
         //check interupt status before any blocking call is entered
         @Override
         public void run() {
            Log.log("event generation beignning", false);
            try {
               //get highest possible z position to image, which is slice index 0
               zOrigin_ = getZTopCoordinate();
               nextTimePointStartTime_ms_ = 0;
               for (int timeIndex = 0; timeIndex < (settings_.timeEnabled_ ? settings_.numTimePoints_ : 1); timeIndex++) {
                  if (eventGenerator_.isShutdown()) {
                     throw new InterruptedException();
                  }
                  pauseUntilReadyForTP();
                  if (eventGenerator_.isShutdown()) {
                     throw new InterruptedException();
                  }

                  //set autofocus position
                  if (settings_.autofocusEnabled_ && timeIndex > 1) { //read it from settings so that you can turn it off during acq                    
                     Log.log(getName() + "Setting AF position", true);
                     events_.put(AcquisitionEvent.createAutofocusEvent(settings_.autoFocusZDevice_, autofocus_.getAutofocusPosition()));
                  } else if (settings_.autofocusEnabled_ && timeIndex <= 1 && settings_.setInitialAutofocusPosition_) {
                     Log.log(getName() + "Setting AF position", true);
                     events_.put(AcquisitionEvent.createAutofocusEvent(settings_.autoFocusZDevice_, settings_.initialAutofocusPosition_));
                  }

                  //set the next time point start time
                  double interval_ms = settings_.timePointInterval_ * (settings_.timeIntervalUnit_ == 1 ? 1000 : (settings_.timeIntervalUnit_ == 2 ? 60000 : 1));
                  nextTimePointStartTime_ms_ = (long) (System.currentTimeMillis() + interval_ms);

                  while (true) {
                     createEventsAtTimepoint(timeIndex);
                     //wait for final image of timepoint to be written before beginning end of timepoint stuff
                     //three ways to get past this barrier:
                     //1) interuption by an abort request will throw an interrupted exception and cause this thread to return
                     //2) image sink will get a timepointFinished signal and call timepointImagesFinishedWriting
                     //3) image sink will get an acquisitionFinsihed signal and call allImagesFinishedWriting
                     //in the unlikely scenario that shudown is called by abort between these two calls, the imagesink should be able
                     //to finish writing images as expected
                        tpImagesFinishedWritingLatch_.await();
                     //make sure that latch was triggered by tp finishing, rather than surface update
                     synchronized (tpFinishedLockObject_) {
                        if (tpImagesFinishedWriting_) {
                           break;
                        } else {
                           tpImagesFinishedWritingLatch_ = new CountDownLatch(1);
                        }
                     }
                  }
                  //timepoint images finshed writing, so rest the latch
                  synchronized (tpFinishedLockObject_) {
                     tpImagesFinishedWritingLatch_ = new CountDownLatch(1);
                     tpImagesFinishedWriting_ = false;
                  }

                  //this call starts a new thread to not hang up cyclic barriers   
                  //signal to next acquisition in parallel group to start generating events, then continue using the event generator thread
                  //to calculate autofocus
                  acqGroup_.finishedTPEventGeneration(FixedAreaAcquisition.this);

                  //all images finished writing--can now run autofocus
                  if (autofocus_ != null) {
                     try {
                        autofocus_.run(timeIndex);
                     } catch (Exception ex) {
                        IJ.log("Problem running autofocus " + ex.getMessage());
                     }
                  }
               }
               //acqusiition has generated all of its events
               eventGeneratorShutdown();
            } catch (InterruptedException e) {
               eventGeneratorShutdown();
               return; //acq aborted
            }
         }
      });
   }

   private void createEventsAtTimepoint(int timeIndex) throws InterruptedException {
      int positionIndex;
      if (lastEvent_ != null && lastEvent_.timeIndex_ == timeIndex) {
            //continuation of an exisitng time point due to a surface being changed
            positionIndex = lastEvent_.positionIndex_;
         } else {
            positionIndex = 0;
         }
      
      while (positionIndex < positions_.size()) {
         //add events for all slices/channels at this position
         XYStagePosition position = positions_.get(positionIndex);      
         int sliceIndex = (int) Math.round((getZTopCoordinate() - zOrigin_) / zStep_);
         while (true) {
            if (eventGenerator_.isShutdown()) { // check for aborts
               throw new InterruptedException();
            }
            double zPos = zOrigin_ + sliceIndex * zStep_;
            if ((spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D || spaceMode_ == FixedAreaAcquisitionSettings.NO_SPACE)
                    && sliceIndex > 0) {
               break; //2D regions only have 1 slice
            }

            if (isImagingVolumeUndefinedAtPosition(spaceMode_, settings_, position)) {
               break;
            }

            if (isZBelowImagingVolume(spaceMode_, settings_, position, zPos, zOrigin_) || (zStageHasLimits_ && zPos > zStageUpperLimit_)) {
               //position is below z stack or limit of focus device, z stack finished
               break;
            }
            //3D region
            if (isZAboveImagingVolume(spaceMode_, settings_, position, zPos, zOrigin_) || (zStageHasLimits_ && zPos < zStageLowerLimit_)) {
               sliceIndex++;
               continue; //position is above imaging volume or range of focus device
            }

            for (int channelIndex = 0; channelIndex < settings_.channels_.size(); channelIndex++) {
               if (!settings_.channels_.get(channelIndex).uniqueEvent_ || !settings_.channels_.get(channelIndex).use_) {
                  continue;
               }
               AcquisitionEvent event = new AcquisitionEvent(FixedAreaAcquisition.this, timeIndex, channelIndex, sliceIndex,
                       positionIndex, zPos, position, settings_.covariantPairings_);
               if (eventGenerator_.isShutdown()) {
                  throw new InterruptedException();
               }
               //keep track of biggest slice index and smallest slice for drift correciton purposes
               maxSliceIndex_ = Math.max(maxSliceIndex_, event.sliceIndex_);
               minSliceIndex_ = Math.min(minSliceIndex_, event.sliceIndex_);
               events_.put(event); //event generator will block if event queue is full
               //check if surfaces have been changed
               if (acqSettingsUpdated_) {
                  //remove all elements in reverse order in case the front is taken for acquisition while doing this
                  while (events_.pollLast() != null) {}                          
                  acqSettingsUpdated_ = false;
                  createEventsAtTimepoint(timeIndex);  
                  return;
               }

            }
            sliceIndex++;
         } //slice loop finish
         positionIndex++;
      } //position loop finished
      if (timeIndex == (settings_.timeEnabled_ ? settings_.numTimePoints_ : 1) - 1) {
         //acquisition now finished, add event so engine can mark acquisition as finished                 
         events_.put(AcquisitionEvent.createAcquisitionFinishedEvent(FixedAreaAcquisition.this));
      } else {
         events_.put(AcquisitionEvent.createTimepointFinishedEvent(FixedAreaAcquisition.this));
      }
      //check for abort
      if (eventGenerator_.isShutdown()) {
         throw new InterruptedException();
      }
   }

   private void eventGeneratorShutdown() {
      eventGenerator_.shutdown();
      waitForNextTPSerivice_.shutdown();
      if (autofocus_ != null) {
         autofocus_.close();
      }
      SurfaceManager.getInstance().removeSurfaceChangedListener(this);
   }

   public static boolean isImagingVolumeUndefinedAtPosition(int spaceMode, FixedAreaAcquisitionSettings settings, XYStagePosition position) {
      if (spaceMode == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return !settings.fixedSurface_.isSurfaceDefinedAtPosition(position);
      } else if (spaceMode == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return !settings.topSurface_.isSurfaceDefinedAtPosition(position)
                 && !settings.bottomSurface_.isSurfaceDefinedAtPosition(position);
      }
      return false;
   }

   /**
    * This function and the one below determine which slices will be collected
    * for a given position
    *
    * @param position
    * @param zPos
    * @return
    */
   public static boolean isZAboveImagingVolume(int spaceMode, FixedAreaAcquisitionSettings settings, XYStagePosition position, double zPos, double zOrigin) throws InterruptedException {
      if (spaceMode == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return settings.fixedSurface_.isPositionCompletelyAboveSurface(position, settings.fixedSurface_, zPos + settings.distanceAboveFixedSurface_);
      } else if (spaceMode == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.topSurface_.isPositionCompletelyAboveSurface(position, settings.topSurface_, zPos + settings.distanceAboveTopSurface_);
      } else if (spaceMode == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return zPos < settings.zStart_;
      } else {
         //no zStack
         return zPos < zOrigin;
      }
   }

   public static boolean isZBelowImagingVolume(int spaceMode, FixedAreaAcquisitionSettings settings, XYStagePosition position, double zPos, double zOrigin) throws InterruptedException {
      if (spaceMode == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return settings.fixedSurface_.isPositionCompletelyBelowSurface(position, settings.fixedSurface_, zPos - settings.distanceBelowFixedSurface_);
      } else if (spaceMode == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.bottomSurface_.isPositionCompletelyBelowSurface(position, settings.bottomSurface_, zPos - settings.distanceBelowBottomSurface_);
      } else if (spaceMode == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return zPos > settings.zEnd_;
      } else {
         //no zStack
         return zPos > zOrigin;
      }
   }
   
   private double getZTopCoordinate() {
      return getZTopCoordinate(spaceMode_, settings_, towardsSampleIsPositive_, zStageHasLimits_,  zStageLowerLimit_,  zStageUpperLimit_, zStage_);
   }

   public static double getZTopCoordinate(int spaceMode, FixedAreaAcquisitionSettings settings, boolean towardsSampleIsPositive,
           boolean zStageHasLimits, double zStageLowerLimit, double zStageUpperLimit, String zStage) {
      if (spaceMode == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         Point3d[] interpPoints = settings.fixedSurface_.getPoints();
         if (towardsSampleIsPositive) {
            double top = interpPoints[0].z - settings.distanceAboveFixedSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         } else {
            double top = interpPoints[interpPoints.length - 1].z + settings.distanceAboveFixedSurface_;
            return zStageHasLimits ? Math.max(zStageUpperLimit, top) : top;
         }
      } else if (spaceMode == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         if (towardsSampleIsPositive) {
            Point3d[] interpPoints = settings.topSurface_.getPoints();
            double top = interpPoints[0].z - settings.distanceAboveTopSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         } else {
            Point3d[] interpPoints = settings.topSurface_.getPoints();
            double top = interpPoints[interpPoints.length - 1].z + settings.distanceAboveTopSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         }
      } else if (spaceMode == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return settings.zStart_;
      } else {
         try {
            //region2D or no region
            return Magellan.getCore().getPosition(zStage);
         } catch (Exception ex) {
            Log.log("couldn't get z position", true);
            throw new RuntimeException();
         }
      }
   }

   @Override
   public double getZCoordinateOfDisplaySlice(int displaySliceIndex) {
      displaySliceIndex += minSliceIndex_;
      return zOrigin_ + zStep_ * displaySliceIndex;
   }

   @Override
   public int getDisplaySliceIndexFromZCoordinate(double z) {
      return (int) Math.round((z - zOrigin_) / zStep_) - minSliceIndex_;
   }

   @Override
   protected JSONArray createInitialPositionList() {
      JSONArray pList = new JSONArray();
      for (XYStagePosition xyPos : positions_) {
         pList.put(xyPos.getMMPosition());
      }
      return pList;
   }

   @Override
   public double getRank() {
      return settings_.rank_;
   }

   @Override
   public int getFilterType() {
      return settings_.imageFilterType_;
   }

   @Override
   public int getAcqEventQueueCap() {
      return EVENT_QUEUE_CAP;
   }

   //these two used for setting inital file size for speed purposes
   @Override
   public int getInitialNumFrames() {
      return Math.max(1, settings_.numTimePoints_);
   }

   @Override
   public int getInitialNumSlicesEstimate() {
      if (spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         Point3d[] interpPoints = settings_.fixedSurface_.getPoints();
         double top = interpPoints[0].z;
         double bottom = interpPoints[interpPoints.length - 1].z;
         return (int) Math.ceil((Math.abs(top - bottom) + settings_.distanceAboveFixedSurface_ + settings_.distanceBelowFixedSurface_) / zStep_);
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         Point3d[] interpPoints = settings_.topSurface_.getPoints();
         double top = interpPoints[0].z;
         double bottom = interpPoints[interpPoints.length - 1].z;
         return (int) Math.ceil((Math.abs(top - bottom) + settings_.distanceAboveTopSurface_ + settings_.distanceBelowBottomSurface_) / zStep_);
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return (int) Math.ceil(Math.abs(settings_.zStart_ - settings_.zEnd_) / zStep_);
      } else {
         //region2D or no region
         return 1;
      }
   }

}
