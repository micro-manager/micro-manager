package org.micromanager.display.overlay;

import java.util.List;

/**
 * Interface for data viewers that support MM Inspector overlays without
 * implementing the full {@link org.micromanager.display.DisplayWindow}.
 *
 * <p>Implement this interface to allow the Overlays Inspector panel to attach
 * to a custom viewer (e.g. NDViewer2DataViewer).
 */
public interface OverlaySupport {

   /**
    * Add an overlay to this viewer.
    *
    * @param overlay the overlay to add
    */
   void addOverlay(Overlay overlay);

   /**
    * Remove an overlay from this viewer.
    *
    * @param overlay the overlay to remove
    */
   void removeOverlay(Overlay overlay);

   /**
    * Return the list of overlays currently attached to this viewer.
    *
    * @return unmodifiable list of overlays
    */
   List<Overlay> getOverlays();
}
