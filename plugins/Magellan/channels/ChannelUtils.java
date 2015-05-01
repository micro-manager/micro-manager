/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package channels;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import misc.GlobalSettings;
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
      if (numCameraChannels <= 1) {
         for (String name : getChannelNames(channelGroup)) {
            double exposure = GlobalSettings.getInstance().getDoubleInPrefs(PREF_EXPOSURE + name);
            exposure = exposure == 0 ? 10 : exposure;
            Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + name));
            color = color.equals(Color.black) ? DEFAULT_COLORS[Arrays.asList(getChannelNames(channelGroup)).indexOf(name)] : color;
            boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + name);
            channels.add(new ChannelSetting(name, exposure, color, use, null));
         }
      } else {
         for (int i = 0; i < numCameraChannels; i++) {
            String cameraChannelName = MMStudio.getInstance().getCore().getCameraChannelName(numCameraChannels);
            if (getChannelNames(channelGroup).length == 0) {
                  double exposure = GlobalSettings.getInstance().getDoubleInPrefs(PREF_EXPOSURE + cameraChannelName );
                  exposure = exposure == 0 ? 10 : exposure;
                  Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + cameraChannelName ));
                  boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + cameraChannelName);
                  channels.add(new ChannelSetting(cameraChannelName, exposure, color, use,cameraChannelName));
            } else {
               for (String name : getChannelNames(channelGroup)) {
                  double exposure = GlobalSettings.getInstance().getDoubleInPrefs(PREF_EXPOSURE + cameraChannelName + "-" + name);
                  exposure = exposure == 0 ? 10 : exposure;
                  Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + cameraChannelName + "-" + name));
                  boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + cameraChannelName + "-" + name);
                  channels.add(new ChannelSetting(cameraChannelName + "-" + name, exposure, color, use, cameraChannelName));
               }
            }
         }
      }
      return channels;
   }
   
}
