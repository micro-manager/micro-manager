package org.micromanager.explorer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import mmcorej.TaggedImage;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.tileddataviewer.TiledDataViewerAPI;
import org.micromanager.tileddataviewer.TiledDataViewerAcqInterface;
import org.micromanager.tileddataviewer.TiledDataViewerCanvasMouseListenerInterface;
import org.micromanager.tileddataviewer.TiledDataViewerDataSource;
import org.micromanager.tileddataviewer.TiledDataViewerExploreControls;
import org.micromanager.tileddataviewer.TiledDataViewerOverlayerPlugin;
import org.micromanager.tileddataviewer.overlay.OvalRoi;
import org.micromanager.tileddataviewer.overlay.Overlay;
import org.micromanager.tileddataviewer.overlay.Roi;
import org.micromanager.tileddataviewer.overlay.TextRoi;

/**
 * Data source for the Explorer mode TiledDataViewer.
 * Implements the interfaces required for the tiled viewer to function,
 * including mouse handling and overlay rendering for tile selection.
 *
 * <p>Tile display dimensions ({@code tileWidth_}/{@code tileHeight_}) reflect the
 * pipeline-processed image size and are set after the first tile is acquired.
 * Stage step size is tracked separately via {@code stageTileWidthUm_}/{@code stageTileHeightUm_}
 * in ExplorerManager (camera FOV in microns, fixed for the session).
 */
public class ExplorerDataSource implements TiledDataViewerDataSource, TiledDataViewerAcqInterface,
         TiledDataViewerCanvasMouseListenerInterface, TiledDataViewerOverlayerPlugin,
         TiledDataViewerExploreControls {

   private static final double ZOOM_FACTOR = 1.4;

   private final ExplorerManager manager_;
   private final CopyOnWriteArrayList<AcquisitionStateListener> acqStateListeners_ =
         new CopyOnWriteArrayList<>();
   private volatile TiledDataViewerAPI viewer_;
   private volatile MultiresNDTiffAPI storage_;
   private volatile boolean finished_ = false;

   // Mouse interaction state
   private volatile Point selectionStart_ = null;   // First corner of selection (row, col)
   private volatile Point selectionEnd_ = null;     // Second corner of selection (row, col)

   private Point dragStart_ = null;
   private boolean isRightDragging_ = false;
   private boolean isLeftDragging_ = false;
   private volatile boolean acquisitionInProgress_ = false;
   // True when pixel size or camera ROI has changed from session start; blocks new acquisitions.
   private volatile boolean settingsMismatch_ = false;

   // When true, tile selection and acquisition are disabled (opened dataset, not live explore).
   private volatile boolean readOnly_ = false;

   // Cache for getImageKeys() — invalidated by ExplorerManager after putImageMultiRes.
   private volatile Set<HashMap<String, Object>> imageKeysCache_ = null;

   // Lazily built from XPositionPix/YPositionPix tags; empty map = no position tags present.
   private volatile Map<HashMap<String, Object>, Point> tilePositions_ = null;
   private int fullResTileW_ = 0;
   private int fullResTileH_ = 0;

   // Current stage position in full-resolution pixel coordinates (center of FOV).
   private volatile Point2D.Double stagePositionPixel_ = null;

   // Tile tracking (accessed from multiple threads)
   private final Set<String> acquiredTiles_ =
         Collections.newSetFromMap(new ConcurrentHashMap<>());
   private final Set<String> pendingTiles_ =
         Collections.newSetFromMap(new ConcurrentHashMap<>());

   // Display tile dimensions (pipeline-output size; set after first acquisition)
   private int tileWidth_ = -1;
   private int tileHeight_ = -1;

   // Vessel outline — set by ExplorerManager when anchor is confirmed; null = not set.
   private VesselType vesselType_ = null;
   private double vesselTlX_ = 0;
   private double vesselTlY_ = 0;
   private double vesselWidthPx_ = 0;
   private double vesselHeightPx_ = 0;

   public ExplorerDataSource(ExplorerManager manager) {
      manager_ = manager;
   }

   public void setViewer(TiledDataViewerAPI viewer) {
      viewer_ = viewer;
   }

   public void setStorage(MultiresNDTiffAPI storage) {
      storage_ = storage;
      // Reset the lazy position-tag cache so getBounds() re-probes the new storage.
      tilePositions_ = null;
      fullResTileW_ = 0;
      fullResTileH_ = 0;
   }

   private synchronized void buildTilePositions() {
      if (tilePositions_ != null) {
         return;
      }
      Map<HashMap<String, Object>, Point> map = new LinkedHashMap<>();
      for (HashMap<String, Object> axes : storage_.getAxesSet()) {
         mmcorej.TaggedImage ti = storage_.getImage(axes, 0);
         if (ti == null || ti.tags == null) {
            continue;
         }
         if (!ti.tags.has("XPositionPix") || !ti.tags.has("YPositionPix")) {
            // This tile has no position tag — dataset does not use per-tile positioning.
            tilePositions_ = new LinkedHashMap<>();
            return;
         }
         int x = ti.tags.optInt("XPositionPix", 0);
         int y = ti.tags.optInt("YPositionPix", 0);
         if (fullResTileW_ == 0) {
            fullResTileW_ = ti.tags.optInt("Width", 0);
            fullResTileH_ = ti.tags.optInt("Height", 0);
         }
         map.put(axes, new Point(x, y));
      }
      tilePositions_ = map;
   }

   public void invalidateImageKeysCache() {
      imageKeysCache_ = null;
   }

   public void setStagePositionPixel(Point2D.Double pos) {
      stagePositionPixel_ = pos;
   }

   public void setTileDimensions(int width, int height) {
      tileWidth_ = width;
      tileHeight_ = height;
   }

   public int getTileWidth() {
      return tileWidth_;
   }

   public int getTileHeight() {
      return tileHeight_;
   }

   public void markTileAcquired(int row, int col) {
      acquiredTiles_.add(row + "," + col);
   }

   public void addPendingTile(int row, int col) {
      pendingTiles_.add(row + "," + col);
   }

   public void removePendingTile(int row, int col) {
      pendingTiles_.remove(row + "," + col);
   }

   public void clearPendingTiles() {
      pendingTiles_.clear();
   }

   /**
    * Sets the vessel outline to be drawn in the overlay.
    * All coordinates are in full-resolution pixel space (tile-grid coordinates).
    */
   public synchronized void setVesselOutline(VesselType type,
         double tlX, double tlY, double widthPx, double heightPx) {
      vesselType_     = type;
      vesselTlX_      = tlX;
      vesselTlY_      = tlY;
      vesselWidthPx_  = widthPx;
      vesselHeightPx_ = heightPx;
   }

   /** Removes the vessel outline from the overlay. */
   public synchronized void clearVesselOutline() {
      vesselType_ = null;
   }

   public void clearSelection() {
      selectionStart_ = null;
      selectionEnd_ = null;
      manager_.redrawOverlay();
   }

   public void setReadOnly(boolean readOnly) {
      readOnly_ = readOnly;
   }

   public void setAcquisitionInProgress(boolean inProgress) {
      acquisitionInProgress_ = inProgress;
      for (AcquisitionStateListener l : acqStateListeners_) {
         l.acquisitionInProgressChanged(inProgress);
      }
   }

   // ===================== TiledDataViewerExploreControls =====================

   @Override
   public void interruptAcquisition() {
      manager_.interruptAcquisition();
   }

   @Override
   public boolean isAcquisitionInProgress() {
      return acquisitionInProgress_;
   }

   @Override
   public void addAcquisitionStateListener(AcquisitionStateListener l) {
      acqStateListeners_.add(l);
   }

   @Override
   public void removeAcquisitionStateListener(AcquisitionStateListener l) {
      acqStateListeners_.remove(l);
   }

   public void setSettingsMismatch(boolean mismatch) {
      settingsMismatch_ = mismatch;
      if (mismatch) {
         selectionStart_ = null;
         selectionEnd_ = null;
      }
   }

   /**
    * Returns selected tiles in serpentine order to minimize stage travel.
    */
   public List<Point> getSelectedTiles() {
      List<Point> tiles = new ArrayList<>();
      if (selectionStart_ == null) {
         return tiles;
      }

      int startRow = selectionStart_.x;
      int startCol = selectionStart_.y;
      int endRow = selectionEnd_ != null ? selectionEnd_.x : startRow;
      int endCol = selectionEnd_ != null ? selectionEnd_.y : startCol;

      int minRow = Math.min(startRow, endRow);
      int maxRow = Math.max(startRow, endRow);
      int minCol = Math.min(startCol, endCol);
      int maxCol = Math.max(startCol, endCol);

      Point2D.Double stagePixel = stagePositionPixel_;
      Point closestCorner;

      if (stagePixel != null && tileWidth_ > 0 && tileHeight_ > 0) {
         double overlapPercent = manager_.getOverlapPercentage();
         int overlapPixels = (int) Math.round(tileWidth_ * overlapPercent / 100.0);
         double effectiveTileWidth = tileWidth_ - overlapPixels;
         double effectiveTileHeight = tileHeight_ - overlapPixels;
         int stageRow = (int) Math.floor(stagePixel.y / effectiveTileHeight);
         int stageCol = (int) Math.floor(stagePixel.x / effectiveTileWidth);
         closestCorner = findClosestCorner(stageRow, stageCol, minRow, maxRow, minCol, maxCol);
      } else {
         closestCorner = new Point(minRow, minCol);
      }

      boolean startFromTop = (closestCorner.x == minRow);
      boolean startFromLeft = (closestCorner.y == minCol);

      if (startFromTop) {
         for (int row = minRow; row <= maxRow; row++) {
            boolean scanRight = ((row - minRow) % 2 == 0) == startFromLeft;
            if (scanRight) {
               for (int col = minCol; col <= maxCol; col++) {
                  tiles.add(new Point(row, col));
               }
            } else {
               for (int col = maxCol; col >= minCol; col--) {
                  tiles.add(new Point(row, col));
               }
            }
         }
      } else {
         for (int row = maxRow; row >= minRow; row--) {
            boolean scanRight = ((maxRow - row) % 2 == 0) == startFromLeft;
            if (scanRight) {
               for (int col = minCol; col <= maxCol; col++) {
                  tiles.add(new Point(row, col));
               }
            } else {
               for (int col = maxCol; col >= minCol; col--) {
                  tiles.add(new Point(row, col));
               }
            }
         }
      }
      return tiles;
   }

   private Point findClosestCorner(int stageRow, int stageCol,
                                   int minRow, int maxRow,
                                   int minCol, int maxCol) {
      Point[] corners = {
         new Point(minRow, minCol),
         new Point(minRow, maxCol),
         new Point(maxRow, minCol),
         new Point(maxRow, maxCol)
      };

      Point closest = corners[0];
      int minDistance = Integer.MAX_VALUE;
      for (Point corner : corners) {
         int distance = Math.abs(corner.x - stageRow) + Math.abs(corner.y - stageCol);
         if (distance < minDistance) {
            minDistance = distance;
            closest = corner;
         }
      }
      return closest;
   }

   public boolean isTileAcquired(int row, int col) {
      return acquiredTiles_.contains(row + "," + col);
   }

   // ===================== TiledDataViewerDataSource =====================

   @Override
   public int[] getBounds() {
      if (storage_ == null) {
         return null;
      }
      buildTilePositions();
      if (tilePositions_.isEmpty()) {
         return null; // No position tags → unbounded explore mode.
      }
      int xMin = Integer.MAX_VALUE;
      int yMin = Integer.MAX_VALUE;
      int xMax = Integer.MIN_VALUE;
      int yMax = Integer.MIN_VALUE;
      for (Point p : tilePositions_.values()) {
         xMin = Math.min(xMin, p.x);
         yMin = Math.min(yMin, p.y);
         xMax = Math.max(xMax, p.x + fullResTileW_);
         yMax = Math.max(yMax, p.y + fullResTileH_);
      }
      return new int[]{xMin, yMin, xMax, yMax};
   }

   @Override
   public TaggedImage getImageForDisplay(HashMap<String, Object> axes, int resolutionIndex,
                                         double xOffset, double yOffset,
                                         int imageWidth, int imageHeight) {
      if (storage_ == null) {
         return null;
      }
      // NDTiffStorage.getDisplayImage handles grid-based tile placement internally.
      return storage_.getDisplayImage(axes, resolutionIndex,
              (int) xOffset, (int) yOffset, imageWidth, imageHeight);
   }

   @Override
   public Set<HashMap<String, Object>> getImageKeys() {
      if (storage_ == null) {
         return new HashSet<>();
      }
      Set<HashMap<String, Object>> cached = imageKeysCache_;
      if (cached != null) {
         return cached;
      }
      Set<HashMap<String, Object>> fresh = storage_.getAxesSet().stream()
              .map(axes -> {
                 HashMap<String, Object> copy = new HashMap<>(axes);
                 copy.remove(NDTiffStorage.ROW_AXIS);
                 copy.remove(NDTiffStorage.COL_AXIS);
                 return copy;
              })
              .collect(Collectors.toSet());
      imageKeysCache_ = fresh;
      return fresh;
   }

   @Override
   public int getMaxResolutionIndex() {
      if (storage_ == null) {
         return 0;
      }
      return storage_.getNumResLevels() - 1;
   }

   @Override
   public void increaseMaxResolutionLevel(int newMaxResolutionLevel) {
      if (storage_ != null) {
         storage_.increaseMaxResolutionLevel(newMaxResolutionLevel);
      }
   }

   @Override
   public String getDiskLocation() {
      if (storage_ == null) {
         return null;
      }
      return storage_.getDiskLocation();
   }

   @Override
   public void close() {
      manager_.onViewerClosed();
   }

   @Override
   public int getImageBitDepth(HashMap<String, Object> axesPositions) {
      if (storage_ == null) {
         return 16;
      }
      try {
         for (HashMap<String, Object> storedAxes : storage_.getAxesSet()) {
            return storage_.getEssentialImageMetadata(storedAxes).bitDepth;
         }
      } catch (Exception e) {
         // Fallback
      }
      return 16;
   }

   // ===================== TiledDataViewerAcqInterface =====================

   @Override
   public boolean isFinished() {
      return finished_;
   }

   public void setFinished(boolean finished) {
      finished_ = finished;
   }

   @Override
   public void abort() {
      finished_ = true;
   }

   @Override
   public void setPaused(boolean paused) {
   }

   @Override
   public boolean isPaused() {
      return false;
   }

   @Override
   public void waitForCompletion() {
   }

   // ===================== TiledDataViewerCanvasMouseListenerInterface =====================

   @Override
   public void mousePressed(MouseEvent e) {
      dragStart_ = e.getPoint();
      if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
         isRightDragging_ = false;
         if (!readOnly_ && tileWidth_ > 0 && tileHeight_ > 0) {
            Point tile = getTileFromDisplayCoords(e.getX(), e.getY());
            if (tile != null) {
               selectionStart_ = tile;
               selectionEnd_ = null;
               manager_.redrawOverlay();
            }
         }
      } else if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
         isLeftDragging_ = false;
      }
   }

   @Override
   public void mouseReleased(MouseEvent e) {
      if (javax.swing.SwingUtilities.isRightMouseButton(e) && !isRightDragging_) {
         if (!readOnly_ && !settingsMismatch_ && tileWidth_ > 0 && tileHeight_ > 0) {
            Point tile = getTileFromDisplayCoords(e.getX(), e.getY());
            if (tile != null) {
               selectionStart_ = tile;
               selectionEnd_ = null;
               manager_.redrawOverlay();
            }
         }
      } else if (javax.swing.SwingUtilities.isLeftMouseButton(e) && !isLeftDragging_) {
         if (!readOnly_ && e.isControlDown()) {
            Point2D.Double pixelPos = getFullResPixelCoords(e.getX(), e.getY());
            if (pixelPos != null) {
               manager_.moveStageToPixelPosition(pixelPos.x, pixelPos.y);
            }
         } else if (!readOnly_) {
            List<Point> selectedTiles = getSelectedTiles();
            if (!selectedTiles.isEmpty()) {
               manager_.acquireMultipleTiles(selectedTiles);
               selectionStart_ = null;
               selectionEnd_ = null;
            }
         }
      }

      dragStart_ = null;
      isRightDragging_ = false;
      isLeftDragging_ = false;
   }

   @Override
   public void mouseClicked(MouseEvent e) {
   }

   @Override
   public void mouseDragged(MouseEvent e) {
      if (dragStart_ == null) {
         return;
      }

      Point current = e.getPoint();
      int dx = dragStart_.x - current.x;
      int dy = dragStart_.y - current.y;

      if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
         // Right-drag creates and extends the selection — blocked in read-only mode.
         // Right-press already set selectionStart_ to the tile under the cursor.
         if (!readOnly_ && selectionStart_ != null
                 && tileWidth_ > 0 && tileHeight_ > 0) {
            if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
               isRightDragging_ = true;
            }
            if (isRightDragging_) {
               Point tile = getTileFromDisplayCoords(e.getX(), e.getY());
               if (tile != null && !tile.equals(selectionEnd_)) {
                  selectionEnd_ = tile;
                  manager_.redrawOverlay();
               }
            }
         }
      } else if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
         // Left-drag pans the view. Note: panning intentionally does NOT clear any
         // existing tile selection — a tentative single-tile selection set by a prior
         // right-press is left in place so the user can pan and then keep selecting.
         if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
            isLeftDragging_ = true;
         }
         if (isLeftDragging_) {
            manager_.pan(dx, dy);
            dragStart_ = current;
         }
      }
   }

   @Override
   public void mouseWheelMoved(MouseWheelEvent e) {
      Point mouseLoc = e.getPoint();
      if (e.getWheelRotation() < 0) {
         manager_.zoom(1.0 / ZOOM_FACTOR, mouseLoc);
      } else if (e.getWheelRotation() > 0) {
         manager_.zoom(ZOOM_FACTOR, mouseLoc);
      }
   }

   @Override
   public void mouseMoved(MouseEvent e) {
   }

   @Override
   public void mouseEntered(MouseEvent e) {
   }

   @Override
   public void mouseExited(MouseEvent e) {
   }

   private Point2D.Double getFullResPixelCoords(int displayX, int displayY) {
      Point2D.Double viewOffset = manager_.getViewOffset();
      double mag = manager_.getMagnification();
      if (viewOffset == null || mag <= 0) {
         return null;
      }
      double fullResX = viewOffset.x + displayX / mag;
      double fullResY = viewOffset.y + displayY / mag;
      return new Point2D.Double(fullResX, fullResY);
   }

   private Point getTileFromDisplayCoords(int displayX, int displayY) {
      if (tileWidth_ <= 0 || tileHeight_ <= 0) {
         return null;
      }
      Point2D.Double viewOffset = manager_.getViewOffset();
      double mag = manager_.getMagnification();
      if (viewOffset == null || mag <= 0) {
         return null;
      }

      double fullResX = viewOffset.x + displayX / mag;
      double fullResY = viewOffset.y + displayY / mag;

      double overlapPercent = manager_.getOverlapPercentage();
      int overlapPixels = (int) Math.round(tileWidth_ * overlapPercent / 100.0);
      double effectiveTileWidth = tileWidth_ - overlapPixels;
      double effectiveTileHeight = tileHeight_ - overlapPixels;

      int col = (int) Math.floor(fullResX / effectiveTileWidth);
      int row = (int) Math.floor(fullResY / effectiveTileHeight);
      return new Point(row, col);
   }

   // ===================== TiledDataViewerOverlayerPlugin =====================

   @Override
   public void drawOverlay(Overlay overlay, Point2D.Double displayImageSize,
                           double downsampleFactor, Graphics g,
                           HashMap<String, Object> axes, double magnification,
                           Point2D.Double viewOffset) {
      if (tileWidth_ <= 0 || tileHeight_ <= 0) {
         return;
      }

      if (!readOnly_ && selectionStart_ == null && !acquisitionInProgress_
               && acquiredTiles_.isEmpty()) {
         int centerX = (int) (displayImageSize.x / 2);
         int centerY = (int) (displayImageSize.y / 2);

         TextRoi line1 = new TextRoi(centerX - 120, centerY - 30, "Right-click: select tile");
         line1.setStrokeColor(Color.WHITE);
         overlay.add(line1);

         TextRoi line2 = new TextRoi(centerX - 120, centerY - 10, "Right-drag: expand selection");
         line2.setStrokeColor(Color.WHITE);
         overlay.add(line2);

         TextRoi line3 = new TextRoi(centerX - 120, centerY + 10,
               "Left-click: acquire selected tiles");
         line3.setStrokeColor(Color.WHITE);
         overlay.add(line3);

         TextRoi line4 = new TextRoi(centerX - 120, centerY + 30, "Left-drag: pan view");
         line4.setStrokeColor(Color.WHITE);
         overlay.add(line4);

         TextRoi line5 = new TextRoi(centerX - 120, centerY + 50,
               "Ctrl+left-click: move stage to position");
         line5.setStrokeColor(Color.WHITE);
         overlay.add(line5);
      }

      if (!pendingTiles_.isEmpty()) {
         double overlapPercent = manager_.getOverlapPercentage();
         int overlapPixels = (int) Math.round(tileWidth_ * overlapPercent / 100.0);
         double effectiveTileWidth = tileWidth_ - overlapPixels;
         double effectiveTileHeight = tileHeight_ - overlapPixels;
         for (String key : pendingTiles_) {
            String[] parts = key.split(",");
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);
            double tilePixelX = col * effectiveTileWidth;
            double tilePixelY = row * effectiveTileHeight;
            int dispX = (int) ((tilePixelX - viewOffset.x) * magnification);
            int dispY = (int) ((tilePixelY - viewOffset.y) * magnification);
            int dispW = (int) (effectiveTileWidth * magnification);
            int dispH = (int) (effectiveTileHeight * magnification);
            Roi rectRoi = new Roi(dispX, dispY, dispW, dispH);
            rectRoi.setStrokeColor(new Color(0, 100, 255));
            rectRoi.setStrokeWidth(3);
            rectRoi.setFillColor(new Color(0, 100, 255, 100));
            overlay.add(rectRoi);
         }
      }

      if (selectionStart_ != null) {
         int startRow = selectionStart_.x;
         int startCol = selectionStart_.y;
         int endRow = selectionEnd_ != null ? selectionEnd_.x : startRow;
         int endCol = selectionEnd_ != null ? selectionEnd_.y : startCol;

         int minRow = Math.min(startRow, endRow);
         int maxRow = Math.max(startRow, endRow);
         int minCol = Math.min(startCol, endCol);
         int maxCol = Math.max(startCol, endCol);

         int tileCount = (maxRow - minRow + 1) * (maxCol - minCol + 1);

         double overlapPercent = manager_.getOverlapPercentage();
         int overlapPixels = (int) Math.round(tileWidth_ * overlapPercent / 100.0);
         double effectiveTileWidth = tileWidth_ - overlapPixels;
         double effectiveTileHeight = tileHeight_ - overlapPixels;

         for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
               double tilePixelX = col * effectiveTileWidth;
               double tilePixelY = row * effectiveTileHeight;
               int dispX = (int) ((tilePixelX - viewOffset.x) * magnification);
               int dispY = (int) ((tilePixelY - viewOffset.y) * magnification);
               int dispW = (int) (effectiveTileWidth * magnification);
               int dispH = (int) (effectiveTileHeight * magnification);
               Roi rectRoi = new Roi(dispX, dispY, dispW, dispH);
               rectRoi.setStrokeColor(new Color(0, 100, 255));
               rectRoi.setStrokeWidth(3);
               rectRoi.setFillColor(new Color(0, 100, 255, 100));
               overlay.add(rectRoi);
            }
         }

         double centerPixelX = (minCol + maxCol + 1) * effectiveTileWidth / 2.0;
         double centerPixelY = (minRow + maxRow + 1) * effectiveTileHeight / 2.0;
         int textX = (int) ((centerPixelX - viewOffset.x) * magnification);
         int textY = (int) ((centerPixelY - viewOffset.y) * magnification);

         String instructions;
         if (selectionEnd_ == null) {
            instructions = acquisitionInProgress_
                  ? "Right-drag to extend, left-click to queue"
                  : "Right-drag to extend, left-click to acquire";
         } else {
            instructions = acquisitionInProgress_
                  ? "Left-click to queue " + tileCount + " tile(s)"
                  : "Left-click to acquire " + tileCount + " tile(s)";
         }
         TextRoi textRoi = new TextRoi(textX - 100, textY - 20, instructions);
         textRoi.setStrokeColor(Color.WHITE);
         overlay.add(textRoi);

         String boundsText = "Selection: rows " + minRow + "-" + maxRow
                 + ", cols " + minCol + "-" + maxCol;
         TextRoi boundsRoi = new TextRoi(textX - 100, textY, boundsText);
         boundsRoi.setStrokeColor(Color.YELLOW);
         overlay.add(boundsRoi);
      }

      // Red rectangle showing the current stage FOV position.
      // Size scales with the current pixel size relative to the session pixel size,
      // so the rectangle reflects the physical area the camera sees on the tile grid.
      Point2D.Double stagePixel = stagePositionPixel_;
      if (stagePixel != null && tileWidth_ > 0 && tileHeight_ > 0) {
         double overlapPct = manager_.getOverlapPercentage();
         int overlapPxX = (int) Math.round(tileWidth_  * overlapPct / 100.0);
         int overlapPxY = (int) Math.round(tileHeight_ * overlapPct / 100.0);
         double effW = tileWidth_  - overlapPxX;
         double effH = tileHeight_ - overlapPxY;
         double pixelSizeRatio = manager_.getPixelSizeRatio();
         double fovW = effW * pixelSizeRatio;
         double fovH = effH * pixelSizeRatio;
         double tilePixelX = stagePixel.x - fovW / 2.0;
         double tilePixelY = stagePixel.y - fovH / 2.0;
         int dispX = (int) ((tilePixelX - viewOffset.x) * magnification);
         int dispY = (int) ((tilePixelY - viewOffset.y) * magnification);
         int dispW = (int) (fovW * magnification);
         int dispH = (int) (fovH * magnification);
         Roi stageRoi = new Roi(dispX, dispY, dispW, dispH);
         stageRoi.setStrokeColor(Color.RED);
         stageRoi.setStrokeWidth(2);
         overlay.add(stageRoi);
      }

      // Green vessel outline — drawn last so it appears on top.
      VesselType vessel;
      double tlX;
      double tlY;
      double widthPx;
      double heightPx;
      synchronized (this) {
         vessel   = vesselType_;
         tlX      = vesselTlX_;
         tlY      = vesselTlY_;
         widthPx  = vesselWidthPx_;
         heightPx = vesselHeightPx_;
      }
      if (vessel != null && !vessel.isNone() && widthPx > 0 && heightPx > 0) {
         int bx = (int) ((tlX - viewOffset.x) * magnification);
         int by = (int) ((tlY - viewOffset.y) * magnification);
         int bw = (int) (widthPx  * magnification);
         int bh = (int) (heightPx * magnification);
         Roi border = new Roi(bx, by, bw, bh);
         border.setStrokeColor(new Color(0, 220, 80));
         border.setStrokeWidth(3);
         overlay.add(border);

         if (vessel.isMultiWell()) {
            double pxPerUm  = widthPx / vessel.getWidthUm();
            double wellW    = vessel.getWellWidthUm()    * pxPerUm;
            double wellH    = vessel.getWellHeightUm()   * pxPerUm;
            double spacX    = vessel.getWellSpacingXUm() * pxPerUm;
            double spacY    = vessel.getWellSpacingYUm() * pxPerUm;
            double firstWx  = vessel.getFirstWellXUm()  * pxPerUm;
            double firstWy  = vessel.getFirstWellYUm()  * pxPerUm;
            for (int r = 0; r < vessel.getWellRows(); r++) {
               for (int c = 0; c < vessel.getWellCols(); c++) {
                  double cx = tlX + firstWx + c * spacX;
                  double cy = tlY + firstWy + r * spacY;
                  int wx = (int) ((cx - wellW / 2.0 - viewOffset.x) * magnification);
                  int wy = (int) ((cy - wellH / 2.0 - viewOffset.y) * magnification);
                  int ww = (int) (wellW * magnification);
                  int wh = (int) (wellH * magnification);
                  Roi wellRoi = vessel.isWellsCircular()
                        ? new OvalRoi(wx, wy, ww, wh)
                        : new Roi(wx, wy, ww, wh);
                  wellRoi.setStrokeColor(new Color(0, 180, 60));
                  wellRoi.setStrokeWidth(1);
                  overlay.add(wellRoi);
               }
            }
         }
      }
   }
}
