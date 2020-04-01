package org.micromanager.magellan.internal.channels;

import org.micromanager.acqj.api.ChannelSetting;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.acqj.api.ChannelSetting;
import org.micromanager.acqj.internal.acqengj.Engine;

/**
 * Class to encapsulate a bunch of ChannelsSettings. Should be owned by a
 * specific acquisition settings object
 *
 * @author Henry
 */
public class ChannelGroupSettings {

   protected ArrayList<ChannelSetting> channels_;
   private static CMMCore core_;
   protected String group_;

   public ChannelGroupSettings(String channelGroup) {
      group_ = channelGroup;
      core_ = Engine.getCore();
      updateChannelGroup(channelGroup);
   }

   public List<String> getChannelNames() {
      LinkedList<String> names = new LinkedList<String>();
      for (ChannelSetting c : channels_) {
         names.add(c.name);
      }
      return names;
   }

   public ChannelSetting getChannelSetting(String name) {
      if (name == null) {
         //no channels, just a placeholder, return the default
         return channels_.get(0);
      }
      for (ChannelSetting c : channels_) {
         if (c.name.equals(name)) {
            return c;
         }
      }
      throw new RuntimeException("channel with name " + name + " not found");
   }

   public void updateChannelGroup(String channelGroup) {
      group_ = channelGroup;
      if (channels_ != null && !channels_.isEmpty() && channels_.get(0).group.equals(channelGroup)) {
         //nothing to update
         return;
      }
      //The channel group for this object has been 
      int numCamChannels = (int) core_.getNumberOfCameraChannels();
      channels_ = new ArrayList<ChannelSetting>();
      if (numCamChannels <= 1) {
         for (String config : getChannelConfigs(channelGroup)) {
            channels_.add(new ChannelSetting(channelGroup, channelGroup == null || channelGroup.equals("") ? null : config, config));
         }
      } 
   }

   public void setUseOnAll(boolean use) {
      for (ChannelSetting c : channels_) {
         c.use = use;
      }
   }

   public void synchronizeExposures() {
      double e = channels_.get(0).exposure;
      for (ChannelSetting c : channels_) {
         c.exposure = e;
      }
   }

   public int getNumChannels() {
      return channels_.size();
   }

   //for use with tables of channels in the GUI but not in acquisition
   public ChannelSetting getChannelListSetting(int i) {
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
      return channels_.get(index).config;
   }
   
   public String getChannelGroup() {
      return channels_.isEmpty() ? null : channels_.get(0).group;
   }

   public String nextActiveChannel(String channelName) {
      if (channelName == null) {
         for (ChannelSetting c : channels_) {
            if (c.use) {
               return c.name;
            }
         }
         return null;
      }
      ChannelSetting current = getChannelSetting(channelName);
      int currentInd = channels_.indexOf(current);
      for (int i = currentInd + 1; i < channels_.size(); i++) {
         if (channels_.get(i).use) {
            return channels_.get(i).name;
         }
      }
      return null;
   }

}
