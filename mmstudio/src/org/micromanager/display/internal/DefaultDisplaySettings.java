package org.micromanager.display.internal;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Coords;
import org.micromanager.display.DisplaySettings;
import org.micromanager.MultiStagePosition;

import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;

import org.micromanager.PropertyMap;

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
    * Note: we explicitly don't cache these settings, to ensure that
    * displays don't end up with copies of the same settings.
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
      // TODO: should we store user data in the display prefs?
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
      private Color[] channelColors_ = null;
      private Integer[] channelContrastMins_ = null;
      private Integer[] channelContrastMaxes_ = null;
      private Double[] channelGammas_ = null;
      private Double magnification_ = null;
      private Integer animationFPS_ = null;
      private Integer channelDisplayModeIndex_ = null;
      private Coords imageCoords_ = null;
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
      private PropertyMap userData_ = null;

      @Override
      public DefaultDisplaySettings build() {
         return new DefaultDisplaySettings(this);
      }
      
      @Override
      public DisplaySettingsBuilder channelColors(Color[] channelColors) {
         channelColors_ = (channelColors == null) ? null : channelColors.clone();
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelContrastMins(Integer[] channelContrastMins) {
         channelContrastMins_ = (channelContrastMins == null) ? null : channelContrastMins.clone();
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelContrastMaxes(Integer[] channelContrastMaxes) {
         channelContrastMaxes_ = (channelContrastMaxes == null) ? null : channelContrastMaxes.clone();
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelGammas(Double[] channelGammas) {
         channelGammas_ = (channelGammas == null) ? null : channelGammas.clone();
         return this;
      }

      @Override
      public DisplaySettingsBuilder magnification(Double magnification) {
         magnification_ = magnification;
         return this;
      }

      @Override
      public DisplaySettingsBuilder animationFPS(Integer animationFPS) {
         animationFPS_ = animationFPS;
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelDisplayModeIndex(Integer channelDisplayModeIndex) {
         channelDisplayModeIndex_ = channelDisplayModeIndex;
         return this;
      }

      @Override
      public DisplaySettingsBuilder imageCoords(Coords imageCoords) {
         imageCoords_ = imageCoords;
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

      @Override
      public DisplaySettingsBuilder userData(PropertyMap userData) {
         userData_ = userData;
         return this;
      }
   }

   private Color[] channelColors_ = null;
   private Integer[] channelContrastMins_ = null;
   private Integer[] channelContrastMaxes_ = null;
   private Double[] channelGammas_ = null;
   private Double magnification_ = null;
   private Integer animationFPS_ = null;
   private Integer channelDisplayModeIndex_ = null;
   private Coords imageCoords_ = null;
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
   private PropertyMap userData_ = null;

   public DefaultDisplaySettings(Builder builder) {
      channelColors_ = builder.channelColors_;
      channelContrastMins_ = builder.channelContrastMins_;
      channelContrastMaxes_ = builder.channelContrastMaxes_;
      channelGammas_ = builder.channelGammas_;
      magnification_ = builder.magnification_;
      animationFPS_ = builder.animationFPS_;
      channelDisplayModeIndex_ = builder.channelDisplayModeIndex_;
      imageCoords_ = builder.imageCoords_;
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
      userData_ = builder.userData_;
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
   public Double getMagnification() {
      return magnification_;
   }

   @Override
   public Integer getAnimationFPS() {
      return animationFPS_;
   }

   @Override
   public Integer getChannelDisplayModeIndex() {
      return channelDisplayModeIndex_;
   }

   @Override
   public Coords getImageCoords() {
      return imageCoords_;
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
   public PropertyMap getUserData() {
      return userData_;
   }

   @Override
   public DisplaySettingsBuilder copy() {
      return new Builder()
            .channelColors(channelColors_)
            .channelContrastMins(channelContrastMins_)
            .channelContrastMaxes(channelContrastMaxes_)
            .channelGammas(channelGammas_)
            .magnification(magnification_)
            .animationFPS(animationFPS_)
            .channelDisplayModeIndex(channelDisplayModeIndex_)
            .imageCoords(imageCoords_)
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
            .shouldUseLogScale(shouldUseLogScale_)
            .userData(userData_);
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
         Builder builder = new Builder();
         // Check for both methods of storing colors (see toJSON, below)
         if (MDUtils.hasChannelColor(tags)) {
            builder.channelColors(new Color[] {rgbToColor(MDUtils.getChannelColor(tags))});
         }
         if (tags.has("channelColors")) {
            JSONArray colorTags = tags.getJSONArray("channelColors");
            Color[] colors = new Color[colorTags.length()];
            for (int i = 0; i < colorTags.length(); ++i) {
               colors[i] = rgbToColor(colorTags.getInt(i));
            }
            builder.channelColors(colors);
         }

         if (tags.has("ChContrastMin")) {
            builder.channelContrastMins(new Integer[] {tags.getInt("ChContrastMin")});
         }
         if (tags.has("ChContrastMax")) {
            builder.channelContrastMaxes(new Integer[] {tags.getInt("ChContrastMax")});
         }
         if (tags.has("magnification")) {
            builder.magnification(tags.getDouble("magnification"));
         }
         if (tags.has("animationFPS")) {
            builder.animationFPS(tags.getInt("animationFPS"));
         }
         if (tags.has("histogramUpdateRate")) {
            builder.histogramUpdateRate(tags.getDouble("histogramUpdateRate"));
         }
         if (tags.has("shouldSyncChannels")) {
            builder.shouldSyncChannels(tags.getBoolean("shouldSyncChannels"));
         }
         if (tags.has("scaleBarColorIndex")) {
            builder.scaleBarColorIndex(tags.getInt("scaleBarColorIndex"));
         }
         if (tags.has("scaleBarLocationIndex")) {
            builder.scaleBarLocationIndex(tags.getInt("scaleBarLocationIndex"));
         }
         if (tags.has("shouldShowScaleBar")) {
            builder.shouldShowScaleBar(tags.getBoolean("shouldShowScaleBar"));
         }
         if (tags.has("shouldAutostretch")) {
            builder.shouldAutostretch(tags.getBoolean("shouldAutostretch"));
         }
         if (tags.has("trimPercentage")) {
            builder.trimPercentage(tags.getDouble("trimPercentage"));
         }
         if (tags.has("shouldUseLogScale")) {
            builder.shouldUseLogScale(tags.getBoolean("shouldUseLogScale"));
         }
         // TODO: not restoring user data at this time.

         return builder.build();
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't convert JSON into DisplaySettings");
         return null;
      }
   }

   private static final String RECORD_DELIMETER = "==============END OF RECORD==============";

   @Override
   public void save(String path) {
      File file = new File(path, DisplaySettings.FILENAME);
      try {
         FileWriter writer = new FileWriter(file, true);
         writer.write(toJSON().toString(1));
         writer.write("\n" + RECORD_DELIMETER + "\n");
         writer.close();
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Unable to convert DisplaySettings into JSON for saving");
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error while saving DisplaySettings");
      }
   }

   /**
    * Load the displaySettings.txt file and create a DefaultDisplaySettings
    * for each record in the file.
    * TODO: our mechanism for splitting apart JSON records is rather hacky.
    */
   public static List<DisplaySettings> load(String path) {
      ArrayList<DisplaySettings> result = new ArrayList<DisplaySettings>();
      File file = new File(path, DisplaySettings.FILENAME);
      try {
         BufferedReader reader = new BufferedReader(new FileReader(file));
         String curSettings = "";
         String curLine = reader.readLine();
         while (curLine != null) {
            if (curLine.contentEquals(RECORD_DELIMETER)) {
               // Ending a record; create a DisplaySettings from it.
               result.add(legacyFromJSON(new JSONObject(curSettings)));
               curSettings = "";
            }
            else {
               curSettings += curLine;
            }
            curLine = reader.readLine();
         }
      }
      catch (FileNotFoundException e) {
         ReportingUtils.logError("No display settings found at " + path);
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error while reading display settings file");
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Error while converting saved settings into JSON");
      }
      return result;
   }

   /**
    * For backwards compatibility, generate a JSONObject representing this
    * DefaultDisplaySettings.
    */
   public JSONObject toJSON() {
      try {
         JSONObject result = new JSONObject();
         // We store color in two ways: the backwards-compatible
         // "ChColor" tag (via setChannelColor()), and a method that preserves
         // all channel colors.
         if (channelColors_ != null && channelColors_.length > 0) {
            MDUtils.setChannelColor(result, colorToRGB(channelColors_[0]));
            JSONArray colors = new JSONArray();
            for (Color color : channelColors_) {
               colors.put(colorToRGB(color));
            }
            result.put("channelColors", colors);
         }
         if (channelContrastMins_ != null && channelContrastMins_.length > 0) {
            result.put("ChContrastMin", channelContrastMins_[0]);
         }
         if (channelContrastMaxes_ != null && channelContrastMaxes_.length > 0) {
            result.put("ChContrastMax", channelContrastMaxes_[0]);
         }
         result.put("magnification", magnification_);
         result.put("animationFPS", animationFPS_);
         result.put("histogramUpdateRate", histogramUpdateRate_);
         result.put("shouldSyncChannels", shouldSyncChannels_);
         result.put("scaleBarColorIndex", scaleBarColorIndex_);
         result.put("scaleBarLocationIndex", scaleBarLocationIndex_);
         result.put("shouldShowScaleBar", shouldShowScaleBar_);
         result.put("shouldAutostretch", shouldAutostretch_);
         result.put("trimPercentage", trimPercentage_);
         result.put("shouldUseLogScale", shouldUseLogScale_);
         // TODO: not storing user data at this time.
         return result;
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't convert DefaultDisplaySettings to JSON");
         return null;
      }
   }

   /**
    * Given a java.awt.Color, convert it into a 24-bit RGB int.
    */
   private static int colorToRGB(Color color) {
      return color.getRed() + (color.getBlue() << 8) + (color.getGreen() << 16);
   }

   /**
    * Given a 24-bit RGB int, convert into a java.awt.Color.
    */
   private static Color rgbToColor(int rgb) {
      int red = rgb & 0xff;
      int blue = (rgb >> 8) & 0xff;
      int green = (rgb >> 16) & 0xff;
      return new Color(red, green, blue);
   }

   /**
    * Generate a textual description of this object. For debugging purposes
    * only.
    */
   @Override
   public String toString() {
      Field[] fields = getClass().getDeclaredFields();
      String result = "<DisplaySettings " + hashCode() + ": ";
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

   // There are a number of attributes in the DisplaySettings that apply on a
   // per-channel basis (e.g. channelColors, channelContrastMins). Some code
   // needs to work with these attributes in a generic fashion. The below
   // functions handle abstracting out these arrays into a group and then
   // de-abstracting them as appropriate.
   //
   // HACK: all of this is rather closely-tied to
   // org.micromanager.imagedisplay.link.ContrastLinker.getID().
   // But it's also needed by
   // org.micromanager.imagedisplay.ChannelControlPanel, and could be useful
   // elsewhere, which is why it's here.

   /**
    * Down-convert our per-channel properties into an array of Object[]s.
    * If you update this method, also update makePerChannelArray() and
    * updateChannelArray(), which make assumptions about the ordering of
    * types and methods of the objects in this method's output. Also scan
    * the code for anyone calling this method, since they may have similar
    * assumptions!
    */
   public static Object[] getPerChannelArrays(DisplaySettings settings) {
      return new Object[] {
         settings.getChannelColors(), settings.getChannelContrastMins(),
         settings.getChannelContrastMaxes(), settings.getChannelGammas()};
   }

   /**
    * Generate a new array of the appropriate type, having a length that is
    * the maximum of the length of the input array, or the provided minLength
    * parameter (e.g. if the input array is null).
    * If you change this method, also update updateChannelArray() and
    * getPerChannelArrays().
    */
   public static Object[] makePerChannelArray(int attrIndex, Object[] oldVals,
         int minLength) {
      int oldLen = -1;
      if (oldVals != null) {
         oldLen = oldVals.length;
      }
      int len = Math.max(oldLen, minLength);
      // Create the array of the appropriate type.
      Object[] result;
      switch(attrIndex) {
         case 0: // channelColors;
            result = new Color[len];
            break;
         case 1: // channelContrastMins
         case 2: // channelContrastMaxes
            result = new Integer[len];
            break;
         case 3: // channelGammas
            result = new Double[len];
            break;
         default:
            throw new IllegalArgumentException("Invalid attribute index value " + attrIndex);
      }
      // Copy values into the new array if possible.
      if (oldVals != null) {
         for (int i = 0; i < oldLen; ++i) {
            result[i] = oldVals[i];
         }
      }
      return result;
   }

   /**
    * Insert the given array, whose identity is determined by attrIndex, into
    * the provided builder.
    * If you change this method, also update makePerChannelArray() and
    * getPerChannelArrays.
    */
   public static void updateChannelArray(int attrIndex, Object[] newVals,
         DisplaySettings.DisplaySettingsBuilder builder) {
      switch (attrIndex) {
         case 0: // channelColors
            builder.channelColors((Color[]) newVals);
            break;
         case 1: // channelContrastMins
            builder.channelContrastMins((Integer[]) newVals);
            break;
         case 2: // channelContrastMaxes
            builder.channelContrastMaxes((Integer[]) newVals);
            break;
         case 3: // channelGammas
            builder.channelGammas((Double[]) newVals);
            break;
         default:
            throw new IllegalArgumentException("Invalid attribute index " + attrIndex);
      }
   }
}
