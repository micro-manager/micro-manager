package org.micromanager.acquisition.internal.acqengjcompat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mmcorej.CMMCore;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;


public class MDAAcqEventModules {
   /**
    * A utility class with multiple "modules" functions for creating common
    * acquisition functions that can be combined to encode complex behaviors.
    *
    * @author henrypinkard
    */

   public static final String POSITION_AXIS = "position";

   /**
    * Translates desired Z stack settings into acquisition events.
    *
    * @param acquisitionSettings Settings for this acquisition
    * @param zOrigin Origin of Z drive.  When MDA uses absolute Z, this will be zero, when
    *                using relative Z, it will be the current Z position
    * @param positionList List of positions used for relative Z calculations;
    *                     may be null if not used
    * @param chSpecs List of channel specifications,
    *                used to determine channel-specific Z stack behavior
    * @param extraTags Additional metadata tags to be added to each event; may be null if not used
    * @return Not quite sure, but something that tells the AcqEngineJ what to do.
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack(
                                            SequenceSettings acquisitionSettings,
                                            double zOrigin,
                                            PositionList positionList,
                                            List<ChannelSpec> chSpecs,
                                            HashMap<String, String> extraTags) {
      final int startSliceIndex = 0;
      final int stopSliceIndex = acquisitionSettings.slices().size() - 1;
      final double zStep  = acquisitionSettings.sliceZStepUm();
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {

            private int zIndex_ = startSliceIndex;

            @Override
            public boolean hasNext() {
               if (event != null) {
                  Integer chIndex = (Integer) event.getAxisPosition("channel");
                  if (chIndex != null) {
                     if (!chSpecs.get(chIndex).doZStack()) {
                        return zIndex_ == 0;
                     }
                  }
               }
               return zIndex_ <= stopSliceIndex;
            }

            @Override
            public AcquisitionEvent next() {
               if (event == null) {
                  zIndex_++;
                  return null;
               }
               double zBegin = zOrigin;
               if (positionList != null && (
                        acquisitionSettings.relativeZSlice() || !acquisitionSettings.useSlices())) {
                  // Get Z origin from position list if available
                  MultiStagePosition msp = positionList.getPosition(
                        (Integer) event.getAxisPosition(POSITION_AXIS));
                  try {
                     StagePosition sp = msp.get(Engine.getCore().getFocusDevice());
                     if (sp != null) {
                        zBegin = sp.get1DPosition();
                        if (!acquisitionSettings.slices().isEmpty()) {
                           zBegin += acquisitionSettings.slices().get(0);
                        }
                     }
                  } catch (Exception e) {
                     throw new RuntimeException(e);
                  }
               }

               double zPos = zIndex_ * zStep + zBegin;
               // Do plus equals here in case z positions have been modified by
               // another function (e.g. channel specific focal offsets)
               Integer chIndex = (Integer) event.getAxisPosition("channel");
               if (chIndex != null) {
                  if (!chSpecs.get(chIndex).doZStack()) {
                     zPos = zBegin + ((stopSliceIndex - startSliceIndex) / 2) * zStep;
                  }
               }
               AcquisitionEvent sliceEvent = event.copy();
               sliceEvent.setZ(zIndex_,
                      (sliceEvent.getZPosition() == null ? 0.0 : sliceEvent.getZPosition()) + zPos);
               HashMap<String, String> tags = sliceEvent.getTags();
               if (extraTags != null) {
                  for (String key :  extraTags.keySet()) {
                     tags.put(key, extraTags.get(key));
                  }
               }
               sliceEvent.setTags(tags);
               zIndex_++;
               return sliceEvent;
            }
         };
      };
   }

   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse(
         int numTimePoints, double intervalMs, HashMap<String, String> extraTags) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {

            int frameIndex_ = 0;

            @Override
            public boolean hasNext() {
               if (frameIndex_ == 0) {
                  return true;
               }
               return frameIndex_ < numTimePoints;
            }

            @Override
            public AcquisitionEvent next() {
               AcquisitionEvent timePointEvent = event.copy();
               timePointEvent.setMinimumStartTime((long) (intervalMs * frameIndex_));
               timePointEvent.setTimeIndex(frameIndex_);
               HashMap<String, String> tags = timePointEvent.getTags();
               if (extraTags != null) {
                  for (String key :  extraTags.keySet()) {
                     tags.put(key, extraTags.get(key));
                  }
               }
               timePointEvent.setTags(tags);
               frameIndex_++;

               return timePointEvent;
            }
         };
      };
   }

   /**
    * Make an iterator for events for each active channel.
    *
    * @param channelList Channel settings for this acquisition.  Should only include
    *                    the channels that are actually used.
    * @param middleSliceIndex Only used when use ZStack is not checked, indicates index
    *                         of the middle slice
    * @return Function with AcquisitionEvent and Iterator
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels(
         List<ChannelSpec> channelList, Integer middleSliceIndex,
         HashMap<String, String> extraTags) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            int index = 0;

            @Override
            public boolean hasNext() {
               return index < channelList.size();
            }

            @Override
            public AcquisitionEvent next() {
               // For Slice, Channel Acquisitions with channels not doing Z Stacks
               if (!channelList.get(index).doZStack()) {
                  if (event.getZIndex() != null) {
                     if (!Objects.equals(event.getZIndex(), middleSliceIndex)) {
                        index++;
                        return null;
                     }
                  }
               }
               // Implement Skip Frames:
               if (channelList.get(index).skipFactorFrame() != 0) {
                  if (event.getTIndex() != null) {
                     if (event.getTIndex() % (channelList.get(index).skipFactorFrame() + 1) != 0) {
                        index++;
                        return null;
                     }
                  }
               }
               AcquisitionEvent channelEvent = event.copy();
               channelEvent.setConfigGroup(channelList.get(index).channelGroup());
               channelEvent.setConfigPreset(channelList.get(index).config());
               channelEvent.setAxisPosition(AcqEngMetadata.CHANNEL_AXIS, index);
               boolean hasZOffsets = channelList.stream().map(t -> t.zOffset())
                           .filter(t -> t != 0).collect(Collectors.toList()).size() > 0;
               Double zPos;
               if (event.getZPosition() == null) {
                  if (hasZOffsets) {
                     try {
                        zPos = Engine.getCore().getPosition() + channelList.get(index).zOffset();
                     } catch (Exception e) {
                        throw new RuntimeException(e);
                     }
                  } else {
                     zPos = null;
                  }
               } else {
                  zPos = event.getZPosition() + channelList.get(index).zOffset();
               }
               channelEvent.setZ(channelEvent.getZIndex(), zPos);
               channelEvent.setExposure(channelList.get(index).exposure());
               HashMap<String, String> tags = channelEvent.getTags();
               if (extraTags != null) {
                  for (String key :  extraTags.keySet()) {
                     tags.put(key, extraTags.get(key));
                  }
               }
               channelEvent.setTags(tags);
               index++;
               return channelEvent;
            }
         };
      };
   }

   /**
    * Iterate over an arbitrary list of positions. Adds in position indices to
    * the axes that assume the order in the list provided corresponds to the
    * desired indices.
    *
    * @param positionList MM PositionList used in this acquisition
    * @param extraTags - Key Value pairs that will be added to Image Metadata
    * @return Function with AcquisitionEvent and Iterator
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions(
         PositionList positionList, HashMap<String, String> extraTags, CMMCore core) {
      return (AcquisitionEvent event) -> {
         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
         if (positionList == null || positionList.getNumberOfPositions() == 0) {
            builder.accept(event);
         } else {
            for (int index = 0; index < positionList.getNumberOfPositions(); index++) {
               AcquisitionEvent posEvent = event.copy();

               MultiStagePosition msp = positionList.getPosition(index);
               for (int s = 0; s < msp.size(); s++) {
                  StagePosition sp = msp.get(s);
                  if (sp.is2DStagePosition()) {
                     // we will run into trouble when there is more than 1 XY stage.
                     // for now, assume it is always the core XY stage
                     if (sp.getStageDeviceLabel().equals(core.getXYStageDevice())) {
                        posEvent.setX(sp.get2DPositionX());
                        posEvent.setY(sp.get2DPositionY());
                     } else {
                        // API does not handle non-default XY stages
                     }
                  } else {
                     posEvent.setStageCoordinate(sp.getStageDeviceLabel(), sp.get1DPosition());
                  }
               }
               HashMap<String, String> tags = posEvent.getTags();
               tags.put(AcqEngMetadata.POS_NAME, msp.getLabel());
               if (extraTags != null) {
                  for (String key :  extraTags.keySet()) {
                     tags.put(key, extraTags.get(key));
                  }
               }
               posEvent.setTags(tags);
               posEvent.setAxisPosition(POSITION_AXIS, index);
               builder.accept(posEvent);
            }
         }
         return builder.build().iterator();
      };
   }

}
