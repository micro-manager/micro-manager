/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay.events;

import org.micromanager.magellan.internal.imagedisplay.AxisScroller;
import org.micromanager.magellan.internal.imagedisplay.AxisScroller;

/**
 *
 * @author henrypinkard
 */
   /**
    * This class is used to communicate with our master when the scrollbar is
    * moved to a different position.
    */
    public class ScrollPositionEvent {
      private int value_;
      public AxisScroller scroller_;
      
      public ScrollPositionEvent(AxisScroller scroller, int value) {
         scroller_ = scroller;
         value_ = value;
      }

      public int getValue() {
         return value_;
      }
   }
