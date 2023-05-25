package org.micromanager.acquisition.internal.acqengjcompat;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acquisition.ChannelSpec;


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
    * @param startSliceIndex index of first slice (most often 0)
    * @param stopSliceIndex index of last slice
    * @param zStep Desired step size in microns
    * @param zOrigin Origin of Z drive.  When MDA uses absolute Z, this will be zero, when
    *                using relative Z, it will be the current Z position
    * @return Not quire sure, but something that tells the AcqEngineJ what to do.
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack(int startSliceIndex,
                                            int stopSliceIndex,
                                            double zStep,
                                            double zOrigin,
                                            List<ChannelSpec> chSpecs) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {

            private int zIndex_ = startSliceIndex;

            @Override
            public boolean hasNext() {
               Integer chIndex = (Integer) event.getAxisPosition("channel");
               if (chIndex != null) {
                  if (!chSpecs.get(chIndex).doZStack()) {
                     return zIndex_ == 0;
                  }
               }
               return zIndex_ <= stopSliceIndex;
            }

            @Override
            public AcquisitionEvent next() {
               double zPos = zIndex_ * zStep + zOrigin;
               // Do plus equals here in case z positions have been modified by
               // another function (e.g. channel specific focal offsets)
               Integer chIndex = (Integer) event.getAxisPosition("channel");
               if (chIndex != null) {
                  if (!chSpecs.get(chIndex).doZStack()) {
                     zPos = zOrigin + (stopSliceIndex - startSliceIndex) / 2 * zStep;
                  }
               }
               AcquisitionEvent sliceEvent = event.copy();
               sliceEvent.setZ(zIndex_,
                     (sliceEvent.getZPosition() == null ? 0.0 : sliceEvent.getZPosition()) + zPos);
               zIndex_++;
               return sliceEvent;
            }
         };
      };
   }

   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse(
         int numTimePoints, double intervalMs) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {

            int frameIndex_ = 0;

            @Override
            public boolean hasNext() {
               if (frameIndex_ == 0) {
                  return true;
               }
               if (frameIndex_ < numTimePoints) {
                  return true;
               }
               return false;
            }

            @Override
            public AcquisitionEvent next() {
               AcquisitionEvent timePointEvent = event.copy();
               timePointEvent.setMinimumStartTime((long) (intervalMs * frameIndex_));
               timePointEvent.setTimeIndex(frameIndex_);
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
    * @return
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels(
         List<ChannelSpec> channelList, Integer middleSliceIndex) {
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
               index++;
               return channelEvent;
            }
         };
      };
   }

   /**
    * Iterate over an arbitrary list of positions. Adds in position indices to
    * the axes that assume the order in the list provided correspondis to the
    * desired indices.
    *
    * @param positionList
    * @return
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions(
         PositionList positionList) {
      return (AcquisitionEvent event) -> {
         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
         if (positionList == null || positionList.getNumberOfPositions() == 0) {
            builder.accept(event);
         } else {
            for (int index = 0; index < positionList.getNumberOfPositions(); index++) {
               AcquisitionEvent posEvent = event.copy();

               MultiStagePosition msp = positionList.getPosition(index);
               posEvent.setX(msp.getX());
               posEvent.setY(msp.getY());
               // Not implemented for now because this is the magellan/pycromanager grid
               // concept which is not identical
               // posEvent.setGridRow(positionList.get(index).getGridRow());
               // posEvent.setGridCol(positionList.get(index).getGridCol());
               // posEvent.setAxisPosition(AcqEngMetadata.AXES_GRID_ROW,
               //                          positionList.get(index).getGridRow());
               // posEvent.setAxisPosition(AcqEngMetadata.AXES_GRID_COL,
               //                          positionList.get(index).getGridCol());
               posEvent.setAxisPosition(POSITION_AXIS, index);
               builder.accept(posEvent);
            }
         }
         return builder.build().iterator();
      };
   }

}
