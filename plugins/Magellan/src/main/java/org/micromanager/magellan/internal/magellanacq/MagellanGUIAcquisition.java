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
package org.micromanager.magellan.internal.magellanacq;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Stream;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.*;
import org.micromanager.acqj.internal.acqengj.AcquisitionEventIterator;
import org.micromanager.acqj.api.xystage.XYStagePosition;
import org.micromanager.acqj.api.AcqEventModules;
import org.micromanager.acqj.api.channels.ChannelSetting;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.magellan.internal.channels.ChannelGroupSettings;
import org.micromanager.magellan.internal.channels.SingleChannelSetting;
import org.micromanager.magellan.internal.gui.GUI;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.Point3d;

/**
 *
 * @author Henry
 */
public class MagellanGUIAcquisition extends Acquisition implements MagellanAcquisition {

   private double zOrigin_, zStep_;
   private int minSliceIndex_, maxSliceIndex_;
   private List<XYStagePosition> positions_;
   private MagellanGenericAcquisitionSettings settings_;
   private volatile boolean started_ = false;

   /**
    * Acquisition with fixed XY positions (although they can potentially all be
    * translated between time points Supports time points Z stacks that can
    * change at positions between time points
    *
    * Acquisition engine manages a thread that reads events, fixed area
    * acquisition has another thread that generates events
    *
    * @param settings
    * @throws java.lang.Exception
    */
   public MagellanGUIAcquisition(MagellanGUIAcquisitionSettings settings, DataSink sink) {
      super(sink);
      settings_ = settings;
      int overlapX = (int) (Magellan.getCore().getImageWidth() * GUI.getTileOverlap() / 100);
      int overlapY = (int) (Magellan.getCore().getImageHeight() * GUI.getTileOverlap() / 100);
      initialize(overlapX, overlapY);
   }

   public void start() {
         super.start();
         if (finished_) {
            throw new RuntimeException("Cannot start acquistion since it has already been run");
         }
         Iterator<AcquisitionEvent> acqEventIterator = buildAcqEventGenerator();
         Engine.getInstance().submitEventIterator(acqEventIterator, this);
         Engine.getInstance().finishAcquisition(this);
         started_ = true;
   }

   @Override
   public void addToSummaryMetadata(JSONObject summaryMetadata) {
      MagellanMD.setExploreAcq(summaryMetadata, false);
      MagellanMD.setSavingName(summaryMetadata, ((MagellanDataManager) dataSink_).getName());
      MagellanMD.setSavingName(summaryMetadata, ((MagellanDataManager) dataSink_).getDir());

      zStep_ = ((MagellanGUIAcquisitionSettings) settings_).zStep_;

      AcqEngMetadata.setZStepUm(summaryMetadata, zStep_);
      AcqEngMetadata.setZStepUm(summaryMetadata, zStep_);
      AcqEngMetadata.setIntervalMs(summaryMetadata, getTimeInterval_ms());
      createXYPositions();
   }

   @Override
   public void addToImageMetadata(JSONObject tags) {

      //add metadata specific to magellan acquisition
      AcqEngMetadata.setIntervalMs(tags, ((MagellanGUIAcquisition) this).getTimeInterval_ms());

      //add data about surface
      //right now this only works for fixed distance from the surface
      if (getSpaceMode() == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         //add metadata about surface
         MagellanMD.setSurfacePoints(tags, getFixedSurfacePoints());
      }
   }

   private Iterator<AcquisitionEvent> buildAcqEventGenerator() {
      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
              = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      //Define where slice index 0 will be
      zOrigin_ = getZTopCoordinate(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
              ((MagellanGUIAcquisitionSettings) settings_), zStageHasLimits_,
              zStageLowerLimit_, zStageUpperLimit_, zStage_);

      if (((MagellanGUIAcquisitionSettings) settings_).timeEnabled_) {
         acqFunctions.add(AcqEventModules.timelapse(((MagellanGUIAcquisitionSettings) settings_).numTimePoints_,
                 (int) (((MagellanGUIAcquisitionSettings) settings_).timePointInterval_
                 * (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 1
                         ? 1000 : (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 2 ? 60000 : 1)))));
      }
      if (positions_ != null) {
         acqFunctions.add(AcqEventModules.positions(positions_));
      }

      ArrayList<ChannelSetting> channels = new ArrayList<ChannelSetting>();
      if (getChannels() != null) {
         for (String name : getChannels().getChannelNames()) {
            SingleChannelSetting s = getChannels().getChannelSetting(name);
            if (s.use_) {
               channels.add(s);
            }
         }
      }

      if (((MagellanGUIAcquisitionSettings) settings_).spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D) {
         if (((MagellanGUIAcquisitionSettings) settings_).channels_.getNumChannels() != 0) {
            acqFunctions.add(AcqEventModules.channels(channels));
         }
      } else if (((MagellanGUIAcquisitionSettings) settings_).spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D_SURFACE_GUIDED) {
         acqFunctions.add(surfaceGuided2D());
         if (!channels.isEmpty()) {
            acqFunctions.add(AcqEventModules.channels(channels));
         }
      } else if (((MagellanGUIAcquisitionSettings) settings_).channelsAtEverySlice_) {
         acqFunctions.add(MagellanZStack());
         if (!channels.isEmpty()) {
            acqFunctions.add(AcqEventModules.channels(channels));
         }
      } else {
         if (!channels.isEmpty()) {
            acqFunctions.add(AcqEventModules.channels(channels));
         }
         acqFunctions.add(MagellanZStack());
      }
      AcquisitionEvent baseEvent = new AcquisitionEvent(this);
      baseEvent.setAxisPosition(MagellanMD.POSITION_AXIS, 0);
      return new AcquisitionEventIterator(baseEvent, acqFunctions, monitorSliceIndices());
   }

   public void waitForCompletion() {
      if (!started_) {
         //it was never successfully started
         return;
      }
      super.waitForCompletion();
   }

   @Override
   public String toString() {
      return settings_.toString();
   }

   public int getMinSliceIndex() {
      return minSliceIndex_;
   }

   public int getMaxSliceIndex() {
      return maxSliceIndex_;
   }

   public double getTimeInterval_ms() {
      return ((MagellanGUIAcquisitionSettings) settings_).timePointInterval_
              * (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 1
                      ? 1000 : (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 2 ? 60000 : 1));
   }

   private Function<AcquisitionEvent, AcquisitionEvent> monitorSliceIndices() {
      return (AcquisitionEvent event) -> {
         if (event.getZIndex() != null) {
            maxSliceIndex_ = Math.max(maxSliceIndex_, event.getZIndex());
            minSliceIndex_ = Math.min(minSliceIndex_, event.getZIndex());
         }
         return event;
      };
   }

   /**
    * F ancy Z stack Magellan style
    */
   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> MagellanZStack() {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            private int sliceIndex_ = (int) Math.round((getZTopCoordinate(
                    ((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                    ((MagellanGUIAcquisitionSettings) settings_),
                    zStageHasLimits_, zStageLowerLimit_, zStageUpperLimit_, zStage_) - zOrigin_) / zStep_);

            @Override
            public boolean hasNext() {
               double zPos = zOrigin_ + sliceIndex_ * zStep_;
               boolean undefined = isImagingVolumeUndefinedAtPosition(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                       ((MagellanGUIAcquisitionSettings) settings_), event.getDisplayPositionCorners());
               //position is below z stack or limit of focus device, z stack finished
               boolean below = isZBelowImagingVolume(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                       ((MagellanGUIAcquisitionSettings) settings_), event.getDisplayPositionCorners(), zPos, zOrigin_)
                       || (zStageHasLimits_ && zPos > zStageUpperLimit_);
               return (undefined || below) ? false : true;
            }

            @Override
            public AcquisitionEvent next() {
               double zPos = zOrigin_ + sliceIndex_ * zStep_;
               while (isZAboveImagingVolume(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                       ((MagellanGUIAcquisitionSettings) settings_),
                       event.getDisplayPositionCorners(), zPos, zOrigin_) || (zStageHasLimits_ && zPos < zStageLowerLimit_)) {
                  sliceIndex_++;
                  zPos = zOrigin_ + sliceIndex_ * zStep_;
               }
               AcquisitionEvent sliceEvent = event.copy();
               //Do plus equals here in case z positions have been modified by another function (e.g. channel specific focal offsets)

               sliceEvent.setZ(sliceIndex_, (sliceEvent.getZPosition() == null
                       ? 0 : sliceEvent.getZPosition()) + zPos);
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
         if (((MagellanGUIAcquisitionSettings) settings_).collectionPlane_ == null) {
            Log.log("Expected surface but didn't find one. Check acquisition settings");
            throw new RuntimeException("Expected surface but didn't find one. Check acquisition settings");
         }
         if (((MagellanGUIAcquisitionSettings) settings_).collectionPlane_.getCurentInterpolation().isInterpDefined(
                 event.getXPosition(), event.getYPosition())) {
            zPos = ((MagellanGUIAcquisitionSettings) settings_).collectionPlane_.getCurentInterpolation().getInterpolatedValue(
                    event.getXPosition(), event.getYPosition());
         } else {
            zPos = ((MagellanGUIAcquisitionSettings) settings_).collectionPlane_.getExtrapolatedValue(
                    event.getXPosition(), event.getYPosition());
         }
         event.setZ(0, event.getZPosition() + zPos);
         //Make z index all 0 for the purposes of the display even though they may be in very differnet locations
         return Stream.of(event).iterator();
      };

   }

   public static boolean isImagingVolumeUndefinedAtPosition(int spaceMode, MagellanGUIAcquisitionSettings settings,
           Point2D.Double[] displayPosCorners) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         return !settings.xyFootprint_.isDefinedAtPosition(displayPosCorners);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return !settings.topSurface_.isDefinedAtPosition(displayPosCorners)
                 && !settings.bottomSurface_.isDefinedAtPosition(displayPosCorners);
      }
      return false;
   }

   /**
    * This function and the one below determine which slices will be collected
    * for a given position
    *
    * @param zPos
    * @return
    */
   public static boolean isZAboveImagingVolume(int spaceMode, MagellanGUIAcquisitionSettings settings,
           Point2D.Double[] positionCorners, double zPos, double zOrigin) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         boolean extrapolate = settings.fixedSurface_ != settings.xyFootprint_;
         //extrapolate only if different surface used for XY positions than footprint
         return settings.fixedSurface_.isPositionCompletelyAboveSurface(positionCorners, settings.fixedSurface_, zPos + settings.distanceAboveFixedSurface_, extrapolate);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.topSurface_.isPositionCompletelyAboveSurface(positionCorners, settings.topSurface_, zPos + settings.distanceAboveTopSurface_, false);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         return zPos < settings.zStart_;
      } else {
         //no zStack
         return zPos < zOrigin;
      }
   }

   public static boolean isZBelowImagingVolume(int spaceMode, MagellanGUIAcquisitionSettings settings,
           Point2D.Double[] positionCorners, double zPos, double zOrigin) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         boolean extrapolate = settings.fixedSurface_ != settings.xyFootprint_;
         //extrapolate only if different surface used for XY positions than footprint
         return settings.fixedSurface_.isPositionCompletelyBelowSurface(
                 positionCorners, settings.fixedSurface_, zPos - settings.distanceBelowFixedSurface_, extrapolate);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.bottomSurface_.isPositionCompletelyBelowSurface(
                 positionCorners, settings.bottomSurface_, zPos - settings.distanceBelowBottomSurface_, false);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         return zPos > settings.zEnd_;
      } else {
         //no zStack
         return zPos > zOrigin;
      }
   }

//           
   public static double getZTopCoordinate(int spaceMode, MagellanGUIAcquisitionSettings settings,
           boolean zStageHasLimits, double zStageLowerLimit, double zStageUpperLimit, String zStage) {
      boolean towardsSampleIsPositive = true;
      if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
              || spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         int dir = 0;
         try {
            dir = Magellan.getCore().getFocusDirection(Magellan.getCore().getFocusDevice());
         } catch (Exception ex) {
            Log.log("Couldnt get focus direction from  core");
            throw new RuntimeException();
         }
         if (dir > 0) {
            towardsSampleIsPositive = true;
         } else if (dir < 0) {
            towardsSampleIsPositive = false;
         } else {
            Log.log("Couldn't get focus direction of Z drive. Configre using \"Devices--Hardware Configuration Wizard\"");
            throw new RuntimeException("Focus direction undefined");
         }
      }

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
      Point3d[] points = ((MagellanGUIAcquisitionSettings) settings_).fixedSurface_.getPoints();
      JSONArray pointArray = new JSONArray();
      for (Point3d p : points) {
         pointArray.put(p.x + "_" + p.y + "_" + p.z);
      }
      return pointArray;
   }

   public int getSpaceMode() {
      return ((MagellanGUIAcquisitionSettings) settings_).spaceMode_;
   }

   private void createXYPositions() {
      try {
         if (((MagellanGUIAcquisitionSettings) settings_).xyFootprint_ == null) { //Use current stage position
            positions_ = new ArrayList<XYStagePosition>();
//            int fullTileWidth = (int) Magellan.getCore().getImageWidth();
//            int fullTileHeight = (int) Magellan.getCore().getImageHeight();
            positions_.add(new XYStagePosition(new Point2D.Double(
                    Magellan.getCore().getXPosition(), Magellan.getCore().getYPosition()), 0, 0));
         } else {
//                    ((MagellanGUIAcquisitionSettings) settings_).tileOverlap_
            positions_ = ((MagellanGUIAcquisitionSettings) settings_).xyFootprint_.getXYPositions();
         }

         getPixelStageTranslator().setPositions(positions_);
      } catch (Exception e) {
         e.printStackTrace();
         Log.log("Problem with Acquisition's XY positions. Check acquisition settings");
         throw new RuntimeException();
      }
   }

   @Override
   public double getZOrigin() {
      return zOrigin_;
   }

   @Override
   public double getZStep() {
      return zStep_;
   }

   @Override
   public ChannelGroupSettings getChannels() {
      return settings_.channels_;
   }

   @Override
   public MagellanGenericAcquisitionSettings getAcquisitionSettings() {
      return settings_;
   }

}
