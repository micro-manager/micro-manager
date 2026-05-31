package org.micromanager.tileddataviewer.internal;

import java.awt.Point;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import mmcorej.TaggedImage;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;

/**
 * Detects whether an NDTiff dataset carries per-tile position tags
 * ({@code XPositionPix}/{@code YPositionPix}) and provides position-aware bounds computation.
 *
 * <p>When position tags are present, tile origins are read from each image's metadata tags and
 * used to compute the canvas bounds in {@link #computeBounds()}.  Actual pixel compositing is
 * delegated to {@link MultiresNDTiffAPI#getDisplayImage}, which handles grid-based tile
 * placement internally.</p>
 *
 * <p>Thread-safety: {@link #buildPositions()} is synchronized; once built the map is
 * effectively immutable.</p>
 */
public class PositionedTileCompositor {

   private final MultiresNDTiffAPI storage_;

   // Built lazily; key = full axes map (includes row/col), value = full-res origin.
   private volatile Map<HashMap<String, Object>, Point> tilePositions_ = null;
   private int fullResTileW_ = 0;
   private int fullResTileH_ = 0;

   public PositionedTileCompositor(MultiresNDTiffAPI storage) {
      storage_ = storage;
   }

   /**
    * Returns true if stored tiles carry {@code XPositionPix}/{@code YPositionPix} tags.
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
    * Delegates to {@link MultiresNDTiffAPI#getDisplayImage}.
    * The {@code nonSpatialAxes} must not contain row or column.
    */
   public TaggedImage getImageForDisplay(HashMap<String, Object> nonSpatialAxes,
                                         int resolutionIndex,
                                         int xOffset, int yOffset,
                                         int width, int height) {
      return storage_.getDisplayImage(nonSpatialAxes, resolutionIndex,
            xOffset, yOffset, width, height);
   }

   // -------------------------------------------------------------------------

   private synchronized void buildPositions() {
      if (tilePositions_ != null) {
         return;
      }
      Map<HashMap<String, Object>, Point> map = new LinkedHashMap<>();
      Set<HashMap<String, Object>> allAxes = storage_.getAxesSet();
      for (HashMap<String, Object> axes : allAxes) {
         TaggedImage ti = storage_.getImage(axes, 0);
         if (ti == null || ti.tags == null) {
            continue;
         }
         int x = ti.tags.optInt("XPositionPix", Integer.MIN_VALUE);
         int y = ti.tags.optInt("YPositionPix", Integer.MIN_VALUE);
         if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
            tilePositions_ = new LinkedHashMap<>();
            return;
         }
         if (fullResTileW_ == 0) {
            fullResTileW_ = ti.tags.optInt("Width", 0);
            fullResTileH_ = ti.tags.optInt("Height", 0);
         }
         map.put(axes, new Point(x, y));
      }
      tilePositions_ = map;
   }
}
