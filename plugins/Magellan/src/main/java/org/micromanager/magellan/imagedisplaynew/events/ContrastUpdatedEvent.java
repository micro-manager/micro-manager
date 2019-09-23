/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.imagedisplaynew.events;

import java.util.ArrayList;
import org.micromanager.magellan.imagedisplaynew.MagellanChannelDisplaySettings;
import org.micromanager.magellan.imagedisplaynew.MagellanChannelDisplaySettings;

/**
 *
 * @author henrypinkard
 */
 public class ContrastUpdatedEvent {

   public int displayMode = -1;
   public int index = -1;
   public MagellanChannelDisplaySettings channel;
   
   public ContrastUpdatedEvent(int i, MagellanChannelDisplaySettings c) {
      index = i;
      channel = c;
   }

   public ContrastUpdatedEvent(int selectedIndex) {
      displayMode = selectedIndex;
   }
   

}
