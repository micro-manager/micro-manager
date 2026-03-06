package org.micromanager.magellan.internal.export;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.micromanager.exporttiles.ExportTiles;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndviewer.api.CanvasMouseListenerInterface;
import org.micromanager.ndviewer.api.OverlayerPlugin;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.overlay.Overlay;

/**
 * Orchestrates the interactive export-region workflow inside Magellan.
 *
 * <p>Installs an "Export" controls panel tab, then on user request switches the
 * viewer to crosshair/ROI-drag mode. When the drag completes it converts canvas
 * pixels to full-resolution coordinates and delegates to
 * {@link ExportTiles#showDialogAndExport}.
 */
public class ExportModeController {

   private final NDViewer display_;
   private final OverlayerPlugin normalOverlay_;
   private final CanvasMouseListenerInterface normalMouseListener_;
   private final MultiresNDTiffAPI storage_;
   private final Supplier<HashMap<String, Object>> baseAxesSupplier_;
   private final Supplier<List<String>> channelNamesSupplier_;

   private ExportControlsPanel controlsPanel_;
   // Last confirmed ROI in full-resolution pixels; null when no ROI is active.
   private int[] lastRoi_ = null; // [x, y, w, h]

   public ExportModeController(NDViewer display,
                               OverlayerPlugin normalOverlay,
                               CanvasMouseListenerInterface normalMouseListener,
                               MultiresNDTiffAPI storage,
                               Supplier<HashMap<String, Object>> baseAxesSupplier,
                               Supplier<List<String>> channelNamesSupplier) {
      display_ = display;
      normalOverlay_ = normalOverlay;
      normalMouseListener_ = normalMouseListener;
      storage_ = storage;
      baseAxesSupplier_ = baseAxesSupplier;
      channelNamesSupplier_ = channelNamesSupplier;
   }

   /**
    * Adds an "Export" tab to the viewer. Call once after constructing the controller.
    */
   public void installControlsPanel() {
      controlsPanel_ = new ExportControlsPanel(this::onExportButtonClicked);
      display_.addControlPanel(controlsPanel_);
   }

   private void onExportButtonClicked() {
      if (lastRoi_ != null) {
         // Re-use the existing ROI without re-drawing.
         Window owner = SwingUtilities.getWindowAncestor(display_.getCanvasJPanel());
         ExportTiles.showDialogAndExport(owner, storage_,
                 display_.getDisplaySettingsJSON(),
                 baseAxesSupplier_.get(), channelNamesSupplier_.get(),
                 lastRoi_[0], lastRoi_[1], lastRoi_[2], lastRoi_[3]);
      } else {
         startExportMode();
      }
   }

   /**
    * Switches the viewer into export-mode: cursor becomes a crosshair and
    * the user can drag a rectangle to define the export ROI.
    */
   public void startExportMode() {
      display_.getCanvasJPanel().setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      if (controlsPanel_ != null) {
         controlsPanel_.setStatus("Draw a selection on the image");
      }

      ExportSelectionOverlay exportOverlay = new ExportSelectionOverlay(display_);
      display_.setOverlayerPlugin(exportOverlay);

      ExportMouseListener exportListener = new ExportMouseListener(display_,
              () -> {
                 // Restore normal overlayer/cursor; keep the ROI overlay visible.
                 exportOverlay.setExportMouseListener(null);
                 display_.setOverlayerPlugin(normalOverlay_);
                 display_.setCustomCanvasMouseListener(new CanvasMouseListenerInterface() {
                    // One-shot listener: any click clears the ROI and restores normal listener.
                    private void dismiss() {
                       lastRoi_ = null;
                       display_.setOverlay(new Overlay());
                       display_.setCustomCanvasMouseListener(normalMouseListener_);
                       if (controlsPanel_ != null) {
                          controlsPanel_.setStatus(null);
                       }
                    }
                    @Override public void mousePressed(MouseEvent e) { dismiss(); }
                    @Override public void mouseReleased(MouseEvent e) {}
                    @Override public void mouseClicked(MouseEvent e) {}
                    @Override public void mouseDragged(MouseEvent e) {}
                    @Override public void mouseMoved(MouseEvent e) {}
                    @Override public void mouseEntered(MouseEvent e) {}
                    @Override public void mouseExited(MouseEvent e) {}
                    @Override public void mouseWheelMoved(MouseWheelEvent e) {}
                 });
                 display_.getCanvasJPanel().setCursor(Cursor.getDefaultCursor());
                 if (controlsPanel_ != null) {
                    controlsPanel_.setStatus("Click to dismiss selection");
                 }
              },
              this::onExportRoiSelected);
      exportOverlay.setExportMouseListener(exportListener);
      display_.setCustomCanvasMouseListener(exportListener);
   }

   private void onExportRoiSelected(Point dragStart, Point dragEnd) {
      Point2D.Double viewOffset = display_.getViewOffset();
      double mag = display_.getMagnification();
      int x1 = (int) (viewOffset.x + Math.min(dragStart.x, dragEnd.x) / mag);
      int y1 = (int) (viewOffset.y + Math.min(dragStart.y, dragEnd.y) / mag);
      int x2 = (int) (viewOffset.x + Math.max(dragStart.x, dragEnd.x) / mag);
      int y2 = (int) (viewOffset.y + Math.max(dragStart.y, dragEnd.y) / mag);
      int roiW = Math.max(1, x2 - x1);
      int roiH = Math.max(1, y2 - y1);

      lastRoi_ = new int[]{x1, y1, roiW, roiH};

      Window owner = SwingUtilities.getWindowAncestor(display_.getCanvasJPanel());
      ExportTiles.showDialogAndExport(owner, storage_,
              display_.getDisplaySettingsJSON(),
              baseAxesSupplier_.get(), channelNamesSupplier_.get(),
              x1, y1, roiW, roiH);
   }
}
