/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import coordinates.XYStagePosition;
import java.util.ArrayList;
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
   private Thread eventGeneratingThread_;
   private ArrayList<XYStagePosition> positions_;
   private long nextTimePointStartTime_ms_;

   /**
    * Acquisition with fixed XY positions (although they can potentially all be
    * translated between time points Supports time points Z stacks that can
    * change at positions between time points
    *
    * Acquisition engine manages a thread that reads events, fixed area
    * acquisition has another thread that generates events
    */
   public FixedAreaAcquisition(FixedAreaAcquisitionSettings settings) {
      super(settings.zStep_);
      settings_ = settings;
      //get positions to be imaged
      //z slices to be dynamically calculated at the start of each time point?
      storeXYPositions();
      initialize(settings.dir_, settings.name_);
      createEventGenerator();
   }

   /**
    * abort acquisition. Block until successfully finished
    */
   public void abort() {
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
   }

   public boolean isPaused() {
      return paused_;
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
         //TODO
      }
   }

   public long getNextWakeTime_ms() {
      return nextTimePointStartTime_ms_;
   }

   private void createEventGenerator() {
      eventGeneratingThread_ = new Thread(new Runnable() {

         @Override
         public void run() {
            nextTimePointStartTime_ms_ = 0;
            for (int timeIndex = 0; timeIndex < settings_.numTimePoints_; timeIndex++) {
               String imageLabel = null;
               //wait enough time to pass to start new time point
               while (System.currentTimeMillis() < nextTimePointStartTime_ms_) {
                  try {
                     Thread.sleep(5);
                  } catch (InterruptedException ex) {
                     return; //thread has been interrupted due to abort request, return;
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
                     imageLabel = MDUtils.generateLabel(channelIndex, sliceIndex, timeIndex, positionIndex);
                  }
               }

               while (imageSink_.getLastImageLabel() != null && !imageSink_.getLastImageLabel().equals(imageLabel)) {
                  //wait for final image of timepoint to be written before beginning end of timepoint stuff
                  try {
                     Thread.sleep(5);
                  } catch (InterruptedException ex) {
                     //thread has been interrupted due to abort request, return;
                     return;
                  }
               }
               //do end of timepoint stuff
               endOfTimePoint(timeIndex);
            }
            //acquisition now finished, add event with null ac w field so engine will mark acquisition as finished
            events_.add(new AcquisitionEvent(null, 0, 0, 0, 0, 0, 0, 0));
         }
      });
      eventGeneratingThread_.start();
   }

   private void endOfTimePoint(int timeIndex) {
      //run autofocus
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
      } else if (settings_.spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D) {
         try {
            return core_.getPosition(zStage_);
         } catch (Exception ex) {
            ReportingUtils.showError("Couldn't read z position from core");
            throw new RuntimeException();
         }
      } else {
         throw new RuntimeException();
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
