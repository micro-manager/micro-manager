package org.micromanager.data;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.prefs.Preferences;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.MultiStagePosition;

import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

public class DefaultDisplaySettings implements DisplaySettings {

   /**
    * Clear preferences associated with this class.
    */
   public static void clearPrefs() {
      Preferences prefs = Preferences.userNodeForPackage(DefaultDisplaySettings.class);
      try {
         prefs.clear();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Unable to clear preferences for DefaultDisplaySettings");
      }
   }

   /**
    * Retrieve the display settings that have been saved in the preferences.
    */
   public static DefaultDisplaySettings getStandardSettings() {
      Builder builder = new Builder();
      Preferences prefs = Preferences.userNodeForPackage(DefaultDisplaySettings.class);
      if (prefs == null) {
         // No saved settings.
         return builder.build();
      }
      // We have to convert colors to/from byte arrays, that being the only
      // way to store complex datatypes in Preferences. Fortunately colors
      // aren't that complex.
      // Seven colors because ImageJ only supports 7 channels; put yellow/cyan
      // first for colorblind-friendliness.
      Color[] defaultColors = new Color[] {Color.YELLOW, Color.CYAN,
         Color.MAGENTA, Color.RED, Color.GREEN, Color.BLUE, Color.ORANGE};
      byte[] defaultByteColors = colorsToBytes(defaultColors);
      byte[] preferenceColors = prefs.getByteArray("channelColors", defaultByteColors);
      builder.channelColors(bytesToColors(preferenceColors));

      builder.channelDisplayModeIndex(prefs.getInt("channelDisplayModeIndex", 0));
      builder.histogramUpdateRate(prefs.getDouble("histogramUpdateRate", 0));
      builder.shouldSyncChannels(prefs.getBoolean("shouldSyncChannels", false));
      builder.scaleBarColorIndex(prefs.getInt("scaleBarColorIndex", 0));
      builder.scaleBarLocationIndex(prefs.getInt("scaleBarLocationIndex", 0));
      builder.scaleBarOffsetX(prefs.getInt("scaleBarOffsetX", 15));
      builder.scaleBarOffsetY(prefs.getInt("scaleBarOffsetY", 15));
      builder.scaleBarIsFilled(prefs.getBoolean("scaleBarIsFilled", true));
      builder.shouldShowScaleBar(prefs.getBoolean("shouldShowScaleBar", false));
      builder.shouldAutostretch(prefs.getBoolean("shouldAutostretch", false));
      builder.trimPercentage(prefs.getDouble("trimPercentage", 0));
      builder.shouldUseLogScale(prefs.getBoolean("shouldUseLogScale", false));
      return builder.build();
   }

   /**
    * Set new settings in the preferences.
    */
   public static void setStandardSettings(DisplaySettings settings) {
      Preferences prefs = Preferences.userNodeForPackage(DefaultDisplaySettings.class);
      prefs.putByteArray("channelColors", colorsToBytes(settings.getChannelColors()));
      prefs.putInt("channelDisplayModeIndex", settings.getChannelDisplayModeIndex());
      prefs.putDouble("histogramUpdateRate", settings.getHistogramUpdateRate());
      prefs.putBoolean("shouldSyncChannels", settings.getShouldSyncChannels());
      prefs.putInt("scaleBarColorIndex", settings.getScaleBarColorIndex());
      prefs.putInt("scaleBarLocationIndex", settings.getScaleBarLocationIndex());
      prefs.putInt("scaleBarOffsetX", settings.getScaleBarOffsetX());
      prefs.putInt("scaleBarOffsetY", settings.getScaleBarOffsetY());
      prefs.putBoolean("scaleBarIsFilled", settings.getScaleBarIsFilled());
      prefs.putBoolean("shouldShowScaleBar", settings.getShouldShowScaleBar());
      prefs.putBoolean("shouldAutostretch", settings.getShouldAutostretch());
      prefs.putDouble("trimPercentage", settings.getTrimPercentage());
      prefs.putBoolean("shouldUseLogScale", settings.getShouldUseLogScale());
   }

   /**
    * Convert the provided array of Colors to an array of Bytes, in RGB order.
    */
   private static byte[] colorsToBytes(Color[] colors) {
      byte[] result = new byte[colors.length * 3];
      for (int i = 0; i < colors.length; ++i) {
         result[i * 3] = (byte) colors[i].getRed();
         result[i * 3 + 1] = (byte) colors[i].getGreen();
         result[i * 3 + 2] = (byte) colors[i].getBlue();
      }
      return result;
   }

   /**
    * Reverse the process performed by colorsToBytes().
    */
   private static Color[] bytesToColors(byte[] bytes) {
      Color[] result = new Color[bytes.length / 3];
      for (int i = 0; i < result.length; ++i) {
         int red = ImageUtils.unsignedValue(bytes[i * 3]);
         int green = ImageUtils.unsignedValue(bytes[i * 3 + 1]);
         int blue = ImageUtils.unsignedValue(bytes[i * 3 + 2]);
         result[i] = new Color(red, green, blue);
      }
      return result;
   }

   public static class Builder implements DisplaySettings.DisplaySettingsBuilder {
      private String[] channelNames_ = null;
      private Color[] channelColors_ = null;
      private Integer[] channelContrastMins_ = null;
      private Integer[] channelContrastMaxes_ = null;
      private Double[] channelGammas_ = null;
      private Integer channelDisplayModeIndex_ = null;
      private Double histogramUpdateRate_ = null;
      private Boolean shouldSyncChannels_ = null;
      private Integer scaleBarColorIndex_ = null;
      private Integer scaleBarLocationIndex_ = null;
      private Integer scaleBarOffsetX_ = null;
      private Integer scaleBarOffsetY_ = null;
      private Boolean scaleBarIsFilled_ = null;
      private Boolean shouldShowScaleBar_ = null;
      private Boolean shouldAutostretch_ = null;
      private Double trimPercentage_ = null;
      private Boolean shouldUseLogScale_ = null;

      @Override
      public DefaultDisplaySettings build() {
         return new DefaultDisplaySettings(this);
      }
      
      @Override
      public DisplaySettingsBuilder channelNames(String[] channelNames) {
         channelNames_ = channelNames;
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelColors(Color[] channelColors) {
         channelColors_ = channelColors;
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelContrastMins(Integer[] channelContrastMins) {
         channelContrastMins_ = channelContrastMins;
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelContrastMaxes(Integer[] channelContrastMaxes) {
         channelContrastMaxes_ = channelContrastMaxes;
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelGammas(Double[] channelGammas) {
         channelGammas_ = channelGammas;
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelDisplayModeIndex(Integer channelDisplayModeIndex) {
         channelDisplayModeIndex_ = channelDisplayModeIndex;
         return this;
      }

      @Override
      public DisplaySettingsBuilder histogramUpdateRate(Double histogramUpdateRate) {
         histogramUpdateRate_ = histogramUpdateRate;
         return this;
      }

      @Override
      public DisplaySettingsBuilder shouldSyncChannels(Boolean shouldSyncChannels) {
         shouldSyncChannels_ = shouldSyncChannels;
         return this;
      }

      @Override
      public DisplaySettingsBuilder scaleBarColorIndex(Integer scaleBarColorIndex) {
         scaleBarColorIndex_ = scaleBarColorIndex;
         return this;
      }

      @Override
      public DisplaySettingsBuilder scaleBarLocationIndex(Integer scaleBarLocationIndex) {
         scaleBarLocationIndex_ = scaleBarLocationIndex;
         return this;
      }

      @Override
      public DisplaySettingsBuilder scaleBarOffsetX(Integer scaleBarOffsetX) {
         scaleBarOffsetX_ = scaleBarOffsetX;
         return this;
      }

      @Override
      public DisplaySettingsBuilder scaleBarOffsetY(Integer scaleBarOffsetY) {
         scaleBarOffsetY_ = scaleBarOffsetY;
         return this;
      }

      @Override
      public DisplaySettingsBuilder scaleBarIsFilled(Boolean scaleBarIsFilled) {
         scaleBarIsFilled_ = scaleBarIsFilled;
         return this;
      }

      @Override
      public DisplaySettingsBuilder shouldShowScaleBar(Boolean shouldShowScaleBar) {
         shouldShowScaleBar_ = shouldShowScaleBar;
         return this;
      }

      @Override
      public DisplaySettingsBuilder shouldAutostretch(Boolean shouldAutostretch) {
         shouldAutostretch_ = shouldAutostretch;
         return this;
      }

      @Override
      public DisplaySettingsBuilder trimPercentage(Double trimPercentage) {
         trimPercentage_ = trimPercentage;
         return this;
      }

      @Override
      public DisplaySettingsBuilder shouldUseLogScale(Boolean shouldUseLogScale) {
         shouldUseLogScale_ = shouldUseLogScale;
         return this;
      }

   }

   private String[] channelNames_ = null;
   private Color[] channelColors_ = null;
   private Integer[] channelContrastMins_ = null;
   private Integer[] channelContrastMaxes_ = null;
   private Double[] channelGammas_ = null;
   private Integer channelDisplayModeIndex_ = null;
   private Double histogramUpdateRate_ = null;
   private Boolean shouldSyncChannels_ = null;
   private Integer scaleBarColorIndex_ = null;
   private Integer scaleBarLocationIndex_ = null;
   private Integer scaleBarOffsetX_ = null;
   private Integer scaleBarOffsetY_ = null;
   private Boolean scaleBarIsFilled_ = null;
   private Boolean shouldShowScaleBar_ = null;
   private Boolean shouldAutostretch_ = null;
   private Double trimPercentage_ = null;
   private Boolean shouldUseLogScale_ = null;

   public DefaultDisplaySettings(Builder builder) {
      channelNames_ = builder.channelNames_;
      channelColors_ = builder.channelColors_;
      channelContrastMins_ = builder.channelContrastMins_;
      channelContrastMaxes_ = builder.channelContrastMaxes_;
      channelGammas_ = builder.channelGammas_;
      channelDisplayModeIndex_ = builder.channelDisplayModeIndex_;
      histogramUpdateRate_ = builder.histogramUpdateRate_;
      shouldSyncChannels_ = builder.shouldSyncChannels_;
      scaleBarColorIndex_ = builder.scaleBarColorIndex_;
      scaleBarLocationIndex_ = builder.scaleBarLocationIndex_;
      scaleBarOffsetX_ = builder.scaleBarOffsetX_;
      scaleBarOffsetY_ = builder.scaleBarOffsetY_;
      scaleBarIsFilled_ = builder.scaleBarIsFilled_;
      shouldShowScaleBar_ = builder.shouldShowScaleBar_;
      shouldAutostretch_ = builder.shouldAutostretch_;
      trimPercentage_ = builder.trimPercentage_;
      shouldUseLogScale_ = builder.shouldUseLogScale_;
   }

   @Override
   public String[] getChannelNames() {
      return channelNames_;
   }

   @Override
   public Color[] getChannelColors() {
      return channelColors_;
   }

   @Override
   public Integer[] getChannelContrastMins() {
      return channelContrastMins_;
   }

   @Override
   public Integer[] getChannelContrastMaxes() {
      return channelContrastMaxes_;
   }

   @Override
   public Double[] getChannelGammas() {
      return channelGammas_;
   }

   @Override
   public Integer getChannelDisplayModeIndex() {
      return channelDisplayModeIndex_;
   }

   @Override
   public Double getHistogramUpdateRate() {
      return histogramUpdateRate_;
   }

   @Override
   public Boolean getShouldSyncChannels() {
      return shouldSyncChannels_;
   }

   @Override
   public Integer getScaleBarColorIndex() {
      return scaleBarColorIndex_;
   }

   @Override
   public Integer getScaleBarLocationIndex() {
      return scaleBarLocationIndex_;
   }

   @Override
   public Integer getScaleBarOffsetX() {
      return scaleBarOffsetX_;
   }

   @Override
   public Integer getScaleBarOffsetY() {
      return scaleBarOffsetY_;
   }

   @Override
   public Boolean getScaleBarIsFilled() {
      return scaleBarIsFilled_;
   }

   @Override
   public Boolean getShouldShowScaleBar() {
      return shouldShowScaleBar_;
   }

   @Override
   public Boolean getShouldAutostretch() {
      return shouldAutostretch_;
   }

   @Override
   public Double getTrimPercentage() {
      return trimPercentage_;
   }

   @Override
   public Boolean getShouldUseLogScale() {
      return shouldUseLogScale_;
   }

   @Override
   public DisplaySettingsBuilder copy() {
      return new Builder()
            .channelNames(channelNames_)
            .channelColors(channelColors_)
            .channelContrastMins(channelContrastMins_)
            .channelContrastMaxes(channelContrastMaxes_)
            .channelGammas(channelGammas_)
            .channelDisplayModeIndex(channelDisplayModeIndex_)
            .histogramUpdateRate(histogramUpdateRate_)
            .shouldSyncChannels(shouldSyncChannels_)
            .scaleBarColorIndex(scaleBarColorIndex_)
            .scaleBarLocationIndex(scaleBarLocationIndex_)
            .scaleBarOffsetX(scaleBarOffsetX_)
            .scaleBarOffsetY(scaleBarOffsetY_)
            .scaleBarIsFilled(scaleBarIsFilled_)
            .shouldShowScaleBar(shouldShowScaleBar_)
            .shouldAutostretch(shouldAutostretch_)
            .trimPercentage(trimPercentage_)
            .shouldUseLogScale(shouldUseLogScale_);
   }

   /**
    * For backwards compatibility, generate a DefaultDisplaySettings from
    * a JSONObject.
    */
   public static DefaultDisplaySettings legacyFromJSON(JSONObject tags) {
      if (tags == null) {
         return new Builder().build();
      }
      try {
         Integer color = MDUtils.getChannelColor(tags);
         Color fakeColor = new Color(color, color, color);
         return new Builder()
            .channelNames(new String[] {MDUtils.getChannelName(tags)})
            .channelColors(new Color[] {fakeColor})
            .channelContrastMins(new Integer[] {tags.getInt("ChContrastMin")})
            .channelContrastMaxes(new Integer[] {tags.getInt("ChContrastMax")})
            .build();
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't convert JSON into DisplaySettings");
         return null;
      }
   }

   /**
    * For backwards compatibility, generate a JSONObject representing this
    * DefaultDisplaySettings.
    */
   @Override
   public JSONObject legacyToJSON() {
      try {
         JSONObject result = new JSONObject();
         if (channelNames_ != null) {
            MDUtils.setChannelName(result, channelNames_[0]);
         }
         // TODO: no idea how we represent a color with an int in the current
         // system, but at least using a hashCode() uniquely represents this
         // RGBA color!
         if (channelColors_ != null && channelColors_.length > 0) {
            MDUtils.setChannelColor(result, channelColors_[0].hashCode());
         }
         if (channelContrastMins_ != null && channelContrastMins_.length > 0) {
            result.put("ChContrastMin", channelContrastMins_[0]);
         }
         if (channelContrastMaxes_ != null && channelContrastMaxes_.length > 0) {
            result.put("ChContrastMax", channelContrastMaxes_[0]);
         }
         result.put("histogramUpdateRate", histogramUpdateRate_);
         result.put("shouldSyncChannels", shouldSyncChannels_);
         result.put("scaleBarColorIndex", scaleBarColorIndex_);
         result.put("scaleBarLocationIndex", scaleBarLocationIndex_);
         result.put("shouldShowScaleBar", shouldShowScaleBar_);
         result.put("shouldAutostretch", shouldAutostretch_);
         result.put("trimPercentage", trimPercentage_);
         result.put("shouldUseLogScale", shouldUseLogScale_);
         return result;
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't convert DefaultDisplaySettings to JSON");
         return null;
      }
   }

   /**
    * Generate a textual description of this object. For debugging purposes
    * only.
    */
   @Override
   public String toString() {
      Field[] fields = getClass().getDeclaredFields();
      String result = "<DisplaySettings: ";
      for (Field field : fields) {
         try {
            Object val = field.get(this);
            if (val == null) {
               val = "null";
            }
            result += String.format("\n    %s: %s", field.getName(), val.toString());
         }
         catch (IllegalAccessException e) {
            ReportingUtils.logError(e, "Couldn't access field " + field.getName());
         }
      }
      result += ">";
      return result;
   }
}
