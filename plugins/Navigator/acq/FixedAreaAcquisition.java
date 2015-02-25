/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import coordinates.XYStagePosition;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.Point3d;

/**
 *
 * @author Henry
 */
public class FixedAreaAcquisition extends Acquisition {

   private static final int ACQUISITION_EVENT_BUFFER_SIZE = 100;
   private volatile boolean paused_ = false;
   private FixedAreaAcquisitionSettings settings_;
   private int numTimePoints_;
   private Thread eventGeneratingThread_;
   private ArrayList<XYStagePosition> positions_;
   private long nextTimePointStartTime_ms_;
   private CustomAcqEngine eng_;
   private ParallelAcquisitionGroup acqGroup_;
   private AtomicInteger readyForTP_ = new AtomicInteger(-1);
   
   /**
    * Acquisition with fixed XY positions (although they can potentially all be
    * translated between time points Supports time points Z stacks that can
    * change at positions between time points
    *
    * Acquisition engine manages a thread that reads events, fixed area
    * acquisition has another thread that generates events
    */
   public FixedAreaAcquisition(FixedAreaAcquisitionSettings settings, MultipleAcquisitionManager multiAcqManager,
           CustomAcqEngine eng, ParallelAcquisitionGroup acqGroup){
      super(settings.zStep_);
      eng_ = eng;
      acqGroup_ = acqGroup;
      settings_ = settings;
      readSettings();
      //get positions to be imaged
      //z slices to be dynamically calculated at the start of each time point?
      try {
         storeXYPositions();
      } catch (Exception e) {
         ReportingUtils.showError("Problem with Acquisition's XY positions. Check acquisition settings");
         throw new RuntimeException();
      }
      initialize(settings.dir_, settings.name_);
      createEventGenerator();
   }
   
   public FixedAreaAcquisitionSettings getSettings() {
      return settings_;
   }
   
   private void readSettings() {
      numTimePoints_ = settings_.timeEnabled_ ? settings_.numTimePoints_ : 1;
   }

   /**
    * abort acquisition. Block until successfully finished
    */
   public void abort() {
      if (finished_) {
         //acq already aborted
         return;
      }
      eventGeneratingThread_.interrupt();
      try {
         eventGeneratingThread_.join();
      } catch (InterruptedException ex) {
         //shouldn't happen
         throw new RuntimeException("Abort request interrupted");
      }
      eventGeneratingThread_ = null;
      engineOutputQueue_.clear();
      //signal image sink that it is done
      engineOutputQueue_.add(new TaggedImage(null, null));
      //wait for image sink to drain
      imageSink_.waitToDie();
      //when image sink dies it will call finish
      acqGroup_.acqAborted(this);
   }

   public boolean isPaused() {
      return paused_;
   }
   
   public double getTimeInterval_ms() {
      return settings_.timePointInterval_ * (settings_.timeIntervalUnit_ == 1 ? 1000 : (settings_.timeIntervalUnit_ == 2 ? 60000 : 1));
   }
   
   public int getNumRows() {
      int maxIndex = 0;
      for (XYStagePosition p : positions_) {
         maxIndex = Math.max(maxIndex, p.getGridRow());
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
   
   private void storeXYPositions() {
      //get XY positions
      if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         positions_ = settings_.fixedSurface_.getXYPositions();
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         //TODO
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         positions_ = settings_.footprint_.getXYPositions();
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D) {
         positions_ = settings_.footprint_.getXYPositions();
      } else {
         //no space mode, use current stage positon
         positions_ = new ArrayList<XYStagePosition>();
         try {
            positions_.add(new XYStagePosition(MMStudio.getInstance().getCore().getXYStagePosition(MMStudio.getInstance().getCore().getXYStageDevice()),
                    0,0,0,0,null));
         } catch (Exception ex) {
            ReportingUtils.showError("Couldn't get XY stage position");
         }
      }
   }

   public long getNextWakeTime_ms() {
      return nextTimePointStartTime_ms_;
   }
    
   public int readyForNextTimePoint() {
       return readyForTP_.getAndIncrement() + 1;
   }

   private void createEventGenerator() {
      eventGeneratingThread_ = new Thread(new Runnable() {

         @Override
         public void run() {
            nextTimePointStartTime_ms_ = 0;
            for (int timeIndex = 0; timeIndex < numTimePoints_; timeIndex++) {               
               //wait enough time to pass to start new time point
               while (System.currentTimeMillis() < nextTimePointStartTime_ms_  || 
                       timeIndex > readyForTP_.get()) {
                  try {
                     Thread.sleep(5);
                  } catch (InterruptedException ex) {
                     //thread has been interrupted due to abort request, return
                     return; 
                  }
               }
               //set the next time point start time
               double interval_ms = settings_.timePointInterval_ * (settings_.timeIntervalUnit_ == 1 ? 1000 : (settings_.timeIntervalUnit_ == 2 ? 60000 : 1));
               nextTimePointStartTime_ms_ = (long) (System.currentTimeMillis() + interval_ms);

               for (int positionIndex = 0; positionIndex < positions_.size(); positionIndex++) {
                  //add events for all slices/channels at this position
                  XYStagePosition position = positions_.get(positionIndex);

                  int channelIndex = 0; //TODO: channels

                  //TODO: check signs for all of these
                  //get highest possible z position to image, which is slice index 0
                  double zTop = getZTopCoordinate();
                  int sliceIndex = -1;
                  while (true) {
                     //if buffer is full of events waiting to execute, wait
                     while (events_.size() >= ACQUISITION_EVENT_BUFFER_SIZE) {
                        try {
                           Thread.sleep(5);
                        } catch (InterruptedException ex) {
                           //thread has been interrupted due to abort request, return;
                           return;
                        }
                     }

                     sliceIndex++;
                     double zPos = zTop + sliceIndex * zStep_;

                     if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D) {
                        //2D region
                        if (sliceIndex > 0) {
                           break;
                        }
                     } else {
                        //3D region
                        if (isZAboveImagingVolume(position, zPos)) {
                           continue; //position is above imaging volume
                        }
                        if (isZBelowImagingVolume(position, zPos)) {
                           //position is below z stack, z stack finished
                           break;
                        }
                     }

                     AcquisitionEvent event = new AcquisitionEvent(FixedAreaAcquisition.this, timeIndex, channelIndex, sliceIndex,
                             positionIndex, zPos, position.getCenter().x, position.getCenter().y);
                     if (Thread.interrupted()) {
                        //Acquisition has been aborted, clear pending events and return
                        events_.clear();
                        return;
                     }
                     addEvent(event);
                  }
               }
               
               if (timeIndex == numTimePoints_ - 1) {
                  //acquisition now finished, add event with null ac w field so engine will mark acquisition as finished
                  events_.add(AcquisitionEvent.createAcquisitionFinishedEvent(FixedAreaAcquisition.this));
               }

               //wait for final image of timepoint to be written before beginning end of timepoint stuff
               while (!( eng_.isIdle() && events_.isEmpty() && imageSink_.isIdle())) {
                  try {
                     Thread.sleep(5);
                  } catch (InterruptedException ex) {                   
                     return; //thread has been interrupted due to abort request, return;
                  }
               }
               //do end of timepoint stuff (autofocus, swap to another acq, etc)
              acqGroup_.finishedTimePoint(FixedAreaAcquisition.this);
               endOfTimePoint(timeIndex);
            }
         }
      }, "Fixed Area Acquisition Event generating thread");
      eventGeneratingThread_.start();
   }

   private void endOfTimePoint(int timeIndex) {
      //TODO: run autofocus
   }

   /**
    * This function and the one below determine which slices will be collected
    * for a given position
    *
    * @param position
    * @param zPos
    * @return
    */
   private boolean isZAboveImagingVolume(XYStagePosition position, double zPos) {
      if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return settings_.fixedSurface_.isPositionCompletelyAboveSurface(position, settings_.fixedSurface_.getCurrentInterpolation(),
                 zPos, settings_.fixedSurface_.getZPadding());
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
//         return settings_.surface_.isPositionCompletelyAboveSurface(position, settings_.surface_.getCurrentInterpolation(),
//                 zPos, settings_.surface_.getZPadding());
         //TODO: fix
         return false;
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return zPos < settings_.zStart_;
      } else {
         //no zStack
         throw new RuntimeException(); //TODO: something better
      }
   }

   private boolean isZBelowImagingVolume(XYStagePosition position, double zPos) {
      if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return settings_.fixedSurface_.isPositionCompletelyBelowSurface(position,
                 settings_.fixedSurface_.getCurrentInterpolation(), zPos, settings_.distanceBelowSurface_);
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings_.bottomSurface_.isPositionCompletelyBelowSurface(position,
                 settings_.bottomSurface_.getCurrentInterpolation(), zPos, settings_.bottomSurface_.getZPadding());
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return zPos > settings_.zEnd_;
      } else {
         //no zStack
         throw new RuntimeException(); //TODO: something better
      }
   }

   private double getZTopCoordinate() {
      if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         Point3d[] interpPoints = settings_.fixedSurface_.getPoints();
         return interpPoints[0].z - settings_.fixedSurface_.getZPadding();
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         //TODO
         return 0;
//         Point3d[] interpPoints = settings_.getPoints();
//         return interpPoints[0].z - settings_.surface_.getZPadding();
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK) {
         return settings_.zStart_;
      } else {
         //region2D or no region
         try {
            return core_.getPosition(zStage_);
         } catch (Exception ex) {
            ReportingUtils.showError("Couldn't read z position from core");
            throw new RuntimeException();
         }
      } 
   }  

   //TODO account for autofocusing via frame on both of these
   @Override
   public double getZCoordinateOfSlice(int displaySliceIndex, int displayFrameIndex) {
      return getZTopCoordinate() + (displaySliceIndex - 1) * zStep_;
   }

   @Override
   public int getDisplaySliceIndexFromZCoordinate(double z, int displayFrameIndex) {
      return (int) Math.round((z - getZTopCoordinate()) / zStep_) + 1;
   }

   @Override
   protected JSONArray createInitialPositionList() {
      JSONArray pList = new JSONArray();
      for (XYStagePosition xyPos : positions_) {
         pList.put(xyPos.getMMPosition());
      }
      return pList;
   }
}
