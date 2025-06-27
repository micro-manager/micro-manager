///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display;


import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.UserProfile;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.internal.DefaultChannelDisplaySettings;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.internal.MMStudio;
import org.micromanager.propertymap.MutablePropertyMapView;


/**
 * This class defines the parameters that control how a given DisplayWindow
 * displays data from the Datastore.
 * It is immutable; construct it with a DisplaySettings.Builder.
 * You are not expected to implement this interface; it is here to describe how
 * you can interact with DisplaySettings created by Micro-Manager itself. If
 * you need a DisplaySettings.Builder, you can generate one via the
 * {@link org.micromanager.display.DisplayManager} displaySettingsBuilder()
 * method or by using one of the copy() methods of an existing DisplaySettings instance.
 * This class uses a Builder pattern. Please see
 * https://micro-manager.org/wiki/Using_Builders for more information.
 */
public interface DisplaySettings {

   /**
    * Keys used by Studio for Window positioning.
    */
   String ALBUM_DISPLAY = "ALBUM_DISPLAY";
   String PREVIEW_DISPLAY = "PREVIEW_DISPLAY";
   String MDA_DISPLAY = "MDA_DISPLAY";

   /**
    * Key used to store DisplaySettings in the UserProfile.
    * This key is prepended to the key used to store the DisplaySettings
    * in the UserProfile.
    */
   String PROFILEKEY = "Default_Display_Settings";

   /**
    * Builder for DisplaySettings.  Get an instance using the
    * {@link org.micromanager.display.DisplayManager} displaySettingsBuilder()
    * method or by using one of the copy() methods of an existing DisplaySettings instance.
    */
   interface Builder {
      Builder zoomRatio(double ratio);

      Builder playbackFPS(double fps);

      /**
       * Color mode or lookup table for displaying the image.
       *
       * @param mode ColorMode to appply
       * @return Builder
       */
      Builder colorMode(ColorMode mode);

      Builder colorModeComposite();

      Builder colorModeGrayscale();

      Builder colorModeSingleColor();

      Builder colorModeHighlightSaturated();

      /**
       * Whether to use the same intensity scaling for every channel.
       *
       * @param enable flag
       * @return Builder
       */
      Builder uniformChannelScaling(boolean enable);

      /**
       * Whether to continuously apply autoscale.
       *
       * @param enable flag
       * @return Builder
       */
      Builder autostretch(boolean enable);

      Builder roiAutoscale(boolean enable);

      Builder autoscaleIgnoredQuantile(double quantile);

      Builder autoscaleIgnoredPercentile(double percentile);

      Builder autoscaleIgnoringZeros(boolean ignoreZeros);

      /**
       * Whether the histogram should be shown with a logarithmic (true)
       * or linear (false) y-axis.
       *
       * @param histogramLogarithmic true when y-axis should be logarithmic.
       * @return builder instance
       */
      Builder histogramLogarithmic(boolean histogramLogarithmic);

      /**
       * Increases the number of ChannelDisplaySettings in this Builder to
       * the given number.  DefaultSettings for each channel will be added.
       *
       * @param channel Final number of ChannelDisplaySettings
       * @return builder instance to enable chaining commands
       */
      Builder channel(int channel);

      /**
       * Sets the ChannelDisplaySettings for the given channel.
       * If the number of ChannelDisplaySettings is smaller than the given channel
       * number, "missing" channels will be added (using Default Display Settings).
       *
       * @param channel Indicates the number of the channel for which to set
       *                the DisplaySettings
       * @return builder instance to enable chaining commands
       */
      Builder channel(int channel, ChannelDisplaySettings settings);

      /**
       * Replaces ChannelDisplaySettings with those given.
       * If provided with null or an empty list, current ChannelDisplaySettings
       * will be removed.
       *
       * @param channelSettings iterable with ChannelDisplaySettings to replace the current
       *                        ones.  Can be empty or null, in which case ChannelDisplaySettings
       *                        will be removed from this builder.
       * @return builder instance to enable chaining commands
       */
      Builder channels(Iterable<ChannelDisplaySettings> channelSettings);

      /**
       * Adds a key to the DisplaySettings that will be used to position the Windows.
       *
       * @param key Key to use for positioning the DisplayWindow. New DisplayWindows with the
       *            same key will be positioned at the same position
       * @return builder instance to enable chaining commands
       */
      Builder windowPositionKey(String key);

      /**
       * Sets the key under which these DisplaySettings will be stored in the profile.
       * This key allows the DisplaySettings to keep an up-to-date copy in the profile.
       *
       * @param profile UserProfile to which these DisplaySettings will be saved.
       * @param key Used to identify these DisplaySettings in the profile.
       * @return builder instance to enable chaining commands
       */
      Builder profileKey(UserProfile profile, String key);

      /**
       * Number of ChannelDisplaySettings in this builder.  Not sure why a builder needs this...
       *
       * @return Number of ChannelDisplaySettings in this Builder.
       */
      int getNumberOfChannels();

      /**
       * Returns the ChannelDisplaySettings for the given channel number.
       * If this, or any channel with lower number, does not exist, it will be created,
       * and filled with DefaultChannelDisplaySettings.
       *
       * @param channel Number (index) of the requested channel
       * @return ChannelDisplaySettings for the requested channel.
       */
      ChannelDisplaySettings getChannelSettings(int channel); // Creates channel if necessary

      DisplaySettings build();
   }

   /**
    * Zoom level expressed as a ratio (i.e. 1.0 denotes that each pixel in the
    * image occupies 1 pixel on the screen, 0.5 indicates that 4 image pixels
    * are combined in 1 screen pixel.
    *
    * @return Zoom ratio
    */
   double getZoomRatio();


   double getPlaybackFPS();

   /**
    * Color mode or lookup table for displaying the image.
    *
    * @return ColorMode used by these DisplaySettings
    */
   ColorMode getColorMode();

   /**
    * Whether to use the same intensity scaling for every channel.
    *
    * @return true if all channels use the same intensity scaling
    */
   boolean isUniformChannelScalingEnabled();

   /**
    * Whether to continuously apply autoscale.
    *
    * @return true if AutoStretch is enabled
    */
   boolean isAutostretchEnabled();

   /**
    * Whether to only look at the ROI when autoscaling.
    *
    * @return true if only the ROI should be taken into account when autoscaling
    */
   boolean isROIAutoscaleEnabled();

   /**
    * When autoscaling, the minimum value will have this fraction of pixels
    * with lower intensities, and the maximum value will have this fraction
    * of pixels with higher intensities.
    *
    * @return Number used in Autostretch mode to determine where to set the
    *     white and black points.  Expressed as fraction.
    */
   double getAutoscaleIgnoredQuantile();

   /**
    * When autoscaling, the minimum value will have this fraction of pixels
    * with lower intensities, and the maximum value will have this fraction
    * of pixels with higher intensities.
    *
    * @return Number used in Autostretch mode to determine where to set the
    *     white and black points.  Expressed as percentage.
    */
   double getAutoscaleIgnoredPercentile();


   /**
    * When autoscaling, take zero values into account or not?
    * This can be useful for images that contain artificial zero values.
    *
    * @return Whether zero pixel values are ignored when autoscaling
    */
   boolean isAutoscaleIgnoringZeros();

   /**
    * Whether the y-axis of the histogram uses a logarithmic or linear scale.
    *
    * @return Logarithmic scale when true.
    */
   boolean isHistogramLogarithmic();

   /**
    * Returns the number of channels in these DisplaySettings
    * Note that this number may be different from the number of the channels
    * in the image being shown.
    *
    * @return number of channels in these DisplaySettings
    */
   int getNumberOfChannels();


   ChannelDisplaySettings getChannelSettings(int channel);

   List<ChannelDisplaySettings> getAllChannelSettings();

   // Convenience methods
   List<Color> getAllChannelColors();

   Color getChannelColor(int channel);

   List<Boolean> getAllChannelVisibilities();

   boolean isChannelVisible(int channel);

   String getWindowPositionKey();

   String getProfileKey();

   UserProfile getProfile();

   Builder copyBuilder();

   Builder copyBuilderWithChannelSettings(int channel, ChannelDisplaySettings settings);

   Builder copyBuilderWithComponentSettings(
         int channel, int component, ComponentDisplaySettings settings);


   /**
    * Restore DisplaySettings from a PropertyMap.
    * If PropertyMap was null, the builder will return defaults.
    * These defaults happen to be the same as the defaults in DefaultDisplaySettings.builder()
    *
    * @param pMap PropertyMap to be restored to DisplaySettings
    * @return restored DisplaySettings.  Any missing component will be replaced
    *     with the (Builder's) default.
    */
   static DisplaySettings.Builder fromPropertyMap(PropertyMap pMap) {
      DisplaySettings.Builder ddsb = DefaultDisplaySettings.builder();

      if (pMap != null) {
         if (pMap.containsDouble(PropertyKey.ZOOM_RATIO.key())) {
            ddsb.zoomRatio(pMap.getDouble(PropertyKey.ZOOM_RATIO.key(), 1.0));
         }
         if (pMap.containsDouble(PropertyKey.PLAYBACK_FPS.key())) {
            ddsb.playbackFPS(pMap.getDouble(PropertyKey.PLAYBACK_FPS.key(), 10.0));
         }
         if (pMap.containsStringForEnum(PropertyKey.COLOR_MODE.key(), ColorMode.class)) {
            ddsb.colorMode(pMap.getStringAsEnum(PropertyKey.COLOR_MODE.key(),
                     ColorMode.class, ColorMode.GRAYSCALE));
         }
         if (pMap.containsBoolean(PropertyKey.UNIFORM_CHANNEL_SCALING.key())) {
            ddsb.uniformChannelScaling(pMap.getBoolean(
                     PropertyKey.UNIFORM_CHANNEL_SCALING.key(), false));
         }
         if (pMap.containsBoolean(PropertyKey.AUTOSTRETCH.key())) {
            ddsb.autostretch(pMap.getBoolean(PropertyKey.AUTOSTRETCH.key(), true));
         }
         if (pMap.containsBoolean(PropertyKey.HISTOGRAM_IS_LOGARITHMIC.key())) {
            ddsb.histogramLogarithmic(pMap.getBoolean(PropertyKey.HISTOGRAM_IS_LOGARITHMIC.key(),
                     false));
         }
         if (pMap.containsBoolean(PropertyKey.ROI_AUTOSCALE.key())) {
            ddsb.roiAutoscale(pMap.getBoolean(PropertyKey.ROI_AUTOSCALE.key(), true));
         }
         if (pMap.containsBoolean(PropertyKey.IGNORE_ZEROS_AUTOSCALE.key())) {
            ddsb.autoscaleIgnoringZeros(pMap.getBoolean(PropertyKey.IGNORE_ZEROS_AUTOSCALE.key(),
                     false));
         }
         if (pMap.containsDouble(PropertyKey.AUTOSCALE_IGNORED_QUANTILE.key())) {
            ddsb.autoscaleIgnoredQuantile(pMap.getDouble(PropertyKey
                              .AUTOSCALE_IGNORED_QUANTILE.key(),
                     0.001));
         }
         if (pMap.containsPropertyMapList(PropertyKey.CHANNEL_SETTINGS.key())) {
            List<PropertyMap> propertyMapList = pMap.getPropertyMapList(
                     PropertyKey.CHANNEL_SETTINGS.key(), new ArrayList<>());
            for (int i = 0; i < propertyMapList.size(); i++) {
               ddsb.channel(i, DefaultChannelDisplaySettings
                        .fromPropertyMap(propertyMapList.get(i)));
            }
         }
         if (pMap.containsString(PropertyKey.WINDOW_POSITION_KEY.key())) {
            ddsb.windowPositionKey(pMap.getString(PropertyKey.WINDOW_POSITION_KEY.key(),
                     null));
         }
         if (pMap.containsString(PropertyKey.PROFILE_KEY.key())) {
            ddsb.profileKey(MMStudio.getInstance().profile(),
                     pMap.getString(PropertyKey.PROFILE_KEY.key(), null));
         }
      }

      return ddsb;
   }

   /**
    * Store displaySettings in a propertyMap.
    *
    * @param displaySettings DisplaySettings to be stored
    * @return PropertyMap containing these DisplaySettings
    */
   static PropertyMap toPropertyMap(DisplaySettings displaySettings) {
      List<PropertyMap> channelSettings = new ArrayList<>();
      for (ChannelDisplaySettings cs : displaySettings.getAllChannelSettings()) {
         channelSettings.add(((DefaultChannelDisplaySettings) cs).toPropertyMap());
      }

      return PropertyMaps.builder()
               .putDouble(PropertyKey.ZOOM_RATIO.key(), displaySettings.getZoomRatio())
               .putDouble(PropertyKey.PLAYBACK_FPS.key(), displaySettings.getPlaybackFPS())
               .putEnumAsString(PropertyKey.COLOR_MODE.key(), displaySettings.getColorMode())
               .putBoolean(PropertyKey.UNIFORM_CHANNEL_SCALING.key(),
                        displaySettings.isUniformChannelScalingEnabled())
               .putBoolean(PropertyKey.AUTOSTRETCH.key(), displaySettings.isAutostretchEnabled())
               .putBoolean(PropertyKey.HISTOGRAM_IS_LOGARITHMIC.key(),
                        displaySettings.isHistogramLogarithmic())
               .putBoolean(PropertyKey.ROI_AUTOSCALE.key(), displaySettings.isROIAutoscaleEnabled())
               .putBoolean(PropertyKey.IGNORE_ZEROS_AUTOSCALE.key(),
                        displaySettings.isAutoscaleIgnoringZeros())
               .putDouble(PropertyKey.AUTOSCALE_IGNORED_QUANTILE.key(),
                        displaySettings.getAutoscaleIgnoredQuantile())
               .putPropertyMapList(PropertyKey.CHANNEL_SETTINGS.key(), channelSettings)
               .putString(PropertyKey.WINDOW_POSITION_KEY.key(),
                        displaySettings.getWindowPositionKey())
               .putString(PropertyKey.PROFILE_KEY.key(), displaySettings.getWindowPositionKey())
               .build();
   }


   /**
    * Retrieve DisplaySettings from the User Profile.
    *
    * @param profile UserProfile to restore these Display Settings from.
    * @param key     Key use to retrieve the DisplaySettings.
    *                Will be pre-pended with PROFILEKEY.
    * @return Stored DisplaySettings or null if none found.
    */
   static DisplaySettings.Builder restoreFromProfile(UserProfile profile, String key) {
      MutablePropertyMapView mpmv = profile.getSettings(DefaultDisplaySettings.class);
      final String finalKey = new StringBuilder(PROFILEKEY).append("-").append(key).toString();
      if (mpmv.containsPropertyMap(finalKey)) {
         PropertyMap propertyMap = mpmv.getPropertyMap(finalKey, null);
         return DisplaySettings.fromPropertyMap(propertyMap).profileKey(profile, key);
      }
      return null;
   }


   /**
    * ColorMode enums.
    */
   enum ColorMode {
      // TODO Integer indices should be implementation detail of file format
      COLOR(0), COMPOSITE(1), GRAYSCALE(2), HIGHLIGHT_LIMITS(3), FIRE(4),
      RED_HOT(5), @Deprecated SPECTRUM(6);

      private final int index_;

      ColorMode(int index) {
         index_ = index;
      }

      @Deprecated
      public int getIndex() {
         return index_;
      }

      @Deprecated
      public static ColorMode fromInt(int index) {
         for (ColorMode mode : ColorMode.values()) {
            if (mode.getIndex() == index) {
               return mode;
            }
         }
         return null;
      }
   }


   //////////////////////////////// Deprecated methods below ////////////////////////////////


   /**
    * This object contains contrast settings for a single channel. It is used
    * to set the minimum contrast value (treated as black), maximum contrast
    * value (treated as white, or full-intensity for colored displays), and
    * gamma (how linear the mapping is between min and max).
    * This object is used for both single-component (i.e. grayscale) and
    * multi-component (e.g. RGB) channels.
    * You can create a new ContrastSettings object via
    * DisplayManager.createContrastSettings().
    *
    * @deprecated TODO: explain
    */
   @Deprecated
   interface ContrastSettings {
      /**
       * Return the array of minimum contrast settings for all components.
       *
       * @return array of minimum contrast settings (i.e. the intensity value
       *     in the image that results in black display) for all components. May
       *     be null.
       */
      @Deprecated
      Integer[] getContrastMins();

      /**
       * Return the minimum contrast setting for this channel and the
       * specified component. If the contrastMins property is not set or is
       * of inadequate length, then the provided default value will be
       * returned instead.
       *
       * @param component  The component index to use. For example, for an
       *                   RGB image, a value of 0 would indicate the red component.
       *                   For single-component images, use an index of 0.
       * @param defaultVal Value to return if there is no value available for
       *                   the specified component number.
       * @return The minimum contrast setting for the component
       */
      @Deprecated
      Integer getSafeContrastMin(int component, Integer defaultVal);

      /**
       * Return the array of maximum contrast settings for all components.
       *
       * @return array of maximum contrast settings (i.e. the intensity value
       *     in the image that results in the brightest display value) for all
       *     components. May be null.
       */
      @Deprecated
      Integer[] getContrastMaxes();

      /**
       * Return the maximum contrast setting for this channel and the
       * specified component. If the contrastMaxes property is not set or is
       * of inadequate length, then the provided default value will be
       * returned instead.
       *
       * @param component  The component index to use. For example, for an
       *                   RGB image, a value of 0 would indicate the red component.
       *                   For single-component images, use an index of 0.
       * @param defaultVal Value to return if there is no value available for
       *                   the specified component number.
       * @return The maximum contrast setting for the component
       */
      @Deprecated
      Integer getSafeContrastMax(int component, Integer defaultVal);

      /**
       * Return the array of gamma settings for all components. Note that
       * multi-component images do not currently make use of the gamma setting.
       *
       * @return Array of gamma settings for all components. May be null.
       */
      @Deprecated
      Double[] getContrastGammas();

      /**
       * Return the gamma setting for this channel and the specified component.
       * If the contrastGammas property is not set or is of inadequate length,
       * then the provided default value will be returned instead. Note that
       * multi-component images do not currently make use of the gamma setting.
       *
       * @param component  The component index to use. For example, for an
       *                   RGB image, a value of 0 would indicate the red component.
       *                   For single-component images, use an index of 0.
       * @param defaultVal Value to return if there is no value available for
       *                   the specified component number.
       * @return The gamma setting controlling how linear the contrast mapping
       *     is.
       */
      @Deprecated
      Double getSafeContrastGamma(int component, Double defaultVal);

      /**
       * Nonstandard alias for {@code isVisible}.
       *
       * @return Flag indicating whether the channel is currently displayed
       * @deprecated Use {@link #isVisible()}
       */
      @Deprecated
      Boolean getIsVisible();

      /**
       * Return true if the channel is currently displayed, and false
       * otherwise. This is only relevant when the display is showing
       * multiple channels at the same time (i.e. ColorMode is COMPOSITE); in
       * all other modes this value is ignored.
       *
       * @return Flag indicating whether the channel is currently displayed
       * @deprecated Use isVisible
       */
      @Deprecated
      Boolean isVisible();

      /**
       * Return the number of components this ContrastSettings object has
       * information for. This will be the length of the smallest array from
       * the contrastMins, contrastMaxes, and contrastGammas attributes. Note
       * that it is still possible for there to be nulls in one of those
       * arrays.
       *
       * @return The number of components this object has information for.
       */
      @Deprecated
      int getNumComponents();
   }

   /**
    * Deprecated version of a DisplaySettings Builder.
    *
    * @deprecated Use DisplaySettings.Builder instead
    */
   @Deprecated
   interface DisplaySettingsBuilder extends Builder {
      /**
       * Construct a DisplaySettings from the DisplaySettingsBuilder. Call
       * this once you are finished setting all DisplaySettings parameters.
       *
       * @return Build object
       */
      @Override
      @Deprecated
      DisplaySettings build();

      // The following functions each set the relevant value for the
      // DisplaySettings. See the getters of the DisplaySettings, below,
      // for information on the meaning of these fields.
      @Deprecated
      DisplaySettingsBuilder channelColors(Color[] channelColors);

      /**
       * "Safely" update the channelColors property to include the new
       * provided color at the specified index. If the channelColors
       * property is null or is not large enough to incorporate the specified
       * index, then a new array will be created that is long enough, values
       * will be copied across, and any missing values will be null.
       *
       * @param newColor     New color for the specified channel.
       * @param channelIndex Index into the channelColors array.
       * @return builder to be used to build the new DisplaySettings
       */
      @Deprecated
      DisplaySettingsBuilder safeUpdateChannelColor(Color newColor,
                                                    int channelIndex);

      @Deprecated
      DisplaySettingsBuilder channelContrastSettings(ContrastSettings[] contrastSettings);

      /**
       * "Safely" update the contrastSettings property to have the provided
       * ContrastSettings object at the specified index. If the
       * contrastSettings property is null or is not large enough to incorporate
       * the specified index, then a new array will be created that is long
       * enough, values will be copied across, and any missing values will be
       * null.
       *
       * @param newSettings  New ContrastSettings for the channel.
       * @param channelIndex Index into the contrastSettings array.
       * @return builder to be used to build the new DisplaySettings
       */
      @Deprecated
      DisplaySettingsBuilder safeUpdateContrastSettings(
            ContrastSettings newSettings, int channelIndex);

      @Deprecated
      DisplaySettingsBuilder magnification(Double magnification);

      @Deprecated
      DisplaySettingsBuilder zoom(Double ratio);

      @Deprecated
      DisplaySettingsBuilder animationFPS(Double animationFPS);

      @Deprecated
      DisplaySettingsBuilder channelColorMode(ColorMode channelColorMode);

      @Deprecated
      DisplaySettingsBuilder shouldSyncChannels(Boolean shouldSyncChannels);

      @Deprecated
      DisplaySettingsBuilder shouldAutostretch(Boolean shouldAutostretch);

      @Deprecated
      DisplaySettingsBuilder shouldScaleWithROI(Boolean shouldScaleWithROI);

      @Deprecated
      DisplaySettingsBuilder extremaPercentage(Double extremaPercentage);
   }

   /**
    * Generate a new DisplaySettingsBuilder whose values are initialized to be
    * the values of this DisplaySettings.
    *
    * @return Copy of the DisplaySettingsBuilder
    */
   @Deprecated
   DisplaySettingsBuilder copy();


   /**
    * The object containing contrast information for each channel.
    *
    * @return An array of ContrastSettings objects, one per channel. May
    *     potentially be null or of inadequate length.
    */
   @Deprecated
   ContrastSettings[] getChannelContrastSettings();

   /**
    * Safely retrieve the ContrastSettings object for the specified channel. If
    * the contrastSettings property is null, is too small to have a value for
    * the given index, or has a value of null for that index, then the provided
    * default value will be returned instead.
    *
    * @param index      Channel index to get the ContrastSettings for
    * @param defaultVal Default value to return if no contrast setting is
    *                   available.
    * @return Contrast settings for the specified channel
    */
   @Deprecated
   ContrastSettings getSafeContrastSettings(int index, ContrastSettings defaultVal);

   /**
    * Safely extract the channel contrast min property from the contrast
    * settings for the specified channel and component. If the
    * contrastSettings property is null, is too small to have a value for the
    * given index, has a value of null for the given index, or has a
    * ContrastSettings object whose contrastMin property is null, then the
    * provided default value will be returned instead.
    *
    * @param index      Channel index to get the contrast min for.
    * @param component  Component index to get the contrast min for.
    * @param defaultVal Default value to return if no contrast min is
    *                   available.
    * @return Black point for the specified channel/component
    */
   @Deprecated
   Integer getSafeContrastMin(int index, int component, Integer defaultVal);

   /**
    * Safely extract the channel contrast max property from the contrast
    * settings for the specified channel and component. If the
    * contrastSettings property is null, is too small to have a value for the
    * given index, has a value of null for the given index, or has a
    * ContrastSettings object whose contrastMax property is null, then the
    * provided default value will be returned instead.
    *
    * @param index      Channel index to get the contrast max for.
    * @param component  Component index to get the contrast max for.
    * @param defaultVal Default value to return if no contrast max is
    *                   available.
    * @return White point for the specified channel/component
    */
   @Deprecated
   Integer getSafeContrastMax(int index, int component, Integer defaultVal);

   /**
    * Safely extract the channel contrast gamma property from the contrast
    * settings for the specified channel and component. If the
    * contrastSettings property is null, is too small to have a value for the
    * given index, has a value of null for the given index, or has a
    * ContrastSettings object whose contrastGamma property is null, then the
    * provided default value will be returned instead.
    *
    * @param index      Channel index to get the contrast gamma for.
    * @param component  Component index to get the contrast gamma for.
    * @param defaultVal Default value to return if no contrast gamma is
    *                   available.
    * @return Gamma for the specified channel/component
    */
   @Deprecated
   Double getSafeContrastGamma(int index, int component, Double defaultVal);

   /**
    * Safely determine if the specified channel is visible when in composite
    * view mode. If the contrastSettings property is null, is too small to
    * have a value for the given index, has a value of null for the given
    * index, or has a ContrastSettings object whose isVisible property is null,
    * then the provided default value will be returned instead.
    *
    * @param index      Channel index to get visibility for.
    * @param defaultVal Default value to return if no visibility is available.
    * @return Flag indicating visibility of the specified channel
    */
   @Deprecated
   Boolean getSafeIsVisible(int index, Boolean defaultVal);


   /**
    * The index into the "Display mode" control.
    *
    * @return index into the "Display mode" control
    * @deprecated - use getColorMode() instead
    */
   @Deprecated
   ColorMode getChannelColorMode();

   /**
    * Whether histogram settings should be synced across channels.
    *
    * @return True if histograms should sync between channels
    */
   @Deprecated
   Boolean getShouldSyncChannels();


   @Deprecated
   String FILENAME = "displaySettings.txt";
}