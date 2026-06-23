package org.micromanager.display.internal;

import org.micromanager.display.ComponentIntensityRange;

public class DefaultComponentIntensityRange implements ComponentIntensityRange {
   private final long min_;
   private final long max_;

   public static class Builder implements ComponentIntensityRange.Builder {
      private long min_ = 0;
      private long max_ = Long.MAX_VALUE;

      @Override
      public Builder minimum(long min) {
         min_ = min;
         return this;
      }

      @Override
      public Builder maximum(long max) {
         max_ = max;
         return this;
      }

      @Override
      public Builder range(long min, long max) {
         min_ = min;
         max_ = max;
         return this;
      }

      @Override
      public DefaultComponentIntensityRange build() {
         return new DefaultComponentIntensityRange(this);
      }
   }

   private DefaultComponentIntensityRange(Builder builder) {
      min_ = builder.min_;
      max_ = builder.max_;
   }

   @Override
   public Builder copyBuilder() {
      return new Builder().range(min_, max_);
   }

   @Override
   public long getMinimum() {
      return min_;
   }

   @Override
   public long getMaximum() {
      return max_;
   }
}
