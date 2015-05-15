/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package channels;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import main.Magellan;
import misc.GlobalSettings;
import misc.Log;
import mmcorej.StrVector;

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
         return new String[0];
      }
      StrVector configs = Magellan.getCore().getAvailableConfigs(channelGroup);
      String[] names = new String[(int) configs.size()];
      for (int i = 0; i < names.length; i++) {
         names[i] = configs.get(i);
      }
      return names;
   }
   
   public static void storeChannelInfo(ArrayList<ChannelSetting> channels) {
      for (ChannelSetting c : channels) {
         GlobalSettings.getInstance().storeDoubleInPrefs(PREF_EXPOSURE + c.name_, c.exposure_);
         GlobalSettings.getInstance().storeIntInPrefs(PREF_COLOR + c.name_, c.color_.getRGB());
         GlobalSettings.getInstance().storeBooleanInPrefs(PREF_USE + c.name_, c.use_);
      }
   }

   public static ArrayList<ChannelSetting> getAvailableChannels(String channelGroup) {
      int numCamChannels = (int) (GlobalSettings.getInstance().getDemoMode() ? 6 : Magellan.getCore().getNumberOfCameraChannels());
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
                    DEFAULT_COLORS[Arrays.asList(getChannelConfigs(channelGroup)).indexOf(config)].getRGB()));
            boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + config, true);
            channels.add(new ChannelSetting(channelGroup, config, config, exposure, color, use, true));
         }
      } else { //multichannel camera
         for (int i = 0; i < numCamChannels; i++) {
            String cameraChannelName = GlobalSettings.getInstance().getDemoMode() ?
                    new String[]{"Violet","Blue","Green","Yellow","Red","FarRed"}[i]
                    : Magellan.getCore().getCameraChannelName(i);
            if (getChannelConfigs(channelGroup).length == 0) {
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
