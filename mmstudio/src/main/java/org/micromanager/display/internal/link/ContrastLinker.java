///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
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

package org.micromanager.display.internal.link;

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
   // This identifies ourselves with respect to our parent display, but
   // we use the channel names as the basis for linking with other displays,
   // if available.
   private int channelIndex_;
   private static final List<Class<?>> RELEVANT_EVENTS = Arrays.asList(
         new Class<?>[] {ContrastEvent.class});

   public ContrastLinker(int channelIndex, DisplayWindow parent) {
      super(parent, RELEVANT_EVENTS);
      channelIndex_ = channelIndex;
      addToSiblings();
   }

   @Override
   public String getProperty() {
      return "contrast for " + getName();
   }

   // Get our channel name.
   private String getName() {
      String[] names = parent_.getDatastore().getSummaryMetadata().getChannelNames();
      if (names != null && names.length > channelIndex_) {
         return names[channelIndex_];
      }
      return null;
   }

   // Look up our channel name in the specified display's datastore and return
   // the corresponding index, or channelIndex_ if a match was not found.
   private int getIndex(DisplayWindow display, DisplaySettings settings) {
      String[] names = display.getDatastore().getSummaryMetadata().getChannelNames();
      String ourName = getName();
      if (names != null) {
         for (int i = 0; i < names.length; ++i) {
            // Search for string equality and also for both being null.
            if ((names[i] != null &&
                     ourName != null &&
                     names[i].contentEquals(ourName)) ||
                  names[i] == ourName) {
               return i;
            }
         }
      }
      // Can't find it.
      return channelIndex_;
   }

   /**
    * We care about changes if the change is a ContrastEvent and the index
    * we are linked for has changed for one of the channel settings.
    */
   @Override
   public boolean getShouldApplyChanges(DisplayWindow source,
         DisplaySettingsEvent changeEvent) {
      ContrastEvent event = (ContrastEvent) changeEvent;
      String name = event.getChannelName();
      String ourName = getName();
      // This is moderately complicated because one or both of the names could
      // be null.
      if (((name == null) != (ourName == null)) ||
            (name != null && ourName != null && !name.contentEquals(ourName))) {
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

      int newIndex = getIndex(source, newSettings);
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
         Object[] newVals = DefaultDisplaySettings.makePerChannelArray(i, (Object[]) newChannelSettings[i], newIndex + 1);
         if (newVals[newIndex] != oldVals[channelIndex_]) {
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
   public void applyChange(DisplayWindow source,
         DisplaySettingsEvent changeEvent) {
      ContrastEvent event = (ContrastEvent) changeEvent;
      DisplaySettings oldSettings = parent_.getDisplaySettings();
      DisplaySettings newSettings = copySettings(source,
            event.getDisplaySettings(), oldSettings);
      if (newSettings != oldSettings) { // I.e. the copy actually did something
         parent_.setDisplaySettings(newSettings);
      }
   }

   @Override
   public DisplaySettings copySettings(DisplayWindow sourceDisplay,
         DisplaySettings source, DisplaySettings dest) {
      DisplaySettings.DisplaySettingsBuilder builder = dest.copy();

      Object[] destSettings = DefaultDisplaySettings.getPerChannelArrays(dest);
      Object[] sourceSettings = DefaultDisplaySettings.getPerChannelArrays(source);
      boolean shouldChangeSettings = dest.getShouldAutostretch() != source.getShouldAutostretch();
      int sourceIndex = getIndex(sourceDisplay, source);

      for (int i = 0; i < destSettings.length; ++i) {
         Object[] destVals = DefaultDisplaySettings.makePerChannelArray(
               i, (Object[]) (destSettings[i]), channelIndex_ + 1);
         Object[] sourceVals = DefaultDisplaySettings.makePerChannelArray(
               i, (Object[]) (sourceSettings[i]), sourceIndex + 1);
         if (destVals[channelIndex_] != sourceVals[sourceIndex]) {
            // Something changed for our channel, so apply the array.
            shouldChangeSettings = true;
            destVals[channelIndex_] = sourceVals[sourceIndex];
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
      String tmp = "shouldAutostretch_channelColors_channelContrastMins_channelContrastMaxes_channelGammas_";
      String name = getName();
      if (name != null) {
         tmp += name;
      }
      else {
         tmp += Integer.toString(channelIndex_);
      }
      return tmp.hashCode();
   }
}
