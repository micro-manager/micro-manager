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
       * <p>The default implementation ignores the value, so that builders written before
       * float scaling existed keep compiling; they simply carry no float range.
       *
       * @param minIntensity pixel value, or NaN to unset
       * @return this builder
       */
      default Builder floatScalingMinimum(double minIntensity) {
         return this;
      }

      /**
       * Sets the scaling maximum as an actual pixel value, for floating point images.
       *
       * @param maxIntensity pixel value, or NaN to unset
       * @return this builder
       * @see #floatScalingMinimum(double)
       */
      default Builder floatScalingMaximum(double maxIntensity) {
         return this;
      }

      /**
       * Sets both float scaling bounds as actual pixel values.
       *
       * @param minIntensity pixel value, or NaN to unset
       * @param maxIntensity pixel value, or NaN to unset
       * @return this builder
       * @see #floatScalingMinimum(double)
       */
      default Builder floatScalingRange(double minIntensity, double maxIntensity) {
         return floatScalingMinimum(minIntensity).floatScalingMaximum(maxIntensity);
      }

      ComponentDisplaySettings build();
   }

   long getScalingMinimum();

   long getScalingMaximum();

   /**
    * Returns the scaling minimum as an actual pixel value, for floating point images.
    *
    * <p>The default implementation reports no float scaling, so that implementations
    * written before float scaling existed keep compiling.
    *
    * @return pixel value, or {@code Double.NaN} if no float scaling has been set
    * @see #hasFloatScaling()
    */
   default double getFloatScalingMinimum() {
      return Double.NaN;
   }

   /**
    * Returns the scaling maximum as an actual pixel value, for floating point images.
    *
    * @return pixel value, or {@code Double.NaN} if no float scaling has been set
    * @see #hasFloatScaling()
    */
   default double getFloatScalingMaximum() {
      return Double.NaN;
   }

   /**
    * Returns whether a floating point scaling range has been set.
    *
    * <p>When false, callers displaying float images should fall back to deriving a range
    * from the image statistics.
    *
    * @return true if both bounds are set and the maximum is strictly above the minimum;
    *     an empty or inverted range counts as unset, since it cannot be displayed
    */
   default boolean hasFloatScaling() {
      double min = getFloatScalingMinimum();
      double max = getFloatScalingMaximum();
      return !Double.isNaN(min) && !Double.isNaN(max) && max > min;
   }

   /**
    * Scaling minimum as a pixel value, whichever range is in effect.
    *
    * <p>Returns the float range when one is set, and otherwise the long-valued range
    * widened to a double. Use this rather than {@link #getScalingMinimum()} anywhere the
    * value feeds a renderer or a file, so that float images are handled correctly without
    * every caller having to branch on {@link #hasFloatScaling()}.
    *
    * @return the scaling minimum
    */
   default double getEffectiveScalingMinimum() {
      return hasFloatScaling() ? getFloatScalingMinimum() : (double) getScalingMinimum();
   }

   /**
    * Scaling maximum as a pixel value, whichever range is in effect.
    *
    * @return the scaling maximum
    * @see #getEffectiveScalingMinimum()
    */
   default double getEffectiveScalingMaximum() {
      return hasFloatScaling() ? getFloatScalingMaximum() : (double) getScalingMaximum();
   }

   double getScalingGamma();

   Builder copyBuilder();

   static Builder builder() {
      return DefaultComponentDisplaySettings.builder();
   }
}