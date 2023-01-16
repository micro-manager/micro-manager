package org.micromanager.display.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.micromanager.display.ChannelIntensityRanges;
import org.micromanager.display.ComponentIntensityRange;

public class DefaultChannelIntensityRanges implements ChannelIntensityRanges {
   // Store as single array of alternating min/max values. Length is always even.
   private final long[] ranges_; // Never null

   public static class Builder implements ChannelIntensityRanges.Builder {
      private long[] ranges_; // May be null

      private void ensureNumComponents(int nComponents) {
         int addedComponents = 0;
         if (ranges_ == null) {
            addedComponents = nComponents;
            ranges_ = new long[2 * nComponents];
         } else if (ranges_.length < 2 * nComponents) {
            addedComponents = nComponents - ranges_.length / 2;
            ranges_ = Arrays.copyOf(ranges_, 2 * nComponents);
         }
         for (int i = 0; i < addedComponents; ++i) {
            ranges_[2 * i + 1] = Long.MAX_VALUE;
         }
      }

      private void trimComponents(int nComponents) {
         if (ranges_.length > 2 * nComponents) {
            ranges_ = Arrays.copyOfRange(ranges_, 0, 2 * nComponents);
         }
      }

      private void normalize() {
         for (int c = ranges_.length / 2; c >= 0; c -= 2) {
            if (ranges_[2 * c] != 0 || ranges_[2 * c + 1] != Long.MAX_VALUE) {
               ranges_ = Arrays.copyOfRange(ranges_, 0, 2 * c);
               return;
            }
         }
         ranges_ = null; // All components had defaults
      }

      @Override
      public ChannelIntensityRanges.Builder componentRange(int component, long min, long max) {
         ensureNumComponents(component + 1);
         ranges_[2 * component] = min;
         ranges_[2 * component + 1] = max;
         return this;
      }

      @Override
      public ChannelIntensityRanges.Builder componentRange(int component,
            ComponentIntensityRange range) {
         ensureNumComponents(component + 1);
         ranges_[2 * component] = range.getMinimum();
         ranges_[2 * component + 1] = range.getMaximum();
         return this;
      }

      @Override
      public ChannelIntensityRanges.Builder componentMinimum(int component, long min) {
         ensureNumComponents(component + 1);
         ranges_[2 * component] = min;
         return this;
      }

      @Override
      public ChannelIntensityRanges.Builder componentMaximum(int component, long max) {
         ensureNumComponents(component + 1);
         ranges_[2 * component + 1] = max;
         return this;
      }

      @Override
      public ChannelIntensityRanges.Builder componentRanges(List<ComponentIntensityRange> ranges) {
         ensureNumComponents(ranges.size());
         trimComponents(ranges.size());
         for (int c = 0; c < ranges.size(); ++c) {
            ranges_[2 * c] = ranges.get(c).getMinimum();
            ranges_[2 * c + 1] = ranges.get(c).getMaximum();
         }
         return this;
      }

      @Override
      public DefaultChannelIntensityRanges build() {
         return new DefaultChannelIntensityRanges(this);
      }
   }

   private DefaultChannelIntensityRanges(Builder builder) {
      builder.normalize();
      if (builder.ranges_ == null) {
         ranges_ = new long[0];
      } else {
         ranges_ = builder.ranges_.clone();
      }
   }

   @Override
   public Builder copyBuilder() {
      Builder ret = new Builder();
      ret.ranges_ = ranges_.clone();
      return ret;
   }

   @Override
   public int getNumberOfComponents() {
      return ranges_.length / 2;
   }

   @Override
   public long getComponentMinimum(int component) {
      if (component > getNumberOfComponents()) {
         return 0;
      }
      return ranges_[2 * component];
   }

   @Override
   public long getComponentMaximum(int component) {
      if (component > getNumberOfComponents()) {
         return Long.MAX_VALUE;
      }
      return ranges_[2 * component + 1];
   }

   @Override
   public ComponentIntensityRange getComponentRange(int component) {
      if (component > getNumberOfComponents()) {
         return ComponentIntensityRange.builder().build();
      }
      return ComponentIntensityRange.builder()
            .range(ranges_[2 * component], ranges_[2 * component + 1])
            .build();
   }

   @Override
   public List<ComponentIntensityRange> getAllComponentRanges() {
      int nComponents = getNumberOfComponents();
      List<ComponentIntensityRange> ret = new ArrayList<>(nComponents);
      for (int c = 0; c < nComponents; ++c) {
         ret.add(getComponentRange(c));
      }
      return ret;
   }

   @Override
   public List<Long> getComponentMinima() {
      int nComponents = getNumberOfComponents();
      List<Long> ret = new ArrayList<>(nComponents);
      for (int c = 0; c < nComponents; ++c) {
         ret.add(getComponentMinimum(c));
      }
      return ret;
   }

   @Override
   public List<Long> getComponentMaxima() {
      int nComponents = getNumberOfComponents();
      List<Long> ret = new ArrayList<>(nComponents);
      for (int c = 0; c < nComponents; ++c) {
         ret.add(getComponentMaximum(c));
      }
      return ret;
   }
}
