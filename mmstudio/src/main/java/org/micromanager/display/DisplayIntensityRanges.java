package org.micromanager.display;

import java.util.List;
import org.micromanager.display.internal.DefaultDisplayIntensityRanges;

/**
 * The intensity (brightness/contrast) scaling ranges for all channels and components
 * of a display.
 *
 * <p>This interface aggregates {@link ChannelIntensityRanges} objects, one per channel,
 * into a single value object.  It is used as a bulk convenience argument to
 * {@link DisplaySettings.Builder#intensityScaling(DisplayIntensityRanges)}, which applies
 * all channel ranges in one call instead of setting each channel individually.
 *
 * <p>Each channel in turn holds per-component ranges (see {@link ChannelIntensityRanges}).
 * For a grayscale display with multiple channels each channel has one component;
 * for an RGB image each channel has three components (red=0, green=1, blue=2).
 *
 * <p><strong>Note:</strong> this interface is write-only in the sense that
 * {@link DisplaySettings} does not expose a corresponding {@code getIntensityScaling()}
 * getter; ranges are stored inside the per-channel / per-component
 * {@link ComponentDisplaySettings} accessible via
 * {@link DisplaySettings#getChannelSettings(int)} →
 * {@link ChannelDisplaySettings#getComponentSettings(int)}.
 *
 * <p>Instances are immutable.  Use {@link #builder()} or {@link #copyBuilder()} to
 * construct one.
 *
 * @see ChannelIntensityRanges
 * @see ComponentIntensityRange
 * @see DisplaySettings.Builder#intensityScaling(DisplayIntensityRanges)
 */
public interface DisplayIntensityRanges {

   /**
    * Builder for {@link DisplayIntensityRanges}.
    *
    * <p>Obtain an instance via {@link DisplayIntensityRanges#builder()} or
    * {@link DisplayIntensityRanges#copyBuilder()}.
    */
   interface Builder {

      /**
       * Sets the range for a specific channel and component by explicit min/max values.
       *
       * @param channel   zero-based channel index
       * @param component zero-based component index (e.g. 0=red, 1=green, 2=blue for RGB)
       * @param min       black point
       * @param max       white point ({@code Long.MAX_VALUE} = full camera range)
       * @return this builder
       */
      Builder componentRange(int channel, int component, long min, long max);

      /**
       * Sets the range for a specific channel and component from a
       * {@link ComponentIntensityRange}.
       *
       * @param channel   zero-based channel index
       * @param component zero-based component index
       * @param range     range to copy min/max from
       * @return this builder
       */
      Builder componentRange(int channel, int component, ComponentIntensityRange range);

      /**
       * Sets only the minimum (black point) for a specific channel and component.
       *
       * @param channel   zero-based channel index
       * @param component zero-based component index
       * @param min       black point
       * @return this builder
       */
      Builder componentMinimum(int channel, int component, long min);

      /**
       * Sets only the maximum (white point) for a specific channel and component.
       *
       * @param channel   zero-based channel index
       * @param component zero-based component index
       * @param max       white point ({@code Long.MAX_VALUE} = full camera range)
       * @return this builder
       */
      Builder componentMaximum(int channel, int component, long max);

      /**
       * Replaces all component ranges for the given channel with the given list.
       *
       * @param channel zero-based channel index
       * @param ranges  list of ranges, one per component; index 0 is the first component
       * @return this builder
       */
      Builder componentRanges(int channel, List<ComponentIntensityRange> ranges);

      /**
       * Replaces all channel ranges with the given list.
       *
       * @param channelRanges list of {@link ChannelIntensityRanges}, one per channel
       * @return this builder
       */
      Builder channelRanges(List<ChannelIntensityRanges> channelRanges);

      /**
       * Builds and returns the {@link DisplayIntensityRanges}.
       *
       * @return immutable {@link DisplayIntensityRanges}
       */
      DisplayIntensityRanges build();
   }

   /**
    * Returns a new builder with no channels or components set (all defaults).
    *
    * @return new builder
    */
   static Builder builder() {
      return new DefaultDisplayIntensityRanges.Builder();
   }

   /**
    * Returns a builder pre-populated with all channel ranges from this instance.
    *
    * @return copy builder
    */
   Builder copyBuilder();

   /**
    * Returns the number of channels for which ranges have been set.
    *
    * @return number of channels
    */
   int getNumberOfChannels();

   /**
    * Returns the number of components for which ranges have been explicitly set
    * in the given channel.
    *
    * @param channel zero-based channel index
    * @return number of explicitly-set components; may be 0
    */
   int getChannelNumberOfComponents(int channel);

   /**
    * Returns the minimum intensity (black point) for the given channel and component.
    *
    * @param channel   zero-based channel index
    * @param component zero-based component index
    * @return minimum intensity; 0 if not explicitly set
    */
   long getComponentMinimum(int channel, int component);

   /**
    * Returns the maximum intensity (white point) for the given channel and component.
    *
    * @param channel   zero-based channel index
    * @param component zero-based component index
    * @return maximum intensity; {@code Long.MAX_VALUE} if not explicitly set,
    *     meaning "use the full camera bit-depth range"
    */
   long getComponentMaximum(int channel, int component);

   /**
    * Returns the range for the given channel and component as a
    * {@link ComponentIntensityRange}.
    *
    * @param channel   zero-based channel index
    * @param component zero-based component index
    * @return range for that channel/component; returns a default range if out of bounds
    */
   ComponentIntensityRange getComponentRange(int channel, int component);

   /**
    * Returns the {@link ChannelIntensityRanges} for the given channel.
    *
    * @param channel zero-based channel index
    * @return channel ranges; returns a default (empty) instance if out of bounds
    */
   ChannelIntensityRanges getChannelRanges(int channel);

   /**
    * Returns all explicitly-set component ranges for the given channel as a list.
    *
    * @param channel zero-based channel index
    * @return list of size {@link #getChannelNumberOfComponents(int)}
    */
   List<ComponentIntensityRange> getAllComponentRanges(int channel);

   /**
    * Returns the minimum values for all explicitly-set components of the given channel.
    *
    * @param channel zero-based channel index
    * @return list of minimum values, one per component
    */
   List<Long> getComponentMinima(int channel);

   /**
    * Returns the maximum values for all explicitly-set components of the given channel.
    *
    * @param channel zero-based channel index
    * @return list of maximum values, one per component
    */
   List<Long> getComponentMaxima(int channel);

   /**
    * Returns all channel ranges as a list.
    *
    * @return list of {@link ChannelIntensityRanges}, one per channel
    */
   List<ChannelIntensityRanges> getAllChannelRanges();
}
