package org.micromanager.tileddataviewer;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.AbstractInspectorPanelController;
import org.micromanager.exporttiles.ExportTiles;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.tileddataprovider.TiledDataProviderAPI;

public final class TiledDataViewerInspectorPanelController
      extends AbstractInspectorPanelController {

   /**
    * Shared help text describing the Explorer / TiledDataViewer mouse and button controls.
    * Used by this panel's Help button and by the Explorer/Deskew plugin frames so the
    * documentation stays in one place.
    */
   public static final String EXPLORE_HELP_TEXT =
         "Navigation:\n"
               + "  Left-drag: pan view\n"
               + "  Scroll wheel: zoom in/out\n"
               + "\n"
               + "Tile selection (live explore):\n"
               + "  Right-click: select tile\n"
               + "  Right-drag: expand selection\n"
               + "  Left-click: acquire (or queue) selected tiles\n"
               + "  Interrupt: stop all queued and running acquisitions\n"
               + "  Ctrl+left-click: move stage to position\n"
               + "\n"
               + "View controls:\n"
               + "  Center: pan to center of dataset (keep zoom)\n"
               + "  No Zoom: zoom to 1:1 and center on dataset\n"
               + "\n"
               + "Export:\n"
               + "  Click Export, drag to draw ROI, then confirm export\n"
               + "  Click anywhere to dismiss the ROI";

   private static final DecimalFormat FMT_POS = new DecimalFormat("000");

   private final Studio studio_;
   private final JPanel panel_;
   private final JLabel statusLabel_;
   private final JButton exportButton_;
   private final JButton posListButton_;
   private final JButton interruptButton_;
   private final JButton helpButton_;
   private TiledDataViewerDataViewerAPI viewer_;
   private static boolean expanded_ = true;

   // Live-explore acquisition controls for the attached viewer, or null if the data
   // source does not support interrupting (e.g. a read-only opened dataset).
   private TiledDataViewerExploreControls exploreControls_;
   // Listener registered on exploreControls_ while attached; updates the Interrupt button.
   private TiledDataViewerExploreControls.AcquisitionStateListener acqStateListener_;

   // Last confirmed export ROI in full-resolution pixels [x, y, w, h]; null when none.
   private int[] lastRoi_ = null;

   public TiledDataViewerInspectorPanelController(Studio studio) {
      studio_ = studio;
      statusLabel_ = new JLabel(" ");
      exportButton_ = new JButton("Export...");
      posListButton_ = new JButton("Create Positions...");
      interruptButton_ = new JButton("Interrupt");
      helpButton_ = new JButton("Help");
      panel_ = buildPanel();
   }

   private JPanel buildPanel() {
      final JPanel p = new JPanel(new MigLayout("insets 4", "[]4[]4[]", "[]2[]"));
      JButton center = new JButton("Center");
      JButton noZoom = new JButton("No Zoom");
      center.addActionListener(e -> onCenter());
      noZoom.addActionListener(e -> onNoZoom());
      exportButton_.addActionListener(e -> onExportClicked());
      posListButton_.addActionListener(e -> onPosListClicked());
      interruptButton_.setToolTipText(
            "Stop tile acquisition after the current tile finishes.");
      interruptButton_.setEnabled(false);
      interruptButton_.addActionListener(e -> {
         if (exploreControls_ != null) {
            exploreControls_.interruptAcquisition();
         }
      });
      helpButton_.addActionListener(e -> showHelp());
      p.add(center);
      p.add(noZoom, "wrap");
      p.add(exportButton_);
      p.add(posListButton_, "wrap");
      p.add(interruptButton_);
      p.add(helpButton_, "wrap");
      p.add(statusLabel_, "span 2, wrap");
      return p;
   }

   private void showHelp() {
      JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(panel_),
            EXPLORE_HELP_TEXT, "Explorer Help", JOptionPane.PLAIN_MESSAGE);
   }

   /** Returns the center of the dataset in full-res pixel coordinates, or null if unknown. */
   private Point2D.Double getDataCenter() {
      TiledDataViewerDataProviderAPI dp =
               (TiledDataViewerDataProviderAPI) viewer_.getDataProvider();
      int[] b = dp.getStorage().getImageBounds();
      if (b == null) {
         return null;
      }
      return new Point2D.Double((b[0] + b[2]) / 2.0, (b[1] + b[3]) / 2.0);
   }

   /** Pan so the dataset center is in the middle of the canvas; keep current zoom. */
   private void onCenter() {
      if (viewer_ == null) {
         return;
      }
      TiledDataViewerAPI v = viewer_.getNDViewer();
      Point2D.Double dataCenter = getDataCenter();
      if (dataCenter == null) {
         return;
      }
      Point2D.Double source = v.getFullResSourceDataSize();
      v.setViewOffset(dataCenter.x - source.x / 2.0, dataCenter.y - source.y / 2.0);
      v.update();
   }

   /** Set zoom to 1:1 (one full-res pixel per screen pixel), centered on the dataset. */
   private void onNoZoom() {
      if (viewer_ == null) {
         return;
      }
      TiledDataViewerAPI v = viewer_.getNDViewer();
      Point2D.Double displaySize = v.getDisplayImageSize();
      Point2D.Double dataCenter = getDataCenter();
      double centerX = dataCenter != null ? dataCenter.x
               : v.getViewOffset().x + v.getFullResSourceDataSize().x / 2.0;
      double centerY = dataCenter != null ? dataCenter.y
               : v.getViewOffset().y + v.getFullResSourceDataSize().y / 2.0;
      v.setViewOffset(centerX - displaySize.x / 2.0, centerY - displaySize.y / 2.0);
      v.setFullResSourceDataSize(displaySize.x, displaySize.y);
      v.update();
   }

   // ---- Export ----

   private void onExportClicked() {
      if (viewer_ == null) {
         return;
      }
      if (lastRoi_ != null) {
         showExportDialog(lastRoi_);
      } else {
         startExportMode();
      }
   }

   private void startExportMode() {
      TiledDataViewerAPI v = viewer_.getNDViewer();
      v.getCanvasJPanel().setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      setStatus("Draw a selection on the image");

      TiledDataViewerOverlayerPlugin previousOverlay = viewer_.getOverlayerPlugin();
      ExportSelectionOverlay exportOverlay = new ExportSelectionOverlay(v);
      viewer_.setOverlayerPlugin(exportOverlay);

      ExportMouseListener[] el = new ExportMouseListener[1];
      el[0] = new ExportMouseListener(v,
              () -> {
                 v.getCanvasJPanel().setCursor(Cursor.getDefaultCursor());
                 if (lastRoi_ != null) {
                    exportOverlay.freezeRoi(el[0].mouseDragStartPoint_,
                            el[0].currentMouseLocation_);
                    el[0].setOnDismiss(() -> {
                       lastRoi_ = null;
                       viewer_.setOverlayerPlugin(previousOverlay);
                       v.resetCanvasMouseListener();
                       v.update();
                       setStatus(null);
                    });
                    setStatus("Click to dismiss selection");
                 } else {
                    viewer_.setOverlayerPlugin(previousOverlay);
                    v.resetCanvasMouseListener();
                    v.update();
                    setStatus(null);
                 }
              },
              this::onRoiSelected);
      exportOverlay.setExportMouseListener(el[0]);
      v.setCustomCanvasMouseListener(el[0]);
   }

   private void onRoiSelected(Point dragStart, Point dragEnd) {
      TiledDataViewerAPI v = viewer_.getNDViewer();
      Point2D.Double viewOffset = v.getViewOffset();
      double mag = v.getMagnification();
      int x1 = (int) (viewOffset.x + Math.min(dragStart.x, dragEnd.x) / mag);
      int y1 = (int) (viewOffset.y + Math.min(dragStart.y, dragEnd.y) / mag);
      int x2 = (int) (viewOffset.x + Math.max(dragStart.x, dragEnd.x) / mag);
      int y2 = (int) (viewOffset.y + Math.max(dragStart.y, dragEnd.y) / mag);
      int roiW = Math.max(1, x2 - x1);
      int roiH = Math.max(1, y2 - y1);
      lastRoi_ = new int[]{x1, y1, roiW, roiH};
      showExportDialog(lastRoi_);
   }

   private void showExportDialog(int[] roi) {
      TiledDataViewerDataProviderAPI dp =
               (TiledDataViewerDataProviderAPI) viewer_.getDataProvider();
      List<String> chNames = viewer_.getExportChannelNames();
      Window owner = SwingUtilities.getWindowAncestor(panel_);
      ExportTiles.showDialogAndExport(owner, dp.getStorage(),
            buildDisplaySettingsJSON(), new HashMap<String, Object>(), chNames,
            roi[0], roi[1], roi[2], roi[3]);
   }

   private JSONObject buildDisplaySettingsJSON() {
      return viewer_.buildExportDisplaySettingsJSON();
   }

   // ---- Create Position List ----

   private void onPosListClicked() {
      if (viewer_ == null) {
         return;
      }
      startPositionListMode();
   }

   private void startPositionListMode() {
      TiledDataViewerAPI v = viewer_.getNDViewer();
      v.getCanvasJPanel().setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      setStatus("Draw a selection for the position list");

      TiledDataViewerOverlayerPlugin previousOverlay = viewer_.getOverlayerPlugin();
      ExportSelectionOverlay selectionOverlay = new ExportSelectionOverlay(v);
      viewer_.setOverlayerPlugin(selectionOverlay);

      // roiAccepted[0] is set to true when a valid drag completes.
      // Used by the completion handler to decide whether to freeze the overlay; a local
      // flag avoids any race with the async SwingWorker done().
      boolean[] roiAccepted = {false};
      ExportMouseListener[] el = new ExportMouseListener[1];
      el[0] = new ExportMouseListener(v,
              () -> {
                 v.getCanvasJPanel().setCursor(Cursor.getDefaultCursor());
                 if (roiAccepted[0]) {
                    selectionOverlay.freezeRoi(el[0].mouseDragStartPoint_,
                            el[0].currentMouseLocation_);
                    el[0].setOnDismiss(() -> {
                       viewer_.setOverlayerPlugin(previousOverlay);
                       v.resetCanvasMouseListener();
                       v.update();
                       setStatus(null);
                    });
                    setStatus("Click to dismiss selection");
                 } else {
                    viewer_.setOverlayerPlugin(previousOverlay);
                    v.resetCanvasMouseListener();
                    v.update();
                    setStatus(null);
                 }
              },
              (dragStart, dragEnd) -> {
                 roiAccepted[0] = true;
                 onPosListRoiSelected(dragStart, dragEnd);
              });
      selectionOverlay.setExportMouseListener(el[0]);
      v.setCustomCanvasMouseListener(el[0]);
   }

   private void onPosListRoiSelected(Point dragStart, Point dragEnd) {
      TiledDataViewerAPI v = viewer_.getNDViewer();
      Point2D.Double viewOffset = v.getViewOffset();
      double mag = v.getMagnification();
      int x1 = (int) (viewOffset.x + Math.min(dragStart.x, dragEnd.x) / mag);
      int y1 = (int) (viewOffset.y + Math.min(dragStart.y, dragEnd.y) / mag);
      int x2 = (int) (viewOffset.x + Math.max(dragStart.x, dragEnd.x) / mag);
      int y2 = (int) (viewOffset.y + Math.max(dragStart.y, dragEnd.y) / mag);
      int roiW = Math.max(1, x2 - x1);
      int roiH = Math.max(1, y2 - y1);
      int[] roi = new int[]{x1, y1, roiW, roiH};
      createPositionListFromRoi(roi);
   }

   /**
    * Converts a full-resolution pixel ROI to a stage-coordinate position list and
    * installs it as the active MM position list.
    *
    * <p>Two independent sources are used:</p>
    * <ul>
    *   <li><b>Dataset metadata</b> — the stored {@code XPositionUm}/{@code YPositionUm}
    *       tile tags and the stored {@code PixelSize_um} are used to convert canvas
    *       pixels to stage coordinates. These always reflect the original acquisition,
    *       regardless of which microscope is currently connected.</li>
    *   <li><b>Live hardware</b> — the current camera FOV ({@code CMMCore.getImageWidth()}/
    *       {@code getImageHeight()}) and pixel-size affine
    *       ({@code CMMCore.getPixelSizeAffine(true)})
    *       determine the grid step and tile size for the new position list. This allows
    *       the user to plan a re-acquisition with a different objective.</li>
    * </ul>
    * <p>If the live pixel size differs from the stored one a warning is shown in the
    * status bar (but the position list is still created). Falls back to stored tile
    * dimensions / scalar pixel size when the camera or affine is unavailable.</p>
    * <p>The tile overlap is read from the dataset's summary metadata. Positions are
    * ordered in a serpentine pattern to minimise stage travel.</p>
    * <p>The storage probe and grid computation run on a background thread so the EDT
    * is not blocked. The button is disabled for the duration.</p>
    */
   private void createPositionListFromRoi(int[] roi) {
      posListButton_.setEnabled(false);
      setStatus("Computing positions...");

      TiledDataViewerDataProviderAPI dp =
               (TiledDataViewerDataProviderAPI) viewer_.getDataProvider();
      TiledDataProviderAPI storage = dp.getStorage();

      new SwingWorker<PositionList, Void>() {
         // Warning text produced during computation; shown on EDT after done().
         String warning = null;

         @Override
         protected PositionList doInBackground() throws Exception {
            // ---- 1. Probe one tile for a reference stage position and stored pixel size ----
            JSONObject summary = storage.getSummaryMetadata();

            // Find a representative tile: any axes that has XPositionUm/YPositionUm in tags.
            TaggedImage probeTile = null;
            HashMap<String, Object> probeAxes = null;
            for (HashMap<String, Object> axes : storage.getAxesSet()) {
               TaggedImage img = storage.getImage(axes);
               if (img != null && img.tags != null
                     && img.tags.has("XPositionUm") && img.tags.has("YPositionUm")) {
                  probeTile = img;
                  probeAxes = axes;
                  break;
               }
            }

            if (probeTile == null) {
               throw new Exception(
                     "Cannot create position list: no stage-position metadata found "
                     + "in this dataset.");
            }

            // Stored pixel size — used only for canvas-pixel → stage-coordinate conversion,
            // so the ROI drawn on the dataset maps correctly to stage space regardless of
            // which objective is currently on the microscope.
            double storedPixelSizeUm =
                  summary != null ? summary.optDouble("PixelSize_um", 0.0) : 0.0;
            if (storedPixelSizeUm <= 0.0) {
               storedPixelSizeUm = probeTile.tags.optDouble("PixelSizeUm", 1.0);
            }
            if (storedPixelSizeUm <= 0.0) {
               storedPixelSizeUm = 1.0;
            }

            // ---- 2. Live hardware: affine + FOV for the new position grid ----
            // These reflect the current objective and are independent of storedPixelSizeUm.
            AffineTransform pixToStage = null;
            double livePixelSizeUm = 0.0;
            try {
               AffineTransform at = AffineUtils.doubleToAffine(
                     studio_.core().getPixelSizeAffine(true));
               livePixelSizeUm = AffineUtils.deducePixelSize(at);
               if (livePixelSizeUm > 0.0) {
                  pixToStage = at;
               }
            } catch (Exception ignore) {
               // No live core or no affine configured — scalar fallback below.
            }
            if (livePixelSizeUm <= 0.0) {
               livePixelSizeUm = storedPixelSizeUm;
            }

            // Camera FOV from live hardware; fall back to stored tile dimensions.
            int tileW = 0;
            int tileH = 0;
            boolean liveHardwareAvailable = false;
            try {
               tileW = (int) studio_.core().getImageWidth();
               tileH = (int) studio_.core().getImageHeight();
               liveHardwareAvailable = tileW > 0 && tileH > 0;
            } catch (Exception ignore) {
               // Camera unavailable — use stored tile dimensions below.
            }
            if (!liveHardwareAvailable) {
               tileW = probeTile.tags.optInt("Width", 0);
               if (tileW <= 0 && summary != null) {
                  tileW = summary.optInt("Width", 512);
               }
               if (tileW <= 0) {
                  tileW = 512;
               }
               tileH = probeTile.tags.optInt("Height", 0);
               if (tileH <= 0 && summary != null) {
                  tileH = summary.optInt("Height", tileW);
               }
               if (tileH <= 0) {
                  tileH = tileW;
               }
            }

            // Record warning if pixel size changed (different objective), but still proceed.
            if (liveHardwareAvailable
                  && Math.abs(livePixelSizeUm - storedPixelSizeUm) > 0.01 * storedPixelSizeUm) {
               warning = String.format(
                     "Warning: live pixel size (%.4f µm) differs from dataset (%.4f µm) — "
                     + "positions sized for current objective",
                     livePixelSizeUm, storedPixelSizeUm);
            }

            // Overlap from summary metadata (dataset property, not hardware-dependent).
            int overlapX = summary != null ? summary.optInt("GridPixelOverlapX", 0) : 0;
            int overlapY = summary != null ? summary.optInt("GridPixelOverlapY", 0) : 0;
            if (overlapX >= tileW || overlapY >= tileH) {
               throw new Exception(String.format(
                     "Cannot create position list: overlap (%d×%d px) is >= "
                     + "tile size (%d×%d px).", overlapX, overlapY, tileW, tileH));
            }

            // ---- 3. Reference point: map one stored tile's canvas position to stage ----
            // The canvas pixel grid was laid out with the stored tile dims and stored pixel size.
            int storedTileW = probeTile.tags.optInt("Width", tileW);
            int storedTileH = probeTile.tags.optInt("Height", tileH);
            if (storedTileW <= 0) {
               storedTileW = tileW;
            }
            if (storedTileH <= 0) {
               storedTileH = tileH;
            }
            int refRow = probeAxes.get("row") instanceof Integer
                  ? (Integer) probeAxes.get("row") : 0;
            int refCol = probeAxes.get("column") instanceof Integer
                  ? (Integer) probeAxes.get("column") : 0;
            int storedStepPxX = storedTileW - overlapX;
            int storedStepPxY = storedTileH - overlapY;
            double refPixCenterX = refCol * storedStepPxX + storedTileW / 2.0;
            double refPixCenterY = refRow * storedStepPxY + storedTileH / 2.0;

            double refStageX;
            double refStageY;
            try {
               refStageX = probeTile.tags.getDouble("XPositionUm");
               refStageY = probeTile.tags.getDouble("YPositionUm");
            } catch (Exception e) {
               throw new Exception(
                     "Cannot create position list: failed to read stage position from metadata.");
            }

            // ---- 4. Compute ROI corners in stage space ----
            int roiX = roi[0];
            int roiY = roi[1];
            int roiW = roi[2];
            int roiH = roi[3];

            // Canvas pixels → stage must use the STORED pixel size, because the canvas was
            // laid out using the dataset's pixel size (not the current objective).
            // If the live affine is available we scale it to match the stored pixel size so that
            // axis orientation (camera-X/stage-X inversion, rotation) is preserved while the
            // scale matches the stored dataset.  Without the live affine we fall back to the
            // stored scalar.
            AffineTransform storedPixToStage = null;
            if (pixToStage != null && livePixelSizeUm > 0.0) {
               double scale = storedPixelSizeUm / livePixelSizeUm;
               storedPixToStage = new AffineTransform(pixToStage);
               storedPixToStage.scale(scale, scale);
            }
            Point2D.Double stageTL = pixelToStage(roiX,        roiY,
                  refPixCenterX, refPixCenterY, refStageX, refStageY, storedPixelSizeUm,
                  storedPixToStage);
            Point2D.Double stageTR = pixelToStage(roiX + roiW, roiY,
                  refPixCenterX, refPixCenterY, refStageX, refStageY, storedPixelSizeUm,
                  storedPixToStage);
            Point2D.Double stageBL = pixelToStage(roiX,        roiY + roiH,
                  refPixCenterX, refPixCenterY, refStageX, refStageY, storedPixelSizeUm,
                  storedPixToStage);
            Point2D.Double stageBR = pixelToStage(roiX + roiW, roiY + roiH,
                  refPixCenterX, refPixCenterY, refStageX, refStageY, storedPixelSizeUm,
                  storedPixToStage);

            // ---- 5. Compute step vectors for the new acquisition grid ----
            int stepPxX = tileW - overlapX;
            int stepPxY = tileH - overlapY;
            final double stageStepXdx;
            final double stageStepXdy;
            final double stageStepYdx;
            final double stageStepYdy;
            if (pixToStage != null) {
               Point2D.Double sx = new Point2D.Double();
               Point2D.Double sy = new Point2D.Double();
               pixToStage.transform(new Point2D.Double(stepPxX, 0), sx);
               pixToStage.transform(new Point2D.Double(0, stepPxY), sy);
               stageStepXdx = sx.x;
               stageStepXdy = sx.y;
               stageStepYdx = sy.x;
               stageStepYdy = sy.y;
            } else {
               stageStepXdx = stepPxX * livePixelSizeUm;
               stageStepXdy = 0;
               stageStepYdx = 0;
               stageStepYdy = stepPxY * livePixelSizeUm;
            }

            double stepMagX = Math.sqrt(
                  stageStepXdx * stageStepXdx + stageStepXdy * stageStepXdy);
            double stepMagY = Math.sqrt(
                  stageStepYdx * stageStepYdx + stageStepYdy * stageStepYdy);
            if (stepMagX <= 0 || stepMagY <= 0) {
               throw new Exception(
                     "Cannot create position list: degenerate step size.");
            }

            // ---- 6. Grid dimensions and center: project all 4 corners onto step axes ----
            double uxX = stageStepXdx / stepMagX;
            double uxY = stageStepXdy / stepMagX;
            double uyX = stageStepYdx / stepMagY;
            double uyY = stageStepYdy / stepMagY;

            double[] projX = {
               stageTL.x * uxX + stageTL.y * uxY,
               stageTR.x * uxX + stageTR.y * uxY,
               stageBL.x * uxX + stageBL.y * uxY,
               stageBR.x * uxX + stageBR.y * uxY
            };
            double[] projY = {
               stageTL.x * uyX + stageTL.y * uyY,
               stageTR.x * uyX + stageTR.y * uyY,
               stageBL.x * uyX + stageBL.y * uyY,
               stageBR.x * uyX + stageBR.y * uyY
            };

            double projXMin = Math.min(
                  Math.min(projX[0], projX[1]), Math.min(projX[2], projX[3]));
            double projXMax = Math.max(
                  Math.max(projX[0], projX[1]), Math.max(projX[2], projX[3]));
            double projYMin = Math.min(
                  Math.min(projY[0], projY[1]), Math.min(projY[2], projY[3]));
            double projYMax = Math.max(
                  Math.max(projY[0], projY[1]), Math.max(projY[2], projY[3]));

            double roiExtentX = projXMax - projXMin;
            double roiExtentY = projYMax - projYMin;

            double fovW = pixToStage != null
                  ? Math.sqrt(stageStepXdx * stageStepXdx / (stepPxX * stepPxX) * tileW * tileW
                              + stageStepXdy * stageStepXdy / (stepPxX * stepPxX) * tileW * tileW)
                  : tileW * livePixelSizeUm;
            double fovH = pixToStage != null
                  ? Math.sqrt(stageStepYdx * stageStepYdx / (stepPxY * stepPxY) * tileH * tileH
                              + stageStepYdy * stageStepYdy / (stepPxY * stepPxY) * tileH * tileH)
                  : tileH * livePixelSizeUm;

            int nCols = Math.max(1,
                  (int) Math.ceil((roiExtentX + fovW - stepMagX) / stepMagX));
            int nRows = Math.max(1,
                  (int) Math.ceil((roiExtentY + fovH - stepMagY) / stepMagY));

            // ---- 7. Grid origin: center over the ROI ----
            // Use the average of the 4 stage-corner points as the ROI center.
            // This is correct for any affine (including shear); reconstructing stage
            // coordinates from projection-space scalars is only valid when ux and uy
            // are orthogonal.
            double roiStageCX = (stageTL.x + stageTR.x + stageBL.x + stageBR.x) / 4.0;
            double roiStageCY = (stageTL.y + stageTR.y + stageBL.y + stageBR.y) / 4.0;
            // Offset from the grid center (tile [nRows/2, nCols/2]) back to tile [0,0].
            double originX = roiStageCX
                  - (nCols - 1) / 2.0 * stageStepXdx
                  - (nRows - 1) / 2.0 * stageStepYdx;
            double originY = roiStageCY
                  - (nCols - 1) / 2.0 * stageStepXdy
                  - (nRows - 1) / 2.0 * stageStepYdy;

            // ---- 8. XY stage device ----
            String xyStage;
            try {
               xyStage = studio_.core().getXYStageDevice();
            } catch (Exception e) {
               xyStage = null;
            }
            if (xyStage == null || xyStage.isEmpty()) {
               throw new Exception(
                     "Cannot create position list: no XY stage device is configured.");
            }

            // ---- 9. Build position list (serpentine) ----
            PositionList posList = new PositionList();
            final double overlapXUm = overlapX * livePixelSizeUm;
            final double overlapYUm = overlapY * livePixelSizeUm;

            for (int row = 0; row < nRows; row++) {
               for (int col = 0; col < nCols; col++) {
                  int c = ((row & 1) == 0) ? col : (nCols - 1 - col); // serpentine
                  double dx = c * stageStepXdx + row * stageStepYdx;
                  double dy = c * stageStepXdy + row * stageStepYdy;
                  double stageX = originX + dx;
                  double stageY = originY + dy;

                  MultiStagePosition msp = new MultiStagePosition();
                  msp.setDefaultXYStage(xyStage);
                  msp.add(StagePosition.create2D(xyStage, stageX, stageY));
                  msp.setLabel("Pos-" + FMT_POS.format(row) + "_" + FMT_POS.format(c));
                  msp.setGridCoordinates(row, c);
                  msp.setProperty("OverlapUmX", String.valueOf(overlapXUm));
                  msp.setProperty("OverlapUmY", String.valueOf(overlapYUm));
                  msp.setProperty("OverlapPixelsX", String.valueOf(overlapX));
                  msp.setProperty("OverlapPixelsY", String.valueOf(overlapY));
                  msp.setProperty("Source", "TiledDataViewerInspectorPanel");
                  posList.addPosition(msp);
               }
            }
            return posList;
         }

         @Override
         protected void done() {
            posListButton_.setEnabled(true);
            PositionList posList;
            try {
               posList = get();
            } catch (Exception ex) {
               String msg = ex.getCause() != null
                     ? ex.getCause().getMessage() : ex.getMessage();
               JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(panel_),
                     msg, "Create Position List", JOptionPane.WARNING_MESSAGE);
               setStatus(null);
               return;
            }
            if (warning != null) {
               setStatus(warning);
            }
            PositionList existing = studio_.positions().getPositionList();
            for (int i = 0; i < posList.getNumberOfPositions(); i++) {
               existing.addPosition(posList.getPosition(i));
            }
            studio_.positions().setPositionList(existing);
            studio_.app().showPositionList();
            if (warning == null) {
               setStatus("Added " + posList.getNumberOfPositions() + " positions");
            } else {
               // Warning is already in the status bar; append the count.
               setStatus(warning + " (" + posList.getNumberOfPositions() + " positions added)");
            }
         }
      }.execute();
   }

   /**
    * Convert a full-resolution canvas pixel coordinate to a stage coordinate,
    * given a reference tile's pixel center and stage center.
    */
   private static Point2D.Double pixelToStage(double pixX, double pixY,
                                               double refPixCX, double refPixCY,
                                               double refStageX, double refStageY,
                                               double pixelSizeUm,
                                               AffineTransform pixToStage) {
      double dPixX = pixX - refPixCX;
      double dPixY = pixY - refPixCY;
      if (pixToStage != null) {
         Point2D.Double delta = new Point2D.Double();
         pixToStage.transform(new Point2D.Double(dPixX, dPixY), delta);
         return new Point2D.Double(refStageX + delta.x, refStageY + delta.y);
      } else {
         return new Point2D.Double(
               refStageX + dPixX * pixelSizeUm,
               refStageY + dPixY * pixelSizeUm);
      }
   }

   private void setStatus(String text) {
      statusLabel_.setText(text == null ? " " : text);
   }

   @Override
   public void attachDataViewer(DataViewer viewer) {
      // The Inspector reuses one controller instance and may re-attach without an
      // intervening detach when the active viewer changes; detach first so we never
      // leak a listener on the previous viewer's explore controls.
      if (viewer_ != null) {
         detachDataViewer();
      }
      viewer_ = (TiledDataViewerDataViewerAPI) viewer;
      lastRoi_ = null;

      exploreControls_ = viewer_.getExploreControls();
      final boolean inProgress =
            exploreControls_ != null && exploreControls_.isAcquisitionInProgress();
      // Interrupt is only meaningful for a live explore session; for a read-only
      // viewer it stays present but disabled (matching the other panel buttons).
      // Touch Swing on the EDT in case attach is ever called off-EDT.
      SwingUtilities.invokeLater(() -> interruptButton_.setEnabled(inProgress));
      if (exploreControls_ != null) {
         acqStateListener_ = changed ->
               SwingUtilities.invokeLater(() -> interruptButton_.setEnabled(changed));
         exploreControls_.addAcquisitionStateListener(acqStateListener_);
      }
   }

   @Override
   public void detachDataViewer() {
      if (exploreControls_ != null && acqStateListener_ != null) {
         exploreControls_.removeAcquisitionStateListener(acqStateListener_);
      }
      exploreControls_ = null;
      acqStateListener_ = null;
      SwingUtilities.invokeLater(() -> interruptButton_.setEnabled(false));
      viewer_ = null;
      lastRoi_ = null;
      setStatus(null);
   }

   @Override
   public String getTitle() {
      return "Tiled Data Viewer (Explorer) Controls";
   }

   @Override
   public JPanel getPanel() {
      return panel_;
   }

   @Override
   public boolean isVerticallyResizableByUser() {
      return false;
   }

   @Override
   public boolean initiallyExpand() {
      return expanded_;
   }

   @Override
   public void setExpanded(boolean status) {
      expanded_ = status;
   }
}
