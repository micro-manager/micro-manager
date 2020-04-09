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
import java.util.List;
import java.util.Objects;
import org.micromanager.UserProfile;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.internal.MMStudio;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * This container class stores histogram and color settings for a specific
 * named channel (e.g. DAPI or FITC), and is used to ensure that when we need a
 * new display that uses that channel, we are able to recover it from the
 * profile.
 * 
 * NS 8/2019: It looks like this class is a remnant from 2.0-beta that does not
 * really fit with the new Channel and Component scheme.
 * 
 * Functionality of this class was moved into RememberedSettings.
 * That class still uses this class for backwards compatibility
 * Do not use in new code.  
 * 
 * Remove this class by Sept. 2020
 * 
 */

@Deprecated
public final class RememberedChannelSettings {
   private static final String COLOR = "color to use for channels";
   private static final String MINS = "default minimum values to use in the histograms when scaling";
   private static final String MAXES = "default maximum values to use in the histograms when scaling";
   private static final String AUTOSCALE = "whether or not to use autoscaling for this channel"; 

   // Used to prevent simultaneous read/writes of the profile
   private static final Object PROFILELOCK = new Object();
   
   
   private final String channelName_;           //Name of the channel whose settings will be stored
   private final String channelGroup_;          // Channel group to which this channel belongs
   private final Color color_;                  // Color to be used in display
   private final List<Integer> histogramMins_;  // minima (for each component in this channel)
   private final List<Integer> histogramMaxes_; // maxima (for each component in this channel)
   private final Boolean shouldAutoscale_;      // whether or not to autoscale this channel

   /**
    * For purposes of saving to profile, any or all of the settings (i.e.
    * everything except the channel name and channel group) may be null, in
    * which case they will not be saved to the profile.
    * @param channelName   Name of the channel whose settings will be stored
    * @param channelGroup  Channel group to which this channel belongs
    * @param color         Color to be used in display
    * @param histogramMins minima (for each component in this channel)
    * @param histogramMaxes maxima (for each component in this channel)
    * @param shouldAutoscale whether or not to autoscale this channel
    */
   public RememberedChannelSettings(String channelName, 
         String channelGroup,
         Color color,
         List<Integer> histogramMins, 
         List<Integer> histogramMaxes,
         boolean shouldAutoscale) {
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
      return histogramMins_.get(component);
   }

   public List<Integer> getHistogramMins() {
      return histogramMins_;
   }

   public Integer getHistogramMax(int component) {
      return histogramMaxes_.get(component);
   }

   public List<Integer> getHistogramMaxes() {
      return histogramMaxes_;
   }

   public Boolean getShouldAutoscale() {
      return shouldAutoscale_;
   }

   static String genKey(String channelGroup, String channelName) {
      // weird order, keep for backwards compatibility
      return channelName + ":" + channelGroup;
   }

   /**
    * Record this RememberedChannelSettings in the profile under its channel
    * name and group.
    */
   public void saveToProfile() {
      synchronized(PROFILELOCK) {
         MutablePropertyMapView settings = 
                 MMStudio.getInstance().profile().getSettings(RememberedChannelSettings.class);
         
         String ourKey = genKey(channelGroup_, channelName_);
         if (color_ != null) {
            settings.putInteger(ourKey + ":" + COLOR, color_.getRGB());
         }
         if (histogramMins_ != null) {
            settings.putIntegerList(ourKey + ":" + MINS, histogramMins_);
         }
         if (histogramMaxes_ != null) {
            settings.putIntegerList(ourKey + ":" + MAXES, histogramMaxes_);
         }
         if (shouldAutoscale_ != null) {
            settings.putBoolean(
                  ourKey + ":" + AUTOSCALE, shouldAutoscale_);
         }
      }
   }

   /**
    * Read the Profile, find the saved RememberedChannelSettings info for the
    * given name and group, and return the result -- or use the provided
    * default settings if no saved entry can be found.
    * @param channelName   Name of the channel whose settings will be stored
    * @param channelGroup  Channel group to which this channel belongs
    * @param color         Color to be used in display
    * @param histogramMins minima (for each component in this channel)
    * @param histogramMaxes maxima (for each component in this channel)
    * @param shouldAutoscale whether or not to autoscale this channel
    * @return 
    */
   public static RememberedChannelSettings loadSettings(
           String channelGroup,
           String channelName, 
           Color color, 
           List<Integer> histogramMins,
           List<Integer> histogramMaxes, 
           boolean shouldAutoscale) {
      // Don't try to do this when someone else is (potentially) modifying the
      // profile.
      synchronized(PROFILELOCK) {
         MutablePropertyMapView settings = 
                 MMStudio.getInstance().profile().getSettings(RememberedChannelSettings.class);
         String key = genKey(channelGroup, channelName);
         color = new Color(settings.getInteger(key + ":" + COLOR,
                  color.getRGB()));
         histogramMins = settings.getIntegerList(key + ":" + MINS, histogramMins);
         histogramMaxes = settings.getIntegerList(key + ":" + MAXES, histogramMaxes);
         shouldAutoscale = settings.getBoolean(key + ":" + AUTOSCALE, shouldAutoscale);
         return new RememberedChannelSettings(channelGroup, channelName,
               color, histogramMins, histogramMaxes, shouldAutoscale);
      }
   }

   /**
    * Get just the color for the specified channel, or the provided default if
    * no value is found.
    * @param channelName   Name of the channel whose settings will be stored
    * @param channelGroup  Channel group to which this channel belongs
    * @param color         Default color 
    * @return              Color to be used in display
    */
   public static Color getColorForChannel(String channelGroup,
            String channelName, 
            Color color) {
      synchronized(PROFILELOCK) {
         UserProfile profile = MMStudio.getInstance().profile();
         String key = genKey(channelGroup, channelName);
         Integer rgb = profile.getSettings(RememberedChannelSettings.class).
                 getInteger(key + ":" + COLOR, -1);
         if (rgb == -1) {
            return color;
         }
         return new Color(rgb);
      }
   }

   
   public static DisplaySettings loadDefaultDisplaySettings(SummaryMetadata summary) {
      DisplaySettings.Builder builder = DefaultDisplaySettings.builder();
      String channelGroup = summary.getChannelGroup();
      List<String> channelNames = summary.getChannelNameList();
      for (int ch = 0; ch < channelNames.size(); ch++) {
         String name = summary.getSafeChannelName(ch);
         RememberedChannelSettings settings = loadSettings(channelGroup, name, 
                 Color.WHITE, null, null, true);
         builder.channel(ch, settings.toChannelDisplaySetting(channelGroup, name));
      }
      builder.autostretch(true);
      return builder.build();      
   }
   
   public static ChannelDisplaySettings getRememberedChannelDisplaySettings
        (SummaryMetadata summary, int chNr) {
      RememberedChannelSettings settings = loadSettings(summary.getChannelGroup(),
              summary.getChannelNameList().get(chNr), Color.WHITE, null, null, true);
      return settings.toChannelDisplaySetting(summary.getChannelGroup(),
              summary.getChannelNameList().get(chNr));
   }
   
   public ChannelDisplaySettings toChannelDisplaySetting(String channelGroup, String channelName) {
      ChannelDisplaySettings.Builder builder = DefaultChannelDisplaySettings.builder();
      builder.color(color_).groupName(channelGroup).name(channelName);
      // ugly, we rely on a null list to know if we should return defaults...
      if (histogramMins_ != null) {
         for (int comp = 0; comp < histogramMins_.size(); comp++) {
            ComponentDisplaySettings.Builder compBuilder = DefaultComponentDisplaySettings.builder();
            compBuilder.
                    scalingGamma(1.0).
                    scalingMinimum(histogramMins_.get(comp)).
                    scalingMaximum(histogramMaxes_.get(comp));
            builder.component(comp, compBuilder.build());
         }
      }
      return builder.build();
   }
   
   public static RememberedChannelSettings fromChannelDisplaySettings(
           String channelGroup,
           String channelName,
           ChannelDisplaySettings settings) {
      List<Integer> histogramMins = new ArrayList<>();
      List<Integer> histogramMaxs = new ArrayList<>();
      for (ComponentDisplaySettings cs : settings.getAllComponentSettings()) {
         histogramMins.add(((Long) cs.getScalingMinimum()).intValue());
         histogramMaxs.add(((Long) cs.getScalingMaximum()).intValue());
      }
      return new RememberedChannelSettings(
              channelGroup,
              channelName,
              settings.getColor(),
              histogramMins,
              histogramMaxs,
              true);

   }


   @Override
   public String toString() {
      return String.format("<RememberedChannelSettings name %s, color %s, min %d, max %d, auto %s>",
            genKey(channelGroup_, channelName_), color_, histogramMins_,
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
      if (!(histogramMins_.equals(alt.getHistogramMins())) ||
          !(histogramMaxes_.equals(alt.getHistogramMaxes())) ) {
         return false;
      }
      return Objects.equals(shouldAutoscale_, alt.getShouldAutoscale());
   }

   @Override
   public int hashCode() {
      int hash = 3;
      hash = 89 * hash + Objects.hashCode(this.channelName_);
      hash = 89 * hash + Objects.hashCode(this.channelGroup_);
      hash = 89 * hash + Objects.hashCode(this.color_);
      hash = 89 * hash + Objects.hashCode(this.histogramMins_);
      hash = 89 * hash + Objects.hashCode(this.histogramMaxes_);
      hash = 89 * hash + Objects.hashCode(this.shouldAutoscale_);
      return hash;
   }
}
