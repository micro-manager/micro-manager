package org.micromanager.display.internal.link;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

import org.micromanager.display.internal.DefaultDisplaySettings;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * The ContrastLinker links the contrast settings for a specific channel.
 * We have some rather hacky code here that operates on generic arrays, to
 * limit the amount of code duplication -- since we care about the
 * channelColors (Color), channelContrastMins/Maxes (Integer), and
 * channelGammas(Double), but we don't actually care what the specific values
 * are, just if they've changed.
 */
public class ContrastLinker extends SettingsLinker {
   private int channelIndex_;
   private static final List<Class<?>> RELEVANT_EVENTS = Arrays.asList(
         new Class<?>[] {ContrastEvent.class});

   public ContrastLinker(int channelIndex, DisplayWindow parent) {
      super(parent, RELEVANT_EVENTS);
      channelIndex_ = channelIndex;
   }

   /**
    * We care about changes if the change is a ContrastEvent and the index
    * we are linked for has changed for one of the channel settings.
    */
   @Override
   public boolean getShouldApplyChanges(DisplaySettingsEvent changeEvent) {
      ContrastEvent event = (ContrastEvent) changeEvent;
      int index = event.getIndex();
      if (channelIndex_ != index) {
         // Change is for the wrong channel, so we don't care.
         return false;
      }

      DisplaySettings oldSettings = parent_.getDisplaySettings();
      DisplaySettings newSettings = event.getDisplaySettings();

      if (oldSettings.getShouldAutostretch() !=
            newSettings.getShouldAutostretch()) {
         // This counts as an important change.
         return true;
      }

      // Scan each of the arrays we care about and see if our value in those
      // arrays has changed.
      Object[] oldChannelSettings = DefaultDisplaySettings.getPerChannelArrays(oldSettings);
      Object[] newChannelSettings = DefaultDisplaySettings.getPerChannelArrays(newSettings);
      for (int i = 0; i < oldChannelSettings.length; ++i) {
         if (newChannelSettings[i] == oldChannelSettings[i]) {
            // These two arrays are the same reference, so they can't
            // possibly be different.
            continue;
         }
         Object[] oldVals = DefaultDisplaySettings.makePerChannelArray(i, (Object[]) oldChannelSettings[i], channelIndex_ + 1);
         Object[] newVals = DefaultDisplaySettings.makePerChannelArray(i, (Object[]) newChannelSettings[i], channelIndex_ + 1);
         if (newVals[channelIndex_] != oldVals[channelIndex_]) {
            // Found an array where, for the index we care about,
            // we are different.
            return true;
         }
      }
      return false;
   }

   /**
    * Copy over just the channel we are linked for.
    */
   @Override
   public void applyChange(DisplaySettingsEvent changeEvent) {
      ContrastEvent event = (ContrastEvent) changeEvent;
      DisplaySettings oldSettings = parent_.getDisplaySettings();
      DisplaySettings newSettings = event.getDisplaySettings();
      newSettings = copySettings(newSettings, oldSettings);
      if (newSettings != oldSettings) { // I.e. the copy actually did something
         parent_.setDisplaySettings(newSettings);
      }
   }

   @Override
   public DisplaySettings copySettings(DisplaySettings source,
         DisplaySettings dest) {
      DisplaySettings.DisplaySettingsBuilder builder = dest.copy();

      Object[] destSettings = DefaultDisplaySettings.getPerChannelArrays(dest);
      Object[] sourceSettings = DefaultDisplaySettings.getPerChannelArrays(source);
      boolean shouldChangeSettings = dest.getShouldAutostretch() != source.getShouldAutostretch();

      for (int i = 0; i < destSettings.length; ++i) {
         Object[] destVals = DefaultDisplaySettings.makePerChannelArray(
               i, (Object[]) (destSettings[i]), channelIndex_ + 1);
         Object[] sourceVals = DefaultDisplaySettings.makePerChannelArray(
               i, (Object[]) (sourceSettings[i]), channelIndex_ + 1);
         if (destVals[channelIndex_] != sourceVals[channelIndex_]) {
            // Something changed for our channel, so apply the array.
            shouldChangeSettings = true;
            destVals[channelIndex_] = sourceVals[channelIndex_];
            DefaultDisplaySettings.updateChannelArray(i, destVals, builder);
         }
      }
      if (shouldChangeSettings) {
         builder.shouldAutostretch(source.getShouldAutostretch());
         return builder.build();
      }
      else {
         // No change; don't generate a new DisplaySettings.
         return dest;
      }
   }

   /**
    * We care about any of the histogram settings, for a specific channel.
    * This is fairly closely-tied to
    * DefaultDisplaySettings.getPerChannelArrays().
    */
   @Override
   public int getID() {
      return ("shouldAutostretch_channelColors_channelContrastMins_channelContrastMaxes_channelGammas_" + channelIndex_).hashCode();
   }
}
