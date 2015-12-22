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

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.display.DisplaySettings;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;

import org.micromanager.PropertyMap;

public class DefaultDisplaySettings implements DisplaySettings {

   /**
    * Retrieve the display settings that have been saved in the preferences.
    * Note: we explicitly don't cache these settings, to ensure that
    * displays don't end up with copies of the same settings.
    * @return 
    */
   public static DefaultDisplaySettings getStandardSettings() {
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      Builder builder = new Builder();
      // We have to convert colors to/from int arrays.
      // Note we assume RGB tuples in the colors array.
      // Seven colors because ImageJ only supports 7 channels; put yellow/cyan
      // first for colorblind-friendliness.
      Color[] defaultColors = new Color[] {Color.YELLOW, Color.CYAN,
         Color.MAGENTA, Color.RED, Color.GREEN, Color.BLUE, Color.ORANGE};
      Integer[] defaultIntColors = colorsToInts(defaultColors);

      // This value used to be an int, then got changed to a double.
      try {
         builder.animationFPS(profile.getDouble(
                  DefaultDisplaySettings.class, "animationFPS", 10.0));
      }
      catch (PropertyMap.TypeMismatchException e) {
         builder.animationFPS(new Double(profile.getInt(
                  DefaultDisplaySettings.class, "animationFPS", 10)));
      }
      builder.channelColorMode(
            DisplaySettings.ColorMode.fromInt(profile.getInt(
            DefaultDisplaySettings.class, "channelColorMode", 0)));
      builder.histogramUpdateRate(profile.getDouble(
            DefaultDisplaySettings.class, "histogramUpdateRate", 0.0));
      builder.magnification(profile.getDouble(
            DefaultDisplaySettings.class, "magnification", 1.0));
      builder.shouldSyncChannels(profile.getBoolean(
            DefaultDisplaySettings.class, "shouldSyncChannels", false));
      builder.shouldAutostretch(profile.getBoolean(
            DefaultDisplaySettings.class, "shouldAutostretch", true));
      builder.extremaPercentage(profile.getDouble(
            DefaultDisplaySettings.class, "extremaPercentage", 0.0));
      builder.bitDepthIndices(profile.getIntArray(
            DefaultDisplaySettings.class, "bitDepthIndices", null));
      builder.shouldUseLogScale(profile.getBoolean(
            DefaultDisplaySettings.class, "shouldUseLogScale", false));
      // Note we don't store user data in the prefs explicitly; let third-party
      // code manually access the prefs if they want.
      return builder.build();
   }

   /**
    * Set new settings in the user's profile.
    */
   public static void setStandardSettings(DisplaySettings settings) {
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      profile.setDouble(DefaultDisplaySettings.class, "animationFPS",
            settings.getAnimationFPS());
      if (settings.getChannelColorMode() != null) {
         profile.setInt(DefaultDisplaySettings.class,
               "channelColorMode",
               settings.getChannelColorMode().getIndex());
      }
      profile.setDouble(DefaultDisplaySettings.class,
            "histogramUpdateRate", settings.getHistogramUpdateRate());
      profile.setDouble(DefaultDisplaySettings.class,
            "magnification", settings.getMagnification());
      profile.setBoolean(DefaultDisplaySettings.class,
            "shouldSyncChannels", settings.getShouldSyncChannels());
      profile.setBoolean(DefaultDisplaySettings.class,
            "shouldAutostretch", settings.getShouldAutostretch());
      profile.setDouble(DefaultDisplaySettings.class,
            "extremaPercentage", settings.getExtremaPercentage());
      profile.setIntArray(DefaultDisplaySettings.class,
            "bitDepthIndices", settings.getBitDepthIndices());
      profile.setBoolean(DefaultDisplaySettings.class,
            "shouldUseLogScale", settings.getShouldUseLogScale());
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

   public static class DefaultContrastSettings implements DisplaySettings.ContrastSettings {
      Integer[] contrastMins_;
      Integer[] contrastMaxes_;
      Double[] gammas_;
      Boolean isVisible_;

      /**
       * Convenience method for single-component settings.
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
      public Integer[] getContrastMins() {
         return contrastMins_;
      }

      @Override
      public Integer getSafeContrastMin(int component, Integer defaultVal) {
         if (component < 0 || contrastMins_ == null ||
               contrastMins_.length <= component) {
            return defaultVal;
         }
         return contrastMins_[component];
      }

      @Override
      public Integer[] getContrastMaxes() {
         return contrastMaxes_;
      }

      @Override
      public Integer getSafeContrastMax(int component, Integer defaultVal) {
         if (component < 0 || contrastMaxes_ == null ||
               contrastMaxes_.length <= component) {
            return defaultVal;
         }
         return contrastMaxes_[component];
      }

      @Override
      public Double[] getContrastGammas() {
         return gammas_;
      }

      @Override
      public Double getSafeContrastGamma(int component, Double defaultVal) {
         if (component < 0 || gammas_ == null ||
               gammas_.length <= component) {
            return defaultVal;
         }
         return gammas_[component];
      }

      @Override
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
      public Boolean getIsVisible() {
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
         if (((contrastMins_ == null) != (altMins == null)) ||
               ((contrastMaxes_ == null) != (altMaxes == null)) ||
               ((gammas_ == null) != (altGammas == null))) {
            // Someone's array is null where the other one isn't.
            return false;
         }
         if ((contrastMins_ != null &&
               !Arrays.deepEquals(contrastMins_, altMins)) ||
               (contrastMaxes_ != null &&
                !Arrays.deepEquals(contrastMaxes_, altMaxes)) ||
               (gammas_ != null && !Arrays.deepEquals(gammas_, altGammas))) {
            // Arrays contain unequal values.
            return false;
         }
         // All arrays have same contents or are both null.
         return (isVisible_ != alt.getIsVisible());
      }

      @Override
      public String toString() {
         String result = String.format("<ContrastSettings (%d components)", getNumComponents());
         for (int i = 0; i < getNumComponents(); ++i) {
            result += String.format("(%d, %d @ %.2f)", getSafeContrastMin(i, -1), getSafeContrastMax(i, -1), getSafeContrastGamma(i, -1.0));
         }
         return result + ">";
      }
   }

   public static class Builder implements DisplaySettings.DisplaySettingsBuilder {
      private Color[] channelColors_ = null;
      private ContrastSettings[] contrastSettings_ = null;
      private Double magnification_ = null;
      private Double animationFPS_ = null;
      private DisplaySettings.ColorMode channelColorMode_ = null;
      private Double histogramUpdateRate_ = null;
      private Boolean shouldSyncChannels_ = null;
      private Boolean shouldAutostretch_ = null;
      private Double extremaPercentage_ = null;
      private Integer[] bitDepthIndices_ = null;
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
      public DisplaySettingsBuilder safeUpdateChannelColor(Color newColor,
            int channelIndex) {
         if (channelColors_ == null || channelColors_.length <= channelIndex) {
            Color[] newArray = new Color[channelIndex + 1];
            // Fill in nulls to start.
            for (int i = 0; i < newArray.length; ++i) {
               newArray[i] = null;
            }
            if (channelColors_ != null) {
               // Copy old values across.
               for (int i = 0; i < channelColors_.length; ++i) {
                  newArray[i] = channelColors_[i];
               }
            }
            channelColors_ = newArray;
         }
         channelColors_[channelIndex] = newColor;
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelContrastSettings(ContrastSettings[] contrastSettings) {
         contrastSettings_ = (contrastSettings == null) ? null : contrastSettings.clone();
         return this;
      }

      @Override
      public DisplaySettingsBuilder safeUpdateContrastSettings(
            ContrastSettings newSettings, int channelIndex) {
         if (contrastSettings_ == null ||
               contrastSettings_.length <= channelIndex) {
            ContrastSettings[] newArray = new ContrastSettings[channelIndex + 1];
            // Fill in nulls to start.
            for (int i = 0; i < newArray.length; ++i) {
               newArray[i] = null;
            }
            if (contrastSettings_ != null) {
               // Copy old values across.
               for (int i = 0; i < contrastSettings_.length; ++i) {
                  newArray[i] = contrastSettings_[i];
               }
            }
            contrastSettings_ = newArray;
         }
         contrastSettings_[channelIndex] = newSettings;
         return this;
      }

      @Override
      public DisplaySettingsBuilder magnification(Double magnification) {
         magnification_ = magnification;
         return this;
      }

      @Override
      public DisplaySettingsBuilder animationFPS(Double animationFPS) {
         animationFPS_ = animationFPS;
         return this;
      }

      @Override
      public DisplaySettingsBuilder channelColorMode(ColorMode channelColorMode) {
         channelColorMode_ = channelColorMode;
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
      public DisplaySettingsBuilder shouldAutostretch(Boolean shouldAutostretch) {
         shouldAutostretch_ = shouldAutostretch;
         return this;
      }

      @Override
      public DisplaySettingsBuilder extremaPercentage(Double extremaPercentage) {
         extremaPercentage_ = extremaPercentage;
         return this;
      }

      @Override
      public DisplaySettingsBuilder bitDepthIndices(Integer[] bitDepthIndices) {
         bitDepthIndices_ = bitDepthIndices;
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
   private ContrastSettings[] contrastSettings_ = null;
   private Double magnification_ = null;
   private Double animationFPS_ = null;
   private DisplaySettings.ColorMode channelColorMode_ = null;
   private Double histogramUpdateRate_ = null;
   private Boolean shouldSyncChannels_ = null;
   private Boolean shouldAutostretch_ = null;
   private Double extremaPercentage_ = null;
   private Integer[] bitDepthIndices_ = null;
   private Boolean shouldUseLogScale_ = null;
   private PropertyMap userData_ = null;

   public DefaultDisplaySettings(Builder builder) {
      channelColors_ = builder.channelColors_;
      contrastSettings_ = builder.contrastSettings_;
      magnification_ = builder.magnification_;
      animationFPS_ = builder.animationFPS_;
      channelColorMode_ = builder.channelColorMode_;
      histogramUpdateRate_ = builder.histogramUpdateRate_;
      shouldSyncChannels_ = builder.shouldSyncChannels_;
      shouldAutostretch_ = builder.shouldAutostretch_;
      extremaPercentage_ = builder.extremaPercentage_;
      bitDepthIndices_ = builder.bitDepthIndices_;
      shouldUseLogScale_ = builder.shouldUseLogScale_;
      userData_ = builder.userData_;
   }

   @Override
   public Color[] getChannelColors() {
      return channelColors_;
   }

   @Override
   public Color getSafeChannelColor(int index, Color defaultVal) {
      if (index < 0 || channelColors_ == null ||
            channelColors_.length <= index ||
            channelColors_[index] == null) {
         return defaultVal;
      }
      return channelColors_[index];
   }

   @Override
   public ContrastSettings[] getChannelContrastSettings() {
      return contrastSettings_;
   }

   @Override
   public ContrastSettings getSafeContrastSettings(int index,
         ContrastSettings defaultVal) {
      if (index < 0 || contrastSettings_ == null ||
            contrastSettings_.length <= index ||
            contrastSettings_[index] == null) {
         return defaultVal;
      }
      return contrastSettings_[index];
   }

   @Override
   public Integer getSafeContrastMin(int index, int component,
         Integer defaultVal) {
      if (index < 0 || contrastSettings_ == null ||
            contrastSettings_.length <= index ||
            contrastSettings_[index] == null) {
         return defaultVal;
      }
      return contrastSettings_[index].getSafeContrastMin(component, defaultVal);
   }

   @Override
   public Integer getSafeContrastMax(int index, int component,
         Integer defaultVal) {
      if (index < 0 || contrastSettings_ == null ||
            contrastSettings_.length <= index ||
            contrastSettings_[index] == null) {
         return defaultVal;
      }
      return contrastSettings_[index].getSafeContrastMax(component, defaultVal);
   }

   @Override
   public Double getSafeContrastGamma(int index, int component,
         Double defaultVal) {
      if (index < 0 || contrastSettings_ == null ||
            contrastSettings_.length <= index ||
            contrastSettings_[index] == null) {
         return defaultVal;
      }
      return contrastSettings_[index].getSafeContrastGamma(component, defaultVal);
   }

   @Override
   public Boolean getSafeIsVisible(int index, Boolean defaultVal) {
      if (index < 0 || contrastSettings_ == null ||
            contrastSettings_.length <= index ||
            contrastSettings_[index] == null ||
            contrastSettings_[index].getIsVisible() == null) {
         return defaultVal;
      }
      return contrastSettings_[index].getIsVisible();
   }

   @Override
   public Double getMagnification() {
      return magnification_;
   }

   @Override
   public Double getAnimationFPS() {
      return animationFPS_;
   }

   @Override
   public DisplaySettings.ColorMode getChannelColorMode() {
      return channelColorMode_;
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
   public Boolean getShouldAutostretch() {
      return shouldAutostretch_;
   }

   @Override
   public Double getExtremaPercentage() {
      return extremaPercentage_;
   }

   @Override
   public Integer[] getBitDepthIndices() {
      return bitDepthIndices_;
   }


   @Override
   public Integer getSafeBitDepthIndex(int index, Integer defaultVal) {
      if (index < 0 || bitDepthIndices_ == null ||
            bitDepthIndices_.length <= index ||
            bitDepthIndices_[index] == null) {
         return defaultVal;
      }
      return bitDepthIndices_[index];
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
            .channelContrastSettings(contrastSettings_)
            .magnification(magnification_)
            .animationFPS(animationFPS_)
            .channelColorMode(channelColorMode_)
            .histogramUpdateRate(histogramUpdateRate_)
            .shouldSyncChannels(shouldSyncChannels_)
            .shouldAutostretch(shouldAutostretch_)
            .extremaPercentage(extremaPercentage_)
            .bitDepthIndices(bitDepthIndices_)
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
            ArrayList<ContrastSettings> contrastSettings = new ArrayList<ContrastSettings>();
            for (int i = 0; i < minsArr.length; ++i) {
               Integer min = minsArr[i];
               Integer max = maxesArr[i];
               contrastSettings.add(
                     new DefaultContrastSettings(min, max, 1.0, true));
            }
            builder.channelContrastSettings(
                  contrastSettings.toArray(new DefaultContrastSettings[] {}));
         }

         if (tags.has("magnification")) {
            builder.magnification(tags.getDouble("magnification"));
         }
         if (tags.has("animationFPS")) {
            builder.animationFPS(tags.getDouble("animationFPS"));
         }
         if (tags.has("histogramUpdateRate")) {
            builder.histogramUpdateRate(tags.getDouble("histogramUpdateRate"));
         }
         if (tags.has("shouldSyncChannels")) {
            builder.shouldSyncChannels(tags.getBoolean("shouldSyncChannels"));
         }
         if (tags.has("shouldAutostretch")) {
            builder.shouldAutostretch(tags.getBoolean("shouldAutostretch"));
         }
         if (tags.has("extremaPercentage")) {
            builder.extremaPercentage(tags.getDouble("extremaPercentage"));
         }
         if (tags.has("bitDepthIndices")) {
            JSONArray indices = tags.getJSONArray("bitDepthIndices");
            Integer[] indicesArr = new Integer[indices.length()];
            for (int i = 0; i < indicesArr.length; ++i) {
               indicesArr[i] = indices.getInt(i);
            }
            builder.bitDepthIndices(indicesArr);
         }
         if (tags.has("shouldUseLogScale")) {
            builder.shouldUseLogScale(tags.getBoolean("shouldUseLogScale"));
         }
         if (tags.has("userData")) {
            builder.userData(DefaultPropertyMap.fromJSON(tags.getJSONObject("userData")));
         }

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
      FileWriter writer = null;
      try {
         writer = new FileWriter(file, true);
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
      finally {
         if (writer != null) {
            try {
               writer.close();
            }
            catch (IOException e) {
               ReportingUtils.logError(e, "Error while closing writer");
            }
         }
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
      BufferedReader reader = null;
      try {
         reader = new BufferedReader(new FileReader(file));
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
      finally {
         if (reader != null) {
            try {
               reader.close();
            }
            catch (IOException e) {
               ReportingUtils.logError(e, "Error while closing reader");
            }
         }
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
            if (channelColors_[0] != null) {
               MDUtils.setChannelColor(result, channelColors_[0].getRGB());
            }
            JSONArray colors = new JSONArray();
            for (Color color : channelColors_) {
               if (color != null) {
                  colors.put(color.getRGB());
               }
            }
            result.put("ChColors", colors);
         }
         // TODO: doesn't handle multi-component images.
         if (contrastSettings_ != null && contrastSettings_.length > 0) {
            JSONArray mins = new JSONArray();
            JSONArray maxes = new JSONArray();
            for (int i = 0; i < contrastSettings_.length; ++i) {
               mins.put(getSafeContrastMin(i, 0, -1));
               maxes.put(getSafeContrastMax(i, 0, -1));
            }
            result.put("ChContrastMin", mins);
            result.put("ChContrastMax", maxes);
         }
         result.put("magnification", magnification_);
         result.put("animationFPS", animationFPS_);
         result.put("histogramUpdateRate", histogramUpdateRate_);
         result.put("shouldSyncChannels", shouldSyncChannels_);
         result.put("shouldAutostretch", shouldAutostretch_);
         result.put("extremaPercentage", extremaPercentage_);
         if (bitDepthIndices_ != null && bitDepthIndices_.length > 0) {
            JSONArray indices = new JSONArray();
            for (int i = 0; i < bitDepthIndices_.length; ++i) {
               indices.put(bitDepthIndices_[i]);
            }
            result.put("bitDepthIndices", indices);
         }
         result.put("shouldUseLogScale", shouldUseLogScale_);
         if (userData_ != null) {
            result.put("userData", ((DefaultPropertyMap) userData_).toJSON());
         }
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
}
