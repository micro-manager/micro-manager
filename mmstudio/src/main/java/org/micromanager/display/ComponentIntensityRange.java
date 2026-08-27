package org.micromanager.display;

import org.micromanager.display.internal.DefaultComponentIntensityRange;

/**
 * The intensity (brightness/contrast) scaling range for a single image component.
 *
 * <p>A "component" is one scalar channel within a pixel: for a grayscale image there is
 * one component; for an RGB image there are three (red, green, blue).
 *
 * <p>This interface is a simple min/max pair.  It is used as a data-transfer object
 * when building display settings via
 * {@link ComponentDisplaySettings.Builder#scalingRange(ComponentIntensityRange)},
 * {@link ChannelIntensityRanges.Builder#componentRange(int, ComponentIntensityRange)}, and
 * related methods.  It does not carry gamma; for that, use {@link ComponentDisplaySettings}
 * directly.
 *
 * <p>Instances are immutable.  Use {@link #builder()} or {@link #copyBuilder()} to
 * construct one.
 *
 * <p><strong>Default values:</strong> a freshly built instance (no setters called) has
 * {@code minimum = 0} and {@code maximum = Long.MAX_VALUE}.
 * {@code Long.MAX_VALUE} is a sentinel meaning "use the full camera bit-depth range."
 *
 * @see ChannelIntensityRanges
 * @see ComponentDisplaySettings
 */
public interface ComponentIntensityRange {

   /**
    * Builder for {@link ComponentIntensityRange}.
    *
    * <p>Obtain an instance via {@link ComponentIntensityRange#builder()} or
    * {@link ComponentIntensityRange#copyBuilder()}.
    */
   interface Builder {

      /**
       * Sets the minimum intensity (black point) for this component.
       *
       * @param min intensity value at or below which pixels display as black
       * @return this builder
       */
      Builder minimum(long min);

      /**
       * Sets the maximum intensity (white point) for this component.
       *
       * <p>Use {@code Long.MAX_VALUE} to indicate "use the full camera bit-depth range."
       *
       * @param max intensity value at or above which pixels display at full brightness
       * @return this builder
       */
      Builder maximum(long max);

      /**
       * Convenience method to set both minimum and maximum in one call.
       *
       * @param min intensity value at or below which pixels display as black
       * @param max intensity value at or above which pixels display at full brightness
       * @return this builder
       */
      Builder range(long min, long max);

      /**
       * Sets the minimum as an actual pixel value, for floating point images.
       *
       * <p>For float images the long-valued {@link #minimum(long)} is not usable, since
       * the interesting range is rarely integral. Pass {@code Double.NaN} to clear the
       * float range and fall back to the long-valued setting.
       *
       * <p>The default implementation ignores the value, so that builders written before
       * the float range existed keep compiling; they simply carry no float range.
       *
       * @param min pixel value, or NaN to unset
       * @return this builder
       */
      default Builder floatMinimum(double min) {
         return this;
      }

      /**
       * Sets the maximum as an actual pixel value, for floating point images.
       *
       * @param max pixel value, or NaN to unset
       * @return this builder
       * @see #floatMinimum(double)
       */
      default Builder floatMaximum(double max) {
         return this;
      }

      /**
       * Sets both float bounds as actual pixel values.
       *
       * @param min pixel value, or NaN to unset
       * @param max pixel value, or NaN to unset
       * @return this builder
       * @see #floatMinimum(double)
       */
      default Builder floatRange(double min, double max) {
         return floatMinimum(min).floatMaximum(max);
      }

      /**
       * Builds and returns the {@link ComponentIntensityRange}.
       *
       * @return immutable {@link ComponentIntensityRange}
       */
      ComponentIntensityRange build();
   }

   /**
    * Returns a new builder with default values ({@code min=0}, {@code max=Long.MAX_VALUE}).
    *
    * @return new builder
    */
   static Builder builder() {
      return new DefaultComponentIntensityRange.Builder();
   }

   /**
    * Returns a builder pre-populated with this instance's values.
    *
    * @return copy builder
    */
   Builder copyBuilder();

   /**
    * Returns the minimum intensity (black point).
    *
    * @return minimum intensity; 0 when not explicitly set
    */
   long getMinimum();

   /**
    * Returns the maximum intensity (white point).
    *
    * @return maximum intensity; {@code Long.MAX_VALUE} when not explicitly set,
    *     which means "use the full camera bit-depth range"
    */
   long getMaximum();

   /**
    * Returns the minimum as an actual pixel value, for floating point images.
    *
    * <p>The default implementation reports no float range, so that implementations written
    * before the float range existed keep compiling.
    *
    * @return pixel value, or {@code Double.NaN} if no float range has been set
    * @see #hasFloatRange()
    */
   default double getFloatMinimum() {
      return Double.NaN;
   }

   /**
    * Returns the maximum as an actual pixel value, for floating point images.
    *
    * @return pixel value, or {@code Double.NaN} if no float range has been set
    * @see #hasFloatRange()
    */
   default double getFloatMaximum() {
      return Double.NaN;
   }

   /**
    * Returns whether a floating point range has been set.
    *
    * @return true if both bounds are set and the maximum is strictly above the minimum;
    *     an empty or inverted range counts as unset, since it cannot be displayed
    */
   default boolean hasFloatRange() {
      double min = getFloatMinimum();
      double max = getFloatMaximum();
      return !Double.isNaN(min) && !Double.isNaN(max) && max > min;
   }
}
