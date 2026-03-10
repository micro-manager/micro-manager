package org.micromanager.ndviewer2;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import org.micromanager.ndviewer2.overlay.Overlay;
import org.micromanager.ndviewer2.overlay.Roi;

/**
 * NDViewer2OverlayerPlugin that draws a yellow selection rectangle while the
 * user is dragging in export mode.
 */
class ExportSelectionOverlay implements NDViewer2OverlayerPlugin {

   private final NDViewer2API viewer_;
   private volatile ExportMouseListener exportListener_;

   ExportSelectionOverlay(NDViewer2API viewer) {
      viewer_ = viewer;
   }

   void setExportMouseListener(ExportMouseListener ml) {
      exportListener_ = ml;
   }

   @Override
   public void drawOverlay(Overlay defaultOverlay, Point2D.Double displayImageSize,
           double downsampleFactor, Graphics g, HashMap<String, Object> axes,
           double magnification, Point2D.Double viewOffset) throws InterruptedException {
      ExportMouseListener ml = exportListener_;
      Overlay overlay = new Overlay();
      if (ml != null) {
         Point start = ml.mouseDragStartPoint_;
         Point current = ml.currentMouseLocation_;
         if (start != null && current != null) {
            int x = Math.min(start.x, current.x);
            int y = Math.min(start.y, current.y);
            int w = Math.abs(current.x - start.x);
            int h = Math.abs(current.y - start.y);
            if (w > 0 && h > 0) {
               Roi roi = new Roi(x, y, w, h);
               roi.setStrokeColor(Color.YELLOW);
               roi.setStrokeWidth(2f);
               overlay.add(roi);
            }
         }
      }
      viewer_.setOverlay(overlay);
   }
}
