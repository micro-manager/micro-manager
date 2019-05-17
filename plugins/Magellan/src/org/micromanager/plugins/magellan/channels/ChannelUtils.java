///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//

package org.micromanager.plugins.magellan.channels;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.GlobalSettings;
import org.micromanager.plugins.magellan.misc.Log;
import mmcorej.StrVector;
import org.micromanager.plugins.magellan.acq.FixedAreaAcquisition;
import org.micromanager.plugins.magellan.acq.FixedAreaAcquisitionSettings;
import org.micromanager.plugins.magellan.demo.DemoModeImageData;

/**
 *
 * @author Henry
 */
public class ChannelUtils {

   private static final String PREF_EXPOSURE = "EXPOSURE";
   private static final String PREF_COLOR = "COLOR";
   private static final String PREF_USE = "USE";
   private static final Color[] DEFAULT_COLORS = {new Color(160, 32, 240), Color.blue, Color.green, Color.yellow, Color.red, Color.pink};

   private static String[] getChannelConfigs(String channelGroup) {
      if (channelGroup == null || channelGroup.equals("")) {
         return new String[]{"Default"};
      }
      StrVector configs = Magellan.getCore().getAvailableConfigs(channelGroup);
      String[] names = new String[(int) configs.size()];
      for (int i = 0; i < names.length; i++) {
         names[i] = configs.get(i);
      }
      return names;
   }

    public static void storeChannelInfo(ArrayList<ChannelSetting> channels) {
        //store individual channel setttings
        for (int i = 0; i < channels.size(); i++) {
            Magellan.getPrefs().putBoolean(FixedAreaAcquisitionSettings.PREF_PREFIX + "CHANNELGROUP" + channels.get(0).group_ + "CHANNELNAME" + channels.get(i).name_ + "USE", channels.get(i).use_);
            Magellan.getPrefs().putDouble(FixedAreaAcquisitionSettings.PREF_PREFIX + "CHANNELGROUP" + channels.get(0).group_ + "CHANNELNAME" + channels.get(i).name_ + "EXPOSURE", channels.get(i).exposure_);
            Magellan.getPrefs().putInt(FixedAreaAcquisitionSettings.PREF_PREFIX + "CHANNELGROUP" + channels.get(0).group_ + "CHANNELNAME" + channels.get(i).name_ + "COLOR", channels.get(i).color_.getRGB());
        }
    }

    public static ArrayList<ChannelSetting> getAvailableChannels(String channelGroup) {
      int numCamChannels = (int) (GlobalSettings.getInstance().getDemoMode() ? DemoModeImageData.getNumChannels() : Magellan.getCore().getNumberOfCameraChannels());
      ArrayList<ChannelSetting> channels = new ArrayList<ChannelSetting>();
      double exposure = 10;
      try {
         exposure = Magellan.getCore().getExposure();
      } catch (Exception ex) {
         Log.log("Couldnt get camera exposure form core", true);
      }
      if (numCamChannels <= 1) {
         for (String config : getChannelConfigs(channelGroup)) {
            Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + config,
                    DEFAULT_COLORS[Arrays.asList(getChannelConfigs(channelGroup)).indexOf(config) % DEFAULT_COLORS.length].getRGB()));
            boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + config, true);
            channels.add(new ChannelSetting(channelGroup, 
                    channelGroup == null || channelGroup.equals("") ? null : config, config, exposure, color, use, true));
         }
      } else { //multichannel camera
         for (int i = 0; i < numCamChannels; i++) {
            String cameraChannelName = GlobalSettings.getInstance().getDemoMode() ?
                    new String[]{"Violet","Blue","Green","Yellow","Red","FarRed"}[i]
                    : Magellan.getCore().getCameraChannelName(i);
            if (getChannelConfigs(channelGroup).length == 0 || channelGroup == null || channelGroup.isEmpty() ) {
               Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + cameraChannelName,
                       DEFAULT_COLORS[i].getRGB()));
               boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + cameraChannelName, true);
               channels.add(new ChannelSetting(channelGroup, null, cameraChannelName, exposure, color, use, i == 0));
            } else {
               for (String config : getChannelConfigs(channelGroup)) {
                  Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + cameraChannelName + "-" + config,
                          DEFAULT_COLORS[i].getRGB()));
                  boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + cameraChannelName + "-" + config, true);
                  channels.add(new ChannelSetting(channelGroup, config, cameraChannelName + "-" + config, exposure, color, use, i == 0));
               }
            }
         }
      }
      return channels;
   }
}
