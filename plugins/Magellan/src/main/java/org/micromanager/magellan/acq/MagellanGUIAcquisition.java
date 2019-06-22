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

import java.util.ArrayList;
import java.util.List;
import java.awt.geom.Point2D;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.micromanager.magellan.coordinates.MagellanAffineUtils;
import org.micromanager.magellan.coordinates.XYStagePosition;
import org.micromanager.magellan.json.JSONArray;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.Log;
import org.micromanager.magellan.surfacesandregions.Point3d;

/**
 *
 * @author Henry
 */
public class MagellanGUIAcquisition extends Acquisition {

   final private MagellanGUIAcquisitionSettings settings_;
   //executor service to wait for next execution
   private final boolean towardsSampleIsPositive_;
   private long lastTPEventStartTime_ = -1;
   private List<XYStagePosition> positions_;

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
   public MagellanGUIAcquisition(MagellanGUIAcquisitionSettings settings) {
      super(settings.zStep_, settings.channels_);
      settings_ = settings;
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
      createXYPositions();
      initialize(settings.dir_, settings.name_, settings.tileOverlap_);

      //Submit a generating stream to get this acquisition going
      Stream<AcquisitionEvent> acqEventStream = magellanGUIAcqEventStream();
      submitEventStream(acqEventStream);
   }

   /**
    *
    * @return true if finished normally, false if aborted
    */
   public boolean waitForCompletion() {
      while (!finished_) {
         try {
            Thread.sleep(10);
         } catch (InterruptedException ex) {
            //Doesnt matter, should still finish eventually if everything works right
         }
      }
      return !aborted_;
   }

   /**
    * Build the event stream according to the provided acquisition settings
    *
    * @return
    */
   private Stream<AcquisitionEvent> magellanGUIAcqEventStream() {
      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
              = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      //Define where slice index 0 will be
      zOrigin_ = getZTopCoordinate();
      boolean surfaceGuided2D = settings_.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D && settings_.useCollectionPlane_;

      acqFunctions.add(timelapse());
      acqFunctions.add(positions(IntStream.range(0, positions_.size()).toArray(), positions_));
      if (settings_.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D) {
         acqFunctions.add(channels(settings_.channels_));
      } else if (surfaceGuided2D) {
         acqFunctions.add(surfaceGuided2D());
         acqFunctions.add(channels(settings_.channels_));
      } else if (settings_.channelsAtEverySlice_) {
         acqFunctions.add(MagellanZStack());
         acqFunctions.add(channels(settings_.channels_));
      } else {
         acqFunctions.add(channels(settings_.channels_));
         acqFunctions.add(MagellanZStack());
      }
      Stream<AcquisitionEvent> eventStream = makeEventStream(acqFunctions);
      eventStream = eventStream.map(monitorSliceIndices());
      //create event to signal finished
      eventStream = Stream.concat(eventStream, Stream.of(AcquisitionEvent.createAcquisitionFinishedEvent(this)));
      return eventStream;
   }

   @Override
   public int getMinSliceIndex() {
      return minSliceIndex_;
   }

   @Override
   public int getMaxSliceIndex() {
      return maxSliceIndex_;
   }

   public double getTimeInterval_ms() {
      return settings_.timePointInterval_ * (settings_.timeIntervalUnit_ == 1 ? 1000 : (settings_.timeIntervalUnit_ == 2 ? 60000 : 1));
   }

   private Function<AcquisitionEvent, AcquisitionEvent> monitorSliceIndices() {
      return (AcquisitionEvent event) -> {
         maxSliceIndex_ = Math.max(maxSliceIndex_, event.sliceIndex_);
         minSliceIndex_ = Math.min(minSliceIndex_, event.sliceIndex_);
         return event;
      };
   }

   private Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse() {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            int frameIndex_ = 0;

            @Override
            public boolean hasNext() {
               if (frameIndex_ == 0) {
                  return true;
               }
               if (settings_.timeEnabled_ && frameIndex_ < settings_.numTimePoints_) {
                  return true;
               }
               return false;
            }

            @Override
            public AcquisitionEvent next() {
               double interval_ms = settings_.timePointInterval_ * (settings_.timeIntervalUnit_ == 1 ? 1000 : (settings_.timeIntervalUnit_ == 2 ? 60000 : 1));
               AcquisitionEvent timePointEvent = event.copy();

               timePointEvent.miniumumStartTime_ = lastTPEventStartTime_ + (long) interval_ms;
               timePointEvent.timeIndex_ = frameIndex_;
               if (frameIndex_ == 0) {
                  lastTPEventStartTime_ = System.currentTimeMillis();
               } else {
                  lastTPEventStartTime_ = timePointEvent.miniumumStartTime_;
               }
               frameIndex_++;

               return timePointEvent;
            }
         };
      };
   }

   /**
    * Fancy Z stack Magellan style
    */
   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> MagellanZStack() {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            private int sliceIndex_ = (int) Math.round((getZTopCoordinate() - zOrigin_) / zStep_);

            @Override
            public boolean hasNext() {
               double zPos = zOrigin_ + sliceIndex_ * zStep_;
               boolean undefined = isImagingVolumeUndefinedAtPosition(settings_.spaceMode_, settings_, event.xyPosition_);
               //position is below z stack or limit of focus device, z stack finished
               boolean below = isZBelowImagingVolume(settings_.spaceMode_, settings_, event.xyPosition_, zPos, zOrigin_)
                       || (zStageHasLimits_ && zPos > zStageUpperLimit_);
               return (undefined || below) ? false : true;
            }

            @Override
            public AcquisitionEvent next() {
               double zPos = zOrigin_ + sliceIndex_ * zStep_;
               while (isZAboveImagingVolume(settings_.spaceMode_, settings_, event.xyPosition_, zPos, zOrigin_) || (zStageHasLimits_ && zPos < zStageLowerLimit_)) {
                  sliceIndex_++;
                  zPos = zOrigin_ + sliceIndex_ * zStep_;
               }
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

   private Function<AcquisitionEvent, Iterator<AcquisitionEvent>> surfaceGuided2D() {
      return (AcquisitionEvent event) -> {
         //index all slcies as 0, even though they may nto be in the same plane
         double zPos;
         if (settings_.collectionPlane_.getCurentInterpolation().isInterpDefined(
                 event.xyPosition_.getCenter().x, event.xyPosition_.getCenter().y)) {
            zPos = settings_.collectionPlane_.getCurentInterpolation().getInterpolatedValue(
                    event.xyPosition_.getCenter().x, event.xyPosition_.getCenter().y);
         } else {
            zPos = settings_.collectionPlane_.getExtrapolatedValue(event.xyPosition_.getCenter().x, event.xyPosition_.getCenter().y);
         }
         event.zPosition_ += zPos;
         event.sliceIndex_ = 0; //Make these all 0 for the purposes of the display even though they may be in very differnet locations
         return Stream.of(event).iterator();
      };

   }

   public static boolean isImagingVolumeUndefinedAtPosition(int spaceMode, MagellanGUIAcquisitionSettings settings, XYStagePosition position) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return !settings.footprint_.isDefinedAtPosition(position);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return !settings.topSurface_.isDefinedAtPosition(position)
                 && !settings.bottomSurface_.isDefinedAtPosition(position);
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
   public static boolean isZAboveImagingVolume(int spaceMode, MagellanGUIAcquisitionSettings settings, XYStagePosition position, double zPos, double zOrigin) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         boolean extrapolate = settings.fixedSurface_ != settings.footprint_;
         //extrapolate only if different surface used for XY positions than footprint
         return settings.fixedSurface_.isPositionCompletelyAboveSurface(position, settings.fixedSurface_, zPos + settings.distanceAboveFixedSurface_, extrapolate);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.topSurface_.isPositionCompletelyAboveSurface(position, settings.topSurface_, zPos + settings.distanceAboveTopSurface_, false);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         return zPos < settings.zStart_;
      } else {
         //no zStack
         return zPos < zOrigin;
      }
   }

   public static boolean isZBelowImagingVolume(int spaceMode, MagellanGUIAcquisitionSettings settings, XYStagePosition position, double zPos, double zOrigin) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         boolean extrapolate = settings.fixedSurface_ != settings.footprint_;
         //extrapolate only if different surface used for XY positions than footprint
         return settings.fixedSurface_.isPositionCompletelyBelowSurface(position, settings.fixedSurface_, zPos - settings.distanceBelowFixedSurface_, extrapolate);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.bottomSurface_.isPositionCompletelyBelowSurface(position, settings.bottomSurface_, zPos - settings.distanceBelowBottomSurface_, false);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         return zPos > settings.zEnd_;
      } else {
         //no zStack
         return zPos > zOrigin;
      }
   }

   private double getZTopCoordinate() {
      return getZTopCoordinate(settings_.spaceMode_, settings_, towardsSampleIsPositive_, zStageHasLimits_, zStageLowerLimit_, zStageUpperLimit_, zStage_);
   }

   public static double getZTopCoordinate(int spaceMode, MagellanGUIAcquisitionSettings settings, boolean towardsSampleIsPositive,
           boolean zStageHasLimits, double zStageLowerLimit, double zStageUpperLimit, String zStage) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         Point3d[] interpPoints = settings.fixedSurface_.getPoints();
         if (towardsSampleIsPositive) {
            double top = interpPoints[0].z - settings.distanceAboveFixedSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         } else {
            double top = interpPoints[interpPoints.length - 1].z + settings.distanceAboveFixedSurface_;
            return zStageHasLimits ? Math.max(zStageUpperLimit, top) : top;
         }
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         if (towardsSampleIsPositive) {
            Point3d[] interpPoints = settings.topSurface_.getPoints();
            double top = interpPoints[0].z - settings.distanceAboveTopSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         } else {
            Point3d[] interpPoints = settings.topSurface_.getPoints();
            double top = interpPoints[interpPoints.length - 1].z + settings.distanceAboveTopSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         }
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
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

   //TODO: this could be generalized into a method to get metadata specific to any acwuisiton surface type
   public JSONArray getFixedSurfacePoints() {
      Point3d[] points = settings_.fixedSurface_.getPoints();
      JSONArray pointArray = new JSONArray();
      for (Point3d p : points) {
         pointArray.put(p.x + "_" + p.y + "_" + p.z);
      }
      return pointArray;
   }

   public int getSpaceMode() {
      return settings_.spaceMode_;
   }

   private void createXYPositions() {
      try {
         //get XY positions
         if (settings_.spaceMode_ == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
            positions_ = settings_.footprint_.getXYPositions(settings_.tileOverlap_);
         } else if (settings_.spaceMode_ == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
            positions_ = settings_.useTopOrBottomFootprint_ == MagellanGUIAcquisitionSettings.FOOTPRINT_FROM_TOP
                    ? settings_.topSurface_.getXYPositions(settings_.tileOverlap_) : settings_.bottomSurface_.getXYPositions(settings_.tileOverlap_);
         } else if (settings_.spaceMode_ == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
            positions_ = settings_.footprint_.getXYPositions(settings_.tileOverlap_);
         } else if (settings_.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D) {
            positions_ = settings_.footprint_.getXYPositions(settings_.tileOverlap_);
         } else {
             throw new RuntimeException("No space settings specified");
            //no space mode, use current stage positon
//            positions_ = new ArrayList<XYStagePosition>();
//            int fullTileWidth = (int) Magellan.getCore().getImageWidth();
//            int fullTileHeight = (int) Magellan.getCore().getImageHeight();
//            int tileWidthMinusOverlap = fullTileWidth - this.getOverlapX();
//            int tileHeightMinusOverlap = fullTileHeight - this.getOverlapY();
//            Point2D.Double currentStagePos = Magellan.getCore().getXYStagePosition(xyStage_);
//            positions_.add(new XYStagePosition(currentStagePos, tileWidthMinusOverlap, tileHeightMinusOverlap, fullTileWidth, fullTileHeight, 0, 0,
//                    MagellanAffineUtils.getAffineTransform(Magellan.getCore().getCurrentPixelSizeConfig(),
//                            currentStagePos.x, currentStagePos.y)));
         }
      } catch (Exception e) {
         Log.log("Problem with Acquisition's XY positions. Check acquisition settings");
         throw new RuntimeException();
      }
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
