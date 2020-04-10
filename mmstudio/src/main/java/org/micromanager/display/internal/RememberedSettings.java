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
 * Contains code to stay backwards compatible with RememberedChannelSettins
 * mechanism to store settings.  Remove this code after 9/2020
 * 
 * @author nico
 */
public class RememberedSettings {
   private static final String COLOR = "color to use for channels";
   
   /**
    * Stores given ChannelDisplaySettings for given channel in the user profile
    * 
    * @param studio        Object used to get access to profile
    * @param channelGroup  Group to which the channel belongs
    * @param channelName   Channel name
    * @param cds           Settings to be stored
    */
   public static void storeChannel(Studio studio,
           String channelGroup,
           String channelName,
           ChannelDisplaySettings cds) {
      String key = genKey(channelGroup, channelName);
      MutablePropertyMapView settings = 
              studio.profile().getSettings(RememberedSettings.class);
      if (cds instanceof DefaultChannelDisplaySettings) {        
         DefaultChannelDisplaySettings dcds = (DefaultChannelDisplaySettings) cds;
         // for safety, ensure channelgroup and channelname are stored with ChannelDisplaySettings
         if (!dcds.getName().equals(channelName)) {
            dcds = (DefaultChannelDisplaySettings) 
                    dcds.copyBuilder().groupName(channelGroup).name(channelName).build();
         }
         PropertyMap pMap = dcds.toPropertyMap();
         settings.putPropertyMap(key, pMap);
      }
      else {
         studio.logs().logError(
                 "Encountered ChannelDisplaySettings that could not be cast to DefaultChannelDisplaySettings");
      }
   }
   
   /**
    * Loads ChannelDisplaySettings from profile.  For backwards compatibility,
    * will load from RememberChannelSettings if this class' stored settings
    * are not found.  Returns defaults if nothing is found.
    * 
    * @param studio        Object used to get access to profile
    * @param channelGroup  Group to which the channel belongs
    * @param channelName   Channel name
    * @return Stored or Default ChannelDisplaySetting
    */
   public static ChannelDisplaySettings loadChannel(Studio studio, 
           String channelGroup, 
           String channelName) {
      String key = genKey(channelGroup, channelName);
      MutablePropertyMapView settings = 
              studio.profile().getSettings(RememberedSettings.class);
      if (settings.containsPropertyMap(key)) {
          return DefaultChannelDisplaySettings.fromPropertyMap(
                  settings.getPropertyMap(key, null), channelGroup, channelName);
      } else {
         // for backward compatibility
         String rKey = RememberedChannelSettings.genKey(channelGroup, channelName);
         settings = studio.profile().getSettings(RememberedChannelSettings.class);
         if (settings.containsInteger(rKey + ":" + COLOR)) {
            RememberedChannelSettings rcs = RememberedChannelSettings.loadSettings(
                    channelGroup, 
                    channelName, 
                    Color.WHITE, 
                    null, 
                    null, 
                    true);
            return rcs.toChannelDisplaySetting(channelGroup, channelName);
         }
      }
      
      return DefaultChannelDisplaySettings.builder().name(channelName).component(1).build();
   }
   
   /**
    * Given the channels in summary metadata, constructs a DisplaySettings
    * object with the ChannelDisplaySettings appropriate for each channel
    * Note: the "overall" DisplaySettings (i.e. everything unrelated 
    * to channels) are not stored (yet).
    * @param studio
    * @param summary
    * @return 
    */
   public static DisplaySettings loadDefaultDisplaySettings(Studio studio, 
           SummaryMetadata summary) {
      DisplaySettings.Builder builder = DefaultDisplaySettings.builder();
      String channelGroup = summary.getChannelGroup();
      List<String> channelNames = summary.getChannelNameList();
      for (int ch = 0; ch < channelNames.size(); ch++) {
         String channelName = summary.getSafeChannelName(ch);
         ChannelDisplaySettings cds = loadChannel(studio, channelGroup, channelName);
         builder.channel(ch, cds);
      }
      if (channelNames.size() > 1) {
         builder.colorModeComposite();
      }
      builder.autostretch(true);
      return builder.build();      
   }

   
   
   private static String genKey(String channelGroup, String channelName) {
      return channelGroup + ":" + channelName;
   }
}
