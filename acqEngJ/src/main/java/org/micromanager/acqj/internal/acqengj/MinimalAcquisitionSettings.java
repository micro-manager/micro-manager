package org.micromanager.acqj.internal.acqengj;

import org.micromanager.acqj.api.ChannelGroupSettings;

/**
 * The minimal acquisition settings which all types of acquisition settings inherit from.
 * Not all channels need to be active, and new channels can be added at runtime, 
 * 
 * @author henrypinkard
 */
public abstract class MinimalAcquisitionSettings {

   //saving
   public volatile String dir_, name_;

   //channels
   public volatile ChannelGroupSettings channels_;
   public volatile String channelGroup_;

   public MinimalAcquisitionSettings(String dir, String name, 
           String cGroup, ChannelGroupSettings channels) {
      channelGroup_ = cGroup;
      dir_ = dir;
      name_ = name;
      channels_ = channels;
   }
   
   /**
    * Constructor for when intializing is handled manually
    */
   public MinimalAcquisitionSettings() {}

   
}
