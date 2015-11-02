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
import java.util.ArrayList;
import java.util.Arrays;

import org.micromanager.display.DisplaySettings;

import org.micromanager.internal.utils.DefaultUserProfile;

/**
 * This container class stores histogram and color settings for a specific
 * named channel (e.g. DAPI or FITC), and is used to ensure that when we need a
 * new display that uses that channel, we are able to recover it from the
 * profile.
 */
public class RememberedChannelSettings {
   private static final String NAMES = "names of channels and channel groups";
   private static final String COLOR = "color to use for channels";
   private static final String MINS = "default minimum values to use in the histograms when scaling";
   private static final String MAXES = "default maximum values to use in the histograms when scaling";
   private static final String AUTOSCALE = "whether or not to use autoscaling for this channel"; 

   // Used to prevent simultaneous read/writes of the profile
   private static final Object profileLock_ = new Object();
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
      synchronized(profileLock_) {
         DefaultUserProfile profile = DefaultUserProfile.getInstance();
         String ourKey = genKey(channelName_, channelGroup_);
         if (color_ != null) {
            profile.setInt(RememberedChannelSettings.class,
                  ourKey + ":" + COLOR, color_.getRGB());
         }
         if (histogramMins_ != null) {
            profile.setIntArray(RememberedChannelSettings.class,
                  ourKey + ":" + MINS, histogramMins_);
         }
         if (histogramMaxes_ != null) {
            profile.setIntArray(RememberedChannelSettings.class,
                  ourKey + ":" + MAXES, histogramMaxes_);
         }
         if (shouldAutoscale_ != null) {
            profile.setBoolean(RememberedChannelSettings.class,
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
      synchronized(profileLock_) {
         DefaultUserProfile profile = DefaultUserProfile.getInstance();
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
      int defaultRGB = -1;
      if (defaultColor != null) {
         defaultRGB = defaultColor.getRGB();
      }
      synchronized(profileLock_) {
         DefaultUserProfile profile = DefaultUserProfile.getInstance();
         String key = genKey(channelName, channelGroup);
         int rgb = profile.getInt(
               RememberedChannelSettings.class, key + ":" + COLOR, defaultRGB);
         if (defaultColor == null && defaultRGB == -1) {
            // No known color here and user wants a null default.
            return null;
         }
         else {
            return new Color(rgb);
         }
      }
   }

   /**
    * As getColorForChannel, but we use the DisplaySettings as a fallback.
    */
   public static Color getColorWithSettings(String channelName,
         String channelGroup, DisplaySettings settings, int channelIndex,
         Color defaultColor) {
      Color result = getColorForChannel(channelName, channelGroup, null);
      if (result == null) {
         result = settings.getSafeChannelColor(channelIndex, null);
      }
      if (result == null) {
         result = defaultColor;
      }
      return result;
   }

   /**
    * Given a list of channel names, a channel group, and a DisplaySettings
    * object, record the appropriate color/min/max values into the
    * DisplaySettings and return it. Or return null if no changes need to be
    * made. Note that the input channel names are assumed to be in the same
    * order as the ContrastSettings array in the DisplaySettings.
    */
   public static DisplaySettings updateSettings(String[] channelNames,
         String channelGroup, DisplaySettings settings) {
      Color[] origColors = settings.getChannelColors();
      Color[] newColors = new Color[channelNames.length];
      DisplaySettings.ContrastSettings[] newSettings = new DisplaySettings.ContrastSettings[channelNames.length];
      boolean didChange = false;
      for (int i = 0; i < channelNames.length; ++i) {
         // Load this channel's settings, and compare it against the settings
         // in the DisplaySettings for changes.
         String name = channelNames[i];
         RememberedChannelSettings channel = loadSettings(name, channelGroup,
               Color.WHITE, null, null, null);
         DisplaySettings.ContrastSettings oldSettings = settings.getSafeContrastSettings(i, null);
         newSettings[i] = DefaultDisplayManager.getInstance()
            .getContrastSettings(channel.getHistogramMins(),
                  channel.getHistogramMaxes(), null, null);
         if (didChange) {
            // No need to scan the remaining channels.
            continue;
         }
         if (oldSettings == null ||
               oldSettings.getNumComponents() != newSettings[i].getNumComponents()) {
            // Invalid old settings, or changed number of components.
            didChange = true;
            continue;
         }
         for (int j = 0; j < oldSettings.getNumComponents(); ++j) {
            if (oldSettings.getSafeContrastMin(j, null) != newSettings[i].getSafeContrastMin(j, null) ||
                  oldSettings.getSafeContrastMax(j, null) != newSettings[i].getSafeContrastMax(j, null)) {
               // Contrast for this component changed.
               didChange = true;
               continue;
            }
         }
         if (settings.getSafeChannelColor(i, null) != channel.getColor()) {
            // Color changed.
            didChange = true;
            continue;
         }
      }

      if (didChange) {
         settings = settings.copy().channelColors(newColors)
            .channelContrastSettings(newSettings).build();
         return settings;
      }
      return null;
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
