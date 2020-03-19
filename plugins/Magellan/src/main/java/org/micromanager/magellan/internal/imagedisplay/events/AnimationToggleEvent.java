/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay.events;

import org.micromanager.magellan.internal.imagedisplay.AxisScroller;
import org.micromanager.magellan.internal.imagedisplay.MagellanDisplayController;

    public class AnimationToggleEvent {

   /**
    * This class is used to communicate with our master when the animation
    * button is clicked.
    */
   private boolean isAnimated_;
   public AxisScroller scroller_;

   
   public AnimationToggleEvent(AxisScroller scroller, boolean isAnimated) {
      scroller_ = scroller;
      isAnimated_ = isAnimated;
   }

   public boolean getIsAnimated() {
      return isAnimated_;
   }
}
