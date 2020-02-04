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
public class CanvasResizeEvent {

   public final int w, h;
   public CanvasResizeEvent(int width, int height) {
      w = width;
      h = height;
   }
   
}
