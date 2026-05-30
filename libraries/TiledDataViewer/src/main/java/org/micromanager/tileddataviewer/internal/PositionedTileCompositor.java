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
 * ({@code TileAffineTransform} or legacy {@code XPositionPix}/{@code YPositionPix})
 * and provides position-aware bounds computation.
 *
 * <p>When position tags are present, {@link NDTiffStorage#getDisplayImage} handles
 * correct tile placement at all pyramid resolution levels internally.  This class's
 * only remaining responsibilities are:
 * <ul>
 *   <li>{@link #hasPositionTags()} — tell callers whether per-tile positioning is active</li>
 *   <li>{@link #computeBounds()} — return the true pixel-position-based canvas bounds</li>
 *   <li>{@link #getImageForDisplay} — thin delegation to storage (positioning handled there)</li>
 * </ul>
 *
 * <p>Thread-safety: {@link #buildPositions()} is synchronized; once built the map is
 * effectively immutable.</p>
 */
public class PositionedTileCompositor {

   private static final String COL_AXIS = "column";
   private static final String ROW_AXIS = "row";

   private final MultiresNDTiffAPI storage_;

   // Built lazily; key = full axes map (includes row/col), value = full-res origin.
   private volatile Map<HashMap<String, Object>, Point> tilePositions_ = null;
   private int fullResTileW_ = 0;
   private int fullResTileH_ = 0;

   public PositionedTileCompositor(MultiresNDTiffAPI storage) {
      storage_ = storage;
   }

   /**
    * Returns true if stored tiles carry position tags that NDTiffStorage will use
    * for non-uniform placement.
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
    * Delegates to {@link MultiresNDTiffAPI#getDisplayImage}, which handles per-tile
    * positioning internally when {@code TileAffineTransform} tags are present.
    * The {@code nonSpatialAxes} must not contain row or column.
    */
   public TaggedImage getImageForDisplay(HashMap<String, Object> nonSpatialAxes,
                                         int resolutionIndex,
                                         int xOffset, int yOffset,
                                         int width, int height) {
      // NDTiffStorage.getDisplayImage now reads TileAffineTransform tags and uses them
      // for correct positioning at all pyramid levels when present.
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
         // Check for TileAffineTransform first, then fall back to XPositionPix/YPositionPix.
         int x;
         int y;
         mmcorej.org.json.JSONArray affineArr =
               ti.tags.optJSONArray("TileAffineTransform");
         if (affineArr != null && affineArr.length() == 12) {
            x = (int) Math.round(affineArr.optDouble(3, Double.NaN));
            y = (int) Math.round(affineArr.optDouble(7, Double.NaN));
            if (Double.isNaN(affineArr.optDouble(3, Double.NaN))) {
               tilePositions_ = new LinkedHashMap<>();
               return;
            }
         } else {
            x = ti.tags.optInt("XPositionPix", Integer.MIN_VALUE);
            y = ti.tags.optInt("YPositionPix", Integer.MIN_VALUE);
            if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
               tilePositions_ = new LinkedHashMap<>();
               return;
            }
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
