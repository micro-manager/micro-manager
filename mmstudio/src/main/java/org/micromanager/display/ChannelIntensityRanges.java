package org.micromanager.display;

import java.util.List;
import org.micromanager.display.internal.DefaultChannelIntensityRanges;

/**
 * The intensity (brightness/contrast) scaling ranges for all components of one channel.
 *
 * <p>A "component" is one scalar channel within a pixel: for a grayscale image there is
 * one component; for an RGB image there are three (red=0, green=1, blue=2).
 *
 * <p>This interface groups per-component {@link ComponentIntensityRange} objects for a
 * single image channel.  It is used as a convenience argument to
 * {@link ChannelDisplaySettings.Builder#intensityScaling(ChannelIntensityRanges)}, which
 * applies all component ranges in one call instead of building a separate
 * {@link ComponentDisplaySettings} per component.
 *
 * <p><strong>Note:</strong> this interface is write-only in the sense that
 * {@link ChannelDisplaySettings} does not expose a {@code getIntensityScaling()} getter;
 * the ranges are stored inside the per-component {@link ComponentDisplaySettings} accessible
 * via {@link ChannelDisplaySettings#getComponentSettings(int)}.
 *
 * <p><strong>Default values:</strong> components that have not been explicitly set return
 * {@code minimum = 0} and {@code maximum = Long.MAX_VALUE} (full camera range).
 * {@link #getNumberOfComponents()} returns 0 when no components have been explicitly set.
 *
 * <p>Instances are immutable.  Use {@link #builder()} or {@link #copyBuilder()} to
 * construct one.
 *
 * @see ComponentIntensityRange
 * @see ChannelDisplaySettings.Builder#intensityScaling(ChannelIntensityRanges)
 */
public interface ChannelIntensityRanges {

   /**
    * Builder for {@link ChannelIntensityRanges}.
    *
    * <p>Obtain an instance via {@link ChannelIntensityRanges#builder()} or
    * {@link ChannelIntensityRanges#copyBuilder()}.
    */
   interface Builder {

      /**
       * Sets the range for the given component by explicit min and max values.
       *
       * @param component zero-based component index (e.g. 0=red, 1=green, 2=blue for RGB)
       * @param min       black point
       * @param max       white point ({@code Long.MAX_VALUE} = full camera range)
       * @return this builder
       */
      Builder componentRange(int component, long min, long max);

      /**
       * Sets the range for the given component from a {@link ComponentIntensityRange}.
       *
       * @param component zero-based component index
       * @param range     range to copy min/max from
       * @return this builder
       */
      Builder componentRange(int component, ComponentIntensityRange range);

      /**
       * Sets only the minimum (black point) for the given component.
       *
       * @param component zero-based component index
       * @param min       black point
       * @return this builder
       */
      Builder componentMinimum(int component, long min);

      /**
       * Sets only the maximum (white point) for the given component.
       *
       * @param component zero-based component index
       * @param max       white point ({@code Long.MAX_VALUE} = full camera range)
       * @return this builder
       */
      Builder componentMaximum(int component, long max);

      /**
       * Replaces all component ranges with the given list.
       *
       * @param ranges list of ranges, one per component; index 0 is the first component
       * @return this builder
       */
      Builder componentRanges(List<ComponentIntensityRange> ranges);

      /**
       * Builds and returns the {@link ChannelIntensityRanges}.
       *
       * @return immutable {@link ChannelIntensityRanges}
       */
      ChannelIntensityRanges build();
   }

   /**
    * Returns a new builder with no components set (all defaults).
    *
    * @return new builder
    */
   static Builder builder() {
      return new DefaultChannelIntensityRanges.Builder();
   }

   /**
    * Returns a builder pre-populated with this instance's component ranges.
    *
    * @return copy builder
    */
   Builder copyBuilder();

   /**
    * Returns the number of components for which a range has been explicitly set.
    *
    * <p>Components beyond this index return default values (min=0, max=Long.MAX_VALUE).
    * May return 0 if no ranges have been set.
    *
    * @return number of explicitly-set components
    */
   int getNumberOfComponents();

   /**
    * Returns the minimum intensity (black point) for the given component.
    *
    * @param component zero-based component index
    * @return minimum intensity; 0 if not explicitly set or index out of range
    */
   long getComponentMinimum(int component);

   /**
    * Returns the maximum intensity (white point) for the given component.
    *
    * @param component zero-based component index
    * @return maximum intensity; {@code Long.MAX_VALUE} if not explicitly set or
    *     index out of range, meaning "use the full camera bit-depth range"
    */
   long getComponentMaximum(int component);

   /**
    * Returns the range for the given component as a {@link ComponentIntensityRange}.
    *
    * @param component zero-based component index
    * @return range for that component; returns a default range if index is out of bounds
    */
   ComponentIntensityRange getComponentRange(int component);

   /**
    * Returns all explicitly-set component ranges as a list.
    *
    * @return list of size {@link #getNumberOfComponents()}
    */
   List<ComponentIntensityRange> getAllComponentRanges();

   /**
    * Returns the minimum values for all explicitly-set components.
    *
    * @return list of minimum values, one per component
    */
   List<Long> getComponentMinima();

   /**
    * Returns the maximum values for all explicitly-set components.
    *
    * @return list of maximum values, one per component
    */
   List<Long> getComponentMaxima();
}