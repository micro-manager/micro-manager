package org.micromanager.display;

import java.awt.Color;

import org.json.JSONObject;

import org.micromanager.data.Coords;
import org.micromanager.MultiStagePosition;

/**
 * This class defines the parameters that control how a given DisplayWindow
 * displays data from the Datastore.
 * It is immutable; construct it with a DisplaySettingsBuilder.
 * You are not expected to implement this interface; it is here to describe how
 * you can interact with DisplaySettings created by Micro-Manager itself. If
 * you need a DisplaySettingsBuilder, you can generate one via the
 * DataManager's getDisplaySettingsBuilder() method, or by using the copy()
 * method of an existing DisplaySettings instance.
 */
public interface DisplaySettings {

   interface DisplaySettingsBuilder {
      /**
       * Construct a DisplaySettings from the DisplaySettingsBuilder. Call 
       * this once you are finished setting all DisplaySettings parameters.
       */
      DisplaySettings build();

      // The following functions each set the relevant value for the 
      // DisplaySettings. See the getters of the DisplaySettings, below,
      // for information on the meaning of these fields.
      DisplaySettingsBuilder channelNames(String[] channelNames);
      DisplaySettingsBuilder channelColors(Color[] channelColors);
      DisplaySettingsBuilder channelContrastMins(Integer[] channelContrastMins);
      DisplaySettingsBuilder channelContrastMaxes(Integer[] channelContrastMaxes);
      DisplaySettingsBuilder channelGammas(Double[] channelGammas);
      DisplaySettingsBuilder channelDisplayModeIndex(Integer channelDisplayModeIndex);
      DisplaySettingsBuilder imageCoords(Coords imageCoords);
      DisplaySettingsBuilder histogramUpdateRate(Double histogramUpdateRate);
      DisplaySettingsBuilder shouldSyncChannels(Boolean shouldSyncChannels);
      DisplaySettingsBuilder scaleBarColorIndex(Integer scaleBarColorIndex);
      DisplaySettingsBuilder scaleBarLocationIndex(Integer scaleBarLocationIndex);
      DisplaySettingsBuilder scaleBarOffsetX(Integer scaleBarOffsetX);
      DisplaySettingsBuilder scaleBarOffsetY(Integer scaleBarOffsetY);
      DisplaySettingsBuilder scaleBarIsFilled(Boolean scaleBarIsFilled);
      DisplaySettingsBuilder shouldShowScaleBar(Boolean shouldShowScaleBar);
      DisplaySettingsBuilder shouldAutostretch(Boolean shouldAutostretch);
      DisplaySettingsBuilder trimPercentage(Double trimPercentage);
      DisplaySettingsBuilder shouldUseLogScale(Boolean shouldUseLogScale);
   }

   /**
    * Generate a new DisplaySettingsBuilder whose values are initialized to be
    * the values of this DisplaySettings.
    */
   DisplaySettingsBuilder copy();

   /** The names associated with each channel (e.g. "DAPI" and "GFP") */
   public String[] getChannelNames();
   /** The colors of each channel in the image display window */
   public Color[] getChannelColors();
   /** The value that is treated as "black" in the display window */
   public Integer[] getChannelContrastMins();
   /** The value that is treated as "white" (or equivalent max intensity for
     * colored displays) in the display window */
   public Integer[] getChannelContrastMaxes();
   /** The gamma curve modifier for each channel */
   public Double[] getChannelGammas();
   /** The index into the "Display mode" control; 0 = Color, 1 = Grayscale,
     * 2 = Composite */
   public Integer getChannelDisplayModeIndex();
   /** The coordinates of the currently-displayed image */
   public Coords getImageCoords();
   /** How much time to allow to pass between updates to the histogram, in
     * seconds (set to 0 for continuous update, or any negative value to
     * disable updates altogether) */
   public Double getHistogramUpdateRate();
   /** Whether histogram settings should be synced across channels */
   public Boolean getShouldSyncChannels();
   /** Controls the color of the scale bar overlay as an index into its
     * color dropdown menu */
   public Integer getScaleBarColorIndex();
   /** Controls the position of the scale bar overlay as an index into its
     * position dropdown menu */
   public Integer getScaleBarLocationIndex();
   /** How many pixels away from the left/right edge of the display to draw the
     * scale bar overlay */
   public Integer getScaleBarOffsetX();
   /** How many pixels away from the top/bottom edge of the display to draw the
     * scale bar overlay */
   public Integer getScaleBarOffsetY();
   /** Whether to draw the scale bar overlay as hollow or filled */
   public Boolean getScaleBarIsFilled();
   /** Whether to draw the scale bar overlay at all */
   public Boolean getShouldShowScaleBar();
   /** Whether each newly-displayed image should be autostretched */
   public Boolean getShouldAutostretch();
   /** The percentage of values off the top and bottom of the image's value
     * range that get ignored when autostretching */
   public Double getTrimPercentage();
   /** Whether to display the histograms using a logarithmic scale */
   public Boolean getShouldUseLogScale();

   /**
    * For legacy support only: convert to JSONObject.
    */
   public JSONObject legacyToJSON();
}
