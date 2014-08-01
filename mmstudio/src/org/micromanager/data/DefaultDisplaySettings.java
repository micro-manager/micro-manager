package org.micromanager.data;

import java.awt.Color;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.MultiStagePosition;

import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

public class DefaultDisplaySettings implements DisplaySettings {

   public static class Builder implements DisplaySettings.DisplaySettingsBuilder {
      private String[] channelNames_ = null;
      private Color[] channelColors_ = null;
      private Integer[] channelContrastMins_ = null;
      private Integer[] channelContrastMaxes_ = null;
      private Boolean isSlowHistogramsOn_ = null;
      private Boolean shouldSyncChannels_ = null;
      private Integer scaleBarColorIndex_ = null;
      private Integer scaleBarLocationIndex_ = null;
      private Boolean shouldShowScaleBar_ = null;
      private Boolean shouldAutostretch_ = null;
      private Boolean shouldIgnoreOutliers_ = null;
      private Double percentToIgnore_ = null;
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
      public DisplaySettingsBuilder isSlowHistogramsOn(Boolean isSlowHistogramsOn) {
         isSlowHistogramsOn_ = isSlowHistogramsOn;
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
      public DisplaySettingsBuilder shouldIgnoreOutliers(Boolean shouldIgnoreOutliers) {
         shouldIgnoreOutliers_ = shouldIgnoreOutliers;
         return this;
      }

      @Override
      public DisplaySettingsBuilder percentToIgnore(Double percentToIgnore) {
         percentToIgnore_ = percentToIgnore;
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
   private Boolean isSlowHistogramsOn_ = null;
   private Boolean shouldSyncChannels_ = null;
   private Integer scaleBarColorIndex_ = null;
   private Integer scaleBarLocationIndex_ = null;
   private Boolean shouldShowScaleBar_ = null;
   private Boolean shouldAutostretch_ = null;
   private Boolean shouldIgnoreOutliers_ = null;
   private Double percentToIgnore_ = null;
   private Boolean shouldUseLogScale_ = null;

   public DefaultDisplaySettings(Builder builder) {
      channelNames_ = builder.channelNames_;
      channelColors_ = builder.channelColors_;
      channelContrastMins_ = builder.channelContrastMins_;
      channelContrastMaxes_ = builder.channelContrastMaxes_;
      isSlowHistogramsOn_ = builder.isSlowHistogramsOn_;
      shouldSyncChannels_ = builder.shouldSyncChannels_;
      scaleBarColorIndex_ = builder.scaleBarColorIndex_;
      scaleBarLocationIndex_ = builder.scaleBarLocationIndex_;
      shouldShowScaleBar_ = builder.shouldShowScaleBar_;
      shouldAutostretch_ = builder.shouldAutostretch_;
      shouldIgnoreOutliers_ = builder.shouldIgnoreOutliers_;
      percentToIgnore_ = builder.percentToIgnore_;
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
   public Boolean getIsSlowHistogramsOn() {
      return isSlowHistogramsOn_;
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
   public Boolean getShouldShowScaleBar() {
      return shouldShowScaleBar_;
   }

   @Override
   public Boolean getShouldAutostretch() {
      return shouldAutostretch_;
   }

   @Override
   public Boolean getShouldIgnoreOutliers() {
      return shouldIgnoreOutliers_;
   }

   @Override
   public Double getPercentToIgnore() {
      return percentToIgnore_;
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
            .isSlowHistogramsOn(isSlowHistogramsOn_)
            .shouldSyncChannels(shouldSyncChannels_)
            .scaleBarColorIndex(scaleBarColorIndex_)
            .scaleBarLocationIndex(scaleBarLocationIndex_)
            .shouldShowScaleBar(shouldShowScaleBar_)
            .shouldAutostretch(shouldAutostretch_)
            .shouldIgnoreOutliers(shouldIgnoreOutliers_)
            .percentToIgnore(percentToIgnore_)
            .shouldUseLogScale(shouldUseLogScale_);
   }

   /**
    * For backwards compatibility, generate a DefaultDisplaySettings from
    * a JSONObject.
    */
   public static DisplaySettings legacyFromJSON(JSONObject tags) {
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
         MDUtils.setChannelName(result, channelNames_[0]);
         // TODO: no idea how we represent a color with an int in the current
         // system, but at least using a hashCode() uniquely represents this
         // RGBA color!
         MDUtils.setChannelColor(result, channelColors_[0].hashCode());
         result.put("ChContrastMin", channelContrastMins_[0]);
         result.put("ChContrastMax", channelContrastMaxes_[0]);
         result.put("isSlowHistogramsOn", isSlowHistogramsOn_);
         result.put("shouldSyncChannels", shouldSyncChannels_);
         result.put("scaleBarColorIndex", scaleBarColorIndex_);
         result.put("scaleBarLocationIndex", scaleBarLocationIndex_);
         result.put("shouldShowScaleBar", shouldShowScaleBar_);
         result.put("shouldAutostretch", shouldAutostretch_);
         result.put("shouldIgnoreOutliers", shouldIgnoreOutliers_);
         result.put("percentToIgnore", percentToIgnore_);
         result.put("shouldUseLogScale", shouldUseLogScale_);
         return result;
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't convert DefaultDisplaySettings to JSON");
         return null;
      }
   }
}
