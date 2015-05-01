/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import autofocus.CrossCorrelationAutofocus;
import coordinates.XYStagePosition;
import ij.IJ;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import bidc.CoreCommunicator;
import channels.ChannelSetting;
import misc.GlobalSettings;
import misc.Log;
import org.json.JSONArray;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.Point3d;

/**
 *
 * @author Henry
 */
public class FixedAreaAcquisition extends Acquisition {

    private static final int EVENT_QUEUE_CAP = 10; // so that surfaces can be dynamically changed during acq
    
   private final int spaceMode_;
   private FixedAreaAcquisitionSettings settings_;
   private List<XYStagePosition> positions_;
   private long nextTimePointStartTime_ms_;
   private ParallelAcquisitionGroup acqGroup_;
   //barrier to wait for event generation at successive time points
   //signals come from 1) event genreating thread 2) Parallel acq group
   private CountDownLatch startNextTPLatch_ = new CountDownLatch(1);
   //barrier to wait for all images to be written before starting nex time point stuff
   //signals come from 1) event generating thread 2) tagged iamge sink
   private CountDownLatch tpImagesFinishedWritingLatch_ = new CountDownLatch(1);
   //executor service to wait for next execution
   private ScheduledExecutorService waitForNextTPSerivice_ = Executors.newScheduledThreadPool(1);
   private ExecutorService eventGenerator_;
   private CrossCorrelationAutofocus autofocus_;
   private int maxSliceIndex_ = 0;
   private  double zTop_;

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
      eventGenerator_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, settings_.name_ + ": Event generator");
         }
      });
      setupXYPositions();
      initialize(settings.dir_, settings.name_, settings.tileOverlap_);
      createEventGenerator();
      createAutofocus();
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
            int fullTileWidth = (int) CoreCommunicator.getInstance().getImageWidth();
            int fullTileHeight = (int) CoreCommunicator.getInstance().getImageHeight();
            int tileWidthMinusOverlap = fullTileWidth - this.getOverlapX();
            int tileHeightMinusOverlap = fullTileHeight - this.getOverlapY();
            positions_.add(new XYStagePosition(MMStudio.getInstance().getCore().getXYStagePosition(xyStage_),
                    tileWidthMinusOverlap, tileHeightMinusOverlap, fullTileWidth, fullTileHeight, 0, 0,
                    MMStudio.getInstance().getCore().getCurrentPixelSizeConfig()));
         }
      } catch (Exception e) {
         ReportingUtils.showError("Problem with Acquisition's XY positions. Check acquisition settings");
         throw new RuntimeException();
      }
   }
   
   private void createAutofocus() {
      if (settings_.autofocusEnabled_) {
         //convert channel name to channel index
         int cIndex = getAutofocusChannelIndex();     
         autofocus_ = new CrossCorrelationAutofocus(this,cIndex, settings_.autofocusMaxDisplacemnet_um_, settings_.autoFocusZDevice_);
      }
   }

   public int getAutofocusChannelIndex() {
       int index = 0;
       for (ChannelSetting channel : channels_) {
          if (channel.name_.equals(settings_.autofocusChannelName_)  ) {
             return index;
          }
          index++;
       }
       Log.log("Warning: couldn't find autofocus channel index");
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

   public int getNumRows() {
      int maxIndex = 0;
      synchronized (positions_) {
         for (XYStagePosition p : positions_) {
            maxIndex = Math.max(maxIndex, p.getGridRow());
         }
      }
      return maxIndex + 1;
   }

   public int getNumColumns() {
      int maxIndex = 0;
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
       if (finished_) {
         //acq already aborted
         return;
      }
      if (this.isPaused()) {
         this.togglePaused();
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
         events_.put(AcquisitionEvent.createAcquisitionFinishedEvent(this));
         //make sure engine doesnt get stuck
         
      } catch (InterruptedException ex) {
         ReportingUtils.showError("Unexpected interrupted exception while trying to abort"); //shouldnt happen
      }

        
      //singal aborted will wait for the image sink to die so this function doesnt return until abort complete
      acqGroup_.signalAborted(this);
      
      //make sure parallel group doesnt hang waiting to signal this acq to start next TP
      startNextTPLatch_.countDown();
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
         long timeUntilNext = nextTimePointStartTime_ms_ - System.currentTimeMillis();
         if (timeUntilNext > 0) {
            //wait for enough time to pass and parallel group to signal ready
            ScheduledFuture future = waitForNextTPSerivice_.schedule(new Runnable() {

               @Override
               public void run() {
                  try {
                     startNextTPLatch_.await();
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
            startNextTPLatch_.await();
            startNextTPLatch_ = new CountDownLatch(1);
         }

   }

   private void createEventGenerator() {
      eventGenerator_.submit(new Runnable() {
         //check interupt status before any blocking call is entered
         @Override
         public void run() {
            try {
                     //TODO: check signs for all of these
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
                     events_.put(AcquisitionEvent.createAutofocusEvent(settings_.autoFocusZDevice_, autofocus_.getAutofocusPosition()));
                  } else if (autofocus_ != null && timeIndex <= 1 && settings_.setInitialAutofocusPosition_) {
                     events_.put(AcquisitionEvent.createAutofocusEvent(settings_.autoFocusZDevice_, settings_.initialAutofocusPosition_));
                  }
                  //set the next time point start time
                  double interval_ms = settings_.timePointInterval_ * (settings_.timeIntervalUnit_ == 1 ? 1000 : (settings_.timeIntervalUnit_ == 2 ? 60000 : 1));
                  nextTimePointStartTime_ms_ = (long) (System.currentTimeMillis() + interval_ms);

                  for (int positionIndex = 0; positionIndex < positions_.size(); positionIndex++) {
                     //add events for all slices/channels at this position
                     XYStagePosition position = positions_.get(positionIndex);

                     int channelIndex = 0; //TODO: channels

                     int sliceIndex = -1;
                     while (true) {
                        sliceIndex++;
                        double zPos = zTop_ + sliceIndex * zStep_;
                        if (spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D || spaceMode_ == FixedAreaAcquisitionSettings.NO_SPACE) {
                           //2D region
                           if (sliceIndex > 0) {
                              break;
                           }
                        } else {
                           //3D region
                           if (isZAboveImagingVolume(position, zPos) || (zStageHasLimits_ && zPos < zStageLowerLimit_)) {
                              continue; //position is above imaging volume or range of focus device
                           }
                           if (isZBelowImagingVolume(position, zPos) || (zStageHasLimits_ && zPos > zStageUpperLimit_)) {
                              //position is below z stack or limit of focus device, z stack finished
                              break;
                           }
                        }
                        AcquisitionEvent event = new AcquisitionEvent(FixedAreaAcquisition.this, timeIndex, channelIndex, sliceIndex,
                                positionIndex, zPos, position, settings_.covariantPairings_);                        
                        if (eventGenerator_.isShutdown()) {
                           throw new InterruptedException();
                        }
                        //keep track of biggest slice index
                        maxSliceIndex_ = Math.max(maxSliceIndex_, event.sliceIndex_);
                        events_.put(event); //event generator will block if event queueu is full
                     }
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
                         Log.log(FixedAreaAcquisition.this.getName() + " Running autofocus");
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
   //TODO: check sign of Z
   private double getZTopCoordinate() {
      if (spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         Point3d[] interpPoints = settings_.fixedSurface_.getPoints();
         double top = interpPoints[0].z - settings_.distanceAboveFixedSurface_;
         return zStageHasLimits_ ? Math.max(zStageLowerLimit_, top) : top;
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         Point3d[] interpPoints = settings_.topSurface_.getPoints();        
         double top = interpPoints[0].z - settings_.distanceAboveTopSurface_;
         //TODO: check sign
         return zStageHasLimits_ ? Math.max(zStageLowerLimit_, top) : top;
      } else if (spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return settings_.zStart_;
      } else {
         try {
            //region2D or no region
            return core_.getPosition(zStage_);
         } catch (Exception ex) {
            Log.log("couldn't get z position");
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

}
