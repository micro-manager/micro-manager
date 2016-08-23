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
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.DefaultDisplayManager;

/**
 * The ContrastLinker links the contrast settings for a specific channel.
 * We have some rather hacky code here that operates on generic arrays, to
 * limit the amount of code duplication -- since we care about the
 * channelColors (Color), channelContrastMins/Maxes (Integer), and
 * channelGammas(Double), but we don't actually care what the specific values
 * are, just if they've changed.
 */
public final class ContrastLinker extends SettingsLinker {
   // This identifies ourselves with respect to our parent display, but
   // we use the channel names as the basis for linking with other displays,
   // if available.
   private int channelIndex_;
   private static final List<Class<?>> RELEVANT_EVENTS = Arrays.asList(
         new Class<?>[] {ContrastEvent.class});

   public ContrastLinker(int channelIndex, DataViewer parent) {
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
   private int getIndex(DataViewer display, DisplaySettings settings) {
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
   public boolean getShouldApplyChanges(DataViewer source,
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
      DisplaySettings.ContrastSettings oldContrast = oldSettings.getSafeContrastSettings(channelIndex_,
            DefaultDisplayManager.getInstance().getContrastSettings(
               0, 0, 1.0, null));
      DisplaySettings.ContrastSettings newContrast = newSettings.getSafeContrastSettings(newIndex,
            DefaultDisplayManager.getInstance().getContrastSettings(
               0, 0, 1.0, null));
      return (oldContrast != newContrast);
   }

   /**
    * Copy over just the channel we are linked for.
    */
   @Override
   public void applyChange(DataViewer source,
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
   public DisplaySettings copySettings(DataViewer sourceDisplay,
         DisplaySettings source, DisplaySettings dest) {
      int sourceIndex = getIndex(sourceDisplay, source);
      DisplaySettings.ContrastSettings oldSettings = dest.getSafeContrastSettings(
            channelIndex_, DefaultDisplayManager.getInstance().getContrastSettings(
               0, 0, 1.0, null));
      DisplaySettings.ContrastSettings newSettings = source.getSafeContrastSettings(
            sourceIndex, null);
      if (oldSettings == newSettings) {
         // Our channel settings have not changed.
         return dest;
      }
      DisplaySettings.DisplaySettingsBuilder builder = dest.copy();
      builder.safeUpdateContrastSettings(newSettings, channelIndex_);
      builder.shouldAutostretch(source.getShouldAutostretch());
      return builder.build();
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
