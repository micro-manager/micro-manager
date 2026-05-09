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

   private static final DecimalFormat FMT_POS = new DecimalFormat("000");

   private final Studio studio_;
   private final JPanel panel_;
   private final JLabel statusLabel_;
   private final JButton exportButton_;
   private final JButton posListButton_;
   private TiledDataViewerDataViewerAPI viewer_;
   private static boolean expanded_ = true;

   // Last confirmed ROI in full-resolution pixels [x, y, w, h]; null when none.
   private int[] lastRoi_ = null;
   // Last confirmed position-list ROI in full-resolution pixels [x, y, w, h]; null when none.
   private int[] lastPosListRoi_ = null;

   public TiledDataViewerInspectorPanelController(Studio studio) {
      studio_ = studio;
      statusLabel_ = new JLabel(" ");
      exportButton_ = new JButton("Export...");
      posListButton_ = new JButton("Create Positions...");
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
      p.add(center);
      p.add(noZoom, "wrap");
      p.add(exportButton_);
      p.add(posListButton_, "wrap");
      p.add(statusLabel_, "span 2, wrap");
      return p;
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
      if (lastPosListRoi_ != null) {
         createPositionListFromRoi(lastPosListRoi_);
      } else {
         startPositionListMode();
      }
   }

   private void startPositionListMode() {
      TiledDataViewerAPI v = viewer_.getNDViewer();
      v.getCanvasJPanel().setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      setStatus("Draw a selection for the position list");

      TiledDataViewerOverlayerPlugin previousOverlay = viewer_.getOverlayerPlugin();
      ExportSelectionOverlay selectionOverlay = new ExportSelectionOverlay(v);
      viewer_.setOverlayerPlugin(selectionOverlay);

      ExportMouseListener[] el = new ExportMouseListener[1];
      el[0] = new ExportMouseListener(v,
              () -> {
                 v.getCanvasJPanel().setCursor(Cursor.getDefaultCursor());
                 if (lastPosListRoi_ != null) {
                    selectionOverlay.freezeRoi(el[0].mouseDragStartPoint_,
                            el[0].currentMouseLocation_);
                    el[0].setOnDismiss(() -> {
                       lastPosListRoi_ = null;
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
              this::onPosListRoiSelected);
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
      lastPosListRoi_ = new int[]{x1, y1, roiW, roiH};
      createPositionListFromRoi(lastPosListRoi_);
   }

   /**
    * Converts a full-resolution pixel ROI to a stage-coordinate position list and
    * installs it as the active MM position list.
    *
    * <p>The conversion uses the pixel size and, if available, the pixel-size affine
    * transform read from the dataset's stored image metadata. The grid fills the ROI
    * using the camera FOV (from the stored tile dimensions) with the overlap stored
    * in the summary metadata. Positions are ordered in a serpentine pattern to minimise
    * stage travel.</p>
    */
   private void createPositionListFromRoi(int[] roi) {
      TiledDataViewerDataProviderAPI dp =
               (TiledDataViewerDataProviderAPI) viewer_.getDataProvider();
      TiledDataProviderAPI storage = dp.getStorage();

      // ---- 1. Probe one tile for pixel size, tile dimensions, and a reference stage pos ----
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
         JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(panel_),
               "Cannot create position list: no stage-position metadata found in this dataset.",
               "Create Position List", JOptionPane.WARNING_MESSAGE);
         return;
      }

      // Tile dimensions from tags (reliable) or fallback to summary.
      int tileW = probeTile.tags.optInt("Width", 0);
      int tileH = probeTile.tags.optInt("Height", 0);
      if (tileW <= 0 && summary != null) {
         tileW = summary.optInt("Width", 512);
      }
      if (tileH <= 0 && summary != null) {
         tileH = summary.optInt("Height", tileW);
      }
      if (tileW <= 0) {
         tileW = 512;
      }
      if (tileH <= 0) {
         tileH = tileW;
      }

      double pixelSizeUm = summary != null ? summary.optDouble("PixelSize_um", 0.0) : 0.0;

      // Pixel size fallback from per-tile tags.
      if (pixelSizeUm <= 0.0) {
         pixelSizeUm = probeTile.tags.optDouble("PixelSizeUm", 1.0);
      }
      if (pixelSizeUm <= 0.0) {
         pixelSizeUm = 1.0;
      }

      // Overlap from summary metadata.
      int overlapX = summary != null ? summary.optInt("GridPixelOverlapX", 0) : 0;
      int overlapY = summary != null ? summary.optInt("GridPixelOverlapY", 0) : 0;

      // Reference tile: its canvas-pixel top-left corner and its stage center.
      int refRow = probeAxes.get("row") instanceof Integer ? (Integer) probeAxes.get("row") : 0;
      int refCol = probeAxes.get("column") instanceof Integer
            ? (Integer) probeAxes.get("column") : 0;
      int stepPxX = tileW - overlapX;
      int stepPxY = tileH - overlapY;
      // Canvas pixel position of the center of this reference tile.
      double refPixCenterX = refCol * stepPxX + tileW / 2.0;
      double refPixCenterY = refRow * stepPxY + tileH / 2.0;

      double refStageX;
      double refStageY;
      try {
         refStageX = probeTile.tags.getDouble("XPositionUm");
         refStageY = probeTile.tags.getDouble("YPositionUm");
      } catch (Exception e) {
         JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(panel_),
               "Cannot create position list: failed to read stage position from metadata.",
               "Create Position List", JOptionPane.WARNING_MESSAGE);
         return;
      }

      // ---- 2. Build pixel→stage affine transform ----
      // Try the pixel-size affine from the live core first; fall back to scalar.
      AffineTransform pixToStage = null;
      try {
         AffineTransform at = AffineUtils.doubleToAffine(
               studio_.core().getPixelSizeAffine(true));
         double affinePixelSize = AffineUtils.deducePixelSize(at);
         if (Math.abs(pixelSizeUm - affinePixelSize) <= 0.1 * pixelSizeUm) {
            pixToStage = at;
         }
      } catch (Exception ignore) {
         // No live core available or no affine configured — scalar fallback below.
      }

      // ---- 3. Compute ROI corners in stage space ----
      // ROI is in full-res pixels. Convert each corner to stage coords.
      int roiX = roi[0];
      int roiY = roi[1];
      int roiW = roi[2];
      int roiH = roi[3];

      Point2D.Double stageTopLeft     = pixelToStage(roiX,        roiY,
            refPixCenterX, refPixCenterY, refStageX, refStageY, pixelSizeUm, pixToStage);
      Point2D.Double stageBottomRight = pixelToStage(roiX + roiW, roiY + roiH,
            refPixCenterX, refPixCenterY, refStageX, refStageY, pixelSizeUm, pixToStage);

      // ---- 4. Compute step vectors in stage space for one camera FOV (minus overlap) ----
      // Step = affine * (stepPx, 0) and affine * (0, stepPx).
      final double stageStepXdx; // stage-X component when moving one tile to the right
      final double stageStepXdy; // stage-Y component when moving one tile to the right
      final double stageStepYdx; // stage-X component when moving one tile down
      final double stageStepYdy; // stage-Y component when moving one tile down
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
         stageStepXdx = stepPxX * pixelSizeUm;
         stageStepXdy = 0;
         stageStepYdx = 0;
         stageStepYdy = stepPxY * pixelSizeUm;
      }

      // Step magnitude for grid sizing.
      double stepMagX = Math.sqrt(stageStepXdx * stageStepXdx + stageStepXdy * stageStepXdy);
      double stepMagY = Math.sqrt(stageStepYdx * stageStepYdx + stageStepYdy * stageStepYdy);
      if (stepMagX <= 0 || stepMagY <= 0) {
         JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(panel_),
               "Cannot create position list: degenerate step size.",
               "Create Position List", JOptionPane.WARNING_MESSAGE);
         return;
      }

      // ---- 5. Grid dimensions: how many tiles to fill the ROI ----
      // ROI size in stage space (approximation using axis-aligned bounding box of corners).
      double roiStageW = Math.abs(stageBottomRight.x - stageTopLeft.x);
      double roiStageH = Math.abs(stageBottomRight.y - stageTopLeft.y);
      // Camera FOV in stage units.
      double fovW = pixToStage != null
            ? Math.sqrt(stageStepXdx * stageStepXdx / (stepPxX * stepPxX) * tileW * tileW
                        + stageStepXdy * stageStepXdy / (stepPxX * stepPxX) * tileW * tileW)
            : tileW * pixelSizeUm;
      double fovH = pixToStage != null
            ? Math.sqrt(stageStepYdx * stageStepYdx / (stepPxY * stepPxY) * tileH * tileH
                        + stageStepYdy * stageStepYdy / (stepPxY * stepPxY) * tileH * tileH)
            : tileH * pixelSizeUm;

      int nCols = Math.max(1, (int) Math.ceil((roiStageW + fovW - stepMagX) / stepMagX));
      int nRows = Math.max(1, (int) Math.ceil((roiStageH + fovH - stepMagY) / stepMagY));

      // ---- 6. Grid origin: center the grid over the ROI ----
      // Center of the ROI in stage space.
      double roiStageCenterX = (stageTopLeft.x + stageBottomRight.x) / 2.0;
      double roiStageCenterY = (stageTopLeft.y + stageBottomRight.y) / 2.0;
      // Total grid extent.
      double gridW = (nCols - 1) * stepMagX + fovW;
      double gridH = (nRows - 1) * stepMagY + fovH;
      // Top-left tile center of the grid.
      double originX = roiStageCenterX - gridW / 2.0 + fovW / 2.0;
      double originY = roiStageCenterY - gridH / 2.0 + fovH / 2.0;

      // Unit vectors for the step directions.
      double uxX = stageStepXdx / stepMagX;
      double uxY = stageStepXdy / stepMagX;
      double uyX = stageStepYdx / stepMagY;
      double uyY = stageStepYdy / stepMagY;

      // ---- 7. Build position list (serpentine) ----
      String xyStage;
      try {
         xyStage = studio_.core().getXYStageDevice();
      } catch (Exception e) {
         xyStage = "";
      }
      if (xyStage == null) {
         xyStage = "";
      }

      PositionList posList = new PositionList();
      final double overlapXUm = overlapX * pixelSizeUm;
      final double overlapYUm = overlapY * pixelSizeUm;

      for (int row = 0; row < nRows; row++) {
         for (int col = 0; col < nCols; col++) {
            int c = ((row & 1) == 0) ? col : (nCols - 1 - col); // serpentine
            double dx = c * stageStepXdx + row * stageStepYdx;
            double dy = c * stageStepXdy + row * stageStepYdy;
            double stageX = originX + dx;
            double stageY = originY + dy;

            MultiStagePosition msp = new MultiStagePosition();
            msp.setDefaultXYStage(xyStage);
            if (!xyStage.isEmpty()) {
               msp.add(StagePosition.create2D(xyStage, stageX, stageY));
            }
            msp.setLabel("Pos-" + FMT_POS.format(row) + "_" + FMT_POS.format(c));
            msp.setGridCoordinates(row, c);
            msp.setProperty("OverlapUmX",
                  String.valueOf(overlapXUm));
            msp.setProperty("OverlapUmY",
                  String.valueOf(overlapYUm));
            msp.setProperty("OverlapPixelsX", String.valueOf(overlapX));
            msp.setProperty("OverlapPixelsY", String.valueOf(overlapY));
            msp.setProperty("Source", "TiledDataViewerInspectorPanel");
            posList.addPosition(msp);
         }
      }

      PositionList existing = studio_.positions().getPositionList();
      for (int i = 0; i < posList.getNumberOfPositions(); i++) {
         existing.addPosition(posList.getPosition(i));
      }
      studio_.positions().setPositionList(existing);
      SwingUtilities.invokeLater(() -> studio_.app().showPositionList());
      setStatus("Added " + posList.getNumberOfPositions() + " positions");
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
      viewer_ = (TiledDataViewerDataViewerAPI) viewer;
      lastRoi_ = null;
      lastPosListRoi_ = null;
   }

   @Override
   public void detachDataViewer() {
      viewer_ = null;
      lastRoi_ = null;
      lastPosListRoi_ = null;
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
