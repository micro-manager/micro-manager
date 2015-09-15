///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display API
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

package org.micromanager.display;

import java.awt.Color;

import org.micromanager.PropertyMap;

/**
 * This class defines the parameters that control how a given DisplayWindow
 * displays data from the Datastore.
 * It is immutable; construct it with a DisplaySettingsBuilder.
 * You are not expected to implement this interface; it is here to describe how
 * you can interact with DisplaySettings created by Micro-Manager itself. If
 * you need a DisplaySettingsBuilder, you can generate one via the
 * DataManager's getDisplaySettingsBuilder() method, or by using the copy()
 * method of an existing DisplaySettings instance.
 *
 * This class uses a Builder pattern. Please see
 * https://micro-manager.org/wiki/Using_Builders
 * for more information.
 */
public interface DisplaySettings {

   interface DisplaySettingsBuilder {
      /**
       * Construct a DisplaySettings from the DisplaySettingsBuilder. Call 
       * this once you are finished setting all DisplaySettings parameters.
       * @return Build object
       */
      DisplaySettings build();

      // The following functions each set the relevant value for the 
      // DisplaySettings. See the getters of the DisplaySettings, below,
      // for information on the meaning of these fields.
      DisplaySettingsBuilder channelColors(Color[] channelColors);
      /**
       * "Safely" update the channelColors property to include the new
       * provided color at the specified index. If the channelColors
       * property is null or is not large enough to incorporate the specified
       * index, then a new array will be created that is long enough, values
       * will be copied across, and any missing values will be null.
       * @param newColor New color for the specified channel.
       * @param channelIndex Index into the channelColors array.
       */
      DisplaySettingsBuilder safeUpdateChannelColor(Color newColor,
            int channelIndex);
      DisplaySettingsBuilder channelContrastMins(Integer[] channelContrastMins);
      /**
       * "Safely" update the channelContrastMins property to include the new
       * provided min at the specified index. If the channelContrastMins
       * property is null or is not large enough to incorporate the specified
       * index, then a new array will be created that is long enough, values
       * will be copied across, and any missing values will be null.
       * @param newMin New contrast min for the channel.
       * @param channelIndex Index into the channelContrastMins array.
       */
      DisplaySettingsBuilder safeUpdateChannelContrastMin(Integer newMin,
            int channelIndex);
      DisplaySettingsBuilder channelContrastMaxes(Integer[] channelContrastMaxes);
      /**
       * "Safely" update the channelContrastMaxes property to include the new
       * provided max at the specified index. If the channelContrastMaxes
       * property is null or is not large enough to incorporate the specified
       * index, then a new array will be created that is long enough, values
       * will be copied across, and any missing values will be null.
       * @param newMax New contrast max for the channel.
       * @param channelIndex Index into the channelContrastMaxes array.
       */
      DisplaySettingsBuilder safeUpdateChannelContrastMax(Integer newMax,
            int channelIndex);
      DisplaySettingsBuilder channelGammas(Double[] channelGammas);
      DisplaySettingsBuilder magnification(Double magnification);
      DisplaySettingsBuilder animationFPS(Integer animationFPS);
      DisplaySettingsBuilder channelColorMode(ColorMode channelColorMode);
      DisplaySettingsBuilder histogramUpdateRate(Double histogramUpdateRate);
      DisplaySettingsBuilder shouldSyncChannels(Boolean shouldSyncChannels);
      DisplaySettingsBuilder shouldAutostretch(Boolean shouldAutostretch);
      DisplaySettingsBuilder extremaPercentage(Double extremaPercentage);
      DisplaySettingsBuilder bitDepthIndices(Integer[] bitDepthIndices);
      DisplaySettingsBuilder shouldUseLogScale(Boolean shouldUseLogScale);

      DisplaySettingsBuilder userData(PropertyMap userData);
   }

   /**
    * Generate a new DisplaySettingsBuilder whose values are initialized to be
    * the values of this DisplaySettings.
    * @return Copy of the DisplaySettingsBuilder
    */
   DisplaySettingsBuilder copy();

   /** 
    * The colors of each channel in the image display window 
    * @return Channel colors
    */
   public Color[] getChannelColors();

   /**
    * Safely retrieve the color for the specified channel. If the
    * channelColors property is null, is too small to have a value for the
    * given index, or has a value of null for that index, then the provided
    * default value will be returned instead.
    * @param index Channel index to get the color for
    * @param defaultVal Default value to return if no color is available.
    */
   public Color getSafeChannelColor(int index, Color defaultVal);
   
   /** 
    * The value that is treated as "black" in the display window 
    * @return Highest values that are shown as black in the display window
    */
   public Integer[] getChannelContrastMins();
   
   /**
    * Safely retrieve the contrast min for the specified channel. If the
    * channelContrastMins property is null, is too small to have a value for
    * the given index, or has a value of null for that index, then the provided
    * default value will be returned instead.
    * @param index Channel index to get the contrast min for
    * @param defaultVal Default value to return if no contrast min is available.
    */
   public Integer getSafeChannelContrastMin(int index, Integer defaultVal);

   /** 
    * The value that is treated as "white" (or equivalent max intensity for
    * colored displays) in the display window
    * @return Lowest values that are shown as white in the display window 
    */
   public Integer[] getChannelContrastMaxes();
   
   /**
    * Safely retrieve the contrast max for the specified channel. If the
    * channelContrastMaxes property is null, is too small to have a value for
    * the given index, or has a value of null for that index, then the provided
    * default value will be returned instead.
    * @param index Channel index to get the contrast max for
    * @param defaultVal Default value to return if no contrast max is available.
    */
   public Integer getSafeChannelContrastMax(int index, Integer defaultVal);

   /** 
    * The gamma curve modifier for each channel 
    * @return Gamma for each channel
    */
   public Double[] getChannelGammas();
   
   /**
    * Safely retrieve the contrast gamma for the specified channel. If the
    * channelGammas property is null, is too small to have a value for
    * the given index, or has a value of null for that index, then the provided
    * default value will be returned instead.
    * @param index Channel index to get the gamma for
    * @param defaultVal Default value to return if no gamma is available.
    */
   public Double getSafeChannelGamma(int index, Double defaultVal);

   /** 
    * The magnification level of the canvas 
    * @return magnification level of the canvas 
    */
   public Double getMagnification();
   
   /** 
    * Animation speed, when animation of the display is turned on 
    * @return Animation speed in frames per second
    */
   public Integer getAnimationFPS();

   /**
    * This enum defines the order in which values in the "color mode"
    * control are listed. In other words, the value of one of these enums
    * corresponds to the index of the corresponding entry in the dropdown
    * menu.
    */
   public enum ColorMode {
      // TODO: replace numbers with strings.
      COLOR(0), COMPOSITE(1), GRAYSCALE(2), HIGHLIGHT_LIMITS(3), FIRE(4),
         RED_HOT(5), SPECTRUM(6);
      private int index_;

      ColorMode(int index) {
         index_ = index;
      }

      public int getIndex() {
         return index_;
      }

      public static ColorMode fromInt(int index) {
         for (ColorMode mode : ColorMode.values()) {
            if (mode.getIndex() == index) {
               return mode;
            }
         }
         return null;
      }
   }

   /** 
    * The index into the "Display mode" control.
    * @return index into the "Display mode" control
    */
   public ColorMode getChannelColorMode();

   /** 
    * How much time to allow to pass between updates to the histogram, in
    * seconds (set to 0 for continuous update, or any negative value to
    * disable updates altogether) 
    * @return Minimum time between histogram updates in seconds
    */
   public Double getHistogramUpdateRate();
   
   /** 
    * Whether histogram settings should be synced across channels 
    * @return True if histograms should sync between channels
    */
   public Boolean getShouldSyncChannels();
   
   /** 
    * Whether each newly-displayed image should be autostretched 
    * @return True if new images should be auto-stretched
    */
   public Boolean getShouldAutostretch();
   
   /** 
    * The percentage of values off the top and bottom of the image's value
    * range that get ignored when autostretching 
    * @return Number used in Autostretch mode to determine where to set the 
    * white and black points.  Expressed as percentage.
    */
   public Double getExtremaPercentage();
   
   /** 
    * The indices into the bit depth drop-down for controlling the X scale of
    * the histogram, for each channel. 
    * @return Index into dropdown menu
    */
   public Integer[] getBitDepthIndices();

   /**
    * Safely retrieve the bit depth index for the specified channel. If the
    * bitDepthIndices property is null, is too small to have a value for the
    * given index, or has a value of null for that index, then the provided
    * default value will be returned instead.
    * @param index Channel index to get the bit depth index for
    * @param defaultVal Default value to return if no bit depth index is
    *        available.
    */
   public Integer getSafeBitDepthIndex(int index, Integer defaultVal);
   
   /** 
    * Whether or not to display the histograms using a logarithmic scale 
    * @return True if log scale should be used
    */
   public Boolean getShouldUseLogScale();
   
   /** 
    * Any additional user-supplied data
    * @return User data
    */
   public PropertyMap getUserData();

   public static final String FILENAME = "displaySettings.txt";
   
   /** 
    * Save the DisplaySettings to a file in the specified folder. If the file
    * already exists, then the settings will be appended to it. 
    * @param path Full path to directory in which the DisplaySettings file should
    * be saved
    */
   public void save(String path);
}
