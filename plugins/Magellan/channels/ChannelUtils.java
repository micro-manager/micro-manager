/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package channels;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import misc.GlobalSettings;
import misc.Log;
import mmcorej.StrVector;
import org.micromanager.MMStudio;

/**
 *
 * @author Henry
 */
public class ChannelUtils {

   private static final String PREF_EXPOSURE = "EXPOSURE";
   private static final String PREF_COLOR = "COLOR";
   private static final String PREF_USE = "USE";
   private static final Color[] DEFAULT_COLORS = {new Color(160, 32, 240), Color.blue, Color.green, Color.yellow, Color.red, Color.pink};

   private static String[] getChannelNames(String channelGroup) {
      if (channelGroup == null || channelGroup.equals("")) {
         return new String[0];
      }
      StrVector configs = MMStudio.getInstance().getCore().getAvailableConfigs(channelGroup);
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
        int numCameraChannels = (int) MMStudio.getInstance().getCore().getNumberOfCameraChannels();
        ArrayList<ChannelSetting> channels = new ArrayList<ChannelSetting>();
        double exposure = 10;
        try {
            exposure = MMStudio.getInstance().getCore().getExposure();
        } catch (Exception ex) {
            Log.log("Couldnt get camera exposure form core");
        }
        if (numCameraChannels <= 1) {
         for (String name : getChannelNames(channelGroup)) {
//            double exposure = GlobalSettings.getInstance().getDoubleInPrefs(PREF_EXPOSURE + name, 10);
            Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + name,
                    DEFAULT_COLORS[Arrays.asList(getChannelNames(channelGroup)).indexOf(name)].getRGB()));
            boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + name, true);
              channels.add(new ChannelSetting(name, exposure, color, use, true));
          }
      } else {
          for (int i = 0; i < numCameraChannels; i++) {
              String cameraChannelName = MMStudio.getInstance().getCore().getCameraChannelName(i);
              if (getChannelNames(channelGroup).length == 0) {
//                  double exposure = GlobalSettings.getInstance().getDoubleInPrefs(PREF_EXPOSURE + cameraChannelName,10);
                  Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + cameraChannelName,
                          DEFAULT_COLORS[i].getRGB()));
                  boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + cameraChannelName, true);
                  channels.add(new ChannelSetting(cameraChannelName, exposure, color, use, i == 0));
              } else {
                  for (String name : getChannelNames(channelGroup)) {
//                      double exposure = GlobalSettings.getInstance().getDoubleInPrefs(PREF_EXPOSURE + cameraChannelName + "-" + name, 10);
                  Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + cameraChannelName + "-" + name,
                          DEFAULT_COLORS[i].getRGB() ));
                  boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + cameraChannelName + "-" + name,true);
                  channels.add(new ChannelSetting(cameraChannelName + "-" + name, exposure, color, use, i == 0));
               }
            }
         }
      }
      return channels;
   }
   
}
