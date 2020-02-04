/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay.events;

import java.util.ArrayList;

/**
 *
 * @author henrypinkard
 */
 public class ContrastUpdatedEvent {

   public int displayMode = -1;
   public int channelIndex_ = -1;
   
   public ContrastUpdatedEvent(int i) {
      channelIndex_ = i;
   }
}
