/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay.events;

/**
 *
 * @author henrypinkard
 */
public class ExploreZLimitsChangedEvent {
   
   public final double top_, bottom_;
   
   public ExploreZLimitsChangedEvent(double top, double bottom) {
      top_ = top;
      bottom_ = bottom;
   }
   
}
