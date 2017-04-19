/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.overlay;

/**
 *
 * @author mark
 */
public interface OverlayListener {
   /**
    * Called by the overlay when a repaint is required.
    * <p>
    * This method should be called to notify that the overlay needs a repaint
    * due to its configuration changing.
    *
    * @param overlay the caller
    * @see AbstractOverlay#fireOverlayNeedsRepaint
    */
   void overlayNeedsRepaint(Overlay overlay);
}