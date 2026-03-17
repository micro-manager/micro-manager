package org.micromanager.exporttiles;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.tileddataprovider.TiledDataProviderAPI;

/**
 * Composites tiles from a MultiresNDTiffAPI storage into a single RGB image
 * using linear feathering in overlap zones to eliminate hard seam boundaries.
 *
 * <p>For each pixel in the output, all tiles that cover that pixel contribute
 * with a weight proportional to how far the pixel is from the tile edge.
 * Pixels at the centre of a tile get weight ≈ 1; pixels deep in the overlap
 * zone fade smoothly to 0. The final colour is the weighted average of all
 * contributing tiles.</p>
 */
public class TileBlender {

   private final TiledDataProviderAPI storage_;
   private final JSONObject displaySettings_;
   private final HashMap<String, Object> baseAxes_;
   private final List<String> channelNames_;
   private final int overlapX_;
   private final int overlapY_;
   private final int tileWidth_;
   private final int tileHeight_;

   /**
    * @param storage         Source storage with multi-resolution tile data.
    * @param displaySettings Per-channel color/contrast settings from NDViewer.
    * @param baseAxes        Non-channel axes (e.g. current z position).
    * @param channelNames    Channel names to composite (null means no channel axis).
    * @param summaryMetadata Summary metadata containing tile dimensions and overlap.
    */
   public TileBlender(TiledDataProviderAPI storage, JSONObject displaySettings,
                      HashMap<String, Object> baseAxes, List<String> channelNames,
                      JSONObject summaryMetadata) {
      storage_ = storage;
      displaySettings_ = displaySettings;
      baseAxes_ = baseAxes;
      channelNames_ = channelNames;
      overlapX_ = summaryMetadata.optInt("GridPixelOverlapX", 0);
      overlapY_ = summaryMetadata.optInt("GridPixelOverlapY", 0);
      tileWidth_ = summaryMetadata.optInt("Width", 0);
      tileHeight_ = summaryMetadata.optInt("Height", 0);
   }

   /**
    * Composite tiles covering the given ROI at the requested resolution level.
    *
    * @param roiX       Left edge of ROI in full-resolution pixels.
    * @param roiY       Top edge of ROI in full-resolution pixels.
    * @param roiW       Width of ROI in full-resolution pixels.
    * @param roiH       Height of ROI in full-resolution pixels.
    * @param resLevel   Resolution level (0 = full res, 1 = half res, …).
    * @return 8-bit RGB BufferedImage with blended tile seams.
    */
   public BufferedImage composite(int roiX, int roiY, int roiW, int roiH, int resLevel) {
      return composite(roiX, roiY, roiW, roiH, resLevel, null, pct -> {});
   }

   /**
    * Composite tiles covering the given ROI, using optionally corrected tile origins.
    *
    * @param roiX       Left edge of ROI in full-resolution pixels.
    * @param roiY       Top edge of ROI in full-resolution pixels.
    * @param roiW       Width of ROI in full-resolution pixels.
    * @param roiH       Height of ROI in full-resolution pixels.
    * @param resLevel   Resolution level (0 = full res, 1 = half res, …).
    * @param tileOrigins Optional map from Point(col, row) to corrected pixel origin at full
    *                    resolution. Pass null to use nominal grid positions.
    * @return 8-bit RGB BufferedImage with blended tile seams.
    */
   public BufferedImage composite(int roiX, int roiY, int roiW, int roiH, int resLevel,
                                  Map<Point, Point2D.Float> tileOrigins) {
      return composite(roiX, roiY, roiW, roiH, resLevel, tileOrigins, pct -> {});
   }

   public BufferedImage composite(int roiX, int roiY, int roiW, int roiH, int resLevel,
                                  Map<Point, Point2D.Float> tileOrigins, IntConsumer progress) {
      int scale = 1 << resLevel;
      int dsRoiX = roiX / scale;
      int dsRoiY = roiY / scale;
      int dsRoiW = Math.max(1, roiW / scale);
      int dsRoiH = Math.max(1, roiH / scale);

      // Downsampled tile dimensions (storage returns tiles of this size at resLevel).
      // Summary metadata Width/Height may be wrong (e.g. Height=212 when actual tile
      // is 3544x2396). Always validate against actual pixel count from the first image.
      int dsTileW = tileWidth_ / scale;
      int dsTileH = tileHeight_ / scale;
      // Probe at full resolution (level 0) to get actual tile dimensions.
      // "Width" tag stores full-res value; divide by scale for downsampled dimensions.
      outer:
      for (HashMap<String, Object> stored : storage_.getAxesSet()) {
         TaggedImage probe = storage_.getImage(stored, 0);
         if (probe != null && probe.pix instanceof short[]) {
            int nPix = ((short[]) probe.pix).length;
            int twFull = (probe.tags != null) ? probe.tags.optInt("Width", 0) : 0;
            if (twFull > 0 && nPix % twFull == 0) {
               dsTileW = twFull / scale;
               dsTileH = (nPix / twFull) / scale;
               break outer;
            }
            int sq = (int) Math.round(Math.sqrt(nPix));
            if (sq * sq == nPix) {
               dsTileW = sq / scale;
               dsTileH = sq / scale;
               break outer;
            }
         }
      }
      int dsOverlapX = overlapX_ / scale;
      int dsOverlapY = overlapY_ / scale;
      // dsStepX/Y: distance between adjacent tile origins in downsampled pixels.
      // Always derive from probed tile dimensions minus overlap (summary metadata step
      // values are unreliable when Width/Height in metadata are wrong).
      int dsStepX = Math.max(1, dsTileW - dsOverlapX);
      int dsStepY = Math.max(1, dsTileH - dsOverlapY);

      // Guard against zero-overlap case
      int halfOX = Math.max(1, dsOverlapX / 2);
      int halfOY = Math.max(1, dsOverlapY / 2);

      // Accumulator arrays: weighted colour sums + total weight per pixel
      float[] rAcc = new float[dsRoiW * dsRoiH];
      float[] gAcc = new float[dsRoiW * dsRoiH];
      float[] bAcc = new float[dsRoiW * dsRoiH];
      float[] wAcc = new float[dsRoiW * dsRoiH];

      // Get the set of tiles that actually have data at the current z position
      Set<Point> tilesWithData = getTilesWithData();

      // Build the list of (tile, tileOriginX, tileOriginY) to process.
      // Always iterate tilesWithData directly — the nominal grid range is unreliable
      // when tile indices are negative (Magellan explore) or when tileWidth_/tileHeight_
      // are zero in the summary metadata.  The intersection check below filters to ROI.
      java.util.List<int[]> tileList = new java.util.ArrayList<>();
      for (Point tile : tilesWithData) {
         int col = tile.x;
         int row = tile.y;
         int ox;
         int oy;
         if (tileOrigins != null) {
            Point2D.Float corrected = tileOrigins.get(tile);
            ox = corrected != null ? (int) (corrected.x / scale) : col * dsStepX;
            oy = corrected != null ? (int) (corrected.y / scale) : row * dsStepY;
         } else {
            ox = col * dsStepX;
            oy = row * dsStepY;
         }
         tileList.add(new int[]{row, col, ox, oy});
      }

      int numChannels = channelNames_.size();
      int totalTiles = tileList.size();
      int doneTiles = 0;

      for (int[] entry : tileList) {
         progress.accept(totalTiles > 0 ? (doneTiles * 100 / totalTiles) : 0);
         doneTiles++;
         int row = entry[0];
         int col = entry[1];
         int tileOriginX = entry[2];
         int tileOriginY = entry[3];

         // Intersection of this tile with the ROI in downsampled pixel coordinates
         int interX0 = Math.max(dsRoiX, tileOriginX);
         int interY0 = Math.max(dsRoiY, tileOriginY);
         int interX1 = Math.min(dsRoiX + dsRoiW, tileOriginX + dsTileW);
         int interY1 = Math.min(dsRoiY + dsRoiH, tileOriginY + dsTileH);
         if (interX0 >= interX1 || interY0 >= interY1) {
            continue;
         }

         // Accumulate blend weights (independent of channel data)
         for (int py = interY0; py < interY1; py++) {
            for (int px = interX0; px < interX1; px++) {
               int tx = px - tileOriginX;
               int ty = py - tileOriginY;
               float wx = ramp(tx + 1, halfOX) * ramp(dsTileW - tx, halfOX);
               float wy = ramp(ty + 1, halfOY) * ramp(dsTileH - ty, halfOY);
               int outIdx = (py - dsRoiY) * dsRoiW + (px - dsRoiX);
               wAcc[outIdx] += wx * wy;
            }
         }

         // Accumulate each channel into the weighted colour buffers
         for (int c = 0; c < numChannels; c++) {
            String chName = channelNames_.get(c);
            String displayKey = (chName != null) ? chName : "NO_CHANNEL_PRESENT";

            int color = 0xFFFFFF;
            int cMin  = 0;
            int cMax  = 65535;
            if (displaySettings_ != null) {
               try {
                  JSONObject chSettings = displaySettings_.optJSONObject(displayKey);
                  if (chSettings != null) {
                     color = chSettings.optInt("Color", 0xFFFFFF) & 0xFFFFFF;
                     cMin  = chSettings.optInt("Min", 0);
                     cMax  = chSettings.optInt("Max", 65535);
                  }
               } catch (Exception e) {
                  // use defaults
               }
            }
            final float range = Math.max(1f, cMax - cMin);
            final float chR = ((color >> 16) & 0xFF) / 255f;
            final float chG = ((color >>  8) & 0xFF) / 255f;
            final float chB = (color         & 0xFF) / 255f;

            HashMap<String, Object> axes = buildAxesForTile(row, col, chName);
            if (axes == null) {
               continue;
            }

            // Always read at level 0; sample with stride=scale to produce downsampled output.
            TaggedImage taggedImage = storage_.getImage(axes, 0);
            if (taggedImage == null || !(taggedImage.pix instanceof short[])) {
               continue;
            }
            short[] tilePix = (short[]) taggedImage.pix;
            // Determine full-resolution tile width from tags or pixel count.
            int fullTileW = (taggedImage.tags != null) ? taggedImage.tags.optInt("Width", 0) : 0;
            if (fullTileW <= 0 || tilePix.length % fullTileW != 0) {
               int sq = (int) Math.round(Math.sqrt(tilePix.length));
               fullTileW = (sq * sq == tilePix.length) ? sq : dsTileW * scale;
            }

            for (int py = interY0; py < interY1; py++) {
               for (int px = interX0; px < interX1; px++) {
                  int tx = px - tileOriginX;
                  int ty = py - tileOriginY;
                  float wx = ramp(tx + 1, halfOX) * ramp(dsTileW - tx, halfOX);
                  float wy = ramp(ty + 1, halfOY) * ramp(dsTileH - ty, halfOY);
                  float w = wx * wy;

                  // Map downsampled pixel coords to full-res tile coords via stride
                  int tileIdx = (ty * scale) * fullTileW + (tx * scale);
                  if (tileIdx < 0 || tileIdx >= tilePix.length) {
                     continue; // safety guard for edge tiles
                  }
                  float norm = Math.min(1f, Math.max(0f,
                          ((tilePix[tileIdx] & 0xFFFF) - cMin) / range));

                  int outIdx = (py - dsRoiY) * dsRoiW + (px - dsRoiX);
                  rAcc[outIdx] += norm * chR * w;
                  gAcc[outIdx] += norm * chG * w;
                  bAcc[outIdx] += norm * chB * w;
               }
            }
         }
      }

      // Build 8-bit RGB output image; normalise by weight where coverage > 0
      BufferedImage out = new BufferedImage(dsRoiW, dsRoiH, BufferedImage.TYPE_INT_RGB);
      for (int i = 0; i < dsRoiW * dsRoiH; i++) {
         float w = wAcc[i];
         int r;
         int g;
         int b;
         if (w > 0f) {
            r = Math.min(255, (int) (rAcc[i] / w * 255));
            g = Math.min(255, (int) (gAcc[i] / w * 255));
            b = Math.min(255, (int) (bAcc[i] / w * 255));
         } else {
            r = 0;
            g = 0;
            b = 0;
         }
         out.setRGB(i % dsRoiW, i / dsRoiW, (r << 16) | (g << 8) | b);
      }
      return out;
   }

   /**
    * Composite a single channel of tiles into a 16-bit grayscale canvas using feathered blending.
    *
    * <p>Identical blending logic to {@link #composite} but operates directly on
    * raw 16-bit pixel values — no colour/contrast mapping, no 8-bit downscale.</p>
    *
    * @param roiX        Left edge of ROI in full-resolution pixels.
    * @param roiY        Top edge of ROI in full-resolution pixels.
    * @param roiW        Width of ROI in full-resolution pixels.
    * @param roiH        Height of ROI in full-resolution pixels.
    * @param resLevel    Resolution level (0 = full res, 1 = half res, …).
    * @param channelName The channel name to composite, or null for no channel axis.
    * @param tileOrigins Optional map from Point(col,row) to corrected pixel origin.
    *                    Pass null to use nominal grid positions.
    * @param progress    Callback receiving 0–100 percent completion.
    * @return short[] of length roiW*roiH with blended 16-bit pixel values.
    */
   public short[] composite16(int roiX, int roiY, int roiW, int roiH, int resLevel,
                               String channelName,
                               Map<Point, Point2D.Float> tileOrigins, IntConsumer progress) {
      return composite16(roiX, roiY, roiW, roiH, resLevel, channelName, tileOrigins, null,
            progress);
   }

   /**
    * Composite a single channel of tiles into a 16-bit grayscale canvas using feathered blending.
    *
    * <p>Identical to {@link #composite16(int, int, int, int, int, String, Map, IntConsumer)}
    * but accepts an optional per-tile transform applied to each tile's pixel array immediately
    * after it is fetched from storage and before it is blended into the canvas.
    * The transform must not change the pixel count (i.e. it must not rotate 90/270°).</p>
    *
    * @param tileTransform Optional transform applied to each tile's {@code short[]} pixel array
    *                      before blending. Pass null for no transform.
    */
   public short[] composite16(int roiX, int roiY, int roiW, int roiH, int resLevel,
                               String channelName,
                               Map<Point, Point2D.Float> tileOrigins,
                               UnaryOperator<short[]> tileTransform,
                               IntConsumer progress) {
      int scale = 1 << resLevel;
      int dsRoiX = roiX / scale;
      int dsRoiY = roiY / scale;
      int dsRoiW = Math.max(1, roiW / scale);
      int dsRoiH = Math.max(1, roiH / scale);

      int dsTileW = tileWidth_ / scale;
      int dsTileH = tileHeight_ / scale;
      outer16:
      for (HashMap<String, Object> stored : storage_.getAxesSet()) {
         TaggedImage probe = storage_.getImage(stored, 0);
         if (probe != null && probe.pix instanceof short[]) {
            int nPix = ((short[]) probe.pix).length;
            int twFull = (probe.tags != null) ? probe.tags.optInt("Width", 0) : 0;
            if (twFull > 0 && nPix % twFull == 0) {
               dsTileW = twFull / scale;
               dsTileH = (nPix / twFull) / scale;
               break outer16;
            }
            int sq = (int) Math.round(Math.sqrt(nPix));
            if (sq * sq == nPix) {
               dsTileW = sq / scale;
               dsTileH = sq / scale;
               break outer16;
            }
         }
      }
      int dsOverlapX = overlapX_ / scale;
      int dsOverlapY = overlapY_ / scale;
      int dsStepX = Math.max(1, dsTileW - dsOverlapX);
      final int dsStepY = Math.max(1, dsTileH - dsOverlapY);
      int halfOX = Math.max(1, dsOverlapX / 2);
      int halfOY = Math.max(1, dsOverlapY / 2);

      float[] valAcc = new float[dsRoiW * dsRoiH];
      float[] wAcc   = new float[dsRoiW * dsRoiH];

      Set<Point> tilesWithData = getTilesWithData();
      System.out.println("[TileBlender.composite16] dsTileW=" + dsTileW + " dsTileH=" + dsTileH
            + " dsStepX=" + dsStepX + " dsStepY=" + dsStepY + " tileOrigins=" + (tileOrigins != null ? "provided" : "null"));
      java.util.List<int[]> tileList = new java.util.ArrayList<>();
      for (Point tile : tilesWithData) {
         int col = tile.x;
         int row = tile.y;
         int ox;
         int oy;
         if (tileOrigins != null) {
            Point2D.Float corrected = tileOrigins.get(tile);
            ox = corrected != null ? (int) (corrected.x / scale) : col * dsStepX;
            oy = corrected != null ? (int) (corrected.y / scale) : row * dsStepY;
         } else {
            ox = col * dsStepX;
            oy = row * dsStepY;
         }
         System.out.println("[TileBlender.composite16]   tile col=" + col + " row=" + row + " origin=(" + ox + "," + oy + ")");
         tileList.add(new int[]{row, col, ox, oy});
      }

      String chName = channelName;

      int totalTiles = tileList.size();
      int doneTiles = 0;

      for (int[] entry : tileList) {
         progress.accept(totalTiles > 0 ? (doneTiles * 100 / totalTiles) : 0);
         doneTiles++;
         int row = entry[0];
         int col = entry[1];
         int tileOriginX = entry[2];
         int tileOriginY = entry[3];

         int interX0 = Math.max(dsRoiX, tileOriginX);
         int interY0 = Math.max(dsRoiY, tileOriginY);
         int interX1 = Math.min(dsRoiX + dsRoiW, tileOriginX + dsTileW);
         int interY1 = Math.min(dsRoiY + dsRoiH, tileOriginY + dsTileH);
         if (interX0 >= interX1 || interY0 >= interY1) {
            continue;
         }

         HashMap<String, Object> axes = buildAxesForTile(row, col, chName);
         if (axes == null) {
            continue;
         }
         TaggedImage taggedImage = storage_.getImage(axes, 0);
         if (taggedImage == null || !(taggedImage.pix instanceof short[])) {
            continue;
         }
         short[] tilePix = (short[]) taggedImage.pix;
         if (tileTransform != null) {
            tilePix = tileTransform.apply(tilePix);
         }
         int fullTileW = (taggedImage.tags != null) ? taggedImage.tags.optInt("Width", 0) : 0;
         if (fullTileW <= 0 || tilePix.length % fullTileW != 0) {
            int sq = (int) Math.round(Math.sqrt(tilePix.length));
            fullTileW = (sq * sq == tilePix.length) ? sq : dsTileW * scale;
         }

         for (int py = interY0; py < interY1; py++) {
            for (int px = interX0; px < interX1; px++) {
               int tx = px - tileOriginX;
               int ty = py - tileOriginY;
               float wx = ramp(tx + 1, halfOX) * ramp(dsTileW - tx, halfOX);
               float wy = ramp(ty + 1, halfOY) * ramp(dsTileH - ty, halfOY);
               float w = wx * wy;
               int tileIdx = (ty * scale) * fullTileW + (tx * scale);
               if (tileIdx < 0 || tileIdx >= tilePix.length) {
                  continue;
               }
               int outIdx = (py - dsRoiY) * dsRoiW + (px - dsRoiX);
               valAcc[outIdx] += (tilePix[tileIdx] & 0xFFFF) * w;
               wAcc[outIdx]   += w;
            }
         }
      }
      progress.accept(100);

      short[] out = new short[dsRoiW * dsRoiH];
      for (int i = 0; i < out.length; i++) {
         float w = wAcc[i];
         out[i] = w > 0f ? (short) Math.min(65535, Math.round(valAcc[i] / w)) : 0;
      }
      return out;
   }

   /**
    * Linear ramp: returns 0 when d<=0, 1 when d>=halfOverlap, linear in between.
    * Call with d = distance-from-edge + 1 so that the first pixel (d=1) has
    * weight 1/halfOverlap rather than 0, ensuring no pixel is ever fully zero.
    */
   private static float ramp(int d, int halfOverlap) {
      if (d <= 0) {
         return 0f;
      }
      if (d >= halfOverlap) {
         return 1f;
      }
      return (float) d / halfOverlap;
   }

   /**
    * Builds a complete axes map for retrieving a specific tile + channel from storage.
    *
    * <p>storage_.getImage() requires every axis present in the stored data (z, time,
    * position, row, column, channel, …).  We find any stored axes set that is
    * consistent with baseAxes_ and the requested channel, then override the row and
    * column entries with the tile coordinates.  Returns null if no matching entry is
    * found in the storage index.</p>
    */
   private HashMap<String, Object> buildAxesForTile(int row, int col, String chName) {
      for (HashMap<String, Object> stored : storage_.getAxesSet()) {
         // The stored set must contain row and column axes
         if (!stored.containsKey(NDTiffStorage.ROW_AXIS)
                 || !stored.containsKey(NDTiffStorage.COL_AXIS)) {
            continue;
         }
         // Every axis present in baseAxes_ must match the stored entry
         // (axes absent from the stored entry are ignored — e.g. no z-axis in dataset)
         boolean matches = true;
         for (HashMap.Entry<String, Object> entry : baseAxes_.entrySet()) {
            Object storedVal = stored.get(entry.getKey());
            if (storedVal == null) {
               continue;
            }
            if (!storedVal.equals(entry.getValue())) {
               matches = false;
               break;
            }
         }
         if (!matches) {
            continue;
         }
         // Channel must match (or be absent when chName is null)
         if (chName != null) {
            Object storedCh = stored.get("channel");
            if (!chName.equals(storedCh)) {
               continue;
            }
         }
         // Found a compatible entry — copy it and set the desired row/col
         HashMap<String, Object> axes = new HashMap<>(stored);
         axes.put(NDTiffStorage.ROW_AXIS, row);
         axes.put(NDTiffStorage.COL_AXIS, col);
         return axes;
      }
      return null;
   }

   /**
    * Returns the set of (row,col) tile Points that have data consistent with
    * baseAxes_ (i.e. at the current z / time / position).  The Point convention
    * matches NDTiffStorage: x = column, y = row.
    */
   private Set<Point> getTilesWithData() {
      Set<Point> result = new java.util.HashSet<>();
      for (HashMap<String, Object> stored : storage_.getAxesSet()) {
         if (!stored.containsKey(NDTiffStorage.ROW_AXIS)
                 || !stored.containsKey(NDTiffStorage.COL_AXIS)) {
            continue;
         }
         boolean matches = true;
         for (HashMap.Entry<String, Object> entry : baseAxes_.entrySet()) {
            Object storedVal = stored.get(entry.getKey());
            // Skip axes absent from this stored entry (e.g. no z-axis in dataset)
            if (storedVal == null) {
               continue;
            }
            if (!storedVal.equals(entry.getValue())) {
               matches = false;
               break;
            }
         }
         if (matches) {
            int r = ((Number) stored.get(NDTiffStorage.ROW_AXIS)).intValue();
            int c = ((Number) stored.get(NDTiffStorage.COL_AXIS)).intValue();
            result.add(new Point(c, r)); // Point(x=col, y=row)
         }
      }
      return result;
   }

}
