
package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.acqj.internal.Engine;
import org.micromanager.explore.gui.ChannelGroupSettings;
import org.micromanager.magellan.internal.gui.GUI;

/**
 *
 * @author henrypinkard
 */
public abstract class MagellanGenericAcquisitionSettings  {

   public String dir_;
   public String name_;
   public ChannelGroupSettings channels_; 
   //space
   public volatile boolean channelsAtEverySlice_;
   
   public volatile double zStep_;

   public MagellanGenericAcquisitionSettings(String dir, String name, String cGroup,
                                             ChannelGroupSettings channels,
           double zStep, double tileOverlap, boolean channelsAtEverySlice) {
      dir_ = dir;
      name_ = name;
      channels_ = channels;
      zStep_ = zStep;
      channelsAtEverySlice_ = channelsAtEverySlice;
   }
   
   public MagellanGenericAcquisitionSettings() {
      
   }
   
   public void setChannelGroup(String group) {
      channels_.updateChannelGroup(group);
   }
   
   public String getChannelGroup() {
      if (channels_ == null) {
         return null;
      }
      return channels_.getChannelGroup();
   }

   public void setAcquisitionOrder(String order) {
      if (order.equals("cz")) {
         channelsAtEverySlice_ = false;
      } else if  (order.equals("zc")) {
         channelsAtEverySlice_ = true;
      } else {
         throw new RuntimeException("Unrecognized acquisition order");
      }
   }

   public int getXPixelOverlap() {
      return (int) (Engine.getCore().getImageWidth() * GUI.getTileOverlap() / 100);
   }

   public int getYPixelOverlap() {
      return (int) (Engine.getCore().getImageWidth() * GUI.getTileOverlap() / 100);
   }
}
