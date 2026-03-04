package org.micromanager.ndviewer2;

import java.util.HashMap;
import java.util.List;
import org.micromanager.data.Image;
import org.micromanager.display.DataViewer;

/**
 * Public interface for the NDViewer2 data viewer.
 *
 * <p>Extends {@link DataViewer} (which already provides {@code setDisplaySettings}).
 * Use {@link NDViewer2Factory#createDataViewer} to obtain an instance.</p>
 */
public interface NDViewer2DataViewerAPI extends DataViewer {

   /**
    * Enable or disable accumulation of histogram stats across tiles.
    * When enabled, newImageArrived() stats are merged into a running total.
    *
    * @param enabled true to enable accumulation, false to disable
    */
   void setAccumulateStats(boolean enabled);

   /**
    * Set whether MM DisplaySettings colors should be preserved, preventing
    * NDViewer's colors from overwriting them.
    *
    * @param preserve true to preserve MM colors, false for normal bidirectional sync
    */
   void setPreserveMMColors(boolean preserve);

   /**
    * Get the underlying NDViewer instance for direct NDViewer API access.
    *
    * @return the NDViewer2API instance
    */
   NDViewer2API getNDViewer();

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
