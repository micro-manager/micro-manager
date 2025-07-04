///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
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

package org.micromanager.display.internal;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.UserProfile;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Implementation of the DisplaySettings interface.
 */
public final class DefaultDisplaySettings implements DisplaySettings {
   /**
    * Key used to store DisplaySettings in the UserProfile.
    * This key is prepended to the key used to store the DisplaySettings
    * in the UserProfile.
    */
   private static final String PROFILEKEY = "Default_Display_Settings";

   private final double zoom_;
   private final double fps_;
   private final ColorMode mode_;
   private final boolean uniformChannelScaling_;
   private final boolean autostretch_;
   private final boolean useROI_;
   private final double extremaQuantile_;
   private final boolean histogramLogarithmic_;
   private final boolean ignoreZeros_;
   private final List<ChannelDisplaySettings> channelSettings_;

   private static class Builder implements DisplaySettings.Builder {
      private double zoom_ = 1.0;
      private double fps_ = 10.0;
      private ColorMode mode_ = ColorMode.GRAYSCALE;
      private boolean useUniformChannelScaling_ = false;
      private boolean autostretch_ = true;
      private boolean useROI_ = true;
      private boolean histogramLogarithmic_;
      private double extremaQuantile_ = 0.001;
      private boolean ignoreZeros_ = false;
      private List<ChannelDisplaySettings> channelSettings_ = new ArrayList<>();

      private Builder() {
         channelSettings_.add(DefaultChannelDisplaySettings.builder().build());
      }

      @Override
      public Builder zoomRatio(double ratio) {
         Preconditions.checkArgument(ratio > 0.0);
         zoom_ = ratio;
         return this;
      }

      @Override
      public Builder playbackFPS(double fps) {
         Preconditions.checkArgument(fps >= 0.0);
         fps_ = fps;
         return this;
      }

      @Override
      public Builder colorMode(ColorMode mode) {
         mode_ = mode;
         return this;
      }

      @Override
      public Builder colorModeComposite() {
         return colorMode(ColorMode.COMPOSITE);
      }

      @Override
      public Builder colorModeGrayscale() {
         return colorMode(ColorMode.GRAYSCALE);
      }

      @Override
      public Builder colorModeSingleColor() {
         return colorMode(ColorMode.COLOR);
      }

      @Override
      public Builder colorModeHighlightSaturated() {
         return colorMode(ColorMode.HIGHLIGHT_LIMITS);
      }

      @Override
      public Builder uniformChannelScaling(boolean enable) {
         useUniformChannelScaling_ = enable;
         return this;
      }

      @Override
      public Builder autostretch(boolean enable) {
         autostretch_ = enable;
         return this;
      }

      @Override
      public Builder roiAutoscale(boolean enable) {
         useROI_ = enable;
         return this;
      }

      @Override
      public Builder autoscaleIgnoredQuantile(double quantile) {
         Preconditions.checkArgument(quantile >= 0.0);
         extremaQuantile_ = quantile;
         return this;
      }

      @Override
      public Builder autoscaleIgnoredPercentile(double percentile) {
         return autoscaleIgnoredQuantile(0.01 * percentile);
      }

      @Override
      public Builder autoscaleIgnoringZeros(boolean ignoreZeros) {
         ignoreZeros_ = ignoreZeros;
         return this;
      }

      /**
       * Whether the histogram should be shown with a logarithmic (true)
       * or linear (false) y-axis.
       *
       * @param histogramLogarithmic true when y-axis should be logarithmic.
       * @return builder instance
       */
      @Override
      public DisplaySettings.Builder histogramLogarithmic(boolean histogramLogarithmic) {
         histogramLogarithmic_ = histogramLogarithmic;
         return this;
      }

      @Override
      public Builder channel(int channel) {
         Preconditions.checkArgument(channel >= 0);
         while (channelSettings_.size() <= channel) {
            channelSettings_.add(DefaultChannelDisplaySettings.builder().build());
         }
         return this;
      }

      @Override
      public Builder channel(int channel, ChannelDisplaySettings settings) {
         channel(channel);
         channelSettings_.set(channel, settings);
         return this;
      }

      @Override
      public DisplaySettings.Builder channels(Iterable<ChannelDisplaySettings> channelSettings) {
         channelSettings_.clear();
         for (ChannelDisplaySettings channelSetting : channelSettings) {
            channelSettings_.add(channelSetting);
         }
         return this;
      }

      @Override
      public int getNumberOfChannels() {
         return channelSettings_.size();
      }

      @Override
      public ChannelDisplaySettings getChannelSettings(int channel) {
         Preconditions.checkArgument(channel >= 0);
         channel(channel);
         return channelSettings_.get(channel);
      }

      @Override
      public DisplaySettings build() {
         return new DefaultDisplaySettings(this);
      }
   }

   public static DisplaySettings.Builder builder() {
      return new Builder();
   }

   private DefaultDisplaySettings(Builder builder) {
      zoom_ = builder.zoom_;
      fps_ = builder.fps_;
      mode_ = builder.mode_;
      uniformChannelScaling_ = builder.useUniformChannelScaling_;
      autostretch_ = builder.autostretch_;
      useROI_ = builder.useROI_;
      extremaQuantile_ = builder.extremaQuantile_;
      ignoreZeros_ = builder.ignoreZeros_;
      histogramLogarithmic_ = builder.histogramLogarithmic_;
      channelSettings_ =
            new ArrayList<>(builder.channelSettings_);
   }

   @Override
   public double getZoomRatio() {
      return zoom_;
   }

   @Override
   public double getPlaybackFPS() {
      return fps_;
   }

   @Override
   public ColorMode getColorMode() {
      return mode_;
   }

   @Override
   public boolean isUniformChannelScalingEnabled() {
      return uniformChannelScaling_;
   }

   @Override
   public boolean isAutostretchEnabled() {
      return autostretch_;
   }

   @Override
   public boolean isROIAutoscaleEnabled() {
      return useROI_;
   }

   @Override
   public double getAutoscaleIgnoredQuantile() {
      return extremaQuantile_;
   }

   @Override
   public double getAutoscaleIgnoredPercentile() {
      return 100.0 * extremaQuantile_;
   }

   @Override
   public boolean isAutoscaleIgnoringZeros() {
      return ignoreZeros_;
   }

   @Override
   public boolean isHistogramLogarithmic() {
      return histogramLogarithmic_;
   }

   @Override
   public int getNumberOfChannels() {
      return channelSettings_.size();
   }

   @Override
   public ChannelDisplaySettings getChannelSettings(int channel) {
      if (channel >= channelSettings_.size()) {
         return DefaultChannelDisplaySettings.builder().build();
      }
      return channelSettings_.get(channel);
   }

   @Override
   public List<ChannelDisplaySettings> getAllChannelSettings() {
      return new ArrayList<>(channelSettings_);
   }

   @Override
   public List<Color> getAllChannelColors() {
      List<Color> ret = new ArrayList<>(getNumberOfChannels());
      for (ChannelDisplaySettings channelSettings : channelSettings_) {
         ret.add(channelSettings.getColor());
      }
      return ret;
   }

   @Override
   public Color getChannelColor(int channel) {
      return getChannelSettings(channel).getColor();
   }

   @Override
   public List<Boolean> getAllChannelVisibilities() {
      List<Boolean> ret = new ArrayList<>(getNumberOfChannels());
      for (ChannelDisplaySettings channelSettings : channelSettings_) {
         ret.add(channelSettings.isVisible());
      }
      return ret;
   }

   @Override
   public boolean isChannelVisible(int channel) {
      return getChannelSettings(channel).isVisible();
   }


   private static final String ANIMATION_FPS_DOUBLE = "animationFPS_Double";
   private static final String CHANNEL_COLOR_MODE = "channelColorMode";
   private static final String ZOOM_RATIO = "magnification";
   private static final String SHOULD_SYNC_CHANNELS = "shouldSyncChannels";
   private static final String SHOULD_AUTOSTRETCH = "shouldAutostretch";
   private static final String SHOULD_SCALE_WITH_ROI = "shouldScaleWithROI";
   private static final String EXTREMA_PERCENTAGE = "extremaPercentage";
   private static final String IGNORE_ZEROS_AUTOSCALE = "IgnoreZerosWhenAutoscaling";
   private static final String HISTOGRAM_IS_LOGARITHMIC = "histogramIsLogarithmic";


   /**
    * Saves these DisplaySettings in the UserProfile. Implementers are free to
    * save copies of the settings themselves.
    *
    * @param key     Key under which the settings will be stored.
    *                Will be pre-pended with PROFILEKEY
    */
   public void saveToProfile(String key) {
      UserProfile profile = MMStudio.getInstance().profile();
      MutablePropertyMapView mpmv = profile.getSettings(DefaultDisplaySettings.class);
      mpmv.putPropertyMap(PROFILEKEY + "-" + key, toPropertyMap(this));
   }


   /**
    * Restore DisplaySettings from a PropertyMap.
    * If PropertyMap was null, the builder will return defaults.
    * These defaults happen to be the same as the defaults in DefaultDisplaySettings.builder()
    *
    * @param pMap PropertyMap to be restored to DisplaySettings
    * @return restored DisplaySettings.  Any missing component will be replaced
    *     with the (Builder's) default.
    */
   public static DisplaySettings.Builder fromPropertyMap(PropertyMap pMap) {
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
      }

      return ddsb;
   }


   /**
    * Store displaySettings in a propertyMap.
    *
    * @param displaySettings DisplaySettings to be stored
    * @return PropertyMap containing these DisplaySettings
    */
   public static PropertyMap toPropertyMap(DisplaySettings displaySettings) {
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
               .build();
   }

   /**
    * Retrieve DisplaySettings from the User Profile.
    *
    * @param key     Key use to retrieve the DisplaySettings.
    *                Will be pre-pended with PROFILEKEY.
    * @return Stored DisplaySettings or null if none found.
    */
   public static DisplaySettings restoreFromProfile(String key) {
      UserProfile profile = MMStudio.getInstance().profile();
      MutablePropertyMapView mpmv = profile.getSettings(DefaultDisplaySettings.class);
      final String finalKey = PROFILEKEY + "-" + key;
      if (mpmv.containsPropertyMap(finalKey)) {
         PropertyMap propertyMap = mpmv.getPropertyMap(finalKey, null);
         return fromPropertyMap(propertyMap).build();
      }
      return null;
   }

   /**
    * Return the current color mode setting in the profile, or the provided
    * default value. This is specifically available to allow the Snap/Live
    * Manager to default to grayscale instead of the normal default (returned
    * from getStandardSettings) of composite mode.
    *
    * @param key Profile key to use, as per [get|set]StandardSettings.
    */
   public static ColorMode getStandardColorMode(String key,
                                                DisplaySettings.ColorMode defaultVal) {
      UserProfile profile = MMStudio.getInstance().profile();
      key = key + "_";
      Integer mode = profile.getSettings(DefaultDisplaySettings.class)
            .getInteger(CHANNEL_COLOR_MODE, -1);
      if (mode == -1) {
         return defaultVal;
      }
      return DisplaySettings.ColorMode.fromInt(mode);
   }

   /**
    * Convert the provided array of Colors to an array of Integers, in RGB
    * order.
    */
   private static Integer[] colorsToInts(Color[] colors) {
      Integer[] result = new Integer[colors.length * 3];
      for (int i = 0; i < colors.length; ++i) {
         result[i * 3] = colors[i].getRed();
         result[i * 3 + 1] = colors[i].getGreen();
         result[i * 3 + 2] = colors[i].getBlue();
      }
      return result;
   }

   /**
    * Reverse the process performed by colorsToInts().
    */
   private static Color[] intsToColors(Integer[] ints) {
      if (ints == null) {
         return null;
      }
      Color[] result = new Color[ints.length / 3];
      for (int i = 0; i < result.length; ++i) {
         int red = ints[i * 3];
         int green = ints[i * 3 + 1];
         int blue = ints[i * 3 + 2];
         result[i] = new Color(red, green, blue);
      }
      return result;
   }


   @Override
   public DisplaySettings.Builder copyBuilder() {
      DisplaySettings.Builder ret = builder()
            .zoomRatio(zoom_)
            .playbackFPS(fps_)
            .colorMode(mode_)
            .uniformChannelScaling(uniformChannelScaling_)
            .autostretch(autostretch_)
            .roiAutoscale(useROI_)
            .histogramLogarithmic(histogramLogarithmic_)
            .autoscaleIgnoredQuantile(extremaQuantile_)
            .autoscaleIgnoringZeros(ignoreZeros_);
      for (int i = 0; i < getNumberOfChannels(); ++i) {
         ret.channel(i, channelSettings_.get(i));
      }
      return ret;
   }

   @Override
   public DisplaySettings.Builder copyBuilderWithChannelSettings(int channel,
                                                                 ChannelDisplaySettings settings) {
      return copyBuilder().channel(channel, settings);
   }

   @Override
   public DisplaySettings.Builder copyBuilderWithComponentSettings(
         int channel, int component, ComponentDisplaySettings settings) {
      return copyBuilder().channel(channel,
            getChannelSettings(channel)
                  .copyBuilderWithComponentSettings(component, settings)
                  .build());
   }


   // TODO This should go in NonPropertyMapJSONFormats.DisplaySettings
   public static DefaultDisplaySettings legacyFromJSON(JSONObject tags) {
      if (tags == null) {
         return new LegacyBuilder().build();
      }
      try {
         LegacyBuilder builder = new LegacyBuilder();
         // Check for both methods of storing colors (see toJSON, below)
         if (MDUtils.hasChannelColor(tags)) {
            builder.channelColors(new Color[] {new Color(MDUtils.getChannelColor(tags))});
         }
         if (tags.has("ChColors")) {
            JSONArray colorTags = tags.getJSONArray("ChColors");
            Color[] colors = new Color[colorTags.length()];
            for (int i = 0; i < colorTags.length(); ++i) {
               colors[i] = new Color(colorTags.getInt(i));
            }
            builder.channelColors(colors);
         }

         // Reconstruct the channel contrast settings into ContrastSettings
         // objects. Note that gamma and channel visibility are not preserved
         // currently, let alone multi-component values.
         Integer[] minsArr = null;
         Integer[] maxesArr = null;
         if (tags.has("ChContrastMin")) {
            JSONArray mins = tags.getJSONArray("ChContrastMin");
            minsArr = new Integer[mins.length()];
            maxesArr = new Integer[mins.length()];
            for (int i = 0; i < minsArr.length; ++i) {
               minsArr[i] = mins.getInt(i);
            }
         }
         if (tags.has("ChContrastMax")) {
            JSONArray maxes = tags.getJSONArray("ChContrastMax");
            maxesArr = new Integer[maxes.length()];
            if (minsArr == null) {
               minsArr = new Integer[maxes.length()];
            }
            for (int i = 0; i < maxesArr.length; ++i) {
               maxesArr[i] = maxes.getInt(i);
            }
         }
         if (minsArr != null) {
            ArrayList<ContrastSettings> contrastSettings = new ArrayList<>();
            for (int i = 0; i < minsArr.length; ++i) {
               Integer min = minsArr[i];
               Integer max = maxesArr[i];
               contrastSettings.add(
                     new DefaultContrastSettings(min, max, 1.0, true));
            }
            builder.channelContrastSettings(
                  contrastSettings.toArray(new DefaultContrastSettings[] {}));
         }

         if (tags.has(CHANNEL_COLOR_MODE)) {
            builder.channelColorMode(ColorMode.fromInt(
                  tags.getInt(CHANNEL_COLOR_MODE)));
         }
         if (tags.has(ZOOM_RATIO)) {
            builder.zoomRatio(tags.getDouble(ZOOM_RATIO));
         }
         if (tags.has(ANIMATION_FPS_DOUBLE)) {
            builder.playbackFPS(tags.getDouble(ANIMATION_FPS_DOUBLE));
         }
         if (tags.has(SHOULD_SYNC_CHANNELS)) {
            builder.shouldSyncChannels(tags.getBoolean(SHOULD_SYNC_CHANNELS));
         }
         if (tags.has(SHOULD_AUTOSTRETCH)) {
            builder.shouldAutostretch(tags.getBoolean(SHOULD_AUTOSTRETCH));
         }
         if (tags.has(SHOULD_SCALE_WITH_ROI)) {
            builder.shouldScaleWithROI(tags.getBoolean(SHOULD_SCALE_WITH_ROI));
         }
         if (tags.has(EXTREMA_PERCENTAGE)) {
            builder.extremaPercentage(tags.getDouble(EXTREMA_PERCENTAGE));
         }
         return builder.build();
      } catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't convert JSON into DisplaySettings");
         return null;
      }
   }

   @Override
   public String toString() {
      Field[] fields = getClass().getDeclaredFields();
      String result = "<DisplaySettings " + hashCode() + ": ";
      for (Field field : fields) {
         if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            continue;
         }
         try {
            Object val = field.get(this);
            if (val == null) {
               val = "null";
            }
            result += String.format("\n    %s: %s", field.getName(), val.toString());
         } catch (IllegalAccessException e) {
            ReportingUtils.logError(e, "Couldn't access field " + field.getName());
         }
      }
      result += ">";
      return result;
   }


   /**
    * Extracts DisplaySettings from given File.
    *
    * @param sourceFile file to read settings from.
    * @return DisplaySettings restored from file.
    */
   public static DisplaySettings getSavedDisplaySettings(final File sourceFile) {
      if (sourceFile.canRead()) {
         try {
            return fromPropertyMap(PropertyMaps.loadJSON(sourceFile)).build();
         } catch (IOException ioe) {
            ReportingUtils.logError(ioe, "Error reading: " + sourceFile.getPath());
         }
      } else {
         ReportingUtils.logError("No display settings file found at: " + sourceFile.getPath());
      }
      return null;
   }

   /**
    * Saves the current displaySettings to the indicated File.
    * DisplaySettings are saved as a JSON encoded PropertyMap.
    *
    * @param destination File to which these DisplaySettings should be saved
    */
   public void save(File destination) {
      try {
         if (toPropertyMap(this).saveJSON(destination, true, false)) {
            ReportingUtils.logError("Failed to save Display Settings to: "
                  + destination.getPath());
         }
      } catch (IOException ioe) {
         ReportingUtils.logError(ioe, "Failed to save Display Settings to: "
               + destination.getPath());
      }
   }

   /**
    * Saves the current displaySettings to a file in the provided path.
    * This file will be named using a common convention (DisplaySettings.json,
    * defined in PropertyKey.DISPLAY_SETTINGS_FILE_NAME).
    *
    * @param path path under which to create the DisplaySettings file
    */
   public void save(String path) {
      // TODO: test for sanity of input path?      
      File displaySettingsFile = new File(path
            + File.separator + PropertyKey.DISPLAY_SETTINGS_FILE_NAME.key());
      save(displaySettingsFile);
   }



   /////////////////////////////////////Deprecated methods/////////////////////////////////////


   /**
    * Deprecated way of storing default contrast settings.
    */
   @Deprecated
   public static class DefaultContrastSettings implements DisplaySettings.ContrastSettings {
      Integer[] contrastMins_;
      Integer[] contrastMaxes_;
      Double[] gammas_;
      Boolean isVisible_;

      /**
       * Convenience method for single-component settings.
       *
       * @param contrastMin - new value for contrastMin
       * @param contrastMax - new value for contrastMax
       * @param gamma       - new gamma value
       * @param isVisible   - new boolean to indicate visibility
       */
      public DefaultContrastSettings(Integer contrastMin, Integer contrastMax,
                                     Double gamma, Boolean isVisible) {
         contrastMins_ = new Integer[] {contrastMin};
         contrastMaxes_ = new Integer[] {contrastMax};
         gammas_ = new Double[] {gamma};
         isVisible_ = isVisible;
      }

      public DefaultContrastSettings(Integer[] contrastMins,
                                     Integer[] contrastMaxes, Double[] gammas, Boolean isVisible) {
         contrastMins_ = contrastMins;
         contrastMaxes_ = contrastMaxes;
         gammas_ = gammas;
         isVisible_ = isVisible;
      }

      @Override
      @Deprecated
      public Integer[] getContrastMins() {
         return contrastMins_;
      }

      @Override
      @Deprecated
      public Integer getSafeContrastMin(int component, Integer defaultVal) {
         if (component < 0 || contrastMins_ == null
                  || contrastMins_.length <= component) {
            return defaultVal;
         }
         return contrastMins_[component];
      }

      @Override
      @Deprecated
      public Integer[] getContrastMaxes() {
         return contrastMaxes_;
      }

      @Override
      @Deprecated
      public Integer getSafeContrastMax(int component, Integer defaultVal) {
         if (component < 0 || contrastMaxes_ == null
                  || contrastMaxes_.length <= component) {
            return defaultVal;
         }
         return contrastMaxes_[component];
      }

      @Override
      @Deprecated
      public Double[] getContrastGammas() {
         return gammas_;
      }

      @Override
      public Double getSafeContrastGamma(int component, Double defaultVal) {
         if (component < 0 || gammas_ == null
                  || gammas_.length <= component) {
            return defaultVal;
         }
         return gammas_[component];
      }

      @Override
      @Deprecated
      public int getNumComponents() {
         int result = 0;
         if (contrastMins_ != null) {
            result = Math.max(result, contrastMins_.length);
         }
         if (contrastMaxes_ != null) {
            result = Math.max(result, contrastMaxes_.length);
         }
         if (gammas_ != null) {
            result = Math.max(result, gammas_.length);
         }
         return result;
      }

      @Override
      @Deprecated
      public Boolean getIsVisible() {
         return isVisible();
      }

      @Override
      @Deprecated
      public Boolean isVisible() {
         return isVisible_;
      }

      @Override
      public boolean equals(Object obj) {
         if (!(obj instanceof ContrastSettings)) {
            return false;
         }
         ContrastSettings alt = (ContrastSettings) obj;
         if (getNumComponents() != alt.getNumComponents()) {
            return false;
         }
         Integer[] altMins = alt.getContrastMins();
         Integer[] altMaxes = alt.getContrastMaxes();
         Double[] altGammas = alt.getContrastGammas();
         if (((contrastMins_ == null) != (altMins == null))
                  || ((contrastMaxes_ == null) != (altMaxes == null))
                  || ((gammas_ == null) != (altGammas == null))) {
            // Someone's array is null where the other one isn't.
            return false;
         }
         if ((contrastMins_ != null
                  && !Arrays.deepEquals(contrastMins_, altMins))
                  || (contrastMaxes_ != null
                  && !Arrays.deepEquals(contrastMaxes_, altMaxes))
                  || (gammas_ != null && !Arrays.deepEquals(gammas_, altGammas))) {
            // Arrays contain unequal values.
            return false;
         }
         // All arrays have same contents or are both null.
         return (isVisible_ != alt.isVisible());
      }

      @Override
      public String toString() {
         String result = String.format("<ContrastSettings (%d components)", getNumComponents());
         for (int i = 0; i < getNumComponents(); ++i) {
            result += String.format("(%d, %d @ %.2f)", getSafeContrastMin(i, -1),
                     getSafeContrastMax(i, -1), getSafeContrastGamma(i, -1.0));
         }
         if (isVisible_ != null) {
            result += isVisible_ ? " (visible)" : " (hidden)";
         }
         return result + ">";
      }
   }

   @Deprecated
   public static class LegacyBuilder extends Builder
            implements DisplaySettings.DisplaySettingsBuilder {
      @Override
      public DefaultDisplaySettings build() {
         return new DefaultDisplaySettings(this);
      }

      @Override
      public DisplaySettingsBuilder channelColors(Color[] channelColors) {
         for (int i = 0; i < channelColors.length; ++i) {
            if (channelColors[i] == null) {
               continue;
            }
            channel(i, getChannelSettings(i).copyBuilder()
                     .color(channelColors[i]).build());
         }
         return this;
      }

      @Override
      public DisplaySettingsBuilder safeUpdateChannelColor(Color newColor,
                                                           int channelIndex) {
         channel(channelIndex, getChannelSettings(channelIndex).copyBuilder()
                  .color(newColor).build());
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelContrastSettings(ContrastSettings[] contrastSettings) {
         if (contrastSettings == null) {
            return this;
         }
         for (int i = 0; i < contrastSettings.length; ++i) {
            if (contrastSettings[i] == null) {
               continue;
            }
            safeUpdateContrastSettings(contrastSettings[i], i);
         }
         return this;
      }

      @Override
      public DisplaySettingsBuilder safeUpdateContrastSettings(
               ContrastSettings legacySettings, int channelIndex) {
         if (legacySettings == null) {
            return this;
         }
         ChannelDisplaySettings channelSettings = getChannelSettings(channelIndex);
         ChannelDisplaySettings.Builder channelBuilder =
                  channelSettings.copyBuilder();
         for (int j = 0; j < legacySettings.getNumComponents(); ++j) {
            ComponentDisplaySettings.Builder componentBuilder =
                     channelSettings.getComponentSettings(j).copyBuilder();
            if (legacySettings.getContrastMins() != null
                     && legacySettings.getContrastMins()[j] != null) {
               componentBuilder = componentBuilder.scalingMinimum(
                        legacySettings.getContrastMins()[j]);
            }
            if (legacySettings.getContrastMaxes() != null
                     && legacySettings.getContrastMaxes()[j] != null) {
               componentBuilder = componentBuilder.scalingMaximum(
                        legacySettings.getContrastMaxes()[j]);
            }
            if (legacySettings.getContrastGammas() != null
                     && legacySettings.getContrastGammas()[j] != null) {
               componentBuilder = componentBuilder.scalingGamma(
                        legacySettings.getContrastGammas()[j]);
            }
            channelBuilder.component(j, componentBuilder.build());
         }
         if (legacySettings.isVisible() != null) {
            channelBuilder.visible(legacySettings.isVisible());
         }
         channel(channelIndex, channelBuilder.build());
         return this;
      }

      @Override
      public DisplaySettingsBuilder zoom(Double ratio) {
         if (ratio != null) {
            zoomRatio(ratio);
         }
         return this;
      }

      @Override
      @Deprecated
      public DisplaySettingsBuilder magnification(Double ratio) {
         return zoom(ratio);
      }

      @Override
      public DisplaySettingsBuilder animationFPS(Double animationFPS) {
         if (animationFPS != null) {
            playbackFPS(animationFPS);
         }
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelColorMode(ColorMode channelColorMode) {
         if (channelColorMode != null) {
            colorMode(channelColorMode);
         }
         return this;
      }

      @Override
      public DisplaySettingsBuilder shouldSyncChannels(Boolean shouldSyncChannels) {
         if (shouldSyncChannels != null) {
            uniformChannelScaling(shouldSyncChannels);
         }
         return this;
      }

      @Override
      public DisplaySettingsBuilder shouldAutostretch(Boolean shouldAutostretch) {
         if (shouldAutostretch != null) {
            autostretch(shouldAutostretch);
         }
         return this;
      }

      @Override
      public DisplaySettingsBuilder shouldScaleWithROI(Boolean shouldScaleWithROI) {
         if (shouldScaleWithROI != null) {
            roiAutoscale(shouldScaleWithROI);
         }
         return this;
      }

      @Override
      public DisplaySettingsBuilder extremaPercentage(Double extremaPercentage) {
         if (extremaPercentage != null) {
            autoscaleIgnoredPercentile(extremaPercentage);
         }
         return this;
      }
   }

   @Override
   @Deprecated
   public ContrastSettings[] getChannelContrastSettings() {
      ContrastSettings[] ret = new ContrastSettings[getNumberOfChannels()];
      for (int i = 0; i < getNumberOfChannels(); ++i) {
         ret[i] = getSafeContrastSettings(i, null);
      }
      return ret;
   }

   @Override
   @Deprecated
   public ContrastSettings getSafeContrastSettings(int index,
                                                   ContrastSettings defaultVal) {
      if (index < 0 || index >= getNumberOfChannels()) {
         return defaultVal;
      }
      ChannelDisplaySettings channelSettings = getChannelSettings(index);
      int nComponents = channelSettings.getNumberOfComponents();
      Integer[] mins = new Integer[nComponents];
      Integer[] maxes = new Integer[nComponents];
      Double[] gammas = new Double[nComponents];
      for (int j = 0; j < nComponents; ++j) {
         ComponentDisplaySettings componentSettings =
                  channelSettings.getComponentSettings(j);
         long min = componentSettings.getScalingMinimum();
         mins[j] = min > Integer.MAX_VALUE ? null : (int) min;
         long max = componentSettings.getScalingMaximum();
         maxes[j] = max > Integer.MAX_VALUE ? null : (int) max;
         gammas[j] = componentSettings.getScalingGamma();
      }
      return new DefaultContrastSettings(mins, maxes, gammas,
               channelSettings.isVisible());
   }

   @Override
   @Deprecated
   public Integer getSafeContrastMin(int index, int component,
                                     Integer defaultVal) {
      if (index < 0 || index >= getNumberOfChannels()) {
         return defaultVal;
      }
      ChannelDisplaySettings channelSettings = getChannelSettings(index);
      if (component < 0 || component >= channelSettings.getNumberOfComponents()) {
         return defaultVal;
      }
      long min = channelSettings.getComponentSettings(component).getScalingMinimum();
      return min > Integer.MAX_VALUE ? defaultVal : (int) min;
   }

   @Override
   @Deprecated
   public Integer getSafeContrastMax(int index, int component,
                                     Integer defaultVal) {
      if (index < 0 || index >= getNumberOfChannels()) {
         return defaultVal;
      }
      ChannelDisplaySettings channelSettings = getChannelSettings(index);
      if (component < 0 || component >= channelSettings.getNumberOfComponents()) {
         return defaultVal;
      }
      long max = channelSettings.getComponentSettings(component).getScalingMaximum();
      return max > Integer.MAX_VALUE ? defaultVal : (int) max;
   }

   @Override
   @Deprecated
   public Double getSafeContrastGamma(int index, int component,
                                      Double defaultVal) {
      if (index < 0 || index >= getNumberOfChannels()) {
         return defaultVal;
      }
      ChannelDisplaySettings channelSettings = getChannelSettings(index);
      if (component < 0 || component >= channelSettings.getNumberOfComponents()) {
         return defaultVal;
      }
      return channelSettings.getComponentSettings(component).getScalingGamma();
   }

   @Override
   @Deprecated
   public Boolean getSafeIsVisible(int index, Boolean defaultVal) {
      if (index < 0 || index >= getNumberOfChannels()) {
         return defaultVal;
      }
      return getChannelSettings(index).isVisible();
   }

   @Override
   @Deprecated
   public DisplaySettings.ColorMode getChannelColorMode() {
      return getColorMode();
   }

   @Override
   @Deprecated
   public Boolean getShouldSyncChannels() {
      return null;
   }

   @Override
   @Deprecated
   public DisplaySettingsBuilder copy() {
      DisplaySettings.Builder ret = new LegacyBuilder()
               .zoomRatio(zoom_)
               .playbackFPS(fps_)
               .colorMode(mode_)
               .uniformChannelScaling(uniformChannelScaling_)
               .autostretch(autostretch_)
               .roiAutoscale(useROI_)
               .histogramLogarithmic(histogramLogarithmic_)
               .autoscaleIgnoredQuantile(extremaQuantile_)
               .autoscaleIgnoringZeros(ignoreZeros_);
      for (int i = 0; i < getNumberOfChannels(); ++i) {
         ret.channel(i, channelSettings_.get(i));
      }
      return (LegacyBuilder) ret;
   }

}