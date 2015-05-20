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
      DisplaySettingsBuilder channelContrastMins(Integer[] channelContrastMins);
      DisplaySettingsBuilder channelContrastMaxes(Integer[] channelContrastMaxes);
      DisplaySettingsBuilder channelGammas(Double[] channelGammas);
      DisplaySettingsBuilder magnification(Double magnification);
      DisplaySettingsBuilder animationFPS(Integer animationFPS);
      DisplaySettingsBuilder channelDisplayModeIndex(Integer channelDisplayModeIndex);
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
    * The value that is treated as "black" in the display window 
    * @return Highest values that are shown as black in the display window
    */
   public Integer[] getChannelContrastMins();
   
   /** 
    * The value that is treated as "white" (or equivalent max intensity for
     * colored displays) in the display window
    * @return Lowest values that are shown as white in the display window 
    */
   public Integer[] getChannelContrastMaxes();
   
   /** 
    * The gamma curve modifier for each channel 
    * @return Gamma for each channel
    */
   public Double[] getChannelGammas();
   
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
    * The index into the "Display mode" control; 0 = Color, 1 = Grayscale,
    * 2 = Composite 
    * @return index into the "Display mode" control
    */
   public Integer getChannelDisplayModeIndex();
   
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
