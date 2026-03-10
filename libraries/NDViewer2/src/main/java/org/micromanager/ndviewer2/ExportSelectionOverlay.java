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
   private volatile Point frozenStart_;
   private volatile Point frozenEnd_;

   ExportSelectionOverlay(NDViewer2API viewer) {
      viewer_ = viewer;
   }

   void setExportMouseListener(ExportMouseListener ml) {
      exportListener_ = ml;
   }

   /** Call after drag completes to keep the rectangle visible without a live mouse listener. */
   void freezeRoi(Point start, Point end) {
      frozenStart_ = start;
      frozenEnd_ = end;
      exportListener_ = null;
   }

   @Override
   public void drawOverlay(Overlay defaultOverlay, Point2D.Double displayImageSize,
           double downsampleFactor, Graphics g, HashMap<String, Object> axes,
           double magnification, Point2D.Double viewOffset) throws InterruptedException {
      Point start;
      Point end;
      ExportMouseListener ml = exportListener_;
      if (ml != null) {
         start = ml.mouseDragStartPoint_;
         end = ml.currentMouseLocation_;
      } else {
         start = frozenStart_;
         end = frozenEnd_;
      }
      if (start != null && end != null) {
         int x = Math.min(start.x, end.x);
         int y = Math.min(start.y, end.y);
         int w = Math.abs(end.x - start.x);
         int h = Math.abs(end.y - start.y);
         if (w > 0 && h > 0) {
            Roi roi = new Roi(x, y, w, h);
            roi.setStrokeColor(Color.YELLOW);
            roi.setStrokeWidth(2f);
            defaultOverlay.add(roi);
         }
      }
      viewer_.setOverlay(defaultOverlay);
   }
}
