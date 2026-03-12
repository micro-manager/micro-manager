package org.micromanager.tileddataviewer;

import java.util.HashMap;
import java.util.List;
import org.micromanager.data.Image;
import org.micromanager.display.DataViewer;
import org.micromanager.display.overlay.Overlay;

/**
 * Public interface for the NDViewer2 data viewer.
 *
 * <p>Extends {@link DataViewer} (which already provides {@code setDisplaySettings}).
 * Use {@link TiledDataViewerFactory#createDataViewer} to obtain an instance.</p>
 */
public interface TiledDataViewerDataViewerAPI extends DataViewer {

   /**
    * Enable or disable accumulation of histogram stats across tiles.
    * When enabled, newImageArrived() stats are merged into a running total.
    *
    * @param enabled true to enable accumulation, false to disable
    */
   void setAccumulateStats(boolean enabled);

   /**
    * Get the underlying NDViewer instance for direct NDViewer API access.
    *
    * @return the NDViewer2API instance
    */
   TiledDataViewerAPI getNDViewer();

   /**
    * Close the viewer and release all resources.
    */
   void close();

   /**
    * Shut down MM2-specific resources without touching NDViewer.
    * Use this when NDViewer itself initiated the close (e.g. user clicked X)
    * to avoid calling ndViewer_.close() a second time.
    */
   void closeWithoutNDViewer();

   /**
    * Add an MM Inspector overlay to this viewer.
    * The overlay will be rendered on the NDViewer canvas on top of the image.
    *
    * @param overlay the overlay to add
    */
   void addOverlay(Overlay overlay);

   /**
    * Remove an MM Inspector overlay from this viewer.
    *
    * @param overlay the overlay to remove
    */
   void removeOverlay(Overlay overlay);

   /**
    * Return the list of MM Inspector overlays currently attached to this viewer.
    *
    * @return list of overlays
    */
   List<Overlay> getOverlays();

   /**
    * Set an external overlayer plugin (e.g. for tile grid display).
    * The plugin will be chained after the MM overlays.
    *
    * @param plugin the external overlayer plugin, or null to clear
    */
   void setOverlayerPlugin(TiledDataViewerOverlayerPlugin plugin);

   /**
    * Notify this viewer that new tiles have arrived with images for all channels.
    * All images are submitted as a single stats request so the Inspector
    * receives one result with all channel histograms in one callback.
    *
    * <p>Images and axes lists must correspond 1-to-1.</p>
    *
    * @param images    list of images (one per channel)
    * @param axesList  list of NDViewer axes maps (one per image, same order)
    */
   void newTileArrived(List<Image> images, List<HashMap<String, Object>> axesList);
}
