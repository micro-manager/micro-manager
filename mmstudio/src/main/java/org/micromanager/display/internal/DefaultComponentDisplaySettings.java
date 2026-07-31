package org.micromanager.display.internal;

import com.google.common.base.Preconditions;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.ComponentIntensityRange;

/**
 * @author mark
 */
public final class DefaultComponentDisplaySettings
      implements ComponentDisplaySettings {
   private long scalingMin_;
   private long scalingMax_;
   // For float images: the scaling range as actual pixel values. NaN when unset, in which
   // case the long-valued scalingMin_/scalingMax_ apply. Storing real pixel values (rather
   // than histogram bin indices) is what allows a float range to stay fixed across frames
   // and to be persisted meaningfully.
   private double scalingMinDouble_;
   private double scalingMaxDouble_;
   private final double gamma_;

   private static final class Builder
         implements ComponentDisplaySettings.Builder {
      private long scalingMin_ = 0;
      private long scalingMax_ = Long.MAX_VALUE;
      private double scalingMinDouble_ = Double.NaN;
      private double scalingMaxDouble_ = Double.NaN;
      private double gamma_ = 1.0;

      @Override
      public Builder scalingMinimum(long minIntensity) {
         scalingMin_ = minIntensity;
         return this;
      }

      @Override
      public Builder scalingMaximum(long maxIntensity) {
         scalingMax_ = maxIntensity;
         return this;
      }

      @Override
      public Builder scalingRange(long minIntensity, long maxIntensity) {
         scalingMin_ = minIntensity;
         scalingMax_ = maxIntensity;
         return this;
      }

      @Override
      public Builder scalingRange(ComponentIntensityRange range) {
         return scalingRange(range.getMinimum(), range.getMaximum());
      }

      @Override
      public Builder scalingGamma(double gamma) {
         Preconditions.checkArgument(gamma > 0.0);
         gamma_ = gamma;
         return this;
      }

      @Override
      public Builder scalingMinimumDouble(double minIntensity) {
         scalingMinDouble_ = minIntensity;
         return this;
      }

      @Override
      public Builder scalingMaximumDouble(double maxIntensity) {
         scalingMaxDouble_ = maxIntensity;
         return this;
      }

      @Override
      public Builder scalingRangeDouble(double minIntensity, double maxIntensity) {
         scalingMinDouble_ = minIntensity;
         scalingMaxDouble_ = maxIntensity;
         return this;
      }

      @Override
      public ComponentDisplaySettings build() {
         return new DefaultComponentDisplaySettings(this);
      }
   }

   public static ComponentDisplaySettings.Builder builder() {
      return new Builder();
   }

   private DefaultComponentDisplaySettings(Builder builder) {
      scalingMin_ = builder.scalingMin_;
      scalingMax_ = builder.scalingMax_;
      scalingMinDouble_ = builder.scalingMinDouble_;
      scalingMaxDouble_ = builder.scalingMaxDouble_;
      gamma_ = builder.gamma_;
   }

   @Override
   public long getScalingMinimum() {
      return scalingMin_;
   }

   @Override
   public long getScalingMaximum() {
      return scalingMax_;
   }

   @Override
   public double getScalingMinimumDouble() {
      return scalingMinDouble_;
   }

   @Override
   public double getScalingMaximumDouble() {
      return scalingMaxDouble_;
   }

   @Override
   public boolean hasFloatScaling() {
      return !Double.isNaN(scalingMinDouble_) && !Double.isNaN(scalingMaxDouble_);
   }

   public void hackScalingMinimumDouble(double min) {
      scalingMinDouble_ = min;
   }

   public void hackScalingMaximumDouble(double max) {
      scalingMaxDouble_ = max;
   }

   public void hackScalingMinimum(long min) {
      scalingMin_ = min;
   }
   
   public void hackScalingMaximum(long max) {
      scalingMax_ = max;
   }

   @Override
   public double getScalingGamma() {
      return gamma_;
   }

   @Override
   public ComponentDisplaySettings.Builder copyBuilder() {
      Builder builder = new Builder();
      builder.scalingMin_ = scalingMin_;
      builder.scalingMax_ = scalingMax_;
      builder.scalingMinDouble_ = scalingMinDouble_;
      builder.scalingMaxDouble_ = scalingMaxDouble_;
      builder.gamma_ = gamma_;
      return builder;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof DefaultComponentDisplaySettings)) {
         return false;
      }
      DefaultComponentDisplaySettings o = (DefaultComponentDisplaySettings) obj;
      // Double.compare (not ==) so that the NaN "unset" sentinel compares equal to itself;
      // compareAndSetDisplaySettings relies on equals() and would spin otherwise.
      return scalingMin_ == o.scalingMin_
            && scalingMax_ == o.scalingMax_
            && Double.compare(scalingMinDouble_, o.scalingMinDouble_) == 0
            && Double.compare(scalingMaxDouble_, o.scalingMaxDouble_) == 0
            && Double.compare(gamma_, o.gamma_) == 0;
   }

   @Override
   public int hashCode() {
      int result = Long.hashCode(scalingMin_);
      result = 31 * result + Long.hashCode(scalingMax_);
      result = 31 * result + Double.hashCode(scalingMinDouble_);
      result = 31 * result + Double.hashCode(scalingMaxDouble_);
      result = 31 * result + Double.hashCode(gamma_);
      return result;
   }

   /**
    * Builds the PropertyMap based on what the Builder knows.
    *
    * @return Immutable PropertyMap
    */
   public PropertyMap toPropertyMap() {
      PropertyMap.Builder b = PropertyMaps.builder()
            .putLong(PropertyKey.SCALING_MIN.key(), scalingMin_)
            .putLong(PropertyKey.SCALING_MAX.key(), scalingMax_)
            .putDouble(PropertyKey.GAMMA.key(), gamma_);
      // Only write the float range when actually set, so that files for integer data are
      // unchanged and readers can distinguish "unset" by key absence rather than by NaN.
      if (hasFloatScaling()) {
         b.putDouble(PropertyKey.SCALING_MIN_FLOAT.key(), scalingMinDouble_);
         b.putDouble(PropertyKey.SCALING_MAX_FLOAT.key(), scalingMaxDouble_);
      }
      return b.build();
   }

   /**
    * Extracts ComponentDisplaySettings from the given PropertyMap.
    *
    * @param pMap Input PropertyMap to be used.
    * @return ComponentDisplaySettings based on keys in input
    */
   public static ComponentDisplaySettings fromPropertyMap(PropertyMap pMap) {
      Builder b = new Builder();

      if (pMap.containsLong(PropertyKey.SCALING_MIN.key())) {
         b.scalingMinimum(pMap.getLong(PropertyKey.SCALING_MIN.key(), b.scalingMin_));
      }
      if (pMap.containsLong(PropertyKey.SCALING_MAX.key())) {
         b.scalingMaximum(pMap.getLong(PropertyKey.SCALING_MAX.key(), b.scalingMax_));
      }
      if (pMap.containsDouble(PropertyKey.GAMMA.key())) {
         b.scalingGamma(pMap.getDouble(PropertyKey.GAMMA.key(), b.gamma_));
      }
      // Absent in files written before float scaling was stored as pixel values; the NaN
      // default then leaves hasFloatScaling() false and the old behavior applies.
      if (pMap.containsDouble(PropertyKey.SCALING_MIN_FLOAT.key())) {
         b.scalingMinimumDouble(
               pMap.getDouble(PropertyKey.SCALING_MIN_FLOAT.key(), b.scalingMinDouble_));
      }
      if (pMap.containsDouble(PropertyKey.SCALING_MAX_FLOAT.key())) {
         b.scalingMaximumDouble(
               pMap.getDouble(PropertyKey.SCALING_MAX_FLOAT.key(), b.scalingMaxDouble_));
      }

      return b.build();
   }

}