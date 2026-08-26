package org.micromanager.display.internal;

import org.micromanager.display.ComponentIntensityRange;

public class DefaultComponentIntensityRange implements ComponentIntensityRange {
   private final long min_;
   private final long max_;
   // Pixel-value range for float images; NaN when unset, in which case min_/max_ apply.
   private final double floatMin_;
   private final double floatMax_;

   public static class Builder implements ComponentIntensityRange.Builder {
      private long min_ = 0;
      private long max_ = Long.MAX_VALUE;
      private double floatMin_ = Double.NaN;
      private double floatMax_ = Double.NaN;

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
      public Builder floatMinimum(double min) {
         floatMin_ = min;
         return this;
      }

      @Override
      public Builder floatMaximum(double max) {
         floatMax_ = max;
         return this;
      }

      @Override
      public Builder floatRange(double min, double max) {
         floatMin_ = min;
         floatMax_ = max;
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
      floatMin_ = builder.floatMin_;
      floatMax_ = builder.floatMax_;
   }

   @Override
   public Builder copyBuilder() {
      return new Builder().range(min_, max_).floatRange(floatMin_, floatMax_);
   }

   @Override
   public long getMinimum() {
      return min_;
   }

   @Override
   public long getMaximum() {
      return max_;
   }

   @Override
   public double getFloatMinimum() {
      return floatMin_;
   }

   @Override
   public double getFloatMaximum() {
      return floatMax_;
   }

   @Override
   public boolean hasFloatRange() {
      return !Double.isNaN(floatMin_) && !Double.isNaN(floatMax_) && floatMax_ > floatMin_;
   }
}
