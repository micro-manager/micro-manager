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

package acq;

import autofocus.CrossCorrelationAutofocus;
import bidc.FrameIntegrationMethod;
import coordinates.XYStagePosition;
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
import bidc.JavaLayerImageConstructor;
import channels.ChannelSetting;
import coordinates.AffineUtils;
import java.awt.geom.Point2D;
import json.JSONArray;
import main.Magellan;
import misc.GlobalSettings;
import misc.Log;
import surfacesandregions.Point3d;

/**
 *
 * @author Henry
 */
public class FixedAreaAcquisition extends Acquisition {

    private static final int EVENT_QUEUE_CAP = 10; // so that surfaces can be dynamically changed during acq
    
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
   private int maxSliceIndex_ = 0;
   private double zTop_;
   private final boolean burstMode_;
   private final boolean towardsSampleIsPositive_;

   /**
    * Acquisition with fixed XY positions (although they can potentially all be
    * translated between time points Supports time points Z stacks that can
    * change at positions between time points
    *
    * Acquisition engine manages a thread that reads events, fixed area
    * acquisition has another thread that generates events
   
     */
    public FixedAreaAcquisition(FixedAreaAcquisitionSettings settings, ParallelAcquisitionGroup acqGroup) throws Exception {
        super(settings.zStep_, settings.channels_);
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

   public boolean burstModeActive() {
       return burstMode_;
   }
  
   public double getBasePower(int laserIndex) {
      return laserIndex == 0 ? settings_.laser1BasePower_ : settings_.laser2BasePower_;
   }
   
   public int getMeanFreePath(int laserIndex) {      
      return laserIndex == 0 ? settings_.laser1MeanFreePath_ : settings_.laser2MeanFreePath_;
   }
   
   public int getRadiusOfCurvature() {
      return settings_.radiusOfCurvature_;
   }

   private void setupXYPositions() {
      try {
         //get XY positions
         if (spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
            positions_ = settings_.fixedSurface_.getXYPositions(settings_.tileOverlap_);
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
            int fullTileWidth = (int) JavaLayerImageConstructor.getInstance().getImageWidth();
            int fullTileHeight = (int) JavaLayerImageConstructor.getInstance().getImageHeight();
            int tileWidthMinusOverlap = fullTileWidth - this.getOverlapX();
            int tileHeightMinusOverlap = fullTileHeight - this.getOverlapY();
            Point2D.Double currentStagePos = Magellan.getCore().getXYStagePosition(xyStage_);  
            positions_.add(new XYStagePosition( currentStagePos, tileWidthMinusOverlap, tileHeightMinusOverlap, fullTileWidth, fullTileHeight, 0, 0,
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
          if (channel.name_.equals(settings_.autofocusChannelName_)  ) {
              Log.log("Autofocus channel index: " + index,true);
             return index;
          }
          index++;
       }
       Log.log("Warning: couldn't find autofocus channel index",true);
       return index;
   }
   
   public int getMaxSliceIndex() {
      return maxSliceIndex_;
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
            events_.clear();
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
    * Called by image sink at the end of writing images of each time point
    * or when image sink is finishing, either due to an abort or the end
    * of the final timepoint
    */
   public void imagesAtTimepointFinishedWriting() {
      tpImagesFinishedWritingLatch_.countDown();
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
               zTop_ = getZTopCoordinate();               
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

                  for (int positionIndex = 0; positionIndex < positions_.size(); positionIndex++) {
                     //add events for all slices/channels at this position
                     XYStagePosition position = positions_.get(positionIndex);

                     int sliceIndex = -1;
                     while (true) {
                        if (eventGenerator_.isShutdown()) { // check for aborts
                           throw new InterruptedException();
                        }
                        sliceIndex++;
                        double zPos = zTop_ + sliceIndex * zStep_;
                        if ((spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D || spaceMode_ == FixedAreaAcquisitionSettings.NO_SPACE)
                                && sliceIndex > 0) {
                           break; //2D regions only have 1 slice
                        }
                        
                        if (isImagingVolumeUndefinedAtPosition(position)) {
                           Log.log("Surface undefined at position " + position.getName());
                           break;
                        }

                        if (isZBelowImagingVolume(position, zPos) || (zStageHasLimits_ && zPos > zStageUpperLimit_)) {
                           //position is below z stack or limit of focus device, z stack finished
                           break;
                        }
                        //3D region
                        if (isZAboveImagingVolume(position, zPos) || (zStageHasLimits_ && zPos < zStageLowerLimit_)) {
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
                           //keep track of biggest slice index
                           maxSliceIndex_ = Math.max(maxSliceIndex_, event.sliceIndex_);
                           events_.put(event); //event generator will block if event queue is full
                        }
                     } //slice loop finish
                  } //position loop finished

                  if (timeIndex == (settings_.timeEnabled_ ? settings_.numTimePoints_ : 1) - 1) {
                     //acquisition now finished, add event with null acq field so engine will mark acquisition as finished                    
                     events_.put(AcquisitionEvent.createAcquisitionFinishedEvent(FixedAreaAcquisition.this));
                  } else {
                     events_.put(AcquisitionEvent.createTimepointFinishedEvent(FixedAreaAcquisition.this));
                  }

                  //wait for final image of timepoint to be written before beginning end of timepoint stuff
                  if (eventGenerator_.isShutdown()) {
                     throw new InterruptedException();
                  }
                  //three ways to get past this barrier:
                  //1) interuption by an abort request will throw an interrupted exception and cause this thread to return
                  //2) image sink will get a timepointFinished signal and call timepointImagesFinishedWriting
                  //3) image sink will get an acquisitionFinsihed signal and call allImagesFinishedWriting
                  //in the unlikely scenario that shudown is called by abort between these two calls, the imagesink should be able
                  //to finish writing images as expected
                  tpImagesFinishedWritingLatch_.await();
                  //timepoint images finshed writing, so rest the latch
                  tpImagesFinishedWritingLatch_ = new CountDownLatch(1);

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

   private void eventGeneratorShutdown() {
      eventGenerator_.shutdown();
      waitForNextTPSerivice_.shutdown();
      if (autofocus_ != null) {
          autofocus_.close();
      }
   }

   private boolean isImagingVolumeUndefinedAtPosition(XYStagePosition position) {
       if (spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return !settings_.fixedSurface_.isSurfaceDefinedAtPosition(position);
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return !settings_.topSurface_.isSurfaceDefinedAtPosition(position) &&
                 !settings_.bottomSurface_.isSurfaceDefinedAtPosition(position);
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
   private boolean isZAboveImagingVolume(XYStagePosition position, double zPos) throws InterruptedException {
      if (spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return settings_.fixedSurface_.isPositionCompletelyAboveSurface(position, settings_.fixedSurface_, zPos + settings_.distanceAboveFixedSurface_);
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings_.topSurface_.isPositionCompletelyAboveSurface(position, settings_.topSurface_, zPos + settings_.distanceAboveTopSurface_);
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return zPos < settings_.zStart_;
      } else {
         //no zStack
         return zPos < zTop_;
      }
   }

   private boolean isZBelowImagingVolume(XYStagePosition position, double zPos) throws InterruptedException {
      if (spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return settings_.fixedSurface_.isPositionCompletelyBelowSurface(position,settings_.fixedSurface_, zPos - settings_.distanceBelowFixedSurface_);
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings_.bottomSurface_.isPositionCompletelyBelowSurface(position,settings_.bottomSurface_, zPos - settings_.distanceBelowBottomSurface_);
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return zPos > settings_.zEnd_;
      } else {
         //no zStack
         return zPos > zTop_;
      }
   }

   private double getZTopCoordinate() {
      if (spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {          
         Point3d[] interpPoints = settings_.fixedSurface_.getPoints();
         if (towardsSampleIsPositive_ ) {
             double top = interpPoints[0].z - settings_.distanceAboveFixedSurface_;
            return zStageHasLimits_ ? Math.max(zStageLowerLimit_, top) : top;
         } else {            
            double top = interpPoints[interpPoints.length - 1].z + settings_.distanceAboveFixedSurface_;
            return zStageHasLimits_ ? Math.max(zStageUpperLimit_, top) : top;
         } 
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         if (towardsSampleIsPositive_) {
            Point3d[] interpPoints = settings_.topSurface_.getPoints();
            double top = interpPoints[0].z - settings_.distanceAboveTopSurface_;
            return zStageHasLimits_ ? Math.max(zStageLowerLimit_, top) : top;
         } else  {
            Point3d[] interpPoints = settings_.topSurface_.getPoints();
            double top = interpPoints[interpPoints.length - 1].z + settings_.distanceAboveTopSurface_;
            return zStageHasLimits_ ? Math.max(zStageLowerLimit_, top) : top;
         }  
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return settings_.zStart_;
      } else {
         try {
            //region2D or no region
            return Magellan.getCore().getPosition(zStage_);
         } catch (Exception ex) {
            Log.log("couldn't get z position",true);
            throw new RuntimeException();
         }
      }
   }

   @Override
   public double getZCoordinateOfSlice(int sliceIndex) {
      return zTop_ + sliceIndex  * zStep_;
   }

   @Override
   public int getSliceIndexFromZCoordinate(double z) {
      return (int) Math.round((z - zTop_) / zStep_);
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
         return (int) Math.ceil((Math.abs(top - bottom)+ settings_.distanceAboveFixedSurface_ + settings_.distanceBelowFixedSurface_) / zStep_) ;
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
