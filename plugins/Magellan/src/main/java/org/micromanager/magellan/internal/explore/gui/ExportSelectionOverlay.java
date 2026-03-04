package org.micromanager.magellan.internal.explore.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import org.micromanager.ndviewer.api.NDViewerAPI;
import org.micromanager.ndviewer.api.OverlayerPlugin;
import org.micromanager.ndviewer.overlay.Overlay;
import org.micromanager.ndviewer.overlay.Roi;

/**
 * OverlayerPlugin that draws a yellow selection rectangle while the user is
 * dragging in export mode. The exportListener_ field is volatile and null
 * when not in export mode.
 */
public class ExportSelectionOverlay implements OverlayerPlugin {

   private volatile ExportMouseListener exportListener_;
   private final NDViewerAPI viewer_;

   public ExportSelectionOverlay(NDViewerAPI viewer) {
      viewer_ = viewer;
   }

   public void setExportMouseListener(ExportMouseListener ml) {
      exportListener_ = ml;
   }

   @Override
   public void drawOverlay(Overlay defaultOverlay, Point2D.Double displayImageSize,
                           double downsampleFactor, Graphics g,
                           HashMap<String, Object> axes, double magnification,
                           Point2D.Double viewOffset) throws InterruptedException {
      Overlay overlay = new Overlay();
      ExportMouseListener ml = exportListener_;
      if (ml != null) {
         Point start = ml.mouseDragStartPoint_;
         Point current = ml.currentMouseLocation_;
         if (start != null && current != null) {
            // Roi coordinates are in canvas pixels (same pattern as ExploreOverlayer)
            int x = Math.min(start.x, current.x);
            int y = Math.min(start.y, current.y);
            int w = Math.abs(current.x - start.x);
            int h = Math.abs(current.y - start.y);
            Roi rect = new Roi(x, y, w, h);
            rect.setStrokeColor(Color.YELLOW);
            rect.setStrokeWidth(2f);
            overlay.add(rect);
         }
      }
      viewer_.setOverlay(overlay);
   }

}
