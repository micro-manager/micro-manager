package org.micromanager.exporttiles;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;

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

   private final MultiresNDTiffAPI storage_;
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
   public TileBlender(MultiresNDTiffAPI storage, JSONObject displaySettings,
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
    * @param roiX     Left edge of ROI in full-resolution pixels.
    * @param roiY     Top edge of ROI in full-resolution pixels.
    * @param roiW     Width of ROI in full-resolution pixels.
    * @param roiH     Height of ROI in full-resolution pixels.
    * @param resLevel Resolution level (0 = full res, 1 = half res, …).
    * @return 8-bit RGB BufferedImage with blended tile seams.
    */
   public BufferedImage composite(int roiX, int roiY, int roiW, int roiH, int resLevel) {
      int scale = 1 << resLevel;
      int dsRoiX = roiX / scale;
      int dsRoiY = roiY / scale;
      int dsRoiW = Math.max(1, roiW / scale);
      int dsRoiH = Math.max(1, roiH / scale);

      // Tile step (advance per tile) at full resolution
      int stepX = tileWidth_ - overlapX_;
      int stepY = tileHeight_ - overlapY_;

      // Downsampled tile dimensions (storage returns tiles of this size at resLevel)
      int dsTileW = tileWidth_ / scale;
      int dsTileH = tileHeight_ / scale;
      int dsStepX = stepX / scale;
      int dsStepY = stepY / scale;
      int dsOverlapX = overlapX_ / scale;
      int dsOverlapY = overlapY_ / scale;

      // Guard against zero-overlap case (no blending needed; caller should use the
      // fast path, but this is safe to call anyway)
      int halfOX = Math.max(1, dsOverlapX / 2);
      int halfOY = Math.max(1, dsOverlapY / 2);

      // Accumulator arrays: weighted colour sums + total weight per pixel
      float[] rAcc = new float[dsRoiW * dsRoiH];
      float[] gAcc = new float[dsRoiW * dsRoiH];
      float[] bAcc = new float[dsRoiW * dsRoiH];
      float[] wAcc = new float[dsRoiW * dsRoiH];

      // Determine tile row/col range that covers the ROI.
      // Tile indices are signed (negative rows/cols are valid in explore acquisitions).
      // Use floor division so negative coordinates round toward -infinity.
      int colMin = dsStepX > 0 ? Math.floorDiv(dsRoiX, dsStepX) : 0;
      int rowMin = dsStepY > 0 ? Math.floorDiv(dsRoiY, dsStepY) : 0;
      int colMax = dsStepX > 0 ? Math.floorDiv(dsRoiX + dsRoiW - 1, dsStepX) : 0;
      int rowMax = dsStepY > 0 ? Math.floorDiv(dsRoiY + dsRoiH - 1, dsStepY) : 0;

      // Get the set of tiles that actually have data at the current z position
      Set<Point> tilesWithData = getTilesWithData();

      int numChannels = channelNames_.size();

      for (int row = rowMin; row <= rowMax; row++) {
         for (int col = colMin; col <= colMax; col++) {
            if (!hasTile(tilesWithData, row, col)) {
               continue;
            }

            // Top-left corner of this tile in downsampled pixels
            int tileOriginX = col * dsStepX;
            int tileOriginY = row * dsStepY;

            // Intersection of tile with ROI in downsampled pixel coordinates
            int interX0 = Math.max(dsRoiX, tileOriginX);
            int interY0 = Math.max(dsRoiY, tileOriginY);
            int interX1 = Math.min(dsRoiX + dsRoiW, tileOriginX + dsTileW);
            int interY1 = Math.min(dsRoiY + dsRoiH, tileOriginY + dsTileH);
            if (interX0 >= interX1 || interY0 >= interY1) {
               continue;
            }

            // Pre-compute blend weights for the intersection region and accumulate
            // into wAcc. This is done once per tile, independent of channel data.
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

               // Parse channel display settings
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
               float range = Math.max(1f, cMax - cMin);
               float chR = ((color >> 16) & 0xFF) / 255f;
               float chG = ((color >>  8) & 0xFF) / 255f;
               float chB = (color         & 0xFF) / 255f;

               // Build a complete axes map for this tile + channel.
               // storage_.getImage() requires every axis that the stored data was
               // written with (z, time, position, row, column, channel, …).
               // We find a stored axes set that matches baseAxes_ + channel, then
               // override row and column with the tile coordinates.
               HashMap<String, Object> axes = buildAxesForTile(row, col, chName);
               if (axes == null) {
                  continue;
               }

               TaggedImage taggedImage = storage_.getImage(axes, resLevel);
               if (taggedImage == null || !(taggedImage.pix instanceof short[])) {
                  continue;
               }
               short[] tilePix = (short[]) taggedImage.pix;

               for (int py = interY0; py < interY1; py++) {
                  for (int px = interX0; px < interX1; px++) {
                     int tx = px - tileOriginX;
                     int ty = py - tileOriginY;
                     float wx = ramp(tx + 1, halfOX) * ramp(dsTileW - tx, halfOX);
                     float wy = ramp(ty + 1, halfOY) * ramp(dsTileH - ty, halfOY);
                     float w = wx * wy;

                     int tileIdx = ty * dsTileW + tx;
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
      }

      // Build 8-bit RGB output image; normalise by weight where coverage > 0
      BufferedImage out = new BufferedImage(dsRoiW, dsRoiH, BufferedImage.TYPE_INT_RGB);
      for (int i = 0; i < dsRoiW * dsRoiH; i++) {
         float w = wAcc[i];
         int r, g, b;
         if (w > 0f) {
            r = Math.min(255, (int) (rAcc[i] / w * 255));
            g = Math.min(255, (int) (gAcc[i] / w * 255));
            b = Math.min(255, (int) (bAcc[i] / w * 255));
         } else {
            r = 0; g = 0; b = 0;
         }
         out.setRGB(i % dsRoiW, i / dsRoiW, (r << 16) | (g << 8) | b);
      }
      return out;
   }

   /**
    * Linear ramp: returns 0 when d<=0, 1 when d>=halfOverlap, linear in between.
    * Call with d = distance-from-edge + 1 so that the first pixel (d=1) has
    * weight 1/halfOverlap rather than 0, ensuring no pixel is ever fully zero.
    */
   private static float ramp(int d, int halfOverlap) {
      if (d <= 0) return 0f;
      if (d >= halfOverlap) return 1f;
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
         boolean matches = true;
         for (HashMap.Entry<String, Object> entry : baseAxes_.entrySet()) {
            Object storedVal = stored.get(entry.getKey());
            if (storedVal == null || !storedVal.equals(entry.getValue())) {
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
            if (storedVal == null || !storedVal.equals(entry.getValue())) {
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

   private static boolean hasTile(Set<Point> tilesWithData, int row, int col) {
      return tilesWithData.contains(new Point(col, row));
   }
}
