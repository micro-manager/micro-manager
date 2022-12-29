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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.AcquisitionHook;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.api.TaggedImageProcessor;
import org.micromanager.acqj.api.XYTiledAcquisitionAPI;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acqj.main.XYTiledAcquisition;
import org.micromanager.acqj.util.AcqEventModules;
import org.micromanager.acqj.util.AcquisitionEventIterator;
import org.micromanager.acqj.util.ChannelSetting;
import org.micromanager.acqj.util.xytiling.PixelStageTranslator;
import org.micromanager.acqj.util.xytiling.XYStagePosition;
import org.micromanager.magellan.internal.channels.ChannelGroupSettings;
import org.micromanager.magellan.internal.channels.SingleChannelSetting;
import org.micromanager.magellan.internal.gui.GUI;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.Point3d;
import org.micromanager.ndtiffstorage.NDTiffAPI;

/**
 *
 * @author Henry
 */
public class MagellanGUIAcquisition implements MagellanAcquisition {

   private double zOrigin_;
   private double zStep_;
   private int minSliceIndex_;
   private int maxSliceIndex_;
   private List<XYStagePosition> positions_;
   private MagellanGenericAcquisitionSettings settings_;
   private volatile boolean started_ = false;
   private String zStage_;
   protected boolean zStageHasLimits_ = false;
   protected double zStageLowerLimit_;
   protected double zStageUpperLimit_;

   private XYTiledAcquisitionAPI acq_;

   /**
    * Acquisition with fixed XY positions (although they can potentially all be
    * translated between time points). Supports time points Z stacks that can
    * change at positions between time points
    *
    * <p>Acquisition engine manages a thread that reads events, fixed area
    * acquisition has another thread that generates events
    *
    * @param settings Magellan AcquisitionSettings
    * @throws java.lang.Exception can happen
    */
   public MagellanGUIAcquisition(MagellanGUIAcquisitionSettings settings, boolean showDisplay) {
      settings_ = settings;
      DataSink sink = new MagellanDatasetAndAcquisition(this, settings.dir_,
            settings.name_, showDisplay);
      int overlapX = (int) (Magellan.getCore().getImageWidth() * GUI.getTileOverlap() / 100);
      int overlapY = (int) (Magellan.getCore().getImageHeight() * GUI.getTileOverlap() / 100);
      zStep_ = ((MagellanGUIAcquisitionSettings) settings).zStep_;
      zStage_ = Magellan.getCore().getFocusDevice();

      acq_ = new XYTiledAcquisition(sink, overlapX, overlapY, new Consumer<JSONObject>() {
         @Override
         public void accept(JSONObject jsonObject) {
            addMagellanSummaryMetadata(jsonObject, sink);
         }
      });
      acq_.addImageMetadataProcessor(new Consumer<JSONObject>() {
         @Override
         public void accept(JSONObject imageMetadata) {
            addToImageMetadata(imageMetadata);
         }
      });
      getPixelStageTranslator().setPositions(positions_);

      //"position" is not generic name...and as of right now there is no way of getting
      // generic z positions.
      // from a z device in MM, but the following code works for some devices
      String positionName = "Position";
      try {
         if (Magellan.getCore().getFocusDevice() != null && Magellan.getCore()
               .getFocusDevice().length() > 0) {
            if (Magellan.getCore().hasProperty(zStage_, positionName)) {
               zStageHasLimits_ = Magellan.getCore().hasPropertyLimits(zStage_, positionName);
               if (zStageHasLimits_) {
                  zStageLowerLimit_ = Magellan.getCore()
                        .getPropertyLowerLimit(zStage_, positionName);
                  zStageUpperLimit_ = Magellan.getCore()
                        .getPropertyUpperLimit(zStage_, positionName);
               }
            }
         }
      } catch (Exception ex) {
         throw new RuntimeException("Problem communicating with core to get Z stage limits");
      }
      if (acq_.areEventsFinished()) {
         throw new RuntimeException("Cannot start acquisition since it has already been run");
      }
      Iterator<AcquisitionEvent> acqEventIterator = buildAcqEventGenerator();
      Engine.getInstance().submitEventIterator(acqEventIterator);
      Engine.getInstance().finishAcquisition(acq_);
   }

   private void addMagellanSummaryMetadata(JSONObject summaryMetadata, DataSink sink) {
      MagellanMD.setExploreAcq(summaryMetadata, false);
      AcqEngMetadata.setZStepUm(summaryMetadata, zStep_);
      AcqEngMetadata.setIntervalMs(summaryMetadata, getTimeIntervalMs());
      createXYPositions();
   }

   public PixelStageTranslator pixelStageTranslator() {
      return acq_.getPixelStageTranslator();
   }

   private void addToImageMetadata(JSONObject tags) {
      //add metadata specific to magellan acquisition
      // I don't remember why this here and not in summary metadata
      AcqEngMetadata.setIntervalMs(tags, ((MagellanGUIAcquisition) this).getTimeIntervalMs());
      //add data about surface
      //right now this only works for fixed distance from the surface
      if (getSpaceMode() == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         //add metadata about surface
         MagellanMD.setSurfacePoints(tags, getFixedSurfacePoints());
      }
   }

   //Called by pycromanager
   public NDTiffAPI getStorage() {
      return acq_.getDataSink() == null ? null
            : ((MagellanDatasetAndAcquisition) acq_.getDataSink()).getStorage();
   }

   // Called by pycromanager
   public XYTiledAcquisitionAPI getAcquisition() {
      return acq_;
   }

   public boolean isFinished() {
      if (!started_) {
         return false;
      }
      if (acq_.getDataSink() != null) {
         return acq_.getDataSink().isFinished();
      }
      return true;
   }

   @Override
   public void abort() {
      acq_.abort();
   }

   @Override
   public void abort(Exception e) {
      acq_.abort(e);
   }

   @Override
   public void togglePaused() {
      acq_.setPaused(!isPaused());
   }

   @Override
   public boolean isPaused() {
      return acq_.isPaused();
   }

   @Override
   public void setPaused(boolean pause) {
      acq_.setPaused(pause);
   }

   private Iterator<AcquisitionEvent> buildAcqEventGenerator() {
      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
              = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      //Define where slice index 0 will be
      zOrigin_ = getZTopCoordinate(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
              ((MagellanGUIAcquisitionSettings) settings_), zStageHasLimits_,
              zStageLowerLimit_, zStageUpperLimit_, zStage_);

      if (((MagellanGUIAcquisitionSettings) settings_).timeEnabled_) {
         acqFunctions.add(AcqEventModules.timelapse(((MagellanGUIAcquisitionSettings) settings_)
                     .numTimePoints_,
                 (int) (((MagellanGUIAcquisitionSettings) settings_).timePointInterval_
                 * (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 1
                         ? 1000 : (((MagellanGUIAcquisitionSettings) settings_)
                       .timeIntervalUnit_ == 2 ? 60000 : 1)))));
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

      if (((MagellanGUIAcquisitionSettings) settings_).spaceMode_
            == MagellanGUIAcquisitionSettings.REGION_2D) {
         if (((MagellanGUIAcquisitionSettings) settings_).channels_.getNumChannels() != 0) {
            acqFunctions.add(AcqEventModules.channels(channels));
         }
      } else if (((MagellanGUIAcquisitionSettings) settings_).spaceMode_
            == MagellanGUIAcquisitionSettings.REGION_2D_SURFACE_GUIDED) {
         acqFunctions.add(surfaceGuided2D());
         if (!channels.isEmpty()) {
            acqFunctions.add(AcqEventModules.channels(channels));
         }
      } else if (((MagellanGUIAcquisitionSettings) settings_).channelsAtEverySlice_) {
         acqFunctions.add(magellanZStack());
         if (!channels.isEmpty()) {
            acqFunctions.add(AcqEventModules.channels(channels));
         }
      } else {
         if (!channels.isEmpty()) {
            acqFunctions.add(AcqEventModules.channels(channels));
         }
         acqFunctions.add(magellanZStack());
      }
      AcquisitionEvent baseEvent = new AcquisitionEvent(acq_);
      return new AcquisitionEventIterator(baseEvent, acqFunctions, monitorSliceIndices());
   }

   @Override
   public void start() {
      acq_.start();
      started_ = true;
   }

   public void waitForCompletion() {
      if (!started_) {
         //it was never successfully started
         return;
      }
      acq_.waitForCompletion();
   }

   @Override
   public void finish() {
      acq_.finish();
   }

   @Override
   public boolean areEventsFinished() {
      return acq_.areEventsFinished();
   }

   @Override
   public boolean isAbortRequested() {
      return acq_.isAbortRequested();
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return acq_.getSummaryMetadata();
   }

   @Override
   public boolean anythingAcquired() {
      return acq_.anythingAcquired();
   }

   @Override
   public void addImageMetadataProcessor(Consumer<JSONObject> modifier) {
      acq_.addImageMetadataProcessor(modifier);
   }

   @Override
   public void addImageProcessor(TaggedImageProcessor p) {
      acq_.addImageProcessor(p);
   }

   @Override
   public void addHook(AcquisitionHook hook, int type) {
      acq_.addHook(hook, type);
   }

   @Override
   public Future submitEventIterator(Iterator<AcquisitionEvent> evt) {
      return submitEventIterator(evt);
   }

   @Override
   public DataSink getDataSink() {
      return acq_.getDataSink();
   }

   @Override
   public boolean isDebugMode() {
      return acq_.isDebugMode();
   }

   @Override
   public void checkForExceptions() throws Exception {
      acq_.checkForExceptions();
   }

   @Override
   public void setDebugMode(boolean debug) {
      acq_.setDebugMode(debug);
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

   public double getTimeIntervalMs() {
      return ((MagellanGUIAcquisitionSettings) settings_).timePointInterval_
              * (((MagellanGUIAcquisitionSettings) settings_).timeIntervalUnit_ == 1
                      ? 1000 : (((MagellanGUIAcquisitionSettings) settings_)
            .timeIntervalUnit_ == 2 ? 60000 : 1));
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
    * Fancy Z stack Magellan style.
    */
   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> magellanZStack() {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            private int sliceIndex_ = (int) Math.round((getZTopCoordinate(
                    ((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                    ((MagellanGUIAcquisitionSettings) settings_),
                    zStageHasLimits_, zStageLowerLimit_, zStageUpperLimit_, zStage_) - zOrigin_)
                  / zStep_);

            @Override
            public boolean hasNext() {
               double zPos = zOrigin_ + sliceIndex_ * zStep_;
               boolean undefined = isImagingVolumeUndefinedAtPosition(
                     ((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                       ((MagellanGUIAcquisitionSettings) settings_),
                     event.getDisplayPositionCorners());
               //position is below z stack or limit of focus device, z stack finished
               boolean below = isZBelowImagingVolume(((MagellanGUIAcquisitionSettings) settings_)
                           .spaceMode_,
                       ((MagellanGUIAcquisitionSettings) settings_),
                     event.getDisplayPositionCorners(), zPos, zOrigin_)
                       || (zStageHasLimits_ && zPos > zStageUpperLimit_);
               return (undefined || below) ? false : true;
            }

            @Override
            public AcquisitionEvent next() {
               double zPos = zOrigin_ + sliceIndex_ * zStep_;
               while (isZAboveImagingVolume(((MagellanGUIAcquisitionSettings) settings_).spaceMode_,
                       ((MagellanGUIAcquisitionSettings) settings_),
                       event.getDisplayPositionCorners(), zPos, zOrigin_)
                     || (zStageHasLimits_ && zPos < zStageLowerLimit_)) {
                  sliceIndex_++;
                  zPos = zOrigin_ + sliceIndex_ * zStep_;
               }
               AcquisitionEvent sliceEvent = event.copy();
               // Do plus equals here in case z positions have been modified by another
               // function (e.g. channel specific focal offsets)

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
            throw new RuntimeException(
                  "Expected surface but didn't find one. Check acquisition settings");
         }
         if (((MagellanGUIAcquisitionSettings) settings_).collectionPlane_
               .getCurentInterpolation().isInterpDefined(
                 event.getXPosition(), event.getYPosition())) {
            zPos = ((MagellanGUIAcquisitionSettings) settings_).collectionPlane_
                  .getCurentInterpolation().getInterpolatedValue(
                    event.getXPosition(), event.getYPosition());
         } else {
            zPos = ((MagellanGUIAcquisitionSettings) settings_).collectionPlane_
                  .getExtrapolatedValue(
                    event.getXPosition(), event.getYPosition());
         }
         event.setZ(0, zPos);
         // Make z index all 0 for the purposes of the display even though they may be in
         // very differnet locations
         return Stream.of(event).iterator();
      };

   }

   public static boolean isImagingVolumeUndefinedAtPosition(int spaceMode,
                                                            MagellanGUIAcquisitionSettings settings,
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
    * for a given position.
    *
    * @param zPos
    * @return
    */
   public static boolean isZAboveImagingVolume(int spaceMode,
                                               MagellanGUIAcquisitionSettings settings,
           Point2D.Double[] positionCorners, double zPos, double zOrigin) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         boolean extrapolate = settings.fixedSurface_ != settings.xyFootprint_;
         //extrapolate only if different surface used for XY positions than footprint
         return settings.fixedSurface_.isPositionCompletelyAboveSurface(positionCorners,
               settings.fixedSurface_,
                 zPos + settings.distanceAboveFixedSurface_, extrapolate);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.topSurface_.isPositionCompletelyAboveSurface(positionCorners,
               settings.topSurface_,
                 zPos + settings.distanceAboveTopSurface_, false);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         return zPos < settings.zStart_;
      } else {
         //no zStack
         return zPos < zOrigin;
      }
   }

   public static boolean isZBelowImagingVolume(int spaceMode,
                                               MagellanGUIAcquisitionSettings settings,
           Point2D.Double[] positionCorners, double zPos, double zOrigin) {
      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         boolean extrapolate = settings.fixedSurface_ != settings.xyFootprint_;
         //extrapolate only if different surface used for XY positions than footprint
         return settings.fixedSurface_.isPositionCompletelyBelowSurface(
                 positionCorners, settings.fixedSurface_,
               zPos - settings.distanceBelowFixedSurface_,
                 extrapolate);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         return settings.bottomSurface_.isPositionCompletelyBelowSurface(
                 positionCorners, settings.bottomSurface_,
               zPos - settings.distanceBelowBottomSurface_,
                 false);
      } else if (spaceMode == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         return zPos > settings.zEnd_;
      } else {
         //no zStack
         return zPos > zOrigin;
      }
   }

   public static double getZTopCoordinate(int spaceMode, MagellanGUIAcquisitionSettings settings,
           boolean zStageHasLimits, double zStageLowerLimit, double zStageUpperLimit,
                                          String zStage) {
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
            Log.log("Couldn't get focus direction of Z drive. Configure using "
                  + "\"Devices--Hardware Configuration Wizard\"");
            throw new RuntimeException("Focus direction undefined");
         }
      }

      if (spaceMode == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         List<Point3d> interpPoints = settings.fixedSurface_.getPoints();
         if (towardsSampleIsPositive) {
            double top = interpPoints.get(0).z - settings.distanceAboveFixedSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         } else {
            double top = interpPoints.get(interpPoints.size() - 1).z
                  + settings.distanceAboveFixedSurface_;
            return zStageHasLimits ? Math.max(zStageUpperLimit, top) : top;
         }
      } else if (spaceMode == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         if (towardsSampleIsPositive) {
            List<Point3d> interpPoints = settings.topSurface_.getPoints();
            double top = interpPoints.get(0).z - settings.distanceAboveTopSurface_;
            return zStageHasLimits ? Math.max(zStageLowerLimit, top) : top;
         } else {
            List<Point3d> interpPoints = settings.topSurface_.getPoints();
            double top = interpPoints.get(interpPoints.size() - 1).z
                  + settings.distanceAboveTopSurface_;
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

   //TODO: this could be generalized into a method to get metadata specific to any
   // acwuisiton surface type
   public JSONArray getFixedSurfacePoints() {
      List<Point3d> points = ((MagellanGUIAcquisitionSettings) settings_).fixedSurface_.getPoints();
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
         //Use current stage position
         if (((MagellanGUIAcquisitionSettings) settings_).xyFootprint_ == null) {
            positions_ = new ArrayList<XYStagePosition>();
            positions_.add(new XYStagePosition(new Point2D.Double(
                    Magellan.getCore().getXPosition(), Magellan.getCore().getYPosition()), 0, 0));
         } else {
            positions_ = ((MagellanGUIAcquisitionSettings) settings_).xyFootprint_.getXYPositions();
         }

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

   @Override
   public PixelStageTranslator getPixelStageTranslator() {
      return acq_.getPixelStageTranslator();
   }

}
