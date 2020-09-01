package org.micromanager.magellan.internal.channels;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.magellan.internal.magellanacq.MagellanGUIAcquisitionSettings;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.GlobalSettings;
import org.micromanager.magellan.internal.misc.Log;

/**
 * Class to encapsulate a bunch of ChannelsSettings. Should be owned by a
 * specific acquisition settings object
 *
 * @author Henry
 */
public class ChannelGroupSettings {

   private static final String PREF_EXPOSURE = "EXPOSURE";
   private static final String PREF_COLOR = "COLOR";
   private static final String PREF_USE = "USE";
   private static final String PREF_OFFSET = "OFFSET";
   private static final Color[] DEFAULT_COLORS = {new Color(160, 32, 240), Color.blue, Color.green, Color.yellow, Color.red, Color.pink};

   protected ArrayList<SingleChannelSetting> channels_;
   private static CMMCore core_;
   protected String group_;

   public ChannelGroupSettings(String channelGroup) {
      group_ = channelGroup;
      core_ = Engine.getCore();
      updateChannelGroup(channelGroup);
   }

   public List<String> getChannelNames() {
      LinkedList<String> names = new LinkedList<String>();
      for (SingleChannelSetting c : channels_) {
         names.add(c.config_);
      }
      return names;
   }

   public SingleChannelSetting getChannelSetting(String name) {
      if (name == null) {
         //no channels, just a placeholder, return the default
         return channels_.get(0);
      }
      for (SingleChannelSetting c : channels_) {
         if (c.config_.equals(name)) {
            return c;
         }
      }
      throw new RuntimeException("channel with name " + name + " not found");
   }

   public void updateChannelGroup(String channelGroup) {
      group_ = channelGroup;
      if (channels_ != null && !channels_.isEmpty() && channels_.get(0).group_.equals(channelGroup)) {
         //nothing to update
         return;
      }
      //The channel group for this object has been 
      int numCamChannels = (int) core_.getNumberOfCameraChannels();
      channels_ = new ArrayList<SingleChannelSetting>();
      if (numCamChannels <= 1) {
         for (String config : getChannelConfigs(channelGroup)) {
            channels_.add(new SingleChannelSetting(channelGroup,
                    channelGroup == null || channelGroup.equals("") ? null : config, 1, 0));
         }
      }
      for (SingleChannelSetting c : channels_) {
         setValuesFromPrefs(c);
      }
   }

   public void setUseOnAll(boolean use) {
      for (SingleChannelSetting c : channels_) {
         c.use_ = use;
      }
   }

   public void synchronizeExposures() {
      double e = channels_.get(0).exposure_;
      for (SingleChannelSetting c : channels_) {
         c.exposure_ = e;
      }
   }

   public int getNumChannels() {
      return channels_.size();
   }

   //for use with tables of channels in the GUI but not in acquisition
   public SingleChannelSetting getChannelListSetting(int i) {
      return channels_.get(i);
   }

   private static String[] getChannelConfigs(String channelGroup) {
      if (channelGroup == null || channelGroup.equals("")) {
         return new String[]{};
      }
      StrVector configs = core_.getAvailableConfigs(channelGroup);
      String[] names = new String[(int) configs.size()];
      for (int i = 0; i < names.length; i++) {
         names[i] = configs.get(i);
      }
      return names;
   }

   public String getConfigName(int index) {
      return channels_.get(index).config_;
   }

   public String getChannelGroup() {
      return channels_.isEmpty() ? null : channels_.get(0).group_;
   }

   public String nextActiveChannel(String channelName) {
      if (channelName == null) {
         for (SingleChannelSetting c : channels_) {
            if (c.use_) {
               return c.config_;
            }
         }
         return null;
      }
      SingleChannelSetting current = getChannelSetting(channelName);
      int currentInd = channels_.indexOf(current);
      for (int i = currentInd + 1; i < channels_.size(); i++) {
         if (channels_.get(i).use_) {
            return channels_.get(i).config_;
         }
      }
      return null;
   }

   public Color getPreferredChannelColor(String channelName) {
      String prefix = MagellanGUIAcquisitionSettings.PREF_PREFIX + "CHANNELGROUP"
              + group_ + "CHANNELNAME" + channelName;
      return new Color(GlobalSettings.getInstance().getIntInPrefs(
              prefix + PREF_COLOR, DEFAULT_COLORS[new Random().nextInt(DEFAULT_COLORS.length)].getRGB()));
   }

   private void setValuesFromPrefs(SingleChannelSetting setting) {
      String prefix = MagellanGUIAcquisitionSettings.PREF_PREFIX + "CHANNELGROUP"
              + setting.group_ + "CHANNELNAME" + setting.config_;
      setting.color_ = new Color(GlobalSettings.getInstance().getIntInPrefs(
              prefix + PREF_COLOR, DEFAULT_COLORS[new Random().nextInt(DEFAULT_COLORS.length)].getRGB()));

      try {
         setting.exposure_ = GlobalSettings.getInstance().getDoubleInPrefs(
                 prefix + PREF_EXPOSURE, Magellan.getCore().getExposure());
      } catch (Exception ex) {
         Log.log(ex);
      }

      setting.offset_ = GlobalSettings.getInstance().getDoubleInPrefs(prefix + PREF_OFFSET, 0.0);
      setting.use_ = GlobalSettings.getInstance().getBooleanInPrefs(prefix + PREF_USE, true);
   }

   public void storeValuesInPrefs() {
      for (SingleChannelSetting setting : channels_) {
         String prefix = MagellanGUIAcquisitionSettings.PREF_PREFIX
                 + "CHANNELGROUP" + setting.group_ + "CHANNELNAME" + setting.config_;
         GlobalSettings.getInstance().storeBooleanInPrefs(prefix + PREF_USE, setting.use_);
         GlobalSettings.getInstance().storeDoubleInPrefs(prefix + PREF_EXPOSURE, setting.exposure_);
         GlobalSettings.getInstance().storeIntInPrefs(prefix + PREF_COLOR,
                 setting.color_.getRGB());
         GlobalSettings.getInstance().storeDoubleInPrefs(prefix + PREF_OFFSET, setting.offset_);
      }
   }

}
