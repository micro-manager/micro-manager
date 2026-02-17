package org.micromanager.deskew;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.ndviewer.api.CanvasMouseListenerInterface;
import org.micromanager.ndviewer.api.NDViewerAcqInterface;
import org.micromanager.ndviewer.api.NDViewerDataSource;
import org.micromanager.ndviewer.api.OverlayerPlugin;
import org.micromanager.ndviewer.overlay.Overlay;
import org.micromanager.ndviewer.overlay.Roi;
import org.micromanager.ndviewer.overlay.TextRoi;

/**
 * Data source for the Deskew Explore mode NDViewer.
 * Implements the interfaces required for NDViewer to function,
 * including mouse handling and overlay rendering for tile selection.
 */
public class DeskewExploreDataSource implements NDViewerDataSource, NDViewerAcqInterface,
        CanvasMouseListenerInterface, OverlayerPlugin {

   private static final double ZOOM_FACTOR = 1.4;

   private final DeskewExploreManager manager_;
   private volatile MultiresNDTiffAPI storage_;
   private volatile boolean finished_ = false;

   // Mouse interaction state
   private Point selectionStart_ = null;   // First corner of selection (row, col)
   private Point selectionEnd_ = null;     // Second corner of selection (row, col)
   private Point dragStart_ = null;
   private boolean isRightDragging_ = false;
   private boolean isLeftDragging_ = false;
   private volatile boolean acquisitionInProgress_ = false;

   // Tile tracking
   private final Set<String> acquiredTiles_ = new HashSet<>();

   // Tile dimensions (set after first acquisition)
   private int tileWidth_ = -1;
   private int tileHeight_ = -1;

   public DeskewExploreDataSource(DeskewExploreManager manager) {
      manager_ = manager;
   }

   public void setStorage(MultiresNDTiffAPI storage) {
      storage_ = storage;
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
   public void setAcquisitionInProgress(boolean inProgress) {
      acquisitionInProgress_ = inProgress;
      if (!inProgress) {
         clearSelection();
      }
   }

   /**
    * Returns the list of selected tiles as (row, col) Points.
    * Tiles are returned in row-major order.
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

      for (int row = minRow; row <= maxRow; row++) {
         for (int col = minCol; col <= maxCol; col++) {
            tiles.add(new Point(row, col));
         }
      }
      return tiles;
   }

   public boolean isTileAcquired(int row, int col) {
      return acquiredTiles_.contains(row + "," + col);
   }

   // ===================== NDViewerDataSource interface =====================

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
      // Remove row and column axes from keys - viewer sees single plane
      return storage_.getAxesSet().stream()
              .map(axes -> {
                 HashMap<String, Object> copy = new HashMap<>(axes);
                 copy.remove(NDTiffStorage.ROW_AXIS);
                 copy.remove(NDTiffStorage.COL_AXIS);
                 return copy;
              })
              .collect(Collectors.toSet());
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

   // ===================== NDViewerAcqInterface interface =====================

   @Override
   public boolean isFinished() {
      // Always return true so NDViewer doesn't show "Finish the Acquisition?" dialog
      // Our close() method handles the save prompt
      return true;
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

   // ===================== CanvasMouseListenerInterface =====================

   @Override
   public void mousePressed(MouseEvent e) {
      if (acquisitionInProgress_) {
         return;
      }

      dragStart_ = e.getPoint();
      if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
         isRightDragging_ = false;
      } else if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
         isLeftDragging_ = false;
      }
   }

   @Override
   public void mouseReleased(MouseEvent e) {
      if (acquisitionInProgress_) {
         // Ignore clicks during acquisition
         dragStart_ = null;
         isRightDragging_ = false;
         isLeftDragging_ = false;
         return;
      }

      if (javax.swing.SwingUtilities.isRightMouseButton(e) && !isRightDragging_) {
         // Right-click without drag - start new selection
         if (tileWidth_ > 0 && tileHeight_ > 0) {
            Point tile = getTileFromDisplayCoords(e.getX(), e.getY());
            if (tile != null) {
               selectionStart_ = tile;
               selectionEnd_ = null;
               manager_.redrawOverlay();
            }
         }
      } else if (javax.swing.SwingUtilities.isLeftMouseButton(e) && !isLeftDragging_) {
         if (e.isControlDown()) {
            // Ctrl+left-click - move stage to clicked position
            Point2D.Double pixelPos = getFullResPixelCoords(e.getX(), e.getY());
            if (pixelPos != null) {
               manager_.moveStageToPixelPosition(pixelPos.x, pixelPos.y);
            }
         } else {
            // Left-click without drag - acquire selected tiles
            List<Point> selectedTiles = getSelectedTiles();
            if (!selectedTiles.isEmpty()) {
               manager_.acquireMultipleTiles(selectedTiles);
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
      if (acquisitionInProgress_ || dragStart_ == null) {
         return;
      }

      Point current = e.getPoint();
      int dx = dragStart_.x - current.x;
      int dy = dragStart_.y - current.y;

      if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
         // Right-drag pans the view
         // Only consider it dragging if moved more than a few pixels
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
      } else if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
         // Left-drag extends the selection
         if (selectionStart_ != null && tileWidth_ > 0 && tileHeight_ > 0) {
            // Only consider it dragging if moved more than a few pixels
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

      // Convert to tile indices
      int col = (int) Math.floor(fullResX / tileWidth_);
      int row = (int) Math.floor(fullResY / tileHeight_);

      return new Point(row, col);
   }

   // ===================== OverlayerPlugin interface =====================

   @Override
   public void drawOverlay(Overlay overlay, Point2D.Double displayImageSize,
                           double downsampleFactor, Graphics g,
                           HashMap<String, Object> axes, double magnification,
                           Point2D.Double viewOffset) {
      if (tileWidth_ <= 0 || tileHeight_ <= 0) {
         manager_.setOverlay(overlay);
         return;
      }

      // Show usage instructions when nothing is selected, not acquiring,
      // and no tiles have been acquired yet
      if (selectionStart_ == null && !acquisitionInProgress_ && acquiredTiles_.isEmpty()) {
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
         for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
               // Calculate tile position in full resolution coordinates
               double tilePixelX = col * tileWidth_;
               double tilePixelY = row * tileHeight_;

               // Convert to display/screen coordinates
               int dispX = (int) ((tilePixelX - viewOffset.x) * magnification);
               int dispY = (int) ((tilePixelY - viewOffset.y) * magnification);
               int dispW = (int) (tileWidth_ * magnification);
               int dispH = (int) (tileHeight_ * magnification);

               // Create rectangle ROI at the tile position
               Roi rectRoi = new Roi(dispX, dispY, dispW, dispH);
               rectRoi.setStrokeColor(new Color(0, 100, 255));
               rectRoi.setStrokeWidth(3);
               rectRoi.setFillColor(new Color(0, 100, 255, 100));
               overlay.add(rectRoi);
            }
         }

         // Add instruction text at the center of the selection
         double centerPixelX = (minCol + maxCol + 1) * tileWidth_ / 2.0;
         double centerPixelY = (minRow + maxRow + 1) * tileHeight_ / 2.0;
         int textX = (int) ((centerPixelX - viewOffset.x) * magnification);
         int textY = (int) ((centerPixelY - viewOffset.y) * magnification);

         String instructions;
         if (acquisitionInProgress_) {
            instructions = "Acquiring...";
         } else if (selectionEnd_ == null) {
            instructions = "Left-drag to extend, left-click to acquire";
         } else {
            instructions = "Left-click to acquire " + tileCount + " tile(s)";
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

      // Set the overlay on the viewer
      manager_.setOverlay(overlay);
   }
}
