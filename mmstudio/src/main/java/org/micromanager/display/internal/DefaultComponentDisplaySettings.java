package org.micromanager.display.internal;

import com.google.common.base.Preconditions;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.ComponentDisplaySettings;

/**
 * @author mark
 */
public final class DefaultComponentDisplaySettings
      implements ComponentDisplaySettings {
   private long scalingMin_;
   private long scalingMax_;
   private final double gamma_;

   private static final class Builder
         implements ComponentDisplaySettings.Builder {
      private long scalingMin_ = 0;
      private long scalingMax_ = Long.MAX_VALUE;
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
      public Builder scalingGamma(double gamma) {
         Preconditions.checkArgument(gamma > 0.0);
         gamma_ = gamma;
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

   public void setScalingMinimum(long min) {
      scalingMin_ = min;
   }

   public void setScalingMaximum(long max) {
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
      return scalingMin_ == o.scalingMin_
            && scalingMax_ == o.scalingMax_
            && Double.compare(gamma_, o.gamma_) == 0;
   }

   @Override
   public int hashCode() {
      int result = Long.hashCode(scalingMin_);
      result = 31 * result + Long.hashCode(scalingMax_);
      result = 31 * result + Double.hashCode(gamma_);
      return result;
   }

   /**
    * Builds the PropertyMap based on what the Builder knows.
    *
    * @return Immutable PropertyMap
    */
   public PropertyMap toPropertyMap() {
      return PropertyMaps.builder()
            .putLong(PropertyKey.SCALING_MIN.key(), scalingMin_)
            .putLong(PropertyKey.SCALING_MAX.key(), scalingMax_)
            .putDouble(PropertyKey.GAMMA.key(), gamma_)
            .build();
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

      return b.build();
   }

}