package org.micromanager.deskew;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
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
 * Data source for the Deskew Explore mode NDViewer.
 * Implements the interfaces required for NDViewer to function,
 * including mouse handling and overlay rendering for tile selection.
 */
public class DeskewExploreDataSource implements TiledDataViewerDataSource, TiledDataViewerAcqInterface,
         TiledDataViewerCanvasMouseListenerInterface, TiledDataViewerOverlayerPlugin {

   private static final double ZOOM_FACTOR = 1.4;

   private final DeskewExploreManager manager_;
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

   // Cache for getImageKeys() — invalidated by DeskewExploreManager after putImageMultiRes.
   private volatile Set<HashMap<String, Object>> imageKeysCache_ = null;

   // Current stage position in full-resolution pixel coordinates (center of FOV).
   // Null when unknown. Updated by the stage polling task in DeskewExploreManager.
   private volatile Point2D.Double stagePositionPixel_ = null;

   // Tile tracking (accessed from multiple threads — use concurrent set)
   private final Set<String> acquiredTiles_ =
         Collections.newSetFromMap(new ConcurrentHashMap<>());
   // Tiles queued for acquisition but not yet displayed (shown as persistent blue overlay)
   private final Set<String> pendingTiles_ =
         Collections.newSetFromMap(new ConcurrentHashMap<>());

   // Tile dimensions (set after first acquisition)
   private int tileWidth_ = -1;
   private int tileHeight_ = -1;

   public DeskewExploreDataSource(DeskewExploreManager manager) {
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

   /**
    * Clears the current tile selection.
    */
   public void clearSelection() {
      selectionStart_ = null;
      selectionEnd_ = null;
      manager_.redrawOverlay();
   }

   /**
    * Sets whether an acquisition is currently in progress.
    * Used to prevent new selections during acquisition.
    */
   public void setReadOnly(boolean readOnly) {
      readOnly_ = readOnly;
   }

   public void setAcquisitionInProgress(boolean inProgress) {
      acquisitionInProgress_ = inProgress;
   }

   /**
    * Returns the list of selected tiles as (row, col) Points.
    * Tiles are returned in serpentine order to minimize stage travel distance.
    * The pattern starts from the corner closest to the current stage position
    * and proceeds in a snake-like pattern, alternating direction for each row.
    * Falls back to starting from the top-left corner if stage position is unknown.
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

      // Determine the closest corner to start acquisition from
      Point2D.Double stagePixel = stagePositionPixel_;
      Point closestCorner;

      if (stagePixel != null && tileWidth_ > 0 && tileHeight_ > 0) {
         // Convert stage pixel position to tile indices using effective (non-overlap) spacing
         double overlapPercent = manager_.getOverlapPercentage();
         int overlapPixels = (int) Math.round(tileWidth_ * overlapPercent / 100.0);
         double effectiveTileWidth = tileWidth_ - overlapPixels;
         double effectiveTileHeight = tileHeight_ - overlapPixels;
         int stageRow = (int) Math.floor(stagePixel.y / effectiveTileHeight);
         int stageCol = (int) Math.floor(stagePixel.x / effectiveTileWidth);

         // Find closest corner
         closestCorner = findClosestCorner(stageRow, stageCol, minRow, maxRow, minCol, maxCol);
      } else {
         // Fallback: use top-left corner if stage position unknown
         closestCorner = new Point(minRow, minCol);
      }

      // Determine scan directions based on closest corner
      boolean startFromTop = (closestCorner.x == minRow);
      boolean startFromLeft = (closestCorner.y == minCol);

      // Generate serpentine path starting from the closest corner
      if (startFromTop) {
         // Scan from top to bottom
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
         // Scan from bottom to top
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

   /**
    * Finds the corner of the tile selection that is closest to the given stage position.
    *
    * @param stageRow Current stage row position
    * @param stageCol Current stage column position
    * @param minRow Minimum row of selection
    * @param maxRow Maximum row of selection
    * @param minCol Minimum column of selection
    * @param maxCol Maximum column of selection
    * @return The closest corner as a Point(row, col)
    */
   private Point findClosestCorner(int stageRow, int stageCol,
                                   int minRow, int maxRow,
                                   int minCol, int maxCol) {
      // Four corners
      Point[] corners = {
         new Point(minRow, minCol),  // top-left
         new Point(minRow, maxCol),  // top-right
         new Point(maxRow, minCol),  // bottom-left
         new Point(maxRow, maxCol)   // bottom-right
      };

      // Find closest using Manhattan distance
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

   // ===================== NDViewer2DataSource interface =====================

   @Override
   public int[] getBounds() {
      // Return null for unbounded explore mode
      return null;
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
      // Remove row and column axes from keys - viewer sees single plane
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
      // Don't close or null storage here - let the manager handle its lifecycle.
      // NDViewer's async close thread may still access the data source after this
      // returns, so storage_ must remain valid.
      manager_.onViewerClosed();
   }

   @Override
   public int getImageBitDepth(HashMap<String, Object> axesPositions) {
      if (storage_ == null) {
         return 16; // Default
      }
      try {
         // Need to find an actual stored axes position
         for (HashMap<String, Object> storedAxes : storage_.getAxesSet()) {
            return storage_.getEssentialImageMetadata(storedAxes).bitDepth;
         }
      } catch (Exception e) {
         // Fallback
      }
      return 16;
   }

   // ===================== NDViewer2AcqInterface interface =====================

   @Override
   public boolean isFinished() {
      // Return false while exploring so NDViewer will call increaseMaxResolutionLevel()
      // if the user zooms out further than the pre-built pyramid covers.
      // setFinished(true) is called by the manager when the session stops.
      // Note: the close dialog is controlled by the separate NDViewer2AcqInterface
      // (createAcqInterface()), which always returns isFinished()=true, so changing
      // this does not affect the "Finish Acquisition?" dialog.
      return finished_;
   }

   public void setFinished(boolean finished) {
      finished_ = finished;
   }

   @Override
   public void abort() {
      finished_ = true;
      // Don't call stopExplore here - let close() handle it
      // so the save dialog can be shown
   }

   @Override
   public void setPaused(boolean paused) {
      // Not applicable for explore mode
   }

   @Override
   public boolean isPaused() {
      return false;
   }

   @Override
   public void waitForCompletion() {
      // Non-blocking for explore mode
   }

   // ===================== NDViewer2CanvasMouseListenerInterface =====================

   @Override
   public void mousePressed(MouseEvent e) {
      // Always track drag start so pan/zoom work during acquisition.
      dragStart_ = e.getPoint();
      if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
         isRightDragging_ = false;
         // Tentatively start a selection on press so the tile highlight is visible
         // immediately, even before the button is released.  If the press turns into
         // a drag (pan), mouseDragged will clear selectionStart_.
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
         // Right-click without drag - start new selection (blocked in read-only mode)
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
            // Ctrl+left-click - move stage to clicked position
            Point2D.Double pixelPos = getFullResPixelCoords(e.getX(), e.getY());
            if (pixelPos != null) {
               manager_.moveStageToPixelPosition(pixelPos.x, pixelPos.y);
            }
         } else if (!readOnly_) {
            // Left-click without drag - queue selected tiles for acquisition
            List<Point> selectedTiles = getSelectedTiles();
            if (!selectedTiles.isEmpty()) {
               manager_.acquireMultipleTiles(selectedTiles);
               // Clear selection immediately — tiles are now tracked via pendingTiles_
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
      // Handled in mouseReleased
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
         // Left-drag extends the selection — blocked in read-only mode.
         // Left takes priority over right: if left is held we do selection, not pan.
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
         // Right-drag pans the view — only when left is not also held.
         if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
            isRightDragging_ = true;
            // Clear selection when panning
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
      // Could implement hover preview here
   }

   @Override
   public void mouseEntered(MouseEvent e) {
   }

   @Override
   public void mouseExited(MouseEvent e) {
   }

   /**
    * Convert display coordinates to full-resolution pixel coordinates.
    */
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

   /**
    * Convert display coordinates to tile row/col indices.
    */
   private Point getTileFromDisplayCoords(int displayX, int displayY) {
      if (tileWidth_ <= 0 || tileHeight_ <= 0) {
         return null;
      }

      Point2D.Double viewOffset = manager_.getViewOffset();
      double mag = manager_.getMagnification();

      if (viewOffset == null || mag <= 0) {
         return null;
      }

      // Convert display coordinates to full-resolution pixel coordinates
      double fullResX = viewOffset.x + displayX / mag;
      double fullResY = viewOffset.y + displayY / mag;

      // Calculate effective tile dimensions: overlap is same pixel count in X and Y
      double overlapPercent = manager_.getOverlapPercentage();
      int overlapPixels = (int) Math.round(tileWidth_ * overlapPercent / 100.0);
      double effectiveTileWidth = tileWidth_ - overlapPixels;
      double effectiveTileHeight = tileHeight_ - overlapPixels;

      // Convert to tile indices using effective spacing
      int col = (int) Math.floor(fullResX / effectiveTileWidth);
      int row = (int) Math.floor(fullResY / effectiveTileHeight);

      return new Point(row, col);
   }

   // ===================== NDViewer2OverlayerPlugin interface =====================

   @Override
   public void drawOverlay(Overlay overlay, Point2D.Double displayImageSize,
                           double downsampleFactor, Graphics g,
                           HashMap<String, Object> axes, double magnification,
                           Point2D.Double viewOffset) {
      if (tileWidth_ <= 0 || tileHeight_ <= 0) {
         return;
      }

      // Show usage instructions when nothing is selected, not acquiring,
      // no tiles have been acquired yet, and not in read-only mode
      if (!readOnly_ && selectionStart_ == null && !acquisitionInProgress_
               && acquiredTiles_.isEmpty()) {
         int centerX = (int) (displayImageSize.x / 2);
         int centerY = (int) (displayImageSize.y / 2);

         TextRoi line1 = new TextRoi(centerX - 120, centerY - 30,
                 "Right-click: select tile");
         line1.setStrokeColor(Color.WHITE);
         overlay.add(line1);

         TextRoi line2 = new TextRoi(centerX - 120, centerY - 10,
                 "Left-drag: expand selection");
         line2.setStrokeColor(Color.WHITE);
         overlay.add(line2);

         TextRoi line3 = new TextRoi(centerX - 120, centerY + 10,
                 "Left-click: acquire selected tiles");
         line3.setStrokeColor(Color.WHITE);
         overlay.add(line3);

         TextRoi line4 = new TextRoi(centerX - 120, centerY + 30,
                 "Right-drag: pan view");
         line4.setStrokeColor(Color.WHITE);
         overlay.add(line4);

         TextRoi line5 = new TextRoi(centerX - 120, centerY + 50,
                 "Ctrl+left-click: move stage to position");
         line5.setStrokeColor(Color.WHITE);
         overlay.add(line5);
      }

      // Draw persistent blue overlay for tiles that are queued/in-progress but not yet displayed
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

      // Draw selection if any
      if (selectionStart_ != null) {
         int startRow = selectionStart_.x;
         int startCol = selectionStart_.y;
         int endRow = selectionEnd_ != null ? selectionEnd_.x : startRow;
         int endCol = selectionEnd_ != null ? selectionEnd_.y : startCol;

         int minRow = Math.min(startRow, endRow);
         int maxRow = Math.max(startRow, endRow);
         int minCol = Math.min(startCol, endCol);
         int maxCol = Math.max(startCol, endCol);

         // Count tiles in selection
         int tileCount = (maxRow - minRow + 1) * (maxCol - minCol + 1);

         // Draw each tile in the selection
         // Overlap is the same pixel count in X and Y (derived from X tile width)
         double overlapPercent = manager_.getOverlapPercentage();
         int overlapPixels = (int) Math.round(tileWidth_ * overlapPercent / 100.0);
         double effectiveTileWidth = tileWidth_ - overlapPixels;
         double effectiveTileHeight = tileHeight_ - overlapPixels;

         for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
               // Calculate tile position based on effective spacing
               double tilePixelX = col * effectiveTileWidth;
               double tilePixelY = row * effectiveTileHeight;

               // Convert to display/screen coordinates
               int dispX = (int) ((tilePixelX - viewOffset.x) * magnification);
               int dispY = (int) ((tilePixelY - viewOffset.y) * magnification);
               // Draw the effective (non-overlap) size to match what NDTiffStorage renders
               int dispW = (int) (effectiveTileWidth * magnification);
               int dispH = (int) (effectiveTileHeight * magnification);

               // Create rectangle ROI at the tile position
               Roi rectRoi = new Roi(dispX, dispY, dispW, dispH);
               rectRoi.setStrokeColor(new Color(0, 100, 255));
               rectRoi.setStrokeWidth(3);
               rectRoi.setFillColor(new Color(0, 100, 255, 100));
               overlay.add(rectRoi);
            }
         }

         // Add instruction text at the center of the selection
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

         // Show selection bounds
         String boundsText = "Selection: rows " + minRow + "-" + maxRow
                 + ", cols " + minCol + "-" + maxCol;
         TextRoi boundsRoi = new TextRoi(textX - 100, textY, boundsText);
         boundsRoi.setStrokeColor(Color.YELLOW);
         overlay.add(boundsRoi);
      }

      // Draw red rectangle showing the current stage FOV position
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
