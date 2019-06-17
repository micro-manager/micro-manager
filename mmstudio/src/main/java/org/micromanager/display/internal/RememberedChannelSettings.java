///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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
//

package org.micromanager.display.internal;

import java.awt.Color;
import java.util.Arrays;
import org.micromanager.UserProfile;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.internal.utils.ColorPalettes;
import org.micromanager.internal.MMStudio;

/**
 * This container class stores histogram and color settings for a specific
 * named channel (e.g. DAPI or FITC), and is used to ensure that when we need a
 * new display that uses that channel, we are able to recover it from the
 * profile.
 */
public final class RememberedChannelSettings {
   private static final String NAMES = "names of channels and channel groups";
   private static final String COLOR = "color to use for channels";
   private static final String MINS = "default minimum values to use in the histograms when scaling";
   private static final String MAXES = "default maximum values to use in the histograms when scaling";
   private static final String AUTOSCALE = "whether or not to use autoscaling for this channel"; 

   // Used to prevent simultaneous read/writes of the profile
   private static final Object PROFILELOCK = new Object();
   
   
   private final String channelName_;
   private final String channelGroup_;
   private final Color color_;
   private final Integer[] histogramMins_;
   private final Integer[] histogramMaxes_;
   private final Boolean shouldAutoscale_;

   /**
    * For purposes of saving to profile, any or all of the settings (i.e.
    * everything except the channel name and channel group) may be null, in
    * which case they will not be saved to the profile.
    */
   public RememberedChannelSettings(String channelName, String channelGroup,
         Color color, Integer[] histogramMins, Integer[] histogramMaxes,
         Boolean shouldAutoscale) {
      channelName_ = channelName;
      channelGroup_ = channelGroup;
      color_ = color;
      histogramMins_ = histogramMins;
      histogramMaxes_ = histogramMaxes;
      shouldAutoscale_ = shouldAutoscale;
   }

   public Color getColor() {
      return color_;
   }

   public Integer getHistogramMin(int component) {
      return histogramMins_[component];
   }

   public Integer[] getHistogramMins() {
      return histogramMins_;
   }

   public Integer getHistogramMax(int component) {
      return histogramMaxes_[component];
   }

   public Integer[] getHistogramMaxes() {
      return histogramMaxes_;
   }

   public Boolean getShouldAutoscale() {
      return shouldAutoscale_;
   }

   private static String genKey(String channelName, String channelGroup) {
      return channelName + ":" + channelGroup;
   }

   /**
    * Record this RememberedChannelSettings in the profile under its channel
    * name and group.
    */
   public void saveToProfile() {
      synchronized(PROFILELOCK) {
         UserProfile profile = MMStudio.getInstance().profile();
         String ourKey = genKey(channelName_, channelGroup_);
         if (color_ != null) {
            profile.setInt(RememberedChannelSettings.class,
                  ourKey + ":" + COLOR, color_.getRGB());
         }
         if (histogramMins_ != null) {
            profile.getSettings(RememberedChannelSettings.class).putIntegerList(
                  ourKey + ":" + MINS, Arrays.asList(histogramMins_));
         }
         if (histogramMaxes_ != null) {
            profile.getSettings(RememberedChannelSettings.class).putIntegerList(
                  ourKey + ":" + MAXES, Arrays.asList(histogramMaxes_));
         }
         if (shouldAutoscale_ != null) {
            profile.getSettings(RememberedChannelSettings.class).putBoolean(
                  ourKey + ":" + AUTOSCALE, shouldAutoscale_);
         }
      }
   }

   /**
    * Read the Profile, find the saved RememberedChannelSettings info for the
    * given name and group, and return the result -- or use the provided
    * default settings if no saved entry can be found.
    */
   public static RememberedChannelSettings loadSettings(String channelName,
         String channelGroup, Color defaultColor, Integer[] histogramMins,
         Integer[] histogramMaxes, Boolean shouldAutoscale) {
      // Don't try to do this when someone else is (potentially) modifying the
      // profile.
      synchronized(PROFILELOCK) {
         UserProfile profile = MMStudio.getInstance().profile();
         String key = genKey(channelName, channelGroup);
         defaultColor = new Color(profile.getInt(
                  RememberedChannelSettings.class, key + ":" + COLOR,
                  defaultColor.getRGB()));
         histogramMins = profile.getIntArray(RememberedChannelSettings.class,
               key + ":" + MINS, histogramMins);
         histogramMaxes = profile.getIntArray(RememberedChannelSettings.class,
               key + ":" + MAXES, histogramMaxes);
         shouldAutoscale = profile.getBoolean(
               RememberedChannelSettings.class, key + ":" + AUTOSCALE,
               shouldAutoscale);
         return new RememberedChannelSettings(channelName, channelGroup,
               defaultColor, histogramMins, histogramMaxes, shouldAutoscale);
      }
   }

   /**
    * Get just the color for the specified channel, or the provided default if
    * no value is found.
    */
   public static Color getColorForChannel(String channelName,
         String channelGroup, Color defaultColor) {
      synchronized(PROFILELOCK) {
         UserProfile profile = MMStudio.getInstance().profile();
         String key = genKey(channelName, channelGroup);
         Integer rgb = profile.getSettings(RememberedChannelSettings.class).
                 getInteger(key + ":" + COLOR, -1);
         if (rgb == -1) {
            return defaultColor;
         }
         return new Color(rgb);
      }
   }

   /**
    * Given a SummaryMetadata object and a DisplaySettings object, record the
    * appropriate color/min/max values into the DisplaySettings and return it.
    * Note that the SummaryMetadata's channel names are assumed to be in the
    * same order as the ContrastSettings array in the DisplaySettings.
    */
   //TODO: figute out if this function is still needed and if so, 
   // figure out its intent and how to make it work.
   public static DisplaySettings updateSettings(SummaryMetadata summary,
         DisplaySettings settings, int numChannels) {
      Color[] newColors = new Color[numChannels];
      DisplaySettings.ContrastSettings[] newSettings = 
              new DisplaySettings.ContrastSettings[numChannels];
      String group = summary.getChannelGroup();
      for (int i = 0; i < numChannels; ++i) {
         // Load this channel's settings
         String name = summary.getSafeChannelName(i);
         RememberedChannelSettings channel = loadSettings(name, group,
               Color.WHITE, null, null, null);
         newSettings[i] = MMStudio.getInstance().displays()
            .getContrastSettings(channel.getHistogramMins(),
                  channel.getHistogramMaxes(), null, null);
      }

      //return settings.copyBuilder().channelColors(newColors)
      //   .channelContrastSettings(newSettings).build();
      return settings;  //
   }

   /**
    * Given a DisplaySettings and a SummaryMetadata (for channel names/group),
    * generate RememberedChannelSettings from its values and save them to the
    * profile.
    */
   @Deprecated
   public static void saveSettingsToProfile(DisplaySettings settings,
         SummaryMetadata summary, int numChannels) {
      String group = summary.getChannelGroup();
      for (int i = 0; i < numChannels; ++i) {
         String channel = summary.getSafeChannelName(i);
         DisplaySettings.ContrastSettings contrast = settings.getSafeContrastSettings(i,
               new DefaultDisplaySettings.DefaultContrastSettings(0, 0, 1.0, true));
         Color defaultColor = ColorPalettes.getFromDefaultPalette(i);
         Color color = settings.getChannelColor(i);
         new RememberedChannelSettings(channel, group, color,
               contrast.getContrastMins(), contrast.getContrastMaxes(),
               settings.isAutostretchEnabled()).saveToProfile();
      }
   }

   @Override
   public String toString() {
      return String.format("<RememberedChannelSettings name %s, color %s, min %d, max %d, auto %s>",
            genKey(channelName_, channelGroup_), color_, histogramMins_,
            histogramMaxes_, shouldAutoscale_);
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof RememberedChannelSettings)) {
         return false;
      }
      RememberedChannelSettings alt = (RememberedChannelSettings) obj;
      if (color_ != alt.getColor()) {
         return false;
      }
      if (!Arrays.deepEquals(histogramMins_, alt.getHistogramMins()) ||
            !Arrays.deepEquals(histogramMaxes_, alt.getHistogramMaxes())) {
         return false;
      }
      return shouldAutoscale_ == alt.getShouldAutoscale();
   }
}
