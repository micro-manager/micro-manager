package org.micromanager.display;

import java.util.List;
import org.micromanager.display.internal.DefaultDisplayIntensityRanges;

public interface DisplayIntensityRanges {
   interface Builder {
      Builder componentRange(int channel, int component, long min, long max);

      Builder componentRange(int channel, int component, ComponentIntensityRange range);

      Builder componentMinimum(int channel, int component, long min);

      Builder componentMaximum(int channel, int component, long max);

      Builder componentRanges(int channel, List<ComponentIntensityRange> ranges);

      Builder channelRanges(List<ChannelIntensityRanges> channelRanges);

      DisplayIntensityRanges build();
   }

   static Builder builder() {
      return new DefaultDisplayIntensityRanges.Builder();
   }

   Builder copyBuilder();

   int getNumberOfChannels();

   int getChannelNumberOfComponents(int channel);

   long getComponentMinimum(int channel, int component);

   long getComponentMaximum(int channel, int component);

   ComponentIntensityRange getComponentRange(int channel, int component);

   ChannelIntensityRanges getChannelRanges(int channel);

   List<ComponentIntensityRange> getAllComponentRanges(int channel);

   List<Long> getComponentMinima(int channel);

   List<Long> getComponentMaxima(int channel);

   List<ChannelIntensityRanges> getAllChannelRanges();
}
