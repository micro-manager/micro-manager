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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import mmcorej.TaggedImage;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.tileddataviewer.TiledDataViewerAPI;
import org.micromanager.tileddataviewer.TiledDataViewerAcqInterface;
import org.micromanager.tileddataviewer.TiledDataViewerCanvasMouseListenerInterface;
import org.micromanager.tileddataviewer.TiledDataViewerDataSource;
import org.micromanager.tileddataviewer.TiledDataViewerOverlayerPlugin;
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
         TiledDataViewerCanvasMouseListenerInterface, TiledDataViewerOverlayerPlugin {

   private static final double ZOOM_FACTOR = 1.4;

   private final ExplorerManager manager_;
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

   // When true, tile selection and acquisition are disabled (opened dataset, not live explore).
   private volatile boolean readOnly_ = false;

   // Cache for getImageKeys() — invalidated by ExplorerManager after putImageMultiRes.
   private volatile Set<HashMap<String, Object>> imageKeysCache_ = null;

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

   public ExplorerDataSource(ExplorerManager manager) {
      manager_ = manager;
   }

   public void setViewer(TiledDataViewerAPI viewer) {
      viewer_ = viewer;
   }

   public void setStorage(MultiresNDTiffAPI storage) {
      storage_ = storage;
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
      return null; // Unbounded explore mode
   }

   @Override
   public TaggedImage getImageForDisplay(HashMap<String, Object> axes, int resolutionIndex,
                                         double xOffset, double yOffset,
                                         int imageWidth, int imageHeight) {
      if (storage_ == null) {
         return null;
      }
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
         if (!readOnly_ && tileWidth_ > 0 && tileHeight_ > 0) {
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

      if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
         if (!readOnly_ && selectionStart_ != null
                 && tileWidth_ > 0 && tileHeight_ > 0) {
            if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
               isLeftDragging_ = true;
            }
            if (isLeftDragging_) {
               Point tile = getTileFromDisplayCoords(e.getX(), e.getY());
               if (tile != null && !tile.equals(selectionEnd_)) {
                  selectionEnd_ = tile;
                  manager_.redrawOverlay();
               }
            }
         }
      } else if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
         if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
            isRightDragging_ = true;
            selectionStart_ = null;
            selectionEnd_ = null;
         }
         if (isRightDragging_) {
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

         TextRoi line2 = new TextRoi(centerX - 120, centerY - 10, "Left-drag: expand selection");
         line2.setStrokeColor(Color.WHITE);
         overlay.add(line2);

         TextRoi line3 = new TextRoi(centerX - 120, centerY + 10,
               "Left-click: acquire selected tiles");
         line3.setStrokeColor(Color.WHITE);
         overlay.add(line3);

         TextRoi line4 = new TextRoi(centerX - 120, centerY + 30, "Right-drag: pan view");
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
                  ? "Left-drag to extend, left-click to queue"
                  : "Left-drag to extend, left-click to acquire";
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

      // Red rectangle showing the current stage FOV position
      Point2D.Double stagePixel = stagePositionPixel_;
      if (stagePixel != null && tileWidth_ > 0 && tileHeight_ > 0) {
         double overlapPct = manager_.getOverlapPercentage();
         int overlapPx = (int) Math.round(tileWidth_ * overlapPct / 100.0);
         double effW = tileWidth_ - overlapPx;
         double effH = tileHeight_ - overlapPx;
         double tilePixelX = stagePixel.x - effW / 2.0;
         double tilePixelY = stagePixel.y - effH / 2.0;
         int dispX = (int) ((tilePixelX - viewOffset.x) * magnification);
         int dispY = (int) ((tilePixelY - viewOffset.y) * magnification);
         int dispW = (int) (effW * magnification);
         int dispH = (int) (effH * magnification);
         Roi stageRoi = new Roi(dispX, dispY, dispW, dispH);
         stageRoi.setStrokeColor(Color.RED);
         stageRoi.setStrokeWidth(2);
         overlay.add(stageRoi);
      }
   }
}
