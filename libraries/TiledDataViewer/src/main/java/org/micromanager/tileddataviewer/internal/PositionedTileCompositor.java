package org.micromanager.tileddataviewer.internal;

import java.awt.Point;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;

/**
 * Custom viewport compositor that positions tiles using per-image {@code XPositionPix}/
 * {@code YPositionPix} tags rather than the uniform-grid layout assumed by
 * {@link MultiresNDTiffAPI#getDisplayImage}.
 *
 * <p>On first use the compositor scans all tile axes, reads each tile's position tags
 * from full-resolution metadata, and builds an internal map of tile origin positions.
 * If no tiles carry {@code XPositionPix} tags the compositor reports
 * {@link #hasPositionTags()} == false and the caller should fall back to the storage's
 * own {@code getDisplayImage}.</p>
 *
 * <p>Thread-safety: {@link #buildPositions()} is synchronized; once built the map is
 * effectively immutable and all subsequent reads are safe without locking.</p>
 */
public class PositionedTileCompositor {

   /** Axes key for the column (tile grid X). */
   private static final String COL_AXIS = "column";
   /** Axes key for the row (tile grid Y). */
   private static final String ROW_AXIS = "row";

   private final MultiresNDTiffAPI storage_;

   // Built lazily; key = serialized full axes (includes row/col), value = full-res origin.
   private volatile Map<HashMap<String, Object>, Point> tilePositions_ = null;
   private int fullResTileW_ = 0;
   private int fullResTileH_ = 0;
   // Overlap stored in the NDTiff file — needed to find the content start within each tile.
   private int storedOverlapX_ = 0;
   private int storedOverlapY_ = 0;

   public PositionedTileCompositor(MultiresNDTiffAPI storage) {
      storage_ = storage;
   }

   /**
    * Returns true if any stored tile carries {@code XPositionPix} / {@code YPositionPix} tags.
    * Must be called after storage is populated; triggers lazy position-map build.
    */
   public boolean hasPositionTags() {
      buildPositions();
      return !tilePositions_.isEmpty();
   }

   /**
    * Returns the bounding rectangle of all positioned tiles in full-resolution pixels:
    * {@code [xMin, yMin, xMax, yMax]}.
    */
   public int[] computeBounds() {
      buildPositions();
      if (tilePositions_.isEmpty()) {
         return storage_.getImageBounds();
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

   /**
    * Composites a viewport region from the positioned tiles.
    *
    * <p>The viewport is specified in <em>resolution-level</em> pixel coordinates
    * ({@code xOffset}, {@code yOffset}) and the result has dimensions
    * {@code width × height} in those same coordinates. Full-resolution coords are
    * obtained by multiplying by {@code 2^resolutionIndex}.</p>
    *
    * <p>{@code nonSpatialAxes} must contain all non-spatial axes (z, time, channel)
    * at their current scroll positions but must NOT contain row or column — those are
    * added per-tile by the compositor.</p>
    *
    * @return {@link TaggedImage} with the composited pixels and minimal tags,
    *         or a blank (zero-filled) image if no tiles overlap the viewport.
    */
   public TaggedImage composite(HashMap<String, Object> nonSpatialAxes,
                                int resolutionIndex,
                                int xOffset, int yOffset,
                                int width, int height) {
      buildPositions();

      int dsScale = 1 << resolutionIndex;

      // Viewport in full-resolution pixels.
      int vpX0 = xOffset * dsScale;
      int vpY0 = yOffset * dsScale;
      int vpX1 = vpX0 + width * dsScale;
      int vpY1 = vpY0 + height * dsScale;

      // Downsampled tile dimensions (rounded up).
      int dsTileW = (fullResTileW_ + dsScale - 1) / dsScale;
      int dsTileH = (fullResTileH_ + dsScale - 1) / dsScale;

      Object pixels = null;
      boolean rgb = false;
      boolean is16bit = false;
      JSONObject topLeftTags = null;

      for (Map.Entry<HashMap<String, Object>, Point> entry : tilePositions_.entrySet()) {
         HashMap<String, Object> storedAxes = entry.getKey();

         // Only composite tiles whose non-spatial axes match the current scroll position.
         if (!nonSpatialAxesMatch(storedAxes, nonSpatialAxes)) {
            continue;
         }

         Point tileOrigin = entry.getValue();
         // Full-res tile rect.
         int tx0 = tileOrigin.x;
         int ty0 = tileOrigin.y;
         int tx1 = tx0 + fullResTileW_;
         int ty1 = ty0 + fullResTileH_;

         // Skip tiles that don't overlap the viewport.
         if (tx1 <= vpX0 || tx0 >= vpX1 || ty1 <= vpY0 || ty0 >= vpY1) {
            continue;
         }

         // Fetch tile pixels at the requested resolution level.
         TaggedImage tile = storage_.getImage(storedAxes, resolutionIndex);
         if (tile == null || tile.pix == null) {
            continue;
         }

         // Determine pixel format from the first tile we successfully load.
         if (pixels == null) {
            EssentialImageMetadata eimd =
                  storage_.getEssentialImageMetadata(storedAxes, resolutionIndex);
            if (eimd == null) {
               continue;
            }
            rgb = eimd.rgb;
            is16bit = !rgb && tile.pix instanceof short[];
            int bpp = rgb ? 4 : 1;
            pixels = rgb ? new byte[width * height * 4]
                  : (is16bit ? new short[width * height] : new byte[width * height]);
            if (topLeftTags == null) {
               topLeftTags = tile.tags;
            }
         }

         // Compute intersection in full-res coords.
         int intX0 = Math.max(tx0, vpX0);
         int intY0 = Math.max(ty0, vpY0);
         int intX1 = Math.min(tx1, vpX1);
         int intY1 = Math.min(ty1, vpY1);

         // Convert intersection to downsampled coords within the tile and within the output.
         // Tile pixel at full-res (tx0 + px, ty0 + py) is at downsampled (px/dsScale, py/dsScale).
         // Output pixel at (intX0 - vpX0, intY0 - vpY0) in downsampled output coords.
         int srcStartX = (intX0 - tx0) / dsScale;
         int srcStartY = (intY0 - ty0) / dsScale;
         int dstStartX = (intX0 - vpX0) / dsScale;
         int dstStartY = (intY0 - vpY0) / dsScale;
         int copyW = (intX1 - intX0) / dsScale;
         int copyH = (intY1 - intY0) / dsScale;

         if (copyW <= 0 || copyH <= 0) {
            continue;
         }

         // At full resolution (resIndex==0) the stored pixel buffer includes the overlap border:
         // buffer is (tileW + overlapX) × (tileH + overlapY), and content starts at
         // (overlapX/2, overlapY/2). At lower res levels the buffer is just dsTileW × dsTileH.
         // Since Stitch writes with overlap=0, storedOverlapX_/Y_ are 0 for our datasets.
         int tileContentOffsetX = (resolutionIndex == 0) ? storedOverlapX_ / 2 : 0;
         int tileContentOffsetY = (resolutionIndex == 0) ? storedOverlapY_ / 2 : 0;
         int storedBufW = (resolutionIndex == 0)
               ? (fullResTileW_ + storedOverlapX_)
               : dsTileW;

         int bpp = rgb ? 4 : 1;
         for (int row = 0; row < copyH; row++) {
            int srcRow = srcStartY + tileContentOffsetY + row;
            int srcCol = srcStartX + tileContentOffsetX;
            int srcIdx = (srcRow * storedBufW + srcCol) * bpp;
            int dstRow = dstStartY + row;
            int dstIdx = (dstRow * width + dstStartX) * bpp;
            int len = copyW * bpp;
            System.arraycopy(tile.pix, srcIdx, pixels, dstIdx, len);
         }
      }

      if (pixels == null) {
         // No tiles found — return blank image.
         pixels = is16bit ? new short[width * height] : new byte[width * height];
      }

      // Build minimal tags for the returned composite image.
      JSONObject tags = buildMinimalTags(topLeftTags, width, height, rgb, is16bit);
      return new TaggedImage(pixels, tags);
   }

   // -------------------------------------------------------------------------

   private synchronized void buildPositions() {
      if (tilePositions_ != null) {
         return;
      }
      Map<HashMap<String, Object>, Point> map = new LinkedHashMap<>();

      // Read stored overlap from summary metadata (may be 0 for Stitch-written datasets).
      JSONObject summary = storage_.getSummaryMetadata();
      if (summary != null) {
         storedOverlapX_ = summary.optInt("GridPixelOverlapX", 0);
         storedOverlapY_ = summary.optInt("GridPixelOverlapY", 0);
      }

      Set<HashMap<String, Object>> allAxes = storage_.getAxesSet();
      for (HashMap<String, Object> axes : allAxes) {
         TaggedImage ti = storage_.getImage(axes, 0);
         if (ti == null || ti.tags == null) {
            continue;
         }
         int x = ti.tags.optInt("XPositionPix", Integer.MIN_VALUE);
         int y = ti.tags.optInt("YPositionPix", Integer.MIN_VALUE);
         if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
            // This tile has no position tags — dataset doesn't use positioned layout.
            tilePositions_ = new LinkedHashMap<>();
            return;
         }
         if (fullResTileW_ == 0) {
            // Use the content tile size (without overlap border).
            fullResTileW_ = ti.tags.optInt("Width", 0);
            fullResTileH_ = ti.tags.optInt("Height", 0);
         }
         map.put(axes, new Point(x, y));
      }
      tilePositions_ = map;
   }

   /**
    * Returns true if all non-spatial axes in {@code nonSpatialAxes} match the
    * corresponding values in {@code storedAxes} (row/col keys are ignored).
    */
   private static boolean nonSpatialAxesMatch(HashMap<String, Object> storedAxes,
                                               HashMap<String, Object> nonSpatialAxes) {
      for (Map.Entry<String, Object> e : nonSpatialAxes.entrySet()) {
         String key = e.getKey();
         if (key.equals(ROW_AXIS) || key.equals(COL_AXIS)) {
            continue;
         }
         Object storedVal = storedAxes.get(key);
         if (!e.getValue().equals(storedVal)) {
            return false;
         }
      }
      return true;
   }

   private static JSONObject buildMinimalTags(JSONObject sourceTags,
                                               int width, int height,
                                               boolean rgb, boolean is16bit) {
      JSONObject tags = new JSONObject();
      try {
         tags.put("Width", width);
         tags.put("Height", height);
         tags.put("PixelType", rgb ? "RGB32" : (is16bit ? "GRAY16" : "GRAY8"));
         // Copy through PixelSizeUm for scale-bar overlay support.
         if (sourceTags != null && sourceTags.has("PixelSizeUm")) {
            tags.put("PixelSizeUm", sourceTags.optDouble("PixelSizeUm", 0));
         }
      } catch (Exception e) {
         // JSON errors in tags are non-fatal.
      }
      return tags;
   }
}
