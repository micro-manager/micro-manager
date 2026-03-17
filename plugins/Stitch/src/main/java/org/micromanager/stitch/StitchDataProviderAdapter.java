package org.micromanager.stitch;

import java.io.IOException;
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

   // position index → grid cell
   private final Map<Integer, GridCell> positionGrid_;
   private final int imageWidth_;
   private final int imageHeight_;
   private final int overlapX_;
   private final int overlapY_;

   /**
    * Construct the adapter.
    *
    * @param source the MM DataProvider (must have XPositionUm/YPositionUm per-image
    *               metadata, or stage positions with grid coordinates in summary metadata)
    * @throws IllegalArgumentException if grid coordinates cannot be determined
    */
   public StitchDataProviderAdapter(DataProvider source) {
      super(source);
      int[] dims = probeImageDims(source);
      imageWidth_ = dims[0];
      imageHeight_ = dims[1];
      positionGrid_ = buildPositionGrid(source, imageWidth_, imageHeight_);
      int[] overlap = computeOverlap(source, positionGrid_, imageWidth_, imageHeight_);
      overlapX_ = overlap[0];
      overlapY_ = overlap[1];
   }

   // -------------------------------------------------------------------------
   // Public helpers for callers
   // -------------------------------------------------------------------------

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

   // -------------------------------------------------------------------------
   // Overrides: inject row/col into every axes map
   // -------------------------------------------------------------------------

   @Override
   public Set<HashMap<String, Object>> getAxesSet() {
      Set<HashMap<String, Object>> base = super.getAxesSet();
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
      // Strip row/col before delegating to parent (parent doesn't know them)
      HashMap<String, Object> stripped = stripRowCol(axes);
      return super.getImage(stripped, 0);
   }

   @Override
   public boolean hasImage(HashMap<String, Object> axes) {
      return super.hasImage(stripRowCol(axes));
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
    */
   private HashMap<String, Object> stripRowCol(HashMap<String, Object> axes) {
      HashMap<String, Object> result = new HashMap<>(axes);
      Object rowVal = result.remove("row");
      Object colVal = result.remove("column");
      if (rowVal instanceof Integer && colVal instanceof Integer) {
         int row = (Integer) rowVal;
         int col = (Integer) colVal;
         for (Map.Entry<Integer, GridCell> entry : positionGrid_.entrySet()) {
            if (entry.getValue().row == row && entry.getValue().col == col) {
               result.put(Coords.STAGE_POSITION, entry.getKey());
               break;
            }
         }
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
                                                            int imageWidth, int imageHeight) {
      SummaryMetadata summary = source.getSummaryMetadata();

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
      return anyNonZero ? shiftToZero(grid) : null;
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
                                        int tileW, int tileH) {
      if (grid.size() < 2 || tileW <= 0 || tileH <= 0) {
         return new int[]{0, 0};
      }
      double pixelSizeUm = getPixelSizeUm(source);
      if (pixelSizeUm <= 0) {
         return new int[]{0, 0};
      }

      // Collect XY positions per position index from per-image metadata
      Map<Integer, double[]> xyByPos = new java.util.HashMap<>();
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
            // skip
         }
      }

      // Fall back to summary stage position list if per-image metadata unavailable
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

      // Find minimum step between horizontally and vertically adjacent grid cells
      double minStepX = Double.MAX_VALUE;
      double minStepY = Double.MAX_VALUE;
      for (Map.Entry<Integer, GridCell> a : grid.entrySet()) {
         double[] xyA = xyByPos.get(a.getKey());
         if (xyA == null) {
            continue;
         }
         for (Map.Entry<Integer, GridCell> b : grid.entrySet()) {
            if (a.getKey().equals(b.getKey())) {
               continue;
            }
            double[] xyB = xyByPos.get(b.getKey());
            if (xyB == null) {
               continue;
            }
            int dCol = Math.abs(b.getValue().col - a.getValue().col);
            int dRow = Math.abs(b.getValue().row - a.getValue().row);
            if (dCol == 1 && dRow == 0) {
               double step = Math.abs(xyB[0] - xyA[0]);
               if (step > 0 && step < minStepX) {
                  minStepX = step;
               }
            }
            if (dRow == 1 && dCol == 0) {
               double step = Math.abs(xyB[1] - xyA[1]);
               if (step > 0 && step < minStepY) {
                  minStepY = step;
               }
            }
         }
      }

      int overlapX = 0;
      int overlapY = 0;
      if (minStepX < Double.MAX_VALUE) {
         int stepXPx = (int) Math.round(minStepX / pixelSizeUm);
         overlapX = Math.max(0, tileW - stepXPx);
      }
      if (minStepY < Double.MAX_VALUE) {
         int stepYPx = (int) Math.round(minStepY / pixelSizeUm);
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

   /** Get pixel size in µm from the first image's metadata, or 0 if unavailable. */
   private static double getPixelSizeUm(DataProvider source) {
      for (Coords coords : source.getUnorderedImageCoords()) {
         try {
            Image img = source.getImage(coords);
            if (img != null) {
               Double px = img.getMetadata().getPixelSizeUm();
               if (px != null && px > 0) {
                  return px;
               }
            }
         } catch (IOException e) {
            // try next
         }
      }
      return 0.0;
   }
}
