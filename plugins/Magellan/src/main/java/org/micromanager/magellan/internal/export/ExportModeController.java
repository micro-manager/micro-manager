package org.micromanager.magellan.internal.export;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Window;
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
      controlsPanel_ = new ExportControlsPanel(this::startExportMode);
      display_.addControlPanel(controlsPanel_);
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
                 // Always restore normal state on mouse release
                 exportOverlay.setExportMouseListener(null);
                 display_.setOverlayerPlugin(normalOverlay_);
                 display_.setCustomCanvasMouseListener(normalMouseListener_);
                 display_.getCanvasJPanel().setCursor(Cursor.getDefaultCursor());
                 if (controlsPanel_ != null) {
                    controlsPanel_.setStatus(null);
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

      Window owner = SwingUtilities.getWindowAncestor(display_.getCanvasJPanel());
      HashMap<String, Object> baseAxes = baseAxesSupplier_.get();

      ExportTiles.showDialogAndExport(owner, storage_,
              display_.getDisplaySettingsJSON(),
              baseAxes, channelNamesSupplier_.get(),
              x1, y1, roiW, roiH);
   }
}
