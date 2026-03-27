package org.micromanager.magellan.internal.export;

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
 * OverlayerPlugin that draws a yellow selection rectangle while the user
 * is dragging in export mode. Uses the same Overlay/Roi mechanism as the
 * normal overlayers to avoid flickering.
 */
public class ExportSelectionOverlay implements OverlayerPlugin {

   private final NDViewerAPI viewer_;
   private volatile ExportMouseListener exportListener_;

   public ExportSelectionOverlay(NDViewerAPI viewer) {
      viewer_ = viewer;
   }

   public void setExportMouseListener(ExportMouseListener ml) {
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
