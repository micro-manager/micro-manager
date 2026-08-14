package org.micromanager.display;

import org.micromanager.display.internal.DefaultComponentDisplaySettings;

/**
 * Certain cameras (such as RGB cameras) can produce images where each pixel has multiple
 * components.  This interface determines how these components should be displayed.
 *
 * @author mark
 */
public interface ComponentDisplaySettings {
   /**
    * Interface for ComponentDisplaySettings Builder.
    */
   interface Builder {
      Builder scalingMinimum(long minIntensity);

      Builder scalingMaximum(long maxIntensity);

      Builder scalingRange(long minIntensity, long maxIntensity);

      /**
       * Sets both minimum and maximum scaling values from a {@link ComponentIntensityRange}.
       *
       * <p>Convenience overload for
       * {@link #scalingRange(long, long) scalingRange(range.getMinimum(), range.getMaximum())}.
       *
       * @param range source of min/max values
       * @return this builder
       */
      Builder scalingRange(ComponentIntensityRange range);

      Builder scalingGamma(double gamma);

      /**
       * Sets the scaling minimum as an actual pixel value, for floating point images.
       *
       * <p>For float images the long-valued {@link #scalingMinimum(long)} is not usable,
       * since the interesting range is rarely integral. Pass {@code Double.NaN} to clear
       * the float scaling and fall back to the long-valued setting.
       *
       * @param minIntensity pixel value, or NaN to unset
       * @return this builder
       */
      Builder scalingMinimumDouble(double minIntensity);

      /**
       * Sets the scaling maximum as an actual pixel value, for floating point images.
       *
       * @param maxIntensity pixel value, or NaN to unset
       * @return this builder
       * @see #scalingMinimumDouble(double)
       */
      Builder scalingMaximumDouble(double maxIntensity);

      /**
       * Sets both float scaling bounds as actual pixel values.
       *
       * @param minIntensity pixel value, or NaN to unset
       * @param maxIntensity pixel value, or NaN to unset
       * @return this builder
       * @see #scalingMinimumDouble(double)
       */
      Builder scalingRangeDouble(double minIntensity, double maxIntensity);

      ComponentDisplaySettings build();
   }

   long getScalingMinimum();

   long getScalingMaximum();

   /**
    * Returns the scaling minimum as an actual pixel value, for floating point images.
    *
    * @return pixel value, or {@code Double.NaN} if no float scaling has been set
    * @see #hasFloatScaling()
    */
   double getScalingMinimumDouble();

   /**
    * Returns the scaling maximum as an actual pixel value, for floating point images.
    *
    * @return pixel value, or {@code Double.NaN} if no float scaling has been set
    * @see #hasFloatScaling()
    */
   double getScalingMaximumDouble();

   /**
    * Returns whether a floating point scaling range has been set.
    *
    * <p>When false, callers displaying float images should fall back to deriving a range
    * from the image statistics.
    *
    * @return true if both float scaling bounds are set
    */
   boolean hasFloatScaling();

   double getScalingGamma();

   Builder copyBuilder();

   static Builder builder() {
      return DefaultComponentDisplaySettings.builder();
   }
}