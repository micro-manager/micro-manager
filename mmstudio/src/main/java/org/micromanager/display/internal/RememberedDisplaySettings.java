///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2019
//
// COPYRIGHT:    University of California, San Francisco, 2019
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
import java.util.List;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Stores and restores Display and ChannelDisplaysetting that were last
 * used (in the case of channelDisplaySettings for a given channel)
 *
 * <p>Contains code to stay backwards compatible with RememberedChannelSettins
 * mechanism to store settings.  Remove this code after 9/2020
 *
 * @author nico
 */
public class RememberedDisplaySettings {

   /**
    * Stores given ChannelDisplaySettings for given channel in the user profile.
    *
    * @param studio       Object used to get access to profile
    * @param channelGroup Group to which the channel belongs
    * @param channelName  Channel name
    * @param cds          Settings to be stored
    */
   public static void storeChannel(Studio studio,
                                   String channelGroup,
                                   String channelName,
                                   ChannelDisplaySettings cds) {
      if (cds == null || channelName == null || channelGroup == null) {
         return;
      }
      String key = generateChannelKey(channelGroup, channelName);
      MutablePropertyMapView settings =
            studio.profile().getSettings(RememberedDisplaySettings.class);
      if (cds instanceof DefaultChannelDisplaySettings) {
         DefaultChannelDisplaySettings dcds = (DefaultChannelDisplaySettings) cds;
         // for safety, ensure channelgroup and channelname are stored with ChannelDisplaySettings
         if (!dcds.getName().equals(channelName)) {
            dcds = (DefaultChannelDisplaySettings)
                  dcds.copyBuilder().groupName(channelGroup).name(channelName).build();
         }
         PropertyMap pMap = dcds.toPropertyMap();
         settings.putPropertyMap(key, pMap);
      } else {
         studio.logs().logError(
               "Encountered ChannelDisplaySettings that could not be cast to "
                     + "DefaultChannelDisplaySettings");
      }
   }

   /**
    * Loads ChannelDisplaySettings from profile.  For backwards compatibility,
    * will load from RememberChannelSettings if this class' stored settings
    * are not found.  Returns defaults if nothing is found.
    *
    * @param studio       Object used to get access to profile
    * @param channelGroup Group to which the channel belongs
    * @param channelName  Channel name
    * @param defaultColor If nothing was found, use this color as the default color
    *                     will be ignored when null
    * @return Stored or Default ChannelDisplaySetting
    */
   public static ChannelDisplaySettings loadChannel(Studio studio,
                                                    String channelGroup, String channelName,
                                                    Color defaultColor) {
      String key = generateChannelKey(channelGroup, channelName);
      MutablePropertyMapView settings =
            studio.profile().getSettings(RememberedDisplaySettings.class);
      if (settings.containsPropertyMap(key)) {
         return DefaultChannelDisplaySettings.fromPropertyMap(
               settings.getPropertyMap(key, null), channelGroup, channelName);
      }
      ChannelDisplaySettings.Builder cdsBuilder =
            DefaultChannelDisplaySettings.builder().groupName(channelGroup)
                  .name(channelName).component(1);
      if (defaultColor != null) {
         cdsBuilder.color(defaultColor);
      }
      ChannelDisplaySettings cds = cdsBuilder.build();
      storeChannel(studio, channelGroup, channelName, cds);
      return cds;
   }

   /**
    * Given the channels in summary metadata, constructs a DisplaySettings
    * object with the ChannelDisplaySettings appropriate for each channel
    * Note: the "overall" DisplaySettings (i.e. everything unrelated
    * to channels) are not stored (yet).
    *
    * @param studio  The Studio object
    * @param summary Summary metadata for the dataset used
    * @return DisplaySettings apprpiate for each channel in the summary metadaya
    */
   public static DisplaySettings loadDefaultDisplaySettings(Studio studio,
                                                            SummaryMetadata summary) {
      DisplaySettings.Builder builder = DefaultDisplaySettings.builder();
      String channelGroup = summary.getChannelGroup();
      List<String> channelNames = summary.getChannelNameList();
      for (int ch = 0; ch < channelNames.size(); ch++) {
         String channelName = summary.getSafeChannelName(ch);
         ChannelDisplaySettings cds = loadChannel(studio, channelGroup, channelName, null);
         builder.channel(ch, cds);
      }
      if (channelNames.size() > 1) {
         builder.colorModeComposite();
      }
      builder.autostretch(true);
      return builder.build();
   }

   /**
    * Some MM versions have stored DisplaySettings without storing ChannelNames
    * This causes the channel names not to be shown in the Intensity Inspector
    * Panels.  Fix this here.
    *
    * @param displaySettings Settings as read from disk
    * @param summary         Metadata of the datastore, will be used to fill in missing info
    * @return Copy of the input with Channelgroup and Channelnames from the summary
    *     metadata if they were empty in the input.
    */
   public static DisplaySettings fixMissingInfo(
         DisplaySettings displaySettings, SummaryMetadata summary) {
      DisplaySettings.Builder builder = displaySettings.copyBuilder();
      String channelGroup = summary.getChannelGroup();
      List<String> channelNames = summary.getChannelNameList();
      for (int ch = 0; ch < channelNames.size(); ch++) {
         String channelName = summary.getSafeChannelName(ch);
         ChannelDisplaySettings cdSettings =
               displaySettings.getChannelSettings(ch);  // check for null?
         ChannelDisplaySettings.Builder cddBuilder = cdSettings.copyBuilder();
         if (cdSettings.getGroupName().isEmpty()) {
            cddBuilder.groupName(channelGroup);
         }
         if (cdSettings.getName().isEmpty()) {
            cddBuilder.name(channelName);
         }
         builder.channel(ch, cddBuilder.build());
      }
      return builder.build();
   }


   private static String generateChannelKey(String channelGroup, String channelName) {
      return channelGroup + ":" + channelName;
   }

   private static String generateChannelKey(String key, String channelGroup, String channelName) {
      if (key == null || key.isEmpty()) {
         return generateChannelKey(channelGroup, channelName);
      }
      return key + ":" + channelGroup + ":" + channelName;
   }


}
