package org.micromanager.magellan.internal.channels;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.micromanager.magellan.internal.main.Magellan;
import mmcorej.StrVector;

/**
 * Class to encapsulate a bunch of ChannelsSettings. Should be owned by a specific acquisition settings object 
 * @author Henry
 */
public class MagellanChannelSpec {
    
    private ArrayList<ChannelSetting> channels_;
    
    public List<String> getChannelNames() {
       LinkedList<String> names = new LinkedList<String>();
       for (ChannelSetting c : channels_) {
          names.add(c.name_);
       }
       return names;
    }
    
    public MagellanChannelSpec(String channelGroup) {
        updateChannelGroup(channelGroup);
    }
    
    public ChannelSetting getChannelSetting(String name) {
       if (name == null) {
          //no channels, just a placeholder, return the default
          return channels_.get(0);
       }
       for (ChannelSetting c : channels_) {
          if (c.name_.equals(name)) {
             return c;
          }
       }
       throw new RuntimeException("channel with name " + name + " not found");
    }
    
    public void updateChannelGroup(String channelGroup) {
        if (channels_ != null && !channels_.isEmpty() && channels_.get(0).group_.equals(channelGroup) ) {
            //nothing to update
            return;
        } 
        //The channel group for this object has been 
        int numCamChannels = (int) Magellan.getCore().getNumberOfCameraChannels();
        channels_ = new ArrayList<ChannelSetting>();
        if (numCamChannels <= 1) {
            for (String config : getChannelConfigs(channelGroup)) {
                channels_.add(new ChannelSetting(channelGroup, channelGroup == null || channelGroup.equals("") ? null : config, config, true));
            }
        } else { //multichannel camera
            for (int i = 0; i < numCamChannels; i++) {
                String cameraChannelName = Magellan.getCore().getCameraChannelName(i);
                if (getChannelConfigs(channelGroup).length == 0 || channelGroup == null || channelGroup.isEmpty()) {
                    channels_.add(new ChannelSetting(channelGroup, null, cameraChannelName, i == 0));
                } else {
                    for (String config : getChannelConfigs(channelGroup)) {
                        channels_.add(new ChannelSetting(channelGroup, config, cameraChannelName + "-" + config, i == 0));
                    }
                }
            }
        }
    }
    
    public void setUseOnAll(boolean use) {
        for (ChannelSetting c : channels_) {
           c.use_ = use;
        }
    }

    public void synchronizeExposures() {
        double e = channels_.get(0).exposure_;
        for (ChannelSetting c : channels_) {
            c.exposure_ = e;
        }
    }

    public int getNumChannels() {
        return channels_.size();
    }
    
    //for use with tables of channels in the GUI but not in acquisition
   public ChannelSetting getChannelListSetting(int i) {
      return channels_.get(i);
   }
    
    public void storeCurrentSettingsInPrefs() {        
      for (ChannelSetting c : channels_) {
          c.storeChannelInfoInPrefs();
      }
    }
    
    private static String[] getChannelConfigs(String channelGroup) {
      if (channelGroup == null || channelGroup.equals("")) {
         return new String[]{};
      }
      StrVector configs = Magellan.getCore().getAvailableConfigs(channelGroup);
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
      return channels_.get(0).group_;
   }

   public String nextActiveChannel(String channelName) {
      if (channelName == null) {
         for (ChannelSetting c : channels_) {
            if (c.use_ && c.uniqueEvent_) {
               return c.name_;
            }
         }
         return null;
      }
      ChannelSetting current = getChannelSetting(channelName);
      int currentInd = channels_.indexOf(current);
      for (int i = currentInd + 1; i < channels_.size(); i++) {
         if (channels_.get(i).use_ && channels_.get(i).uniqueEvent_) {
            return channels_.get(i).name_;
         }
      }
      return null;
   }


}
