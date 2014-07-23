package org.micromanager.data;

import java.awt.Color;

import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.MultiStagePosition;

public class DefaultDisplaySettings implements DisplaySettings {

   public class Builder implements DisplaySettings.DisplaySettingsBuilder {
      private String[] channelNames_ = null;
      private Color[] channelColors_ = null;
      private Integer[] channelContrastMins_ = null;
      private Integer[] channelContrastMaxes_ = null;

      @Override
      public DisplaySettings build() {
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
   }

   private String[] channelNames_ = null;
   private Color[] channelColors_ = null;
   private Integer[] channelContrastMins_ = null;
   private Integer[] channelContrastMaxes_ = null;

   public DefaultDisplaySettings(Builder builder {
      channelNames_ = builder.channelNames_;
      channelColors_ = builder.channelColors_;
      channelContrastMins_ = builder.channelContrastMins_;
      channelContrastMaxes_ = builder.channelContrastMaxes_;
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
   public MetadataBuilder copy() {
      return new DefaultMetadataBuilder()
            .channelNames(channelNames_)
            .channelColors(channelColors_)
            .channelContrastMins(channelContrastMins_)
            .channelContrastMaxes(channelContrastMaxes_);
   }
}
