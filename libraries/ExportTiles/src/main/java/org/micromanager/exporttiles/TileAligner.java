package org.micromanager.exporttiles;

import ij.process.FHT;
import ij.process.FloatProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.imageprocessing.ImageTransformUtils;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.tileddataprovider.TiledDataProviderAPI;

/**
 * Computes sub-pixel translation corrections for a grid of overlapping tiles
 * using pairwise phase-correlation alignment followed by BFS global optimisation.
 *
 * <p>Phase correlation is performed on the overlap strips between adjacent tiles.
 * Accepted pairwise translations are propagated via BFS from an anchor tile to
 * produce globally-consistent pixel origins for every tile in the grid.</p>
 */
public class TileAligner {

   private static final double CORRELATION_THRESHOLD = 0.5;
   /** Reject a pairwise shift if its magnitude along the primary axis exceeds this fraction
    *  of the overlap strip width/height.  Shifts at or near the strip edge are almost always
    *  FHT wrap-around artefacts rather than genuine sub-pixel corrections. */
   private static final double MAX_SHIFT_FRACTION = 0.75;

   private final TiledDataProviderAPI storage_;
   private final HashMap<String, Object> baseAxes_;
   private final List<String> channelNames_;
   private final int tileWidth_;
   private final int tileHeight_;
   private final int overlapX_;
   private final int overlapY_;

   /** Optional per-tile orientation correction applied before strip extraction. */
   private boolean tileTransformMirror_ = false;
   private int tileTransformRotation_ = 0;

   /** Set by the last call to computeAlignedOrigins(); null until then. */
   private String lastAlignmentStats_ = null;

   /**
    * Returns a human-readable summary of the most recent alignment run, or null
    * if alignment has not been run yet.  Includes accepted/total pair counts and
    * the RMS deviation of aligned origins from nominal grid positions.
    */
   public String getLastAlignmentStats() {
      return lastAlignmentStats_;
   }

   private static class TranslationResult {
      final Point from;  // (col, row) in Point convention (x=col, y=row)
      final Point to;
      final float dx;
      final float dy;
      final double quality;

      TranslationResult(Point from, Point to, float dx, float dy, double quality) {
         this.from = from;
         this.to = to;
         this.dx = dx;
         this.dy = dy;
         this.quality = quality;
      }
   }

   public TileAligner(TiledDataProviderAPI storage, HashMap<String, Object> baseAxes,
                      List<String> channelNames, JSONObject summaryMetadata) {
      storage_ = storage;
      baseAxes_ = baseAxes;
      channelNames_ = channelNames;
      tileWidth_ = summaryMetadata.optInt("Width", 0);
      tileHeight_ = summaryMetadata.optInt("Height", 0);
      overlapX_ = summaryMetadata.optInt("GridPixelOverlapX", 0);
      overlapY_ = summaryMetadata.optInt("GridPixelOverlapY", 0);
   }

   /**
    * Configure an optional per-tile orientation correction to apply before strip extraction.
    *
    * <p>The same transform used during blending/stitching should be passed here so that
    * phase-correlation alignment operates on corrected tile images. For 90°/270° rotations
    * the horizontal and vertical overlap values are automatically swapped so the correct
    * overlap strip width/height is used for each axis after rotation.</p>
    *
    * @param mirror   true to apply a horizontal mirror before rotation
    * @param rotation rotation in degrees: 0, 90, 180, or 270
    */
   public void setTileTransform(boolean mirror, int rotation) {
      tileTransformMirror_ = mirror;
      tileTransformRotation_ = ((rotation % 360) + 360) % 360;
   }

   /**
    * Computes corrected pixel origins for each (col, row) tile at the given resolution level.
    * Falls back to the nominal grid position for tiles that cannot be reached via accepted
    * pairwise translations.
    *
    * @param resLevel Resolution level (0 = full res, 1 = half res, …).
    * @return Map from Point(col, row) to corrected pixel origin at full resolution.
    *         Returns null if alignment cannot be performed (zero overlap, single tile).
    */
   public Map<Point, Point2D.Float> computeAlignedOrigins(int resLevel) {
      return computeAlignedOrigins(resLevel, -1, pct -> {});
   }

   public Map<Point, Point2D.Float> computeAlignedOrigins(int resLevel, IntConsumer progress) {
      return computeAlignedOrigins(resLevel, -1, progress);
   }

   /**
    * @param maxDisplacementPx maximum allowed deviation from the nominal tile position
    *                          in full-resolution pixels. Tiles whose computed origin
    *                          deviates more than this are reset to nominal. Pass -1 to
    *                          disable the cutoff.
    */
   public Map<Point, Point2D.Float> computeAlignedOrigins(int resLevel, int maxDisplacementPx,
                                                           IntConsumer progress) {
      if (overlapX_ <= 0 && overlapY_ <= 0) {
         return null;
      }

      Set<Point> tiles = collectTiles();
      if (tiles.size() <= 1) {
         return null;
      }

      // When a tile transform is active, overlap axes may be swapped.
      // After a 90°/270° rotation, what was the horizontal (X) overlap between
      // left/right tile neighbours becomes the vertical (Y) overlap, and vice versa.
      boolean swapAxes = (tileTransformRotation_ == 90 || tileTransformRotation_ == 270);
      int effectiveOverlapX = swapAxes ? overlapY_ : overlapX_;
      int effectiveOverlapY = swapAxes ? overlapX_ : overlapY_;

      // Run alignment at the coarsest available resolution to keep FHT sizes manageable.
      // Corrections are then scaled up to full-resolution pixel coords for TileBlender.
      int alignResLevel = storage_.getNumResLevels() - 1;
      int alignScale = 1 << alignResLevel;
      int dsOverlapX = effectiveOverlapX / alignScale;
      int dsOverlapY = effectiveOverlapY / alignScale;

      // Probe the first available tile at full resolution (level 0) to get actual tile
      // dimensions — summary metadata Width/Height can be wrong (e.g. Height=212 when
      // the actual tile is 3544x2396). Most tiles exist at level 0; higher levels may
      // have sparse data only near the centre.
      // If a tile transform is active, the probed dims are the post-correction dims
      // (loadTileGray applies the transform), so we read them from the tile itself.
      int actualTileW = tileWidth_;
      int actualTileH = tileHeight_;
      outer:
      for (Point t : tiles) {
         for (String chName : channelNames_) {
            HashMap<String, Object> axes = buildAxesForTile(t.y, t.x, chName);
            if (axes == null) {
               continue;
            }
            TaggedImage probe = storage_.getImage(axes, 0);  // always probe at full res
            if (probe != null && probe.pix != null) {
               int nPix;
               if (probe.pix instanceof short[]) {
                  nPix = ((short[]) probe.pix).length;
               } else if (probe.pix instanceof byte[]) {
                  int bpp = (probe.tags != null) ? probe.tags.optInt("BytesPerPixel", 1) : 1;
                  nPix = ((byte[]) probe.pix).length / bpp;
               } else {
                  continue;
               }
               if (nPix > 0) {
                  int tw = (probe.tags != null) ? probe.tags.optInt("Width", 0) : 0;
                  if (tw > 0 && nPix % tw == 0) {
                     actualTileW = tw;
                     actualTileH = nPix / tw;
                     // If the tile transform swaps axes, swap the dims to match
                     if (swapAxes) {
                        int tmp = actualTileW;
                        actualTileW = actualTileH;
                        actualTileH = tmp;
                     }
                     break outer;
                  }
               }
            }
         }
      }
      int dsStepX = Math.max(1, (actualTileW - effectiveOverlapX) / alignScale);
      int dsStepY = Math.max(1, (actualTileH - effectiveOverlapY) / alignScale);

      List<TranslationResult> translations = computePairwiseTranslations(
              tiles, alignResLevel, dsStepX, dsStepY, dsOverlapX, dsOverlapY, alignScale, progress);

      // Count candidate pairs: each pair counted once (west+north neighbors only).
      int totalPairs = 0;
      for (Point t : tiles) {
         if (tiles.contains(new Point(t.x - 1, t.y)) && dsOverlapX > 0) {
            totalPairs++;
         }
         if (tiles.contains(new Point(t.x, t.y - 1)) && dsOverlapY > 0) {
            totalPairs++;
         }
      }

      // propagateOrigins returns full-resolution pixel coords regardless of alignResLevel.
      Map<Point, Point2D.Float> origins = propagateOrigins(tiles, translations,
            dsStepX, dsStepY, alignScale, maxDisplacementPx);

      // Compute RMS deviation of aligned origins from nominal grid positions.
      // Use full-resolution step (dsStepX * alignScale) — this matches propagateOrigins.
      int fullStepX = dsStepX * alignScale;
      int fullStepY = dsStepY * alignScale;
      double sumSqDev = 0;
      int nTiles = 0;
      for (Point t : tiles) {
         Point2D.Float o = origins.get(t);
         if (o != null) {
            float nomX = t.x * fullStepX;
            float nomY = t.y * fullStepY;
            float ddx = o.x - nomX;
            float ddy = o.y - nomY;
            sumSqDev += ddx * ddx + ddy * ddy;
            nTiles++;
         }
      }
      double rmsDevPx = nTiles > 0 ? Math.sqrt(sumSqDev / nTiles) : 0;

      lastAlignmentStats_ = String.format(
            "%d/%d pairs accepted, RMS deviation from grid: %.2f px",
            translations.size(), totalPairs, rmsDevPx);

      return origins;
   }

   private Set<Point> collectTiles() {
      Set<Point> result = new HashSet<>();
      for (HashMap<String, Object> stored : storage_.getAxesSet()) {
         if (!stored.containsKey(NDTiffStorage.ROW_AXIS)
                 || !stored.containsKey(NDTiffStorage.COL_AXIS)) {
            continue;
         }
         boolean matches = true;
         for (Map.Entry<String, Object> entry : baseAxes_.entrySet()) {
            Object storedVal = stored.get(entry.getKey());
            // Ignore axes absent from this stored entry (e.g. no z-axis in dataset)
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
            result.add(new Point(c, r)); // x=col, y=row
         }
      }
      return result;
   }

   /**
    * Loads a single tile as a float[] array (first available channel).
    * Returns null if no image is available.
    * Also fills tileDims[0]=width, tileDims[1]=height from the TaggedImage metadata.
    */
   private float[] loadTileGray(int row, int col, int resLevel, int[] tileDims) {
      for (String chName : channelNames_) {
         HashMap<String, Object> axes = buildAxesForTile(row, col, chName);
         if (axes == null) {
            continue;
         }
         TaggedImage ti = storage_.getImage(axes, resLevel);
         if (ti == null || ti.pix == null) {
            continue;
         }
         // "Width"/"Height" tags store full-resolution values; divide by scale.
         int scale = 1 << resLevel;
         int w = 0;
         int h = 0;
         if (ti.tags != null) {
            int wFull = ti.tags.optInt("Width", 0);
            int hFull = ti.tags.optInt("Height", 0);
            w = (wFull > 0) ? wFull / scale : 0;
            h = (hFull > 0) ? hFull / scale : 0;
         }
         float[] dst;
         int nPix;
         Object rawPix = ti.pix;
         if (rawPix instanceof short[]) {
            short[] src = (short[]) rawPix;
            nPix = src.length;
            // Fall back: derive from pixel count when tags are missing/wrong.
            if (w <= 0 || h <= 0 || w * h != nPix) {
               w = tileWidth_ / scale;
               if (w > 0 && nPix % w == 0) {
                  h = nPix / w;
               } else {
                  continue;
               }
            }
            // Apply per-tile orientation correction before strip extraction.
            if (tileTransformMirror_ || tileTransformRotation_ != 0) {
               Object[] xf = ImageTransformUtils.transformPixels(
                     src, w, h, tileTransformMirror_, tileTransformRotation_);
               src = (short[]) xf[0];
               w   = (Integer) xf[1];
               h   = (Integer) xf[2];
            }
            nPix = src.length;
            dst = new float[nPix];
            for (int i = 0; i < nPix; i++) {
               dst[i] = src[i] & 0xFFFF;
            }
         } else if (rawPix instanceof byte[]) {
            byte[] src = (byte[]) rawPix;
            int bpp = (ti.tags != null) ? ti.tags.optInt("BytesPerPixel", 1) : 1;
            if (bpp == 4) {
               // RGB32 (BGRA): apply correction, then convert to grayscale via luminance.
               nPix = src.length / 4;
               if (w <= 0 || h <= 0 || w * h != nPix) {
                  w = tileWidth_ / scale;
                  if (w > 0 && nPix % w == 0) {
                     h = nPix / w;
                  } else {
                     continue;
                  }
               }
               if (tileTransformMirror_ || tileTransformRotation_ != 0) {
                  Object[] xf = ImageTransformUtils.transformPixels(
                        src, w, h, 4, tileTransformMirror_, tileTransformRotation_);
                  src = (byte[]) xf[0];
                  w   = (Integer) xf[1];
                  h   = (Integer) xf[2];
               }
               nPix = src.length / 4;
               dst = new float[nPix];
               for (int i = 0; i < nPix; i++) {
                  // BGRA layout: byte 0=B, 1=G, 2=R, 3=A
                  float b = src[i * 4]     & 0xFF;
                  float g = src[i * 4 + 1] & 0xFF;
                  float r = src[i * 4 + 2] & 0xFF;
                  dst[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b;
               }
            } else {
               // 8-bit gray
               nPix = src.length;
               if (w <= 0 || h <= 0 || w * h != nPix) {
                  w = tileWidth_ / scale;
                  if (w > 0 && nPix % w == 0) {
                     h = nPix / w;
                  } else {
                     continue;
                  }
               }
               if (tileTransformMirror_ || tileTransformRotation_ != 0) {
                  Object[] xf = ImageTransformUtils.transformPixels(
                        src, w, h, tileTransformMirror_, tileTransformRotation_);
                  src = (byte[]) xf[0];
                  w   = (Integer) xf[1];
                  h   = (Integer) xf[2];
               }
               nPix = src.length;
               dst = new float[nPix];
               for (int i = 0; i < nPix; i++) {
                  dst[i] = src[i] & 0xFF;
               }
            }
         } else {
            continue;
         }
         tileDims[0] = w;
         tileDims[1] = h;
         return dst;
      }
      return null;
   }

   /**
    * Returns the next power of two >= n, capped at 4096 to keep FHT manageable.
    * Returns 0 if n <= 0.
    */
   private static int nextPow2(int n) {
      if (n <= 0) {
         return 0;
      }
      int p = 1;
      while (p < n && p < 4096) {
         p <<= 1;
      }
      return p;
   }

   /** Cache of Hann window arrays keyed by length. */
   private static final Map<Integer, double[]> HANN_CACHE = new ConcurrentHashMap<>();

   private static double[] hannWindow(int n) {
      return HANN_CACHE.computeIfAbsent(n, len -> {
         double[] w = new double[len];
         for (int i = 0; i < len; i++) {
            w[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (len - 1)));
         }
         return w;
      });
   }

   /**
    * Cross-correlates two image strips using FHT (Fast Hartley Transform).
    * Both strips must have the same dimensions (stripW x stripH).
    *
    * <p>FHT requires a square input, so the strips are centre-cropped to a square of
    * side {@code min(stripW, stripH)} before correlation. This avoids the zero-padding
    * artifacts that arise when a highly non-square strip (e.g. 246 x 1088) is padded
    * to a large square — the large zero-padded region dominates the correlation and
    * produces spurious peaks at the FHT wrap-around boundary.</p>
    *
    * <p>The crop side is then capped at 512 to keep the FHT manageable.</p>
    *
    * @return float[] pixel data of the correlation map (padN x padN, after swapQuadrants),
    *         where padN is the next power-of-two >= the cropped side length.
    *         Returns null if strip dimensions are degenerate.
    */
   private static float[] crossCorrelateStrip(float[] pix1, float[] pix2,
                                               int stripW, int stripH,
                                               int[] outSize) {
      if (stripW <= 0 || stripH <= 0) {
         return null;
      }

      // Crop to a square to avoid zero-padding artifacts from highly non-square strips.
      // For a vertical overlap strip (narrow and tall, stripW < stripH): crop from the
      // top rather than the centre — the top rows are closest to the tile edge where
      // contrast from the overlap region is highest.
      // For a horizontal overlap strip (wide and short, stripH < stripW): crop from the
      // left for the same reason.
      // Cap at MAX_DIM to keep FHT manageable.
      final int MAX_DIM = 512;
      int squareSide = Math.min(Math.min(stripW, stripH), MAX_DIM);
      if (squareSide != stripW || squareSide != stripH) {
         // Use top-left crop (offset 0) for the short dimension to sample the overlap edge.
         int offX = 0;
         int offY = 0;
         float[] c1 = new float[squareSide * squareSide];
         float[] c2 = new float[squareSide * squareSide];
         for (int y = 0; y < squareSide; y++) {
            System.arraycopy(pix1, (offY + y) * stripW + offX, c1, y * squareSide, squareSide);
            System.arraycopy(pix2, (offY + y) * stripW + offX, c2, y * squareSide, squareSide);
         }
         return crossCorrelateStrip(c1, c2, squareSide, squareSide, outSize);
      }

      // stripW == stripH == squareSide <= MAX_DIM at this point.
      int padN = nextPow2(stripW);
      if (padN <= 0) {
         return null;
      }
      outSize[0] = padN;

      // Subtract mean, then apply a 2D Hann window before padding.
      // The Hann window tapers all edges smoothly to zero so that the padded
      // zeros don't create a hard discontinuity that dominates the correlation.
      double sum1 = 0;
      double sum2 = 0;
      int n = stripW * stripH;
      for (int i = 0; i < n; i++) {
         sum1 += pix1[i];
         sum2 += pix2[i];
      }
      float mean1 = (float) (sum1 / n);
      float mean2 = (float) (sum2 / n);

      // Hann window weights — cached by size since all pairs typically share dimensions.
      double[] hannX = hannWindow(stripW);
      double[] hannY = hannWindow(stripH);

      float[] zm1 = new float[n];
      float[] zm2 = new float[n];
      for (int y = 0; y < stripH; y++) {
         for (int x = 0; x < stripW; x++) {
            float w = (float) (hannY[y] * hannX[x]);
            int idx = y * stripW + x;
            zm1[idx] = (pix1[idx] - mean1) * w;
            zm2[idx] = (pix2[idx] - mean2) * w;
         }
      }

      // Copy windowed strips into padded square arrays
      float[] padded1 = new float[padN * padN];
      float[] padded2 = new float[padN * padN];
      for (int y = 0; y < stripH; y++) {
         System.arraycopy(zm1, y * stripW, padded1, y * padN, stripW);
         System.arraycopy(zm2, y * stripW, padded2, y * padN, stripW);
      }

      FloatProcessor proc1 = new FloatProcessor(padN, padN, padded1, null);
      FloatProcessor proc2 = new FloatProcessor(padN, padN, padded2, null);

      FHT h1 = new FHT(proc1);
      h1.transform();
      FHT h2 = new FHT(proc2);
      h2.transform();
      FHT corr = h1.conjugateMultiply(h2);
      corr.inverseTransform();
      corr.swapQuadrants();

      return (float[]) corr.getPixels();
   }

   /**
    * Finds the peak in the correlation map and returns the sub-pixel shift as a
    * float[2] {dx, dy}.  Quality is peak / RMS of the map.
    *
    * <p>The integer peak location is refined to sub-pixel accuracy using parabolic
    * interpolation along each axis through the three pixels centred on the peak.</p>
    *
    * <p>Shifts where the integer component equals -n/2 are on the FHT wrap-around
    * seam and are rejected (quality set to 0).</p>
    *
    * @param corrMap  Correlation map pixels (square, side = n), after swapQuadrants.
    * @param n        Side length (power of two).
    * @param quality  Output array of length 1; filled with peak / RMS ratio.
    * @return float[2] containing sub-pixel {dx, dy} shift.
    */
   private static float[] findPeak(float[] corrMap, int n, double[] quality) {
      int bestIdx = 0;
      float bestVal = corrMap[0];
      double sumSq = 0;
      for (int i = 0; i < corrMap.length; i++) {
         float v = corrMap[i];
         if (v > bestVal) {
            bestVal = v;
            bestIdx = i;
         }
         sumSq += (double) v * v;
      }
      double rms = Math.sqrt(sumSq / corrMap.length);
      quality[0] = (rms > 0) ? bestVal / rms : 0;

      int px = bestIdx % n;
      int py = bestIdx / n;
      // After swapQuadrants the DC (zero-shift) is at (n/2, n/2).
      int dx = px - n / 2;
      int dy = py - n / 2;
      // Reject the ambiguous wrap-around seam.
      if (Math.abs(dx) == n / 2 || Math.abs(dy) == n / 2) {
         quality[0] = 0;
         return new float[]{dx, dy};
      }

      // Sub-pixel refinement via parabolic interpolation along each axis.
      // For axis X: fit parabola through (px-1, v_left), (px, v_peak), (px+1, v_right).
      // Peak of parabola at: offset = 0.5 * (v_left - v_right) / (v_left - 2*v_peak + v_right)
      // Clamp neighbours rather than wrap — wrapping reads zero-padded FHT border cells
      // which produces an asymmetric parabola and biases the sub-pixel estimate.
      int pxL = Math.max(0, px - 1);
      int pxR = Math.min(n - 1, px + 1);
      int pyU = Math.max(0, py - 1);
      int pyD = Math.min(n - 1, py + 1);
      float vL = corrMap[py * n + pxL];
      float vR = corrMap[py * n + pxR];
      float vU = corrMap[pyU * n + px];
      float vD = corrMap[pyD * n + px];
      float denomX = vL - 2 * bestVal + vR;
      float denomY = vU - 2 * bestVal + vD;
      float subX = (denomX != 0) ? 0.5f * (vL - vR) / denomX : 0;
      float subY = (denomY != 0) ? 0.5f * (vU - vD) / denomY : 0;
      // Clamp sub-pixel offset to ±0.5 to avoid runaway extrapolation.
      subX = Math.max(-0.5f, Math.min(0.5f, subX));
      subY = Math.max(-0.5f, Math.min(0.5f, subY));

      return new float[]{dx + subX, dy + subY};
   }

   /**
    * Extracts a vertical strip (left or right edge) from a full tile pixel array.
    *
    * @param tilePix  Full tile pixels (tileW x tileH).
    * @param tileW    Tile width.
    * @param tileH    Tile height.
    * @param fromRight If true, take the rightmost stripW columns; otherwise leftmost.
    * @param stripW   Strip width.
    * @return float[] of size stripW x tileH.
    */
   private static float[] extractVerticalStrip(float[] tilePix, int tileW, int tileH,
                                                boolean fromRight, int stripW) {
      int startX = fromRight ? (tileW - stripW) : 0;
      float[] strip = new float[stripW * tileH];
      for (int y = 0; y < tileH; y++) {
         System.arraycopy(tilePix, y * tileW + startX, strip, y * stripW, stripW);
      }
      return strip;
   }

   /**
    * Extracts a horizontal strip (top or bottom edge) from a full tile pixel array.
    *
    * @param tilePix    Full tile pixels (tileW x tileH).
    * @param tileW      Tile width.
    * @param tileH      Tile height.
    * @param fromBottom If true, take the bottom stripH rows; otherwise topmost.
    * @param stripH     Strip height.
    * @return float[] of size tileW x stripH.
    */
   private static float[] extractHorizontalStrip(float[] tilePix, int tileW, int tileH,
                                                   boolean fromBottom, int stripH) {
      int startY = fromBottom ? (tileH - stripH) : 0;
      float[] strip = new float[tileW * stripH];
      System.arraycopy(tilePix, startY * tileW, strip, 0, tileW * stripH);
      return strip;
   }

   /** Cached tile: pixels + actual dimensions from image tags. */
   private static class TilePixels {
      final float[] pix;
      final int w;
      final int h;

      TilePixels(float[] pix, int w, int h) {
         this.pix = pix;
         this.w = w;
         this.h = h;
      }
   }

   private List<TranslationResult> computePairwiseTranslations(
           Set<Point> tiles, int resLevel,
           int dsStepX, int dsStepY, int dsOverlapX, int dsOverlapY, int scale,
           IntConsumer progress) {

      List<TranslationResult> results = new ArrayList<>();

      // Cache loaded tile pixels keyed by (col, row) Point
      Map<Point, TilePixels> pixCache = new HashMap<>();

      int total = tiles.size();
      int done = 0;
      for (Point tile : tiles) {
         progress.accept(total > 0 ? (done * 100 / total) : 0);
         done++;
         int col = tile.x;
         int row = tile.y;

         TilePixels cur = getOrLoad(pixCache, row, col, resLevel);
         if (cur == null) {
            continue;
         }

         // --- Horizontal pair: current tile and its west neighbour (col-1) ---
         if (dsOverlapX > 0) {
            Point west = new Point(col - 1, row);
            if (tiles.contains(west)) {
               TilePixels westTile = getOrLoad(pixCache, row, col - 1, resLevel);
               if (westTile != null) {
                  // Use the smaller height so both strips have identical dimensions
                  int stripH = Math.min(cur.h, westTile.h);
                  int stripW = Math.min(dsOverlapX, Math.min(cur.w, westTile.w));
                  float[] strip1 = extractVerticalStrip(westTile.pix, westTile.w, stripH,
                          true, stripW);
                  float[] strip2 = extractVerticalStrip(cur.pix, cur.w, stripH,
                          false, stripW);
                  int[] outSize = new int[1];
                  float[] corrMap = crossCorrelateStrip(strip1, strip2, stripW, stripH, outSize);
                  if (corrMap != null) {
                     double[] quality = new double[1];
                     float[] shift = findPeak(corrMap, outSize[0], quality);
                     int maxDx = (int) (stripW * MAX_SHIFT_FRACTION);
                     if (quality[0] >= CORRELATION_THRESHOLD
                           && Math.abs(shift[0]) <= maxDx) {
                        results.add(new TranslationResult(
                              west, tile, shift[0], shift[1], quality[0]));
                     }
                  }
               }
            }
         }

         // --- Vertical pair: current tile and its north neighbour (row-1) ---
         if (dsOverlapY > 0) {
            Point north = new Point(col, row - 1);
            if (tiles.contains(north)) {
               TilePixels northTile = getOrLoad(pixCache, row - 1, col, resLevel);
               if (northTile != null) {
                  // Use the smaller width so both strips have identical dimensions
                  int stripW = Math.min(cur.w, northTile.w);
                  int stripH = Math.min(dsOverlapY, Math.min(cur.h, northTile.h));
                  float[] strip1 = extractHorizontalStrip(northTile.pix, northTile.w, northTile.h,
                          true, stripH);
                  float[] strip2 = extractHorizontalStrip(cur.pix, cur.w, cur.h,
                          false, stripH);
                  // Trim to common width if tiles differ
                  if (northTile.w != cur.w) {
                     strip1 = trimStripWidth(strip1, northTile.w, stripH, stripW);
                     strip2 = trimStripWidth(strip2, cur.w, stripH, stripW);
                  }
                  int[] outSize = new int[1];
                  float[] corrMap = crossCorrelateStrip(strip1, strip2, stripW, stripH, outSize);
                  if (corrMap != null) {
                     double[] quality = new double[1];
                     float[] shift = findPeak(corrMap, outSize[0], quality);
                     int maxDy = (int) (stripH * MAX_SHIFT_FRACTION);
                     if (quality[0] >= CORRELATION_THRESHOLD
                           && Math.abs(shift[1]) <= maxDy) {
                        results.add(new TranslationResult(
                              north, tile, shift[0], shift[1], quality[0]));
                     }
                  }
               }
            }
         }
      }

      return results;
   }

   private static float[] trimStripWidth(float[] strip, int srcW, int h, int dstW) {
      if (srcW == dstW) {
         return strip;
      }
      float[] out = new float[dstW * h];
      for (int y = 0; y < h; y++) {
         System.arraycopy(strip, y * srcW, out, y * dstW, dstW);
      }
      return out;
   }

   private TilePixels getOrLoad(Map<Point, TilePixels> cache, int row, int col, int resLevel) {
      Point key = new Point(col, row);
      if (!cache.containsKey(key)) {
         int[] dims = new int[2];
         float[] pix = loadTileGray(row, col, resLevel, dims);
         cache.put(key, pix != null ? new TilePixels(pix, dims[0], dims[1]) : null);
      }
      return cache.get(key);
   }

   /**
    * Globally optimises tile origins using weighted least squares.
    *
    * <p>Each accepted pairwise translation contributes one constraint:
    * {@code pos[to] - pos[from] = nominalStep + measuredShift * scale}.
    * An additional high-weight anchor constraint fixes tile (minRow, minCol) at its
    * nominal position.  The system is solved independently for X and Y via the
    * normal equations (AᵀWA · x = AᵀWb), using the correlation quality as the
    * per-constraint weight.  Tiles unreachable from the anchor (no accepted
    * translations connecting them) fall back to their nominal positions.</p>
    *
    * <p>Results are at full resolution (downsampled coords × scale).</p>
    */
   private Map<Point, Point2D.Float> propagateOrigins(
           Set<Point> tiles, List<TranslationResult> translations,
           int dsStepX, int dsStepY, int scale, int maxDisplacementPx) {

      // Assign a stable integer index to each tile.
      List<Point> tileList = new ArrayList<>(tiles);
      // Sort for determinism: by row then col.
      tileList.sort((a, b) -> a.y != b.y ? a.y - b.y : a.x - b.x);
      Map<Point, Integer> idx = new HashMap<>();
      for (int i = 0; i < tileList.size(); i++) {
         idx.put(tileList.get(i), i);
      }
      int n = tileList.size();

      // Anchor: tile at smallest row then col (index 0 after sort).
      Point anchor = tileList.get(0);
      float anchorNomX = anchor.x * dsStepX * scale;
      float anchorNomY = anchor.y * dsStepY * scale;

      // Normal equations: lhs (n×n) = AᵀWA, rhsX/rhsY (n) = AᵀWb for X and Y.
      double[] lhs = new double[n * n];
      double[] rhsX = new double[n];
      double[] rhsY = new double[n];

      // High-weight anchor constraint: pos[anchor] = nominalPos.
      final double anchorWeight = 1e6;
      lhs[0] += anchorWeight;
      rhsX[0] += anchorWeight * anchorNomX;
      rhsY[0] += anchorWeight * anchorNomY;

      // Weak regularization for every tile: pos[i] ≈ nominalPos with low weight.
      // This prevents the normal-equations matrix from being singular when a tile
      // has no accepted pairwise translations connecting it to any other tile — without
      // regularization, that tile's row/column is all-zeros and the matrix is singular,
      // causing the solver to return null and discard ALL tile positions.
      // With regularization, isolated tiles simply stay near their nominal positions.
      final double regWeight = 1e-3;
      for (int i = 0; i < n; i++) {
         Point t = tileList.get(i);
         float nomX = t.x * dsStepX * scale;
         float nomY = t.y * dsStepY * scale;
         lhs[i * n + i] += regWeight;
         rhsX[i] += regWeight * nomX;
         rhsY[i] += regWeight * nomY;
      }

      // One constraint per accepted translation: pos[to] - pos[from] = nomStep + shift*scale.
      for (TranslationResult tr : translations) {
         int iFrom = idx.get(tr.from);
         int iTo   = idx.get(tr.to);
         final float nomDx = (tr.to.x - tr.from.x) * dsStepX * scale;
         final float nomDy = (tr.to.y - tr.from.y) * dsStepY * scale;
         // Weight by correlation quality so high-confidence pairs have stronger influence.
         double w = tr.quality;
         // Row of A: coefficient +1 at iTo, -1 at iFrom.
         // lhs += w * aᵀa, rhs += w * aᵀ * b.
         lhs[iFrom * n + iFrom] += w;
         lhs[iTo   * n + iTo  ] += w;
         lhs[iFrom * n + iTo  ] -= w;
         lhs[iTo   * n + iFrom] -= w;
         final float bx = nomDx + tr.dx * scale;
         final float by = nomDy + tr.dy * scale;
         rhsX[iFrom] -= w * bx;
         rhsX[iTo  ] += w * bx;
         rhsY[iFrom] -= w * by;
         rhsY[iTo  ] += w * by;
      }

      // Solve lhs · x = rhs using Gaussian elimination with partial pivoting.
      double[] solX = solveSymmetric(lhs.clone(), rhsX.clone(), n);
      double[] solY = solveSymmetric(lhs.clone(), rhsY.clone(), n);

      Map<Point, Point2D.Float> origins = new HashMap<>();
      for (int i = 0; i < n; i++) {
         Point t = tileList.get(i);
         float ox = (solX != null) ? (float) solX[i] : t.x * dsStepX * scale;
         float oy = (solY != null) ? (float) solY[i] : t.y * dsStepY * scale;
         origins.put(t, new Point2D.Float(ox, oy));
      }

      // Apply displacement cutoff: remove translations involving excessively displaced
      // tiles and re-solve so that neighbors are not pulled toward the outlier position.
      if (maxDisplacementPx >= 0) {
         float maxDist = maxDisplacementPx;
         boolean anyReset = false;
         Set<Point> outliers = new HashSet<>();
         for (int i = 0; i < n; i++) {
            Point t = tileList.get(i);
            float nomX = t.x * dsStepX * scale;
            float nomY = t.y * dsStepY * scale;
            Point2D.Float o = origins.get(t);
            float ddx = o.x - nomX;
            float ddy = o.y - nomY;
            if (Math.sqrt(ddx * ddx + ddy * ddy) > maxDist) {
               outliers.add(t);
               anyReset = true;
            }
         }
         if (anyReset) {
            // Re-solve with outlier tiles removed from the set and their translations
            // excluded. Removing outliers from `tiles` ensures the anchor (smallest
            // remaining tile) is never itself an outlier, so the anchor constraint
            // pins a well-behaved tile at nominal position.
            Set<Point> nonOutlierTiles = new HashSet<>(tiles);
            nonOutlierTiles.removeAll(outliers);
            List<TranslationResult> filtered = new ArrayList<>();
            for (TranslationResult tr : translations) {
               if (!outliers.contains(tr.from) && !outliers.contains(tr.to)) {
                  filtered.add(tr);
               }
            }
            Map<Point, Point2D.Float> resolvedOrigins =
                  propagateOrigins(nonOutlierTiles, filtered, dsStepX, dsStepY, scale, -1);
            // Carry results into the final map; outlier tiles keep their nominal position.
            for (Point t : tiles) {
               if (outliers.contains(t)) {
                  float nomX = t.x * dsStepX * scale;
                  float nomY = t.y * dsStepY * scale;
                  origins.put(t, new Point2D.Float(nomX, nomY));
               } else {
                  Point2D.Float resolved = resolvedOrigins.get(t);
                  if (resolved != null) {
                     origins.put(t, resolved);
                  }
               }
            }
         }
      }

      return origins;
   }

   /**
    * Solves the symmetric positive-definite system A·x = b (in-place, n×n)
    * using Gaussian elimination with partial pivoting.
    * Returns the solution vector, or null if the matrix is singular.
    */
   private static double[] solveSymmetric(double[] mat, double[] b, int n) {
      // Gaussian elimination with partial pivoting; mat and b are modified in-place.
      for (int col = 0; col < n; col++) {
         // Find pivot row.
         int pivot = col;
         double maxVal = Math.abs(mat[col * n + col]);
         for (int row = col + 1; row < n; row++) {
            double v = Math.abs(mat[row * n + col]);
            if (v > maxVal) {
               maxVal = v;
               pivot = row;
            }
         }
         if (maxVal < 1e-12) {
            return null; // singular
         }
         // Swap rows col and pivot.
         if (pivot != col) {
            for (int k = 0; k < n; k++) {
               double tmp = mat[col * n + k];
               mat[col * n + k] = mat[pivot * n + k];
               mat[pivot * n + k] = tmp;
            }
            double tmp = b[col];
            b[col] = b[pivot];
            b[pivot] = tmp;
         }
         // Eliminate below.
         double diag = mat[col * n + col];
         for (int row = col + 1; row < n; row++) {
            double factor = mat[row * n + col] / diag;
            for (int k = col; k < n; k++) {
               mat[row * n + k] -= factor * mat[col * n + k];
            }
            b[row] -= factor * b[col];
         }
      }
      // Back substitution.
      double[] x = new double[n];
      for (int i = n - 1; i >= 0; i--) {
         double sum = b[i];
         for (int j = i + 1; j < n; j++) {
            sum -= mat[i * n + j] * x[j];
         }
         x[i] = sum / mat[i * n + i];
      }
      return x;
   }

   private HashMap<String, Object> buildAxesForTile(int row, int col, String chName) {
      for (HashMap<String, Object> stored : storage_.getAxesSet()) {
         if (!stored.containsKey(NDTiffStorage.ROW_AXIS)
                 || !stored.containsKey(NDTiffStorage.COL_AXIS)) {
            continue;
         }
         boolean matches = true;
         for (Map.Entry<String, Object> entry : baseAxes_.entrySet()) {
            Object storedVal = stored.get(entry.getKey());
            // Ignore axes absent from this stored entry (e.g. no z-axis in dataset)
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
         if (chName != null) {
            Object storedCh = stored.get("channel");
            if (!ChannelUtils.channelValuesMatch(chName, storedCh)) {
               continue;
            }
         }
         HashMap<String, Object> axes = new HashMap<>(stored);
         axes.put(NDTiffStorage.ROW_AXIS, row);
         axes.put(NDTiffStorage.COL_AXIS, col);
         return axes;
      }
      return null;
   }

}
