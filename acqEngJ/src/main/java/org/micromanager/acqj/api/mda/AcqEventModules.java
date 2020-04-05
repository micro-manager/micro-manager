package org.micromanager.acqj.api.mda;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.acqj.internal.acqengj.AffineTransformUtils;

/**
 * A utility class with multiple "modules" functions for creating common
 * acquisition functions that can be combined to encode complex behaviors
 *
 * @author henrypinkard
 */
public class AcqEventModules {

   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack(int startSliceIndex, int stopSliceIndex, double zStep, double zOrigin) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {

            private int zIndex_ = startSliceIndex;

            @Override
            public boolean hasNext() {
               return zIndex_ < stopSliceIndex;
            }

            @Override
            public AcquisitionEvent next() {
               double zPos = zIndex_ * zStep + zOrigin;
               AcquisitionEvent sliceEvent = event.copy();
               //Do plus equals here in case z positions have been modified by another function (e.g. channel specific focal offsets)
               sliceEvent.setZ(zIndex_,
                       (sliceEvent.getZPosition() == null ? 0.0 : sliceEvent.getZPosition()) + zPos);
               zIndex_++;
               return sliceEvent;
            }
         };
      };
   }

   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse(int numTimePoints, double interval_ms) {
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

               timePointEvent.setMinimumStartTime((long) (interval_ms * frameIndex_));
               
               timePointEvent.setTimeIndex(frameIndex_);
               frameIndex_++;

               return timePointEvent;
            }
         };
      };
   }

   /**
    * Make an iterator for events for each active channel
    *
    * @param channels
    * @return
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels(List<ChannelSetting> channelList) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            int index = 0;

            @Override
            public boolean hasNext() {
               return index < channelList.size();
            }

            @Override
            public AcquisitionEvent next() {
               AcquisitionEvent channelEvent = event.copy();
               channelEvent.setChannelGroup(channelList.get(index).group_);
               channelEvent.setChannelConfig(channelList.get(index).config_);
               channelEvent.setZ(channelEvent.getZIndex(), 
                   channelEvent.getZPosition() != null ? 
                           channelEvent.getZPosition()  + channelList.get(index).offset_ :
                           channelList.get(index).offset_);
               channelEvent.setExposure(channelList.get(index).exposure_);
               index++;
               return channelEvent;
            }
         };
      };
   }

   /**
    * Iterate over an arbitrary list of positions. Adds in postition indices to
    * the axes that assumer the order in the list provided correspond to the
    * desired indices
    *
    * @param positions
    * @return
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions(List<XYStagePosition> positions) {
      return (AcquisitionEvent event) -> {
         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
         if (positions == null) {
            builder.accept(event);
         } else {
            for (int index = 0; index < positions.size(); index++) {
               AcquisitionEvent posEvent = event.copy();
               posEvent.setX(positions.get(index).getCenter().x);
               posEvent.setY(positions.get(index).getCenter().y);
               posEvent.setGridRow(positions.get(index).getGridRow());
               posEvent.setGridCol(positions.get(index).getGridCol());
               event.setAxisPosition(AcqEngMetadata.POSITION_AXIS, index);
               builder.accept(posEvent);
            }
         }
         return builder.build().iterator();
      };
   }

}
