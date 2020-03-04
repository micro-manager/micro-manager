/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.acqj.api.ChannelGroupSettings;
import org.micromanager.acqj.internal.acqengj.MinimalAcquisitionSettings;
import org.micromanager.magellan.internal.gui.GUI;

/**
 *
 * @author henrypinkard
 */
public abstract class MagellanGenericAcquisitionSettings extends MinimalAcquisitionSettings {

   //space
   public volatile double tileOverlap_; //stored as percent * 100, i.e. 55 => 55%
   public volatile boolean channelsAtEverySlice_;
   
   public volatile double zStep_;

   public MagellanGenericAcquisitionSettings(String dir, String name, String cGroup, ChannelGroupSettings channels, 
           double zStep, double tileOverlap, boolean channelsAtEverySlice) {
      super(dir, name, cGroup, channels);
      zStep_ = zStep;
      tileOverlap_ = tileOverlap;
      channelsAtEverySlice_ = channelsAtEverySlice;
   }
   
   public MagellanGenericAcquisitionSettings() {
      
   }

   
   public void setTileOverlapPercent(double overlapPercent) {
      tileOverlap_ = overlapPercent;
      GUI.getInstance().refreshAcqControlsFromSettings();
   }

//   public void setSavingDir(String dirPath) {
//      dir_ = dirPath;
//      GUI.getInstance().refreshAcqControlsFromSettings();
//   }
//   
//   public void setAcquisitionName(String newName) {
//      name_ = newName;
//      GUI.getInstance().refreshAcqControlsFromSettings();
//   }
   
   public void setAcquisitionOrder(String order) {
      if (order.equals("cz")) {
          channelsAtEverySlice_ = false;
      } else if  (order.equals("zc")) {
         channelsAtEverySlice_ = true;
      } else {
         throw new RuntimeException("Unrecognized acquisition order");
      }
   }
}
