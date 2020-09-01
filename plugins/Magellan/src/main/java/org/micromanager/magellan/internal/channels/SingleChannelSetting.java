/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.channels;

import java.awt.Color;
import org.micromanager.acqj.api.channels.ChannelSetting;

/**
 * Convenience class that encapsulates a single channel setting
 * 
 * @author henrypinkard
 */
public class SingleChannelSetting extends ChannelSetting {
   
   public boolean use_ = true;
   public Color color_;
   
   public SingleChannelSetting(String group, String config, 
           double exposure, double offset) {
      super(group, config, exposure, offset);
      color_ = Color.white;
   }
      
}