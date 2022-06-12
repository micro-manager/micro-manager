package org.micromanager.display.overlay;

/**
 * @author mark
 */
public interface OverlayListener {
   /**
    * Called by the overlay when its title has changed.
    *
    * @param overlay the caller
    * @see AbstractOverlay#fireOverlayTitleChanged
    */
   void overlayTitleChanged(Overlay overlay);

   /**
    * Called by the overlay when a repaint is required.
    * <p>
    * This method should be called to notify that the overlay needs a repaint
    * due to its configuration changing.
    *
    * @param overlay the caller
    * @see AbstractOverlay#fireOverlayConfigurationChanged
    */
   void overlayConfigurationChanged(Overlay overlay);

   /**
    * Called by the overlay when it is shown or hidden.
    *
    * @param overlay the caller
    */
   void overlayVisibleChanged(Overlay overlay);
}