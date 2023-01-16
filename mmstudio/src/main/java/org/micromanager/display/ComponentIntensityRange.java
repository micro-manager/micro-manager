package org.micromanager.display;

import org.micromanager.display.internal.DefaultComponentIntensityRange;

public interface ComponentIntensityRange {
   interface Builder {
      Builder minimum(long min);

      Builder maximum(long max);

      Builder range(long min, long max);

      ComponentIntensityRange build();
   }

   static Builder builder() {
      return new DefaultComponentIntensityRange.Builder();
   }

   Builder copyBuilder();

   long getMinimum();

   long getMaximum();
}
