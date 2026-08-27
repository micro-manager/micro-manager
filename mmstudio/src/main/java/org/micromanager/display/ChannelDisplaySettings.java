package org.micromanager.display;

import java.awt.Color;
import java.util.List;
import org.micromanager.display.internal.DefaultChannelDisplaySettings;

/**
 * Stores the display settings for individual channels Coords of image to be displayed.
 * Maintains the state of things like channel color, visibility,
 * min, max, gamma, and histogram range.
 *
 * @author mark
 */
public interface ChannelDisplaySettings {
   /**
    * Builder for ChannelDisplaySettings.
    */
   interface Builder {
      Builder color(Color color);

      Builder colorWhite();

      Builder colorColorBlindFriendly(int number);

      Builder colorRed();

      Builder colorGreen();

      Builder colorBlue();

      Builder colorCyan();

      Builder colorMagenta();

      Builder colorYellow();

      Builder uniformComponentScaling(boolean enable);

      Builder histoRangeBits(int bits);

      Builder useCameraHistoRange(boolean use);

      /**
       * Sets the histogram axis range for floating point images, as pixel values.
       *
       * <p>Only meaningful for float images, whose axis is a real value range rather than
       * a bit depth. Pass {@code Double.NaN} for both to clear it.
       *
       * <p>The default implementation ignores the values, so that builders written before
       * the float axis existed keep compiling; they simply carry no axis range.
       *
       * @param min low end of the axis
       * @param max high end of the axis
       * @return this builder
       */
      default Builder floatHistoRange(double min, double max) {
         return this;
      }

      /**
       * Marks the float histogram axis as chosen by the user.
       *
       * <p>When set, the axis is left exactly as given instead of being widened to take in
       * each newly displayed image.
       *
       * @param pinned true to stop the axis adapting to the data
       * @return this builder
       */
      default Builder floatHistoRangePinned(boolean pinned) {
         return this;
      }

      Builder name(String name);

      Builder groupName(String groupName);

      Builder visible(boolean visible);

      Builder show();

      Builder hide();

      Builder component(int component);

      Builder component(int component, ComponentDisplaySettings settings);

      /**
       * Sets the intensity scaling (min/max) for all components of this channel in one call.
       *
       * <p>This is a convenience alternative to calling
       * {@link #component(int, ComponentDisplaySettings)} once per component.
       * Only min and max are transferred; any gamma values already set on existing
       * component settings are preserved.
       *
       * @param ranges per-component intensity ranges for this channel
       * @return this builder
       */
      Builder intensityScaling(ChannelIntensityRanges ranges);

      int getNumberOfComponents();

      ComponentDisplaySettings getComponentSettings(int component);

      ChannelDisplaySettings build();
   }

   /**
    * Color used to represent this channel, i.e., brightest color of the LUT
    * used to display this channel
    *
    * @return Color for this channel
    */
   Color getColor();

   boolean isUniformComponentScalingEnabled();

   /**
    * Range of this histogram displayed for this channel
    * For now, histogram always starts at zero, so this represents
    * the maximum value on the x-axis of the histogram.
    *
    * @return Maximum value on the x-axis of the histogram expressed as a factor of 2
    */
   int getHistoRangeBits();

   /**
    * Returns whether the camera range is used as histogram range.
    *
    * @return True when historangebits is equal to the maximum intensity coming from the camera
    */
   boolean useCameraRange();

   /**
    * Low end of the histogram axis for floating point images.
    *
    * <p>The default implementation reports no axis range, so that implementations written
    * before the float axis existed keep compiling.
    *
    * @return pixel value, or {@code Double.NaN} if not set
    * @see #hasFloatHistoRange()
    */
   default double getFloatHistoRangeMinimum() {
      return Double.NaN;
   }

   /**
    * High end of the histogram axis for floating point images.
    *
    * @return pixel value, or {@code Double.NaN} if not set
    * @see #hasFloatHistoRange()
    */
   default double getFloatHistoRangeMaximum() {
      return Double.NaN;
   }

   /**
    * Returns whether a float histogram axis range has been recorded.
    *
    * @return true if both ends are set and the high end is strictly above the low end;
    *     an empty or inverted range counts as unset, since it cannot be drawn
    */
   default boolean hasFloatHistoRange() {
      double min = getFloatHistoRangeMinimum();
      double max = getFloatHistoRangeMaximum();
      return !Double.isNaN(min) && !Double.isNaN(max) && max > min;
   }

   /**
    * Returns whether the float histogram axis was chosen by the user.
    *
    * <p>A pinned axis is not widened to include newly displayed images.
    *
    * @return true if the axis should be left as-is
    */
   default boolean isFloatHistoRangePinned() {
      return false;
   }

   /**
    * Indicates whether this is visible.
    *
    * @return True when this channel is visible to the user, false otherwise
    */
   boolean isVisible();

   /**
    * Returns name of the channel that is displayed.
    *
    * @return Name of the channel being displayed.
    */
   String getName();

   /**
    * Name of the channelgroup this channel belongs to.
    *
    * @return Name of the channelGroup this channel belongs to
    */
   String getGroupName();

   int getNumberOfComponents();

   ComponentDisplaySettings getComponentSettings(int component);

   List<ComponentDisplaySettings> getAllComponentSettings();

   Builder copyBuilder();

   Builder copyBuilderWithComponentSettings(int component, ComponentDisplaySettings settings);

   static Builder builder() {
      return DefaultChannelDisplaySettings.builder();
   }
}