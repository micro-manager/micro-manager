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
 * named channel, and is used to ensure that when we need a new display that
 * uses that channel, we are able to recover it from the profile.
 */
public class ChannelSettings {
   private static final String NAMES = "names of channels and channel groups";
   private static final String COLORS = "colors to use for channels";
   private static final String MINS = "default minimum values to use in the histograms when scaling";
   private static final String MAXES = "default maximum values to use in the histograms when scaling";
   private static final String AUTOSCALES = "whether or not to use autoscaling for channels"; 

   // Used to prevent simultaneous read/writes of the profile
   private static final Object profileLock_ = new Object();
   private final String channelName_;
   private final String channelGroup_;

   private final Color color_;
   private final Integer histogramMin_;
   private final Integer histogramMax_;
   private final Boolean shouldAutoscale_;

   public ChannelSettings(String channelName, String channelGroup,
         Color color, Integer histogramMin, Integer histogramMax,
         Boolean shouldAutoscale) {
      channelName_ = channelName;
      channelGroup_ = channelGroup;
      color_ = color;
      histogramMin_ = histogramMin;
      histogramMax_ = histogramMax;
      shouldAutoscale_ = shouldAutoscale;
   }

   public Color getColor() {
      return color_;
   }

   public Integer getHistogramMin() {
      return histogramMin_;
   }

   public Integer getHistogramMax() {
      return histogramMax_;
   }

   public Boolean getShouldAutoscale() {
      return shouldAutoscale_;
   }

   private static String genKey(String channelName, String channelGroup) {
      return channelName + ":" + channelGroup;
   }

   /**
    * Record this ChannelSettings in the profile under its channel name and
    * group. Genera ChannelSettings information is stored as a bunch of
    * arrays (e.g. autoscaling for all saved ChannelSettings is an array of
    * booleans). The index we use into these arrays depends on the array of
    * channel names.
    */
   public void saveToProfile() {
      synchronized(profileLock_) {
         DefaultUserProfile profile = DefaultUserProfile.getInstance();
         // Look for an existing entry using this name/group.
         String[] keysArr = profile.getStringArray(ChannelSettings.class,
               NAMES, new String[] {});
         String ourKey = genKey(channelName_, channelGroup_);
         ArrayList<String> keys = new ArrayList<String>(Arrays.asList(keysArr));
         int index = keys.indexOf(ourKey);
         boolean mustExpand = false;
         if (index == -1) {
            // Haven't saved this channel yet; add it to the list of names and
            // record the new names. We'll also need to expand every other
            // array we work with.
            keys.add(ourKey);
            index = keys.indexOf(ourKey);
            mustExpand = true;
            keysArr = keys.toArray(keysArr);
            profile.setStringArray(ChannelSettings.class, NAMES, keysArr);
         }

         // Save settings, inserting/replacing our value as appropriate.
         Integer[] colorsArr = profile.getIntArray(ChannelSettings.class,
               COLORS, new Integer[] {});
         if (mustExpand || colorsArr[index] != color_.getRGB()) {
            if (mustExpand) {
               ArrayList<Integer> colors = new ArrayList<Integer>(Arrays.asList(colorsArr));
               colors.add(color_.getRGB());
               colorsArr = colors.toArray(colorsArr);
            }
            else {
               colorsArr[index] = color_.getRGB();
            }
            profile.setIntArray(ChannelSettings.class, COLORS, colorsArr);
         }

         Integer[] minsArr = profile.getIntArray(ChannelSettings.class,
               MINS, new Integer[] {});
         if (mustExpand || minsArr[index] != histogramMin_) {
            if (mustExpand) {
               ArrayList<Integer> mins = new ArrayList<Integer>(Arrays.asList(minsArr));
               mins.add(histogramMin_);
               minsArr = mins.toArray(minsArr);
            }
            else {
               minsArr[index] = histogramMin_;
            }
            profile.setIntArray(ChannelSettings.class, MINS, minsArr);
         }

         Integer[] maxesArr = profile.getIntArray(ChannelSettings.class,
               MAXES, new Integer[] {});
         if (mustExpand || maxesArr[index] != histogramMax_) {
            if (mustExpand) {
               ArrayList<Integer> maxes = new ArrayList<Integer>(Arrays.asList(maxesArr));
               maxes.add(histogramMax_);
               maxesArr = maxes.toArray(maxesArr);
            }
            else {
               maxesArr[index] = histogramMax_;
            }
            profile.setIntArray(ChannelSettings.class, MAXES, maxesArr);
         }

         Boolean[] autoscalesArr = profile.getBooleanArray(
               ChannelSettings.class, AUTOSCALES, new Boolean[] {});
         if (mustExpand || autoscalesArr[index] != shouldAutoscale_) {
            if (mustExpand) {
               ArrayList<Boolean> autoscales = new ArrayList<Boolean>(Arrays.asList(autoscalesArr));
               autoscales.add(shouldAutoscale_);
               autoscalesArr = autoscales.toArray(autoscalesArr);
            }
            else {
               autoscalesArr[index] = shouldAutoscale_;
            }
            profile.setBooleanArray(ChannelSettings.class, AUTOSCALES,
                  autoscalesArr);
         }
      }
   }

   /**
    * Return the index of the given channel into our arrays, or -1 if it's
    * not found.
    */
   private static int getIndex(String channelName, String channelGroup) {
      synchronized(profileLock_) {
         DefaultUserProfile profile = DefaultUserProfile.getInstance();
         String[] keysArr = profile.getStringArray(ChannelSettings.class,
               NAMES, new String[] {});
         ArrayList<String> keys = new ArrayList<String>(Arrays.asList(keysArr));
         String ourKey = genKey(channelName, channelGroup);
         return keys.indexOf(ourKey);
      }
   }

   /**
    * Read the Profile, find the saved ChannelSettings info for the given
    * name and group, and return the result -- or use the provided default
    * settings if no saved entry can be found.
    */
   public static ChannelSettings loadSettings(String channelName,
         String channelGroup, Color defaultColor, Integer histogramMin,
         Integer histogramMax, Boolean shouldAutoscale) {
      // Don't try to do this when someone else is (potentially) modifying the
      // profile.
      synchronized(profileLock_) {
         DefaultUserProfile profile = DefaultUserProfile.getInstance();
         int index = getIndex(channelName, channelGroup);
         if (index == -1) {
            // No entry for this channel yet.
            return new ChannelSettings(channelName, channelGroup, defaultColor,
                  histogramMin, histogramMax, shouldAutoscale);
         }

         Integer[] colors = profile.getIntArray(ChannelSettings.class,
               COLORS, new Integer[] {});
         Color color = defaultColor;
         if (colors != null && colors.length > index) {
            color = new Color(colors[index]);
         }

         Integer[] mins = profile.getIntArray(ChannelSettings.class,
               MINS, new Integer[] {});
         if (mins != null && mins.length > index) {
            histogramMin = mins[index];
         }

         Integer[] maxes = profile.getIntArray(ChannelSettings.class,
               MAXES, new Integer[] {});
         if (maxes != null && maxes.length > index) {
            histogramMax = maxes[index];
         }

         Boolean[] autoscales = profile.getBooleanArray(ChannelSettings.class,
               AUTOSCALES, new Boolean[] {});
         if (autoscales != null && autoscales.length > index) {
            shouldAutoscale = autoscales[index];
         }

         return new ChannelSettings(channelName, channelGroup, color,
               histogramMin, histogramMax, shouldAutoscale);
      }
   }

   /**
    * Get just the color for the specified channel, or the provided default if
    * no value is found.
    */
   public static Color getColorForChannel(String channelName,
         String channelGroup, Color defaultColor) {
      synchronized(profileLock_) {
         int index = getIndex(channelName, channelGroup);
         if (index == -1) {
            // No entry for this channel.
            return defaultColor;
         }
         DefaultUserProfile profile = DefaultUserProfile.getInstance();
         Integer[] colors = profile.getIntArray(ChannelSettings.class,
               COLORS, new Integer[] {});
         if (colors == null || colors.length <= index) {
            return defaultColor;
         }
         return new Color(colors[index]);
      }
   }

   /**
    * Given a list of channel names, a channel group, and a DisplaySettings
    * object, record the appropriate color/min/max values into the
    * DisplaySettings. Or return null if no changes need to be made.
    */
   public static DisplaySettings updateSettings(String[] channelNames,
         String channelGroup, DisplaySettings settings) {
      Color[] origColors = settings.getChannelColors();
      Integer[] origMins = settings.getChannelContrastMins();
      Integer[] origMaxes = settings.getChannelContrastMaxes();
      Color[] colors = new Color[channelNames.length];
      Integer[] mins = new Integer[channelNames.length];
      Integer[] maxes = new Integer[channelNames.length];
      boolean didChange = false;
      for (int i = 0; i < channelNames.length; ++i) {
         // Load this channel's settings, and compare it against the settings
         // in the DisplaySettings for changes.
         String name = channelNames[i];
         ChannelSettings channel = loadSettings(name, channelGroup,
               null, null, null, null);
         colors[i] = channel.getColor();
         mins[i] = channel.getHistogramMin();
         maxes[i] = channel.getHistogramMax();
         if (origColors == null || origColors.length != colors.length ||
               origColors[i] != colors[i] ||
               origMins == null || origMins.length != mins.length ||
               origMins[i] != mins[i] ||
               origMaxes == null || origMaxes.length != maxes.length ||
               origMaxes[i] != maxes[i]) {
            didChange = true;
         }
      }

      if (didChange) {
         settings = settings.copy().channelColors(colors)
            .channelContrastMins(mins).channelContrastMaxes(maxes).build();
         return settings;
      }
      return null;
   }

   @Override
   public String toString() {
      return String.format("<ChannelSettings name %s, color %s, min %d, max %d, auto %s>",
            genKey(channelName_, channelGroup_), color_, histogramMin_,
            histogramMax_, shouldAutoscale_);
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof ChannelSettings)) {
         return false;
      }
      ChannelSettings alt = (ChannelSettings) obj;
      return (color_.equals(alt.getColor()) &&
            histogramMin_ == alt.getHistogramMin() &&
            histogramMax_ == alt.getHistogramMax() &&
            shouldAutoscale_ == alt.getShouldAutoscale());
   }
}
