package org.micromanager.stitch;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.MultiStagePosition;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.tileddataprovider.MMDataProviderAdapter;

/**
 * Extends {@link MMDataProviderAdapter} to add {@code "row"} and {@code "column"}
 * axes derived from stage positions, making a plain MM tiled DataProvider consumable
 * by ExportTiles.
 *
 * <h2>Row/column computation</h2>
 * <p>Stage positions are read from the {@link SummaryMetadata#getStagePositionList()}.
 * If every position carries non-zero grid coordinates (set by TileCreatorDlg via
 * {@link MultiStagePosition#setGridCoordinates}), those are used directly.</p>
 *
 * <p>Otherwise, the XY stage positions (in µm) are converted to grid indices by
 * dividing by the effective tile step — image width (or height) in pixels multiplied
 * by the pixel size in µm — and rounding to the nearest integer. Indices are then
 * shifted so the minimum row and column are 0.</p>
 *
 * <h2>Axes map</h2>
 * <p>The returned axes maps include all standard axes plus {@code "row"} and
 * {@code "column"} (Integer values).  The {@code "position"} axis is omitted from the
 * output axes map; row/col replace it.</p>
 */
public class StitchDataProviderAdapter extends MMDataProviderAdapter {

   /** Row/col assignment for a single position index. */
   private static final class GridCell {
      final int row;
      final int col;

      GridCell(int row, int col) {
         this.row = row;
         this.col = col;
      }
   }

   /**
    * Affine-driven orientation model (the "Correct camera orientation" ON path).
    *
    * <p>{@code m} is the stage-delta -> canvas-pixel-delta matrix
    * {@code M = O * A^-1} (row-major {m00, m01, m10, m11}) from
    * {@code ImageTransformUtils.stageToCanvasMatrix}; {@code refX}/{@code refY} is a
    * fixed reference stage point (deltas are taken relative to it). When present, tile
    * placement AND overlap are computed by mapping stage positions through {@code M},
    * guaranteeing consistency with the pixel transform applied to the tiles. When null,
    * the adapter reproduces the legacy raw-sign placement (checkbox OFF) byte-for-byte.</p>
    */
   static final class OrientationModel {
      final double[] m;
      final double refX;
      final double refY;

      OrientationModel(double[] m, double refX, double refY) {
         this.m = m;
         this.refX = refX;
         this.refY = refY;
      }

      /** Map a stage (X,Y) to a canvas-pixel (X,Y) relative to the reference point. */
      double[] toCanvas(double stageX, double stageY) {
         double dx = stageX - refX;
         double dy = stageY - refY;
         return new double[]{m[0] * dx + m[1] * dy, m[2] * dx + m[3] * dy};
      }
   }

   // Serializes reads from the underlying source storage. The Stitch compositing step runs
   // on multiple worker threads, but some MM source storages (notably MultipageTiff, whose
   // MultipageTiffReader shares a single FileChannel) are NOT safe for concurrent reads and
   // throw ClosedChannelException/AsynchronousCloseException. Guarding every source read
   // with this lock keeps reads serial while the expensive per-pixel blending stays parallel.
   private final Object sourceReadLock_ = new Object();

   // position index → grid cell
   private final Map<Integer, GridCell> positionGrid_;
   // (row << 32 | col) → position index, for O(1) reverse lookup in stripRowCol()
   private final Map<Long, Integer> rowColToPosition_;
   private final int imageWidth_;
   private final int imageHeight_;
   private final int overlapX_;
   private final int overlapY_;
   // Non-null when affine-driven orientation is active (checkbox ON).
   private final OrientationModel orientation_;

   /**
    * Construct the adapter with legacy (raw-sign) placement.
    *
    * @param source the MM DataProvider (must have XPositionUm/YPositionUm per-image
    *               metadata, or stage positions with grid coordinates in summary metadata)
    * @throws IllegalArgumentException if grid coordinates cannot be determined
    */
   public StitchDataProviderAdapter(DataProvider source) {
      this(source, null);
   }

   /**
    * Construct the adapter.
    *
    * @param source      the MM DataProvider
    * @param orientation affine-driven orientation model for consistent placement, or null
    *                    to use the legacy raw-sign placement (checkbox OFF)
    * @throws IllegalArgumentException if grid coordinates cannot be determined
    */
   public StitchDataProviderAdapter(DataProvider source, OrientationModel orientation) {
      super(source);
      orientation_ = orientation;
      int[] dims = probeImageDims(source);
      imageWidth_ = dims[0];
      imageHeight_ = dims[1];
      positionGrid_ = buildPositionGrid(source, imageWidth_, imageHeight_, orientation_);
      // Build reverse lookup for O(1) stripRowCol()
      Map<Long, Integer> reverseMap = new HashMap<>();
      for (Map.Entry<Integer, GridCell> entry : positionGrid_.entrySet()) {
         GridCell cell = entry.getValue();
         reverseMap.put(((long) cell.row << 32) | (cell.col & 0xFFFFFFFFL), entry.getKey());
      }
      rowColToPosition_ = reverseMap;
      int[] overlap = computeOverlap(source, positionGrid_, imageWidth_, imageHeight_,
            orientation_);
      overlapX_ = overlap[0];
      overlapY_ = overlap[1];
   }

   // -------------------------------------------------------------------------
   // Public helpers for callers
   // -------------------------------------------------------------------------

   /** Return the computed horizontal tile overlap in pixels (0 if undetermined). */
   public int getOverlapX() {
      return overlapX_;
   }

   /** Return the computed vertical tile overlap in pixels (0 if undetermined). */
   public int getOverlapY() {
      return overlapY_;
   }

   /** Return the number of tiles in the grid. */
   public int getNumTiles() {
      return positionGrid_.size();
   }

   /** Return the full stitched canvas width in pixels. */
   public int getCanvasWidth() {
      int maxCol = 0;
      for (GridCell cell : positionGrid_.values()) {
         if (cell.col > maxCol) {
            maxCol = cell.col;
         }
      }
      int stepX = imageWidth_ - overlapX_;
      return stepX * maxCol + imageWidth_;
   }

   /** Return the full stitched canvas height in pixels. */
   public int getCanvasHeight() {
      int maxRow = 0;
      for (GridCell cell : positionGrid_.values()) {
         if (cell.row > maxRow) {
            maxRow = cell.row;
         }
      }
      int stepY = imageHeight_ - overlapY_;
      return stepY * maxRow + imageHeight_;
   }

   /** Return the maximum column index in the tile grid (0-based). */
   public int getMaxCol() {
      int max = 0;
      for (GridCell cell : positionGrid_.values()) {
         if (cell.col > max) {
            max = cell.col;
         }
      }
      return max;
   }

   /** Return the maximum row index in the tile grid (0-based). */
   public int getMaxRow() {
      int max = 0;
      for (GridCell cell : positionGrid_.values()) {
         if (cell.row > max) {
            max = cell.row;
         }
      }
      return max;
   }

   // -------------------------------------------------------------------------
   // Overrides: inject row/col into every axes map
   // -------------------------------------------------------------------------

   @Override
   public Set<HashMap<String, Object>> getAxesSet() {
      final Set<HashMap<String, Object>> base;
      // Guard in case a source storage does file I/O when enumerating coords.
      synchronized (sourceReadLock_) {
         base = super.getAxesSet();
      }
      Set<HashMap<String, Object>> result = new HashSet<>();
      for (HashMap<String, Object> axes : base) {
         result.add(addRowCol(axes));
      }
      return result;
   }

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes) {
      return getImage(axes, 0);
   }

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes, int resolutionLevel) {
      if (resolutionLevel != 0) {
         return null;
      }
      // Strip row/col before delegating to parent (parent doesn't know them).
      // If the (row,col) pair is not in the grid, return null rather than letting
      // the parent silently fall back to position 0 and return the wrong tile.
      HashMap<String, Object> stripped = stripRowCol(axes);
      if (stripped == null) {
         return null;
      }
      // Serialize the actual source read (see sourceReadLock_).
      synchronized (sourceReadLock_) {
         return super.getImage(stripped, 0);
      }
   }

   @Override
   public boolean hasImage(HashMap<String, Object> axes) {
      HashMap<String, Object> stripped = stripRowCol(axes);
      if (stripped == null) {
         return false;
      }
      synchronized (sourceReadLock_) {
         return super.hasImage(stripped);
      }
   }

   @Override
   public TaggedImage getDisplayImage(HashMap<String, Object> axes, int resolutionLevel,
                                      int xOffset, int yOffset, int width, int height) {
      return getImage(axes, resolutionLevel);
   }

   @Override
   public JSONObject getSummaryMetadata() {
      JSONObject base = super.getSummaryMetadata();
      if (base == null) {
         base = new JSONObject();
      }
      try {
         base.put("Width", imageWidth_);
         base.put("Height", imageHeight_);
         base.put("GridPixelOverlapX", overlapX_);
         base.put("GridPixelOverlapY", overlapY_);
      } catch (JSONException e) {
         // ignore
      }
      return base;
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   /**
    * Add {@code "row"} and {@code "column"} to an axes map based on the
    * {@code "position"} value, then remove {@code "position"}.
    */
   private HashMap<String, Object> addRowCol(HashMap<String, Object> axes) {
      HashMap<String, Object> result = new HashMap<>(axes);
      Object posVal = result.remove(Coords.STAGE_POSITION);
      int posIdx = posVal instanceof Integer ? (Integer) posVal : 0;
      GridCell cell = positionGrid_.get(posIdx);
      if (cell != null) {
         result.put("row", cell.row);
         result.put("column", cell.col);
      }
      return result;
   }

   /**
    * Remove {@code "row"} and {@code "column"} from an axes map, restore
    * {@code "position"} from the grid lookup (reverse of addRowCol).
    *
    * <p>Returns {@code null} if the (row, col) pair is not found in the grid,
    * so callers can distinguish "unknown tile" from position 0.</p>
    */
   private HashMap<String, Object> stripRowCol(HashMap<String, Object> axes) {
      HashMap<String, Object> result = new HashMap<>(axes);
      Object rowVal = result.remove("row");
      Object colVal = result.remove("column");
      if (rowVal instanceof Integer && colVal instanceof Integer) {
         int row = (Integer) rowVal;
         int col = (Integer) colVal;
         long key = ((long) row << 32) | (col & 0xFFFFFFFFL);
         Integer posIdx = rowColToPosition_.get(key);
         if (posIdx == null) {
            return null;  // unknown (row,col) — do not fall through to position 0
         }
         result.put(Coords.STAGE_POSITION, posIdx);
      }
      return result;
   }

   // -------------------------------------------------------------------------
   // Grid computation
   // -------------------------------------------------------------------------

   /**
    * Build the position-index → row/col map.
    *
    * <p>Tries (in order):
    * <ol>
    *   <li>Grid coordinates on MultiStagePosition (set by TileCreatorDlg)</li>
    *   <li>XY stage coordinates from SummaryMetadata stage position list</li>
    *   <li>Per-image XPositionUm/YPositionUm metadata</li>
    * </ol>
    */
   private static Map<Integer, GridCell> buildPositionGrid(DataProvider source,
                                                            int imageWidth, int imageHeight,
                                                            OrientationModel orientation) {
      SummaryMetadata summary = source.getSummaryMetadata();

      // --- Attempt 0: affine-driven placement (checkbox ON) ---
      // Supersedes the label/raw-sign attempts so placement is derived from the SAME
      // affine that drives the per-tile pixel transform, and can never diverge from it.
      // The orientation matrix M already maps stage microns to canvas pixels, so no
      // separate pixel size is needed here.
      if (orientation != null && imageWidth > 0 && imageHeight > 0) {
         Map<Integer, GridCell> grid0 = tryGridFromOrientation(
               source, imageWidth, imageHeight, orientation);
         if (grid0 != null) {
            return grid0;
         }
      }

      // --- Attempt 1: MultiStagePosition grid coordinates ---
      if (summary != null) {
         List<MultiStagePosition> positions = summary.getStagePositionList();
         if (positions != null && !positions.isEmpty()) {
            Map<Integer, GridCell> grid = tryGridFromMultiStagePositions(positions);
            if (grid != null) {
               return grid;
            }
            // --- Attempt 2: XY coordinates from summary stage position list ---
            double pixelSizeUm = getPixelSizeUm(source);
            if (pixelSizeUm > 0) {
               Map<Integer, GridCell> grid2 = tryGridFromXYPositions(
                     positions, imageWidth, imageHeight, pixelSizeUm);
               if (grid2 != null) {
                  return grid2;
               }
            }
         }
      }

      // --- Attempt 3: Per-image XY metadata ---
      double pixelSizeUm = getPixelSizeUm(source);
      if (pixelSizeUm > 0 && imageWidth > 0 && imageHeight > 0) {
         Map<Integer, GridCell> grid = tryGridFromImageMetadata(
               source, imageWidth, imageHeight, pixelSizeUm);
         if (grid != null) {
            return grid;
         }
      }

      throw new IllegalArgumentException(
            "Cannot determine tile grid: no grid coordinates, stage positions, "
                  + "or per-image XY metadata found in DataProvider.");
   }

   /**
    * Try to build the grid from MultiStagePosition.getGridRow/Column().
    * Returns null if not all positions have been assigned grid coordinates
    * (i.e. all are at (0,0), which is the default when not explicitly set).
    */
   private static Map<Integer, GridCell> tryGridFromMultiStagePositions(
         List<MultiStagePosition> positions) {
      boolean anyNonZero = false;
      Map<Integer, GridCell> grid = new HashMap<>();
      for (int i = 0; i < positions.size(); i++) {
         MultiStagePosition msp = positions.get(i);
         int row = msp.getGridRow();
         int col = msp.getGridColumn();
         if (row != 0 || col != 0) {
            anyNonZero = true;
         }
         grid.put(i, new GridCell(row, col));
      }
      // Accept if any position has explicit non-zero grid coords, OR if there is only
      // one position (single-tile dataset: (0,0) is correct, not the unset default).
      if (!anyNonZero && positions.size() != 1) {
         return null;
      }
      Map<Integer, GridCell> shifted = shiftToZero(grid);
      // Validate that gridCol correlates with X and gridRow correlates with Y.
      // If they are spatially transposed (e.g. gridCol tracks Y and gridRow tracks X,
      // as happens when the acquisition snake axis labelling differs from spatial axes),
      // fall back to XY-based grid building instead.
      return gridConsistentWithXY(shifted, positions) ? shifted : null;
   }

   /**
    * Returns true if the grid assignment is spatially consistent with the XY positions:
    * gridCol should be more strongly correlated with X than with Y, and gridRow with Y
    * than with X.  Uses the absolute Pearson correlation coefficient as the metric.
    * Returns true when there are fewer than 2 positions (nothing to validate).
    */
   private static boolean gridConsistentWithXY(Map<Integer, GridCell> grid,
                                                List<MultiStagePosition> positions) {
      int n = positions.size();
      if (n < 2) {
         return true;
      }
      double[] rows = new double[n];
      double[] cols = new double[n];
      double[] xs   = new double[n];
      double[] ys   = new double[n];
      for (int i = 0; i < n; i++) {
         GridCell cell = grid.get(i);
         rows[i] = cell.row;
         cols[i] = cell.col;
         xs[i]   = positions.get(i).getX();
         ys[i]   = positions.get(i).getY();
      }
      double corrColX = Math.abs(pearson(cols, xs));
      double corrColY = Math.abs(pearson(cols, ys));
      double corrRowX = Math.abs(pearson(rows, xs));
      double corrRowY = Math.abs(pearson(rows, ys));
      // Col should track X more than Y; row should track Y more than X.
      return corrColX >= corrColY && corrRowY >= corrRowX;
   }

   /** Pearson correlation coefficient between two equal-length arrays. */
   private static double pearson(double[] a, double[] b) {
      int n = a.length;
      double meanA = 0;
      double meanB = 0;
      for (int i = 0; i < n; i++) {
         meanA += a[i];
         meanB += b[i];
      }
      meanA /= n;
      meanB /= n;
      double num = 0;
      double varA = 0;
      double varB = 0;
      for (int i = 0; i < n; i++) {
         double da = a[i] - meanA;
         double db = b[i] - meanB;
         num  += da * db;
         varA += da * da;
         varB += db * db;
      }
      if (varA == 0 || varB == 0) {
         return 0;
      }
      return num / Math.sqrt(varA * varB);
   }

   /**
    * Build the grid from XY stage positions in the summary metadata position list.
    * Divides XY coordinates by (imageWidth * pixelSize) to get column/row indices.
    */
   private static Map<Integer, GridCell> tryGridFromXYPositions(
         List<MultiStagePosition> positions, int imageWidth, int imageHeight,
         double pixelSizeUm) {
      double stepX = imageWidth * pixelSizeUm;
      double stepY = imageHeight * pixelSizeUm;
      Map<Integer, GridCell> grid = new HashMap<>();
      for (int i = 0; i < positions.size(); i++) {
         MultiStagePosition msp = positions.get(i);
         double x = msp.getX();
         double y = msp.getY();
         int col = (int) Math.round(x / stepX);
         int row = (int) Math.round(y / stepY);
         grid.put(i, new GridCell(row, col));
      }
      return shiftToZero(grid);
   }

   /**
    * Build the grid from per-image XPositionUm/YPositionUm in image metadata.
    * Groups images by position index and uses the XY of the first image at each position.
    */
   private static Map<Integer, GridCell> tryGridFromImageMetadata(
         DataProvider source, int imageWidth, int imageHeight, double pixelSizeUm) {
      double stepX = imageWidth * pixelSizeUm;
      double stepY = imageHeight * pixelSizeUm;
      // Collect one XY sample per position index
      Map<Integer, double[]> xyByPosition = new HashMap<>();
      for (Coords coords : source.getUnorderedImageCoords()) {
         int posIdx = coords.hasAxis(Coords.STAGE_POSITION)
               ? coords.getStagePosition() : 0;
         if (xyByPosition.containsKey(posIdx)) {
            continue;
         }
         try {
            Image img = source.getImage(coords);
            if (img == null) {
               continue;
            }
            Metadata meta = img.getMetadata();
            Double xUm = meta.getXPositionUm();
            Double yUm = meta.getYPositionUm();
            if (xUm != null && yUm != null) {
               xyByPosition.put(posIdx, new double[]{xUm, yUm});
            }
         } catch (IOException e) {
            // skip this position
         }
      }
      if (xyByPosition.isEmpty()) {
         return null;
      }
      Map<Integer, GridCell> grid = new HashMap<>();
      for (Map.Entry<Integer, double[]> entry : xyByPosition.entrySet()) {
         double x = entry.getValue()[0];
         double y = entry.getValue()[1];
         int col = (int) Math.round(x / stepX);
         int row = (int) Math.round(y / stepY);
         grid.put(entry.getKey(), new GridCell(row, col));
      }
      return shiftToZero(grid);
   }

   /**
    * Build the grid by mapping each position's stage XY through the orientation matrix
    * {@code M} into canvas-pixel space, then quantizing to the (pre-rotation) tile-step
    * grid. This is the single authority that keeps placement consistent with the pixel
    * transform. Returns null if no stage positions can be found.
    */
   private static Map<Integer, GridCell> tryGridFromOrientation(
         DataProvider source, int imageWidth, int imageHeight,
         OrientationModel orientation) {
      Map<Integer, double[]> xyByPos = collectStageXY(source);
      if (xyByPos.isEmpty()) {
         return null;
      }
      // Map every position to canvas-pixel space, then quantize each axis to integer
      // grid indices using the ACTUAL measured tile pitch (not imageWidth).
      //
      // The tile pitch in canvas pixels is imageWidth - overlap, which is strictly
      // smaller than imageWidth whenever tiles overlap (the normal case). Quantizing
      // with imageWidth therefore drifts: over many columns, adjacent positions can
      // round to the same index (collisions that drop tiles AND poison the overlap
      // estimate, collapsing the stitch into a single column). Measuring the pitch
      // from the data makes the indices exact regardless of overlap.
      List<Integer> posList = new ArrayList<>(xyByPos.keySet());
      double[] canvasX = new double[posList.size()];
      double[] canvasY = new double[posList.size()];
      for (int i = 0; i < posList.size(); i++) {
         double[] xy = xyByPos.get(posList.get(i));
         double[] canvas = orientation.toCanvas(xy[0], xy[1]);
         canvasX[i] = canvas[0];
         canvasY[i] = canvas[1];
      }
      double pitchX = estimateAxisPitch(canvasX, imageWidth);
      double pitchY = estimateAxisPitch(canvasY, imageHeight);
      Map<Integer, GridCell> grid = new HashMap<>();
      for (int i = 0; i < posList.size(); i++) {
         int col = (int) Math.round(canvasX[i] / pitchX);
         int row = (int) Math.round(canvasY[i] / pitchY);
         grid.put(posList.get(i), new GridCell(row, col));
      }
      return shiftToZero(grid);
   }

   /**
    * Estimate the regular tile pitch (spacing between adjacent tile origins) along a
    * single axis from the projected per-position coordinates.
    *
    * <p>Sorts the distinct coordinate values, takes the gaps between consecutive ones,
    * and returns the median of the "small" gaps (those within 1.5x of the smallest gap),
    * which corresponds to the spacing between immediately-adjacent tiles. Larger gaps
    * (row/column wrap, or missing tiles in an operator-terminated acquisition) are
    * ignored. Falls back to {@code fallback} when fewer than two distinct values exist.</p>
    *
    * @param coords   per-position coordinate along this axis (canvas pixels)
    * @param fallback pitch to use when it cannot be measured (e.g. single column)
    * @return the estimated pitch in the same units as {@code coords}; never <= 0
    */
   private static double estimateAxisPitch(double[] coords, double fallback) {
      if (coords.length < 2 || fallback <= 0) {
         return fallback > 0 ? fallback : 1.0;
      }
      double[] sorted = coords.clone();
      Arrays.sort(sorted);
      // Collect gaps between consecutive sorted coordinates that represent a genuine
      // step to an ADJACENT tile, ignoring tiny gaps from stage jitter between positions
      // in the same row/column. The real tile pitch is imageWidth - overlap, i.e. close
      // to `fallback` (the image dimension); jitter is only a few pixels. Requiring a gap
      // to exceed a fraction of `fallback` cleanly separates the two. (A 1px threshold,
      // as used previously, mistook intra-column jitter for the pitch and produced wildly
      // inflated column indices -- e.g. col 1130 for a 3-column grid.)
      double minRealStep = fallback * 0.3;
      List<Double> gaps = new ArrayList<>();
      for (int i = 1; i < sorted.length; i++) {
         double g = sorted[i] - sorted[i - 1];
         if (g > minRealStep) {
            gaps.add(g);
         }
      }
      if (gaps.isEmpty()) {
         // No adjacent-tile step found (e.g. a single row/column on this axis); the image
         // dimension is the best available estimate of the pitch.
         return fallback;
      }
      // Among real steps, the adjacent-tile pitch is the SMALLEST (larger gaps are
      // row/column wraps or skips over missing tiles). Use the median of the smallest
      // cluster (within 1.5x of the minimum) for robustness against a stray small gap.
      Collections.sort(gaps);
      double smallest = gaps.get(0);
      List<Double> adjacent = new ArrayList<>();
      for (double g : gaps) {
         if (g <= smallest * 1.5) {
            adjacent.add(g);
         }
      }
      double pitch = adjacent.get(adjacent.size() / 2);
      return pitch > 0 ? pitch : fallback;
   }

   /**
    * Returns the median of a non-empty list of values. Sorts a copy, so the caller's
    * list is left unmodified. For an even count, returns the lower of the two central
    * values (the tile step is regular, so interpolation is unnecessary).
    */
   private static double median(List<Double> values) {
      List<Double> sorted = new ArrayList<>(values);
      Collections.sort(sorted);
      return sorted.get((sorted.size() - 1) / 2);
   }

   /**
    * Collect one stage (X,Y) sample per position index, preferring per-image
    * XPositionUm/YPositionUm and falling back to the summary stage position list.
    */
   private static Map<Integer, double[]> collectStageXY(DataProvider source) {
      Map<Integer, double[]> xyByPos = new HashMap<>();
      for (Coords coords : source.getUnorderedImageCoords()) {
         int posIdx = coords.hasAxis(Coords.STAGE_POSITION)
               ? coords.getStagePosition() : 0;
         if (xyByPos.containsKey(posIdx)) {
            continue;
         }
         try {
            Image img = source.getImage(coords);
            if (img == null) {
               continue;
            }
            Double xUm = img.getMetadata().getXPositionUm();
            Double yUm = img.getMetadata().getYPositionUm();
            if (xUm != null && yUm != null) {
               xyByPos.put(posIdx, new double[]{xUm, yUm});
            }
         } catch (IOException e) {
            // skip this position
         }
      }
      if (xyByPos.isEmpty()) {
         SummaryMetadata summary = source.getSummaryMetadata();
         if (summary != null) {
            List<MultiStagePosition> positions = summary.getStagePositionList();
            if (positions != null) {
               for (int i = 0; i < positions.size(); i++) {
                  MultiStagePosition msp = positions.get(i);
                  xyByPos.put(i, new double[]{msp.getX(), msp.getY()});
               }
            }
         }
      }
      return xyByPos;
   }

   /**
    * Shift all row/col values so the minimum row is 0 and minimum col is 0.
    */
   private static Map<Integer, GridCell> shiftToZero(Map<Integer, GridCell> grid) {
      int minRow = Integer.MAX_VALUE;
      int minCol = Integer.MAX_VALUE;
      for (GridCell cell : grid.values()) {
         if (cell.row < minRow) {
            minRow = cell.row;
         }
         if (cell.col < minCol) {
            minCol = cell.col;
         }
      }
      Map<Integer, GridCell> shifted = new HashMap<>();
      for (Map.Entry<Integer, GridCell> entry : grid.entrySet()) {
         shifted.put(entry.getKey(),
               new GridCell(entry.getValue().row - minRow,
                     entry.getValue().col - minCol));
      }
      return shifted;
   }

   // -------------------------------------------------------------------------
   // Utility
   // -------------------------------------------------------------------------

   /**
    * Compute the pixel overlap between adjacent tiles from stage positions.
    *
    * <p>Finds the minimum XY step between grid-adjacent tiles (in µm), converts
    * to pixels, and returns overlap = tileSize - step. Returns {0, 0} if pixel
    * size is unavailable or the grid has fewer than 2 tiles.</p>
    */
   private static int[] computeOverlap(DataProvider source, Map<Integer, GridCell> grid,
                                        int tileW, int tileH, OrientationModel orientation) {
      if (grid.size() < 2 || tileW <= 0 || tileH <= 0) {
         return new int[]{0, 0};
      }

      // Collect one stage XY per position index. When orientation is active, map each to
      // canvas-pixel space so the right/bottom neighbour steps are measured along canvas
      // axes (consistent with placement) rather than raw stage axes.
      Map<Integer, double[]> rawXy = collectStageXY(source);
      Map<Integer, double[]> xyByPos;
      // When mapped through M, steps are already in pixels; raw steps are in microns and
      // need the pixel size to convert (so the legacy path requires a valid pixel size).
      final double xToPx;
      final double yToPx;
      if (orientation != null) {
         xyByPos = new HashMap<>();
         for (Map.Entry<Integer, double[]> e : rawXy.entrySet()) {
            xyByPos.put(e.getKey(),
                  orientation.toCanvas(e.getValue()[0], e.getValue()[1]));
         }
         xToPx = 1.0;
         yToPx = 1.0;
      } else {
         double pixelSizeUm = getPixelSizeUm(source);
         if (pixelSizeUm <= 0) {
            return new int[]{0, 0};
         }
         xyByPos = rawXy;
         xToPx = 1.0 / pixelSizeUm;
         yToPx = 1.0 / pixelSizeUm;
      }

      // Build a (row, col) → posIdx lookup for O(1) neighbour access
      Map<Long, Integer> rcToPos = new HashMap<>();
      for (Map.Entry<Integer, GridCell> entry : grid.entrySet()) {
         GridCell cell = entry.getValue();
         rcToPos.put(((long) cell.row << 32) | (cell.col & 0xFFFFFFFFL), entry.getKey());
      }

      // Collect the step between horizontally and vertically adjacent grid cells.
      // Only check the immediate right (dCol=+1) and bottom (dRow=+1) neighbour of
      // each cell — O(N) rather than O(N²).
      //
      // Use the MEDIAN of these steps rather than the minimum. The minimum is fragile:
      // a single anomalous adjacent pair (e.g. a duplicate/near-coincident position, or
      // a grid quantization collision) produces a near-zero step that, taken as the step,
      // yields overlap = tileSize and collapses every tile into one row/column. The median
      // is robust to such outliers. Steps below a small fraction of the tile size are
      // physically impossible for genuinely adjacent tiles and are discarded outright.
      final double minPlausibleStepX = tileW * 0.05 / xToPx;
      final double minPlausibleStepY = tileH * 0.05 / yToPx;
      List<Double> stepsX = new ArrayList<>();
      List<Double> stepsY = new ArrayList<>();
      for (Map.Entry<Integer, GridCell> a : grid.entrySet()) {
         double[] xyA = xyByPos.get(a.getKey());
         if (xyA == null) {
            continue;
         }
         GridCell cellA = a.getValue();

         // Right neighbour
         Long rightKey = ((long) cellA.row << 32) | ((cellA.col + 1) & 0xFFFFFFFFL);
         Integer rightPos = rcToPos.get(rightKey);
         if (rightPos != null) {
            double[] xyB = xyByPos.get(rightPos);
            if (xyB != null) {
               double step = Math.abs(xyB[0] - xyA[0]);
               if (step > minPlausibleStepX) {
                  stepsX.add(step);
               }
            }
         }

         // Bottom neighbour
         Long bottomKey = (((long) cellA.row + 1) << 32) | (cellA.col & 0xFFFFFFFFL);
         Integer bottomPos = rcToPos.get(bottomKey);
         if (bottomPos != null) {
            double[] xyB = xyByPos.get(bottomPos);
            if (xyB != null) {
               double step = Math.abs(xyB[1] - xyA[1]);
               if (step > minPlausibleStepY) {
                  stepsY.add(step);
               }
            }
         }
      }

      int overlapX = 0;
      int overlapY = 0;
      if (!stepsX.isEmpty()) {
         int stepXPx = (int) Math.round(median(stepsX) * xToPx);
         overlapX = Math.max(0, tileW - stepXPx);
      }
      if (!stepsY.isEmpty()) {
         int stepYPx = (int) Math.round(median(stepsY) * yToPx);
         overlapY = Math.max(0, tileH - stepYPx);
      }
      return new int[]{overlapX, overlapY};
   }

   /** Probe the first available image to determine width and height. */
   private static int[] probeImageDims(DataProvider source) {
      for (Coords coords : source.getUnorderedImageCoords()) {
         try {
            Image img = source.getImage(coords);
            if (img != null) {
               return new int[]{img.getWidth(), img.getHeight()};
            }
         } catch (IOException e) {
            // try next
         }
      }
      return new int[]{0, 0};
   }

   /** Get pixel size in µm from the first image's metadata, or 0 if unavailable.
    *  Falls back to the affine transform diagonal if getPixelSizeUm() is missing. */
   private static double getPixelSizeUm(DataProvider source) {
      for (Coords coords : source.getUnorderedImageCoords()) {
         try {
            Image img = source.getImage(coords);
            if (img == null) {
               continue;
            }
            Double px = img.getMetadata().getPixelSizeUm();
            if (px != null && px > 0) {
               return px;
            }
            // Fallback: derive pixel size from the affine transform.
            // The affine columns represent the X and Y basis vectors in µm/pixel.
            // For a rotation+scale affine: column lengths equal the pixel size.
            // For anisotropic pixels, average the two column lengths.
            AffineTransform af = img.getMetadata().getPixelSizeAffine();
            if (af != null) {
               double colX = Math.sqrt(af.getScaleX() * af.getScaleX()
                     + af.getShearY() * af.getShearY());
               double colY = Math.sqrt(af.getShearX() * af.getShearX()
                     + af.getScaleY() * af.getScaleY());
               double scale = (colX + colY) / 2.0;
               if (scale > 0) {
                  return scale;
               }
            }
         } catch (IOException e) {
            // try next
         }
      }
      return 0.0;
   }
}
