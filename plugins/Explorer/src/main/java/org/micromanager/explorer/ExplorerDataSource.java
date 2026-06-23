package org.micromanager.explorer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
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
import org.micromanager.tileddataviewer.overlay.ImageRoi;
import org.micromanager.tileddataviewer.overlay.OvalRoi;
import org.micromanager.tileddataviewer.overlay.Overlay;
import org.micromanager.tileddataviewer.overlay.PolygonRoi;
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

   /** Drawing tool for the Create-Positions ROI; NONE means draw mode is off. */
   public enum PositionTool { NONE, RECTANGLE, OVAL, POLYGON, FREEHAND }

   // Position-draw mode state. All coordinates are full-resolution canvas pixels.
   // Read by drawOverlay (paint thread) and written by the mouse handlers (EDT),
   // so every access is guarded by synchronized(this) (like the vessel state).
   private volatile PositionTool positionTool_ = PositionTool.NONE;
   // For RECTANGLE/OVAL: [start, end]. For POLYGON/FREEHAND: ordered vertices.
   private final ArrayList<Point2D.Double> roiPointsPx_ = new ArrayList<>();
   // True once the shape is complete (drag finished, or polygon closed).
   private boolean roiClosed_ = false;
   // Elastic point that follows the cursor while drawing (rubber-band rect/oval edge
   // or the in-progress polygon segment); null when not dragging / not hovering.
   private Point2D.Double rubberBandPx_ = null;
   // Distance (display pixels) within which a polygon click snaps to the first vertex to close.
   private static final int POLYGON_CLOSE_TOLERANCE = 8;
   // FOV rectangles (full-res pixels) of the most recently generated positions, drawn in
   // light grey until the operator leaves draw mode. Empty = none generated yet.
   private final ArrayList<Rectangle2D.Double> positionFovsPx_ = new ArrayList<>();

   // Refine-Z mode: when active, ctrl+left-click moves the stage to that tile (for manual focus)
   // instead of the normal move-stage behavior. Reference-point markers (full-res pixels) are
   // drawn until the operator leaves draw mode.
   private volatile boolean refineZActive_ = false;
   private final ArrayList<Point2D.Double> refineZMarkersPx_ = new ArrayList<>();

   /** A Refine-Z in-focus thumbnail to draw at a full-resolution pixel rectangle. */
   public static final class RefineZThumb {
      public final double fullResX;
      public final double fullResY;
      public final double fullResW;
      public final double fullResH;
      public final BufferedImage img;

      public RefineZThumb(double fullResX, double fullResY, double fullResW, double fullResH,
            BufferedImage img) {
         this.fullResX = fullResX;
         this.fullResY = fullResY;
         this.fullResW = fullResW;
         this.fullResH = fullResH;
         this.img = img;
      }
   }

   private final ArrayList<RefineZThumb> refineZThumbs_ = new ArrayList<>();

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

   // ===================== Create-Positions draw mode =====================

   /**
    * Selects the drawing tool. {@link PositionTool#NONE} turns draw mode off.
    * Switching tools discards any in-progress ROI.
    */
   public synchronized void setPositionTool(PositionTool tool) {
      positionTool_ = tool != null ? tool : PositionTool.NONE;
      roiPointsPx_.clear();
      roiClosed_ = false;
      rubberBandPx_ = null;
      positionFovsPx_.clear();
      // Refine-Z markers/thumbnails are NOT cleared here: they belong to the Refine-Z lifecycle
      // (managed by ExplorerManager), which preserves reference points across ROI-tool switches.
   }

   /**
    * True when a drawing tool is active AND Refine Z is not open. While the Refine Z window is
    * open, ROI drawing is suppressed so clicks drive refinement (Ctrl-click navigation, Set Z)
    * instead of redrawing the ROI; the existing ROI/preview stays put.
    */
   public boolean isPositionDrawMode() {
      return positionTool_ != PositionTool.NONE && !refineZActive_;
   }

   /** Discards the current ROI (and any generated positions) but keeps the active tool. */
   public synchronized void clearPositionRoi() {
      roiPointsPx_.clear();
      roiClosed_ = false;
      rubberBandPx_ = null;
      positionFovsPx_.clear();
      // Refine-Z markers/thumbnails are managed separately (see setPositionTool); not cleared here.
   }

   /**
    * Sets the FOV rectangles (full-resolution pixels) of the just-generated positions, to be
    * drawn in light grey until the operator leaves draw mode.
    */
   public synchronized void setGeneratedPositionFovs(java.util.List<Rectangle2D.Double> fovs) {
      positionFovsPx_.clear();
      if (fovs != null) {
         positionFovsPx_.addAll(fovs);
      }
   }

   /** Enables/disables manual Refine-Z mode (ctrl-click moves the stage to focus a tile). */
   public void setRefineZActive(boolean active) {
      refineZActive_ = active;
   }

   /** True when manual Refine-Z mode is active. */
   public boolean isRefineZActive() {
      return refineZActive_;
   }

   /** Sets the Refine-Z reference-point markers (full-res pixels) to draw on the overlay. */
   public synchronized void setRefineZMarkers(java.util.List<Point2D.Double> markers) {
      refineZMarkersPx_.clear();
      if (markers != null) {
         refineZMarkersPx_.addAll(markers);
      }
   }

   /** Sets the Refine-Z in-focus thumbnails (full-res rectangles) to draw on the overlay. */
   public synchronized void setRefineZThumbnails(java.util.List<RefineZThumb> thumbs) {
      refineZThumbs_.clear();
      if (thumbs != null) {
         refineZThumbs_.addAll(thumbs);
      }
   }

   /** True when a completed ROI is available for position generation. */
   public synchronized boolean hasPositionRoi() {
      if (!roiClosed_) {
         return false;
      }
      switch (positionTool_) {
         case RECTANGLE:
         case OVAL:
            return roiPointsPx_.size() == 2;
         case POLYGON:
         case FREEHAND:
            return roiPointsPx_.size() >= 3;
         default:
            return false;
      }
   }

   /**
    * Returns the completed ROI as an {@link Area} in full-resolution pixel space,
    * or null if no completed ROI exists.
    */
   public synchronized Area getPositionRoiAreaPx() {
      if (!hasPositionRoi()) {
         return null;
      }
      switch (positionTool_) {
         case RECTANGLE: {
            Point2D.Double a = roiPointsPx_.get(0);
            Point2D.Double b = roiPointsPx_.get(1);
            double x = Math.min(a.x, b.x);
            double y = Math.min(a.y, b.y);
            double w = Math.abs(a.x - b.x);
            double h = Math.abs(a.y - b.y);
            return new Area(new Rectangle2D.Double(x, y, w, h));
         }
         case OVAL: {
            Point2D.Double a = roiPointsPx_.get(0);
            Point2D.Double b = roiPointsPx_.get(1);
            double x = Math.min(a.x, b.x);
            double y = Math.min(a.y, b.y);
            double w = Math.abs(a.x - b.x);
            double h = Math.abs(a.y - b.y);
            return new Area(new Ellipse2D.Double(x, y, w, h));
         }
         case POLYGON:
         case FREEHAND: {
            Path2D.Double path = new Path2D.Double();
            Point2D.Double first = roiPointsPx_.get(0);
            path.moveTo(first.x, first.y);
            for (int i = 1; i < roiPointsPx_.size(); i++) {
               Point2D.Double p = roiPointsPx_.get(i);
               path.lineTo(p.x, p.y);
            }
            path.closePath();
            return new Area(path);
         }
         default:
            return null;
      }
   }

   /**
    * Returns the vessel boundary as an {@link Area} in full-resolution pixel space,
    * or null when no vessel is set. Simple vessels yield the rectangular outline;
    * multi-well plates yield the union of the individual well shapes.
    */
   public synchronized Area getVesselAreaPx() {
      VesselType vessel = vesselType_;
      if (vessel == null || vessel.isNone() || vesselWidthPx_ <= 0 || vesselHeightPx_ <= 0) {
         return null;
      }
      if (!vessel.isMultiWell()) {
         return new Area(new Rectangle2D.Double(
               vesselTlX_, vesselTlY_, vesselWidthPx_, vesselHeightPx_));
      }
      double pxPerUm = vesselWidthPx_ / vessel.getWidthUm();
      double wellW = vessel.getWellWidthUm()   * pxPerUm;
      double wellH = vessel.getWellHeightUm()  * pxPerUm;
      double spacX = vessel.getWellSpacingXUm() * pxPerUm;
      double spacY = vessel.getWellSpacingYUm() * pxPerUm;
      double firstWx = vessel.getFirstWellXUm() * pxPerUm;
      double firstWy = vessel.getFirstWellYUm() * pxPerUm;
      Area area = new Area();
      for (int r = 0; r < vessel.getWellRows(); r++) {
         for (int c = 0; c < vessel.getWellCols(); c++) {
            double cx = vesselTlX_ + firstWx + c * spacX;
            double cy = vesselTlY_ + firstWy + r * spacY;
            double wx = cx - wellW / 2.0;
            double wy = cy - wellH / 2.0;
            Area well = vessel.isWellsCircular()
                  ? new Area(new Ellipse2D.Double(wx, wy, wellW, wellH))
                  : new Area(new Rectangle2D.Double(wx, wy, wellW, wellH));
            area.add(well);
         }
      }
      return area;
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
      // Refine-Z manual navigation: a ctrl+left-click moves the stage, even while a drawing
      // tool is selected, so it must take precedence over ROI drawing. Handled on release.
      if (refineZActive_ && e.isControlDown()
            && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
         return;
      }
      if (isPositionDrawMode()) {
         // RECTANGLE/OVAL start a drag; FREEHAND starts collecting points.
         // POLYGON is click-driven and handled in mouseClicked.
         if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
            Point2D.Double px = getFullResPixelCoords(e.getX(), e.getY());
            if (px != null) {
               synchronized (this) {
                  if (positionTool_ == PositionTool.RECTANGLE
                        || positionTool_ == PositionTool.OVAL) {
                     roiPointsPx_.clear();
                     roiClosed_ = false;
                     roiPointsPx_.add(px);
                     rubberBandPx_ = px;
                  } else if (positionTool_ == PositionTool.FREEHAND) {
                     roiPointsPx_.clear();
                     roiClosed_ = false;
                     roiPointsPx_.add(px);
                  }
               }
               manager_.redrawOverlay();
            }
         }
         return;
      }
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
      // Refine-Z manual navigation: ctrl+left-click moves the stage to the clicked tile,
      // taking precedence over ROI drawing so the operator can focus there and click Set Z.
      if (refineZActive_ && e.isControlDown()
            && javax.swing.SwingUtilities.isLeftMouseButton(e) && !isLeftDragging_) {
         Point2D.Double px = getFullResPixelCoords(e.getX(), e.getY());
         if (px != null) {
            manager_.refineZManualMove(px.x, px.y);
         }
         dragStart_ = null;
         isLeftDragging_ = false;
         return;
      }
      if (isPositionDrawMode()) {
         if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
            Point2D.Double px = getFullResPixelCoords(e.getX(), e.getY());
            boolean changed = false;
            synchronized (this) {
               if (positionTool_ == PositionTool.RECTANGLE
                     || positionTool_ == PositionTool.OVAL) {
                  if (px != null && roiPointsPx_.size() == 1) {
                     roiPointsPx_.add(px);
                     roiClosed_ = true;
                     rubberBandPx_ = null;
                     changed = true;
                  }
               } else if (positionTool_ == PositionTool.FREEHAND) {
                  if (px != null) {
                     roiPointsPx_.add(px);
                  }
                  if (roiPointsPx_.size() >= 3) {
                     roiClosed_ = true;
                     changed = true;
                  }
               }
            }
            manager_.redrawOverlay();
            if (changed) {
               manager_.onPositionRoiChanged();
            }
         }
         dragStart_ = null;
         isLeftDragging_ = false;
         return;
      }
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
               // In refine-Z mode this navigates to a tile so the operator can focus it.
               manager_.moveStageToPixelPosition(pixelPos.x, pixelPos.y);
            }
         } else if (!readOnly_ && !refineZActive_) {
            // Plain left-click acquires selected tiles, but not while refining Z.
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
      if (!isPositionDrawMode() || positionTool_ != PositionTool.POLYGON
            || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
         return;
      }
      Point2D.Double px = getFullResPixelCoords(e.getX(), e.getY());
      if (px == null) {
         return;
      }
      boolean closed = false;
      synchronized (this) {
         if (roiClosed_) {
            // Start a fresh polygon when clicking after one was completed.
            roiPointsPx_.clear();
            roiClosed_ = false;
         }
         double mag = manager_.getMagnification();
         boolean closeRequested = e.getClickCount() >= 2;
         // Also close when clicking near the first vertex.
         if (!closeRequested && roiPointsPx_.size() >= 3) {
            Point2D.Double first = roiPointsPx_.get(0);
            double dispDx = (px.x - first.x) * mag;
            double dispDy = (px.y - first.y) * mag;
            if (Math.hypot(dispDx, dispDy) <= POLYGON_CLOSE_TOLERANCE) {
               closeRequested = true;
            }
         }
         if (closeRequested) {
            if (roiPointsPx_.size() >= 3) {
               roiClosed_ = true;
               rubberBandPx_ = null;
               closed = true;
            }
         } else {
            roiPointsPx_.add(px);
         }
      }
      manager_.redrawOverlay();
      if (closed) {
         manager_.onPositionRoiChanged();
      }
   }

   @Override
   public void mouseDragged(MouseEvent e) {
      // Don't draw while ctrl-navigating during Refine Z.
      if (refineZActive_ && e.isControlDown()) {
         return;
      }
      if (isPositionDrawMode()) {
         if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
            Point2D.Double px = getFullResPixelCoords(e.getX(), e.getY());
            if (px != null) {
               synchronized (this) {
                  if (positionTool_ == PositionTool.RECTANGLE
                        || positionTool_ == PositionTool.OVAL) {
                     rubberBandPx_ = px;
                  } else if (positionTool_ == PositionTool.FREEHAND && !roiClosed_) {
                     roiPointsPx_.add(px);
                  }
               }
               manager_.redrawOverlay();
            }
         }
         return;
      }
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
      if (isPositionDrawMode() && positionTool_ == PositionTool.POLYGON) {
         Point2D.Double px = getFullResPixelCoords(e.getX(), e.getY());
         boolean redraw = false;
         synchronized (this) {
            if (!roiClosed_ && !roiPointsPx_.isEmpty()) {
               rubberBandPx_ = px;
               redraw = true;
            }
         }
         if (redraw) {
            manager_.redrawOverlay();
         }
      }
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

      // Green vessel outline, drawn over the tiles/stage-FOV. The generated-position FOVs,
      // Refine-Z overlays, and the in-progress ROI are added afterward so they appear on top.
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

      // Generated-position FOV rectangles (light grey), drawn under the ROI outline.
      addGeneratedPositionFovs(overlay, magnification, viewOffset);

      // Refine-Z in-focus thumbnails (under the markers).
      addRefineZThumbnails(overlay, magnification, viewOffset);

      // Refine-Z reference-point markers (green filled squares), drawn over the FOVs/thumbnails.
      addRefineZMarkers(overlay, magnification, viewOffset);

      // Create-Positions ROI — added last so it draws on top of everything (including the
      // vessel) and never hides the vessel outline. Added as Roi objects (not drawn straight
      // to Graphics) so it persists across repaints, like every other overlay element.
      addPositionRoi(overlay, magnification, viewOffset);
   }

   /** Adds the Refine-Z reference-point markers (small green filled squares) to the overlay. */
   private void addRefineZMarkers(Overlay overlay, double magnification,
                                  Point2D.Double viewOffset) {
      ArrayList<Point2D.Double> markers;
      synchronized (this) {
         if (refineZMarkersPx_.isEmpty()) {
            return;
         }
         markers = new ArrayList<>(refineZMarkersPx_);
      }
      Color green = new Color(60, 200, 90);
      int half = 5;
      for (Point2D.Double p : markers) {
         int x = (int) ((p.x - viewOffset.x) * magnification);
         int y = (int) ((p.y - viewOffset.y) * magnification);
         Roi marker = new Roi(x - half, y - half, 2 * half, 2 * half);
         marker.setStrokeColor(green);
         marker.setFillColor(green);
         overlay.add(marker);
      }
   }

   /** Adds the Refine-Z in-focus thumbnails to the overlay as ImageRois. */
   private void addRefineZThumbnails(Overlay overlay, double magnification,
                                     Point2D.Double viewOffset) {
      ArrayList<RefineZThumb> thumbs;
      synchronized (this) {
         if (refineZThumbs_.isEmpty()) {
            return;
         }
         thumbs = new ArrayList<>(refineZThumbs_);
      }
      for (RefineZThumb t : thumbs) {
         if (t.img == null) {
            continue;
         }
         int x = (int) ((t.fullResX - viewOffset.x) * magnification);
         int y = (int) ((t.fullResY - viewOffset.y) * magnification);
         int w = (int) (t.fullResW * magnification);
         int h = (int) (t.fullResH * magnification);
         overlay.add(new ImageRoi(x, y, Math.max(1, w), Math.max(1, h), t.img));
      }
   }

   /** Adds the most recently generated positions' FOV rectangles to the overlay (light grey). */
   private void addGeneratedPositionFovs(Overlay overlay, double magnification,
                                         Point2D.Double viewOffset) {
      ArrayList<Rectangle2D.Double> fovs;
      synchronized (this) {
         if (positionFovsPx_.isEmpty()) {
            return;
         }
         fovs = new ArrayList<>(positionFovsPx_);
      }
      Color grey = new Color(200, 200, 200);
      for (Rectangle2D.Double r : fovs) {
         int x = (int) ((r.x - viewOffset.x) * magnification);
         int y = (int) ((r.y - viewOffset.y) * magnification);
         int w = (int) (r.width * magnification);
         int h = (int) (r.height * magnification);
         Roi roi = new Roi(x, y, Math.max(1, w), Math.max(1, h));
         roi.setStrokeColor(grey);
         roi.setStrokeWidth(1);
         overlay.add(roi);
      }
   }

   /**
    * Adds the in-progress / completed Create-Positions ROI to the overlay, converting the
    * stored full-resolution pixel coordinates to display coordinates. Snapshots the ROI state
    * under the instance lock first.
    */
   private void addPositionRoi(Overlay overlay, double magnification, Point2D.Double viewOffset) {
      PositionTool tool;
      boolean closed;
      Point2D.Double rubber;
      ArrayList<Point2D.Double> pts;
      synchronized (this) {
         tool = positionTool_;
         if (tool == PositionTool.NONE || roiPointsPx_.isEmpty()) {
            return;
         }
         closed = roiClosed_;
         rubber = rubberBandPx_;
         pts = new ArrayList<>(roiPointsPx_);
      }

      final Color roiColor = Color.MAGENTA;
      if (tool == PositionTool.RECTANGLE || tool == PositionTool.OVAL) {
         Point2D.Double a = pts.get(0);
         Point2D.Double b = closed && pts.size() == 2 ? pts.get(1) : rubber;
         if (b != null) {
            int x = (int) ((Math.min(a.x, b.x) - viewOffset.x) * magnification);
            int y = (int) ((Math.min(a.y, b.y) - viewOffset.y) * magnification);
            int w = (int) (Math.abs(a.x - b.x) * magnification);
            int h = (int) (Math.abs(a.y - b.y) * magnification);
            Roi roi = tool == PositionTool.RECTANGLE
                  ? new Roi(x, y, Math.max(1, w), Math.max(1, h))
                  : new OvalRoi(x, y, Math.max(1, w), Math.max(1, h));
            roi.setStrokeColor(roiColor);
            roi.setStrokeWidth(2);
            overlay.add(roi);
         }
      } else {
         // POLYGON / FREEHAND
         int n = pts.size();
         int extra = (!closed && rubber != null) ? 1 : 0;
         int[] xs = new int[n + extra];
         int[] ys = new int[n + extra];
         for (int i = 0; i < n; i++) {
            xs[i] = (int) ((pts.get(i).x - viewOffset.x) * magnification);
            ys[i] = (int) ((pts.get(i).y - viewOffset.y) * magnification);
         }
         if (extra == 1) {
            xs[n] = (int) ((rubber.x - viewOffset.x) * magnification);
            ys[n] = (int) ((rubber.y - viewOffset.y) * magnification);
         }
         PolygonRoi poly = new PolygonRoi(xs, ys, closed);
         poly.setStrokeColor(roiColor);
         poly.setStrokeWidth(2);
         overlay.add(poly);
         // Mark the first vertex so the user can see where to close an open polygon.
         if (!closed && tool == PositionTool.POLYGON && xs.length > 0) {
            Roi marker = new Roi(xs[0] - 3, ys[0] - 3, 7, 7);
            marker.setStrokeColor(roiColor);
            marker.setFillColor(roiColor);
            overlay.add(marker);
         }
      }
   }
}
