package org.micromanager.display;

import java.util.List;
import org.micromanager.display.internal.DefaultChannelIntensityRanges;

public interface ChannelIntensityRanges {
   interface Builder {
      Builder componentRange(int component, long min, long max);

      Builder componentRange(int component, ComponentIntensityRange range);

      Builder componentMinimum(int component, long min);

      Builder componentMaximum(int component, long max);

      Builder componentRanges(List<ComponentIntensityRange> ranges);

      ChannelIntensityRanges build();
   }

   static Builder builder() {
      return new DefaultChannelIntensityRanges.Builder();
   }

   Builder copyBuilder();

   int getNumberOfComponents();

   long getComponentMinimum(int component);

   long getComponentMaximum(int component);

   ComponentIntensityRange getComponentRange(int component);

   List<ComponentIntensityRange> getAllComponentRanges();

   List<Long> getComponentMinima();

   List<Long> getComponentMaxima();
}