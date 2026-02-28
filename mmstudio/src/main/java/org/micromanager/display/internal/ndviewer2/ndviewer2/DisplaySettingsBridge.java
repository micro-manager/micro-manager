package org.micromanager.display.internal.ndviewer2.ndviewer2;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.DefaultComponentDisplaySettings;
import org.micromanager.ndviewer.main.NDViewer;

/**
 * Bidirectional translation between MM DisplaySettings and NDViewer's
 * contrast/display settings.
 *
 * <p>Uses the fully-qualified name for NDViewer's DisplaySettings to avoid
 * collision with MM's DisplaySettings interface.</p>
 */
public final class DisplaySettingsBridge {

   private final AxesBridge axesBridge_;

   public DisplaySettingsBridge(AxesBridge axesBridge) {
      axesBridge_ = axesBridge;
   }

   /**
    * Apply MM DisplaySettings to NDViewer's display settings model.
    *
    * @param mmSettings the MM display settings to apply
    * @param ndSettings the NDViewer display settings to modify
    */
   public void applyToNDViewer(
         DisplaySettings mmSettings,
         org.micromanager.ndviewer.internal.gui.contrast.DisplaySettings ndSettings) {
      List<String> channelNames = getNDViewerChannelNames(ndSettings);
      for (int i = 0; i < mmSettings.getNumberOfChannels()
            && i < channelNames.size(); i++) {
         String chName = channelNames.get(i);
         if (!ndSettings.containsChannel(chName)) {
            continue;
         }
         ChannelDisplaySettings chSettings = mmSettings.getChannelSettings(i);
         ComponentDisplaySettings comp = chSettings.getComponentSettings(0);

         ndSettings.setContrastMin(chName, (int) comp.getScalingMinimum());
         ndSettings.setContrastMax(chName, (int) comp.getScalingMaximum());
         ndSettings.setGamma(chName, comp.getScalingGamma());
         ndSettings.setColor(chName, chSettings.getColor());
         ndSettings.setActive(chName, chSettings.isVisible());
      }

      ndSettings.setCompositeMode(
            mmSettings.getColorMode() == DisplaySettings.ColorMode.COMPOSITE);
      ndSettings.setAutoscale(mmSettings.isAutostretchEnabled());
      ndSettings.setLogHist(mmSettings.isHistogramLogarithmic());

      double percentile = mmSettings.getAutoscaleIgnoredPercentile();
      ndSettings.setIgnoreOutliers(percentile > 0);
      ndSettings.setIgnoreOutliersPercentage(percentile);
   }

   /**
    * Read NDViewer's current display settings and build equivalent
    * MM DisplaySettings.
    *
    * @param ndSettings the NDViewer display settings to read from
    * @param existing   existing MM display settings to use as a template
    *                   (preserves non-contrast fields)
    * @return new MM DisplaySettings reflecting NDViewer state
    */
   public DisplaySettings readFromNDViewer(
         org.micromanager.ndviewer.internal.gui.contrast.DisplaySettings ndSettings,
         DisplaySettings existing) {
      List<String> channelNames = getNDViewerChannelNames(ndSettings);
      DisplaySettings.Builder dsBuilder = existing.copyBuilder();

      for (int i = 0; i < channelNames.size(); i++) {
         String chName = channelNames.get(i);
         if (!ndSettings.containsChannel(chName)) {
            continue;
         }

         int contrastMin = ndSettings.getContrastMin(chName);
         int contrastMax = ndSettings.getContrastMax(chName);
         double gamma = ndSettings.getContrastGamma(chName);
         Color color = ndSettings.getColor(chName);
         boolean active = ndSettings.isActive(chName);

         ComponentDisplaySettings comp =
               DefaultComponentDisplaySettings.builder()
                     .scalingMinimum(contrastMin)
                     .scalingMaximum(contrastMax)
                     .scalingGamma(gamma)
                     .build();

         // Start from the existing channel settings to preserve metadata
         // (name, groupName, histoRangeBits, etc.) that NDViewer doesn't track.
         ChannelDisplaySettings chSettings = existing.getChannelSettings(i)
               .copyBuilder()
               .name(chName)
               .color(color)
               .visible(active)
               .component(0, comp)
               .build();

         dsBuilder.channel(i, chSettings);
      }

      // NDViewer only knows composite vs. non-composite. When non-composite,
      // preserve the existing MM color mode (GRAYSCALE, COLOR, HIGHLIGHT_LIMITS)
      // to avoid oscillation between modes that NDViewer can't distinguish.
      if (ndSettings.isCompositeMode()) {
         dsBuilder.colorMode(DisplaySettings.ColorMode.COMPOSITE);
      } else if (existing.getColorMode() == DisplaySettings.ColorMode.COMPOSITE) {
         // Was composite, NDViewer says not composite → switch to COLOR
         dsBuilder.colorMode(DisplaySettings.ColorMode.COLOR);
      }
      // else: keep existing non-composite mode (GRAYSCALE, COLOR, etc.)

      dsBuilder.autostretch(ndSettings.getAutoscale());
      dsBuilder.histogramLogarithmic(ndSettings.isLogHistogram());

      if (ndSettings.ignoreFractionOn()) {
         dsBuilder.autoscaleIgnoredPercentile(ndSettings.percentToIgnore());
      } else {
         dsBuilder.autoscaleIgnoredPercentile(0.0);
      }

      return dsBuilder.build();
   }

   /**
    * Get the channel names to use for bridging.
    *
    * <p>When there are named channels in the AxesBridge, use those.
    * Otherwise, fall back to NDViewer's internal channel names. NDViewer
    * uses {@code "NO_CHANNEL_PRESENT"} as a synthetic channel name when
    * there is no explicit channel axis (e.g. single-channel tiled data).</p>
    *
    * @param ndSettings the NDViewer display settings
    * @return ordered list of channel names known to NDViewer
    */
   private List<String> getNDViewerChannelNames(
         org.micromanager.ndviewer.internal.gui.contrast.DisplaySettings ndSettings) {
      List<String> names = axesBridge_.getChannelNames();
      if (!names.isEmpty()) {
         return names;
      }
      // No explicit channels — check if NDViewer has its synthetic channel
      List<String> fallback = new ArrayList<>();
      if (ndSettings.containsChannel(NDViewer.NO_CHANNEL)) {
         fallback.add(NDViewer.NO_CHANNEL);
      }
      return fallback;
   }
}
