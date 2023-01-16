package org.micromanager.display.internal;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.display.ChannelIntensityRanges;
import org.micromanager.display.ComponentIntensityRange;
import org.micromanager.display.DisplayIntensityRanges;

public class DefaultDisplayIntensityRanges implements DisplayIntensityRanges {
   private final ArrayList<ChannelIntensityRanges> channelRanges_;

   public static class Builder implements DisplayIntensityRanges.Builder {
      private final ArrayList<ChannelIntensityRanges.Builder> channelBuilders_
              = new ArrayList<>();

      private void ensureNumChannels(int nChannels) {
         channelBuilders_.ensureCapacity(nChannels);
         while (channelBuilders_.size() < nChannels) {
            channelBuilders_.add(ChannelIntensityRanges.builder());
         }
      }

      @Override
      public Builder componentRange(int channel, int component, long min, long max) {
         ensureNumChannels(channel + 1);
         channelBuilders_.get(channel).componentRange(component, min, max);
         return this;
      }

      @Override
      public Builder componentRange(int channel, int component, ComponentIntensityRange range) {
         ensureNumChannels(channel + 1);
         channelBuilders_.get(channel).componentRange(component, range);
         return this;
      }

      @Override
      public Builder componentMinimum(int channel, int component, long min) {
         ensureNumChannels(channel + 1);
         channelBuilders_.get(channel).componentMinimum(component, min);
         return this;
      }

      @Override
      public Builder componentMaximum(int channel, int component, long max) {
         ensureNumChannels(channel + 1);
         channelBuilders_.get(channel).componentMaximum(component, max);
         return this;
      }

      @Override
      public Builder componentRanges(int channel, List<ComponentIntensityRange> ranges) {
         ensureNumChannels(channel);
         channelBuilders_.get(channel).componentRanges(ranges);
         return this;
      }

      @Override
      public Builder channelRanges(List<ChannelIntensityRanges> channelRanges) {
         channelBuilders_.clear();
         channelBuilders_.ensureCapacity(channelRanges.size());
         for (ChannelIntensityRanges chanRange : channelRanges) {
            channelBuilders_.add(chanRange.copyBuilder());
         }
         return this;
      }

      @Override
      public DefaultDisplayIntensityRanges build() {
         return new DefaultDisplayIntensityRanges(this);
      }
   }

   private DefaultDisplayIntensityRanges(Builder builder) {
      channelRanges_ = new ArrayList<>(builder.channelBuilders_.size());
      for (ChannelIntensityRanges.Builder chanBuilder : builder.channelBuilders_) {
         channelRanges_.add(chanBuilder.build());
      }
      // Normalize by removing any channels with zero components at the end
      while (channelRanges_.get(channelRanges_.size() - 1).getNumberOfComponents() == 0) {
         channelRanges_.remove(channelRanges_.size() - 1);
      }
   }

   @Override
   public Builder copyBuilder() {
      return new Builder().channelRanges(channelRanges_);
   }

   @Override
   public int getNumberOfChannels() {
      return channelRanges_.size();
   }

   @Override
   public int getChannelNumberOfComponents(int channel) {
      if (channel > channelRanges_.size()) {
         return 0;
      }
      return channelRanges_.get(channel).getNumberOfComponents();
   }

   @Override
   public long getComponentMinimum(int channel, int component) {
      if (channel > channelRanges_.size()) {
         return 0;
      }
      return channelRanges_.get(channel).getComponentMinimum(component);
   }

   @Override
   public long getComponentMaximum(int channel, int component) {
      if (channel > channelRanges_.size()) {
         return Long.MAX_VALUE;
      }
      return channelRanges_.get(channel).getComponentMaximum(component);
   }

   @Override
   public ComponentIntensityRange getComponentRange(int channel, int component) {
      if (channel > channelRanges_.size()) {
         return ComponentIntensityRange.builder().build();
      }
      return channelRanges_.get(channel).getComponentRange(component);
   }

   @Override
   public ChannelIntensityRanges getChannelRanges(int channel) {
      if (channel > channelRanges_.size()) {
         return ChannelIntensityRanges.builder().build();
      }
      return channelRanges_.get(channel);
   }

   @Override
   public List<ComponentIntensityRange> getAllComponentRanges(int channel) {
      if (channel > channelRanges_.size()) {
         return Lists.newArrayList();
      }
      return channelRanges_.get(channel).getAllComponentRanges();
   }

   @Override
   public List<Long> getComponentMinima(int channel) {
      if (channel > channelRanges_.size()) {
         return Lists.newArrayList();
      }
      return channelRanges_.get(channel).getComponentMinima();
   }

   @Override
   public List<Long> getComponentMaxima(int channel) {
      if (channel > channelRanges_.size()) {
         return Lists.newArrayList();
      }
      return channelRanges_.get(channel).getComponentMaxima();
   }

   @Override
   public List<ChannelIntensityRanges> getAllChannelRanges() {
      return Lists.newArrayList(channelRanges_);
   }
}
