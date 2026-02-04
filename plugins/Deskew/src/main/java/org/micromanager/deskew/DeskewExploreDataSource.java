package org.micromanager.deskew;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
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
   private Point highlightedTile_ = null;  // row, col of highlighted tile
   private Point dragStart_ = null;
   private boolean isDragging_ = false;

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
      highlightedTile_ = null; // Clear highlight after acquisition
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
      if (storage_ != null) {
         storage_.close();
         storage_ = null;
      }
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
      return finished_;
   }

   public void setFinished(boolean finished) {
      finished_ = finished;
   }

   @Override
   public void abort() {
      finished_ = true;
      manager_.stopExplore();
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
      if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
         dragStart_ = e.getPoint();
         isDragging_ = false;
      }
   }

   @Override
   public void mouseReleased(MouseEvent e) {
      if (javax.swing.SwingUtilities.isRightMouseButton(e) && !isDragging_) {
         // Right-click without drag - highlight tile for acquisition
         if (tileWidth_ > 0 && tileHeight_ > 0) {
            Point tile = getTileFromDisplayCoords(e.getX(), e.getY());
            if (tile != null && !isTileAcquired(tile.x, tile.y)) {
               highlightedTile_ = tile;
               manager_.redrawOverlay();
            }
         }
      } else if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
         // Left-click - acquire highlighted tile if any
         if (highlightedTile_ != null) {
            int row = highlightedTile_.x;
            int col = highlightedTile_.y;
            highlightedTile_ = null;
            manager_.acquireTile(row, col);
         }
      }

      dragStart_ = null;
      isDragging_ = false;
   }

   @Override
   public void mouseDragged(MouseEvent e) {
      if (dragStart_ != null && javax.swing.SwingUtilities.isRightMouseButton(e)) {
         Point current = e.getPoint();
         int dx = dragStart_.x - current.x;
         int dy = dragStart_.y - current.y;

         // Only consider it dragging if moved more than a few pixels
         if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
            isDragging_ = true;
            highlightedTile_ = null; // Clear highlight when dragging
         }

         if (isDragging_) {
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
   public void mouseClicked(MouseEvent e) {
      // Handled in mouseReleased
   }

   @Override
   public void mouseEntered(MouseEvent e) {
   }

   @Override
   public void mouseExited(MouseEvent e) {
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
      // Draw highlighted tile if any
      if (highlightedTile_ != null && tileWidth_ > 0 && tileHeight_ > 0) {
         int row = highlightedTile_.x;
         int col = highlightedTile_.y;
         String tileKey = row + "," + col;

         // Only show if not already acquired
         if (!acquiredTiles_.contains(tileKey)) {
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

            // Add text ROI for instructions
            String text = "Left-click to acquire";
            int textX = dispX + dispW / 4;
            int textY = dispY + dispH / 2;
            TextRoi textRoi = new TextRoi(textX, textY, text);
            textRoi.setStrokeColor(Color.WHITE);
            overlay.add(textRoi);

            // Add text ROI for coordinates
            String coordText = "Row: " + row + ", Col: " + col;
            TextRoi coordRoi = new TextRoi(textX, textY + 20, coordText);
            coordRoi.setStrokeColor(Color.YELLOW);
            overlay.add(coordRoi);
         }
      }

      // Set the overlay on the viewer
      manager_.setOverlay(overlay);
   }
}
