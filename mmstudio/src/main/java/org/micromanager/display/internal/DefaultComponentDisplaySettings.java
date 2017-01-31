/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal;

import com.google.common.base.Preconditions;
import org.micromanager.display.ComponentDisplaySettings;

/**
 *
 * @author mark
 */
public final class DefaultComponentDisplaySettings
      implements ComponentDisplaySettings
{
   private final long scalingMin_;
   private final long scalingMax_;
   private final double gamma_;

   private static final class Builder
         implements ComponentDisplaySettings.Builder
   {
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
}