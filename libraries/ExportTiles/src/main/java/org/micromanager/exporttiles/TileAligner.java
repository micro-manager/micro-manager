package org.micromanager.exporttiles;

import ij.process.FHT;
import ij.process.FloatProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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

   private static class TranslationResult {
      final Point from;  // (col, row) in Point convention (x=col, y=row)
      final Point to;
      final float dx;
      final float dy;

      TranslationResult(Point from, Point to, float dx, float dy) {
         this.from = from;
         this.to = to;
         this.dx = dx;
         this.dy = dy;
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

      // propagateOrigins returns full-resolution pixel coords regardless of alignResLevel.
      return propagateOrigins(tiles, translations, dsStepX, dsStepY, alignScale,
            maxDisplacementPx);
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

   /**
    * Cross-correlates two image strips using FHT (Fast Hartley Transform).
    * Both strips must have the same dimensions (stripW x stripH).
    * Pads to the next power-of-two square before computing.
    * Strips wider/taller than 512 px are centre-cropped to 512 before correlation.
    *
    * @return float[] pixel data of the correlation map (padW x padH, after swapQuadrants).
    *         Returns null if strip dimensions are degenerate.
    */
   private static float[] crossCorrelateStrip(float[] pix1, float[] pix2,
                                               int stripW, int stripH,
                                               int[] outSize) {
      if (stripW <= 0 || stripH <= 0) {
         return null;
      }
      // Cap strip dimensions to keep the FHT square manageable.
      final int MAX_DIM = 512;
      if (stripW > MAX_DIM || stripH > MAX_DIM) {
         // Centre-crop both strips to MAX_DIM x MAX_DIM.
         int cropW = Math.min(stripW, MAX_DIM);
         int cropH = Math.min(stripH, MAX_DIM);
         int offX = (stripW - cropW) / 2;
         int offY = (stripH - cropH) / 2;
         float[] c1 = new float[cropW * cropH];
         float[] c2 = new float[cropW * cropH];
         for (int y = 0; y < cropH; y++) {
            System.arraycopy(pix1, (offY + y) * stripW + offX, c1, y * cropW, cropW);
            System.arraycopy(pix2, (offY + y) * stripW + offX, c2, y * cropW, cropW);
         }
         return crossCorrelateStrip(c1, c2, cropW, cropH, outSize);
      }

      int padN = nextPow2(Math.max(stripW, stripH));
      if (padN <= 0) {
         return null;
      }
      outSize[0] = padN;

      // Subtract mean from each strip (zero-mean) before padding to suppress the DC
      // component in the phase correlation.  Without this the dominant frequency is
      // always the mean value, producing a spurious peak at (±N/2, ±N/2).
      double sum1 = 0;
      double sum2 = 0;
      int n = stripW * stripH;
      for (int i = 0; i < n; i++) {
         sum1 += pix1[i];
         sum2 += pix2[i];
      }
      float mean1 = (float) (sum1 / n);
      float mean2 = (float) (sum2 / n);
      float[] zm1 = new float[n];
      float[] zm2 = new float[n];
      for (int i = 0; i < n; i++) {
         zm1[i] = pix1[i] - mean1;
         zm2[i] = pix2[i] - mean2;
      }

      // Copy strips into padded square arrays
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
    * Finds the peak in the correlation map and returns {peakX, peakY} shifted
    * so that (0,0) means no translation.  Also evaluates quality as
    * peak / mean (returned via quality[0]).
    *
    * @param corrMap  Correlation map pixels (square, side = n).
    * @param n        Side length (power of two).
    * @param quality  Output array of length 1; filled with peak-to-mean ratio.
    * @return int[2] containing {dx, dy} shift.
    */
   private static int[] findPeak(float[] corrMap, int n, double[] quality) {
      int bestIdx = 0;
      float bestVal = corrMap[0];
      double sum = 0;
      for (int i = 0; i < corrMap.length; i++) {
         float v = corrMap[i];
         if (v > bestVal) {
            bestVal = v;
            bestIdx = i;
         }
         sum += v;
      }
      double mean = sum / corrMap.length;
      quality[0] = (mean > 0) ? bestVal / mean : 0;

      int px = bestIdx % n;
      int py = bestIdx / n;
      // After swapQuadrants, centre of image = no shift.
      // Shift from image-centre convention:
      if (px > n / 2) {
         px -= n;
      }
      if (py > n / 2) {
         py -= n;
      }
      return new int[]{px, py};
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
                     int[] shift = findPeak(corrMap, outSize[0], quality);
                     int maxDx = (int) (stripW * MAX_SHIFT_FRACTION);
                     if (quality[0] >= CORRELATION_THRESHOLD
                           && Math.abs(shift[0]) <= maxDx) {
                        results.add(new TranslationResult(west, tile, shift[0], shift[1]));
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
                     int[] shift = findPeak(corrMap, outSize[0], quality);
                     int maxDy = (int) (stripH * MAX_SHIFT_FRACTION);
                     if (quality[0] >= CORRELATION_THRESHOLD
                           && Math.abs(shift[1]) <= maxDy) {
                        results.add(new TranslationResult(north, tile, shift[0], shift[1]));
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
    * BFS from anchor tile, propagating corrected origins through accepted translations.
    * Results are at full resolution (not downsampled).
    */
   private Map<Point, Point2D.Float> propagateOrigins(
           Set<Point> tiles, List<TranslationResult> translations,
           int dsStepX, int dsStepY, int scale, int maxDisplacementPx) {

      // Build adjacency: for each tile, list of (neighbour, dx, dy)
      Map<Point, List<TranslationResult>> adjFrom = new HashMap<>();
      Map<Point, List<TranslationResult>> adjTo = new HashMap<>();
      for (Point t : tiles) {
         adjFrom.put(t, new ArrayList<>());
         adjTo.put(t, new ArrayList<>());
      }
      for (TranslationResult tr : translations) {
         adjFrom.get(tr.from).add(tr);
         adjTo.get(tr.to).add(tr);
      }

      // Pick anchor: smallest row then col
      Point anchor = null;
      for (Point t : tiles) {
         if (anchor == null || t.y < anchor.y || (t.y == anchor.y && t.x < anchor.x)) {
            anchor = t;
         }
      }

      Map<Point, Point2D.Float> origins = new HashMap<>();

      // Anchor gets nominal position (in downsampled coords × scale = full-res coords)
      float anchorX = anchor.x * dsStepX * scale;
      float anchorY = anchor.y * dsStepY * scale;
      origins.put(anchor, new Point2D.Float(anchorX, anchorY));

      // BFS
      Queue<Point> queue = new ArrayDeque<>();
      Set<Point> visited = new HashSet<>();
      queue.add(anchor);
      visited.add(anchor);

      while (!queue.isEmpty()) {
         Point cur = queue.poll();
         Point2D.Float curOrigin = origins.get(cur);

         // Traverse edges where cur is the "from" tile
         for (TranslationResult tr : adjFrom.get(cur)) {
            if (!visited.contains(tr.to)) {
               visited.add(tr.to);
               // to is one col/row step ahead of from
               float nomDx = (tr.to.x - cur.x) * dsStepX * scale;
               float nomDy = (tr.to.y - cur.y) * dsStepY * scale;
               float corrX = curOrigin.x + nomDx + tr.dx * scale;
               float corrY = curOrigin.y + nomDy + tr.dy * scale;
               origins.put(tr.to, new Point2D.Float(corrX, corrY));
               queue.add(tr.to);
            }
         }

         // Traverse edges where cur is the "to" tile (reverse direction)
         for (TranslationResult tr : adjTo.get(cur)) {
            if (!visited.contains(tr.from)) {
               visited.add(tr.from);
               float nomDx = (tr.from.x - cur.x) * dsStepX * scale;
               float nomDy = (tr.from.y - cur.y) * dsStepY * scale;
               float corrX = curOrigin.x + nomDx - tr.dx * scale;
               float corrY = curOrigin.y + nomDy - tr.dy * scale;
               origins.put(tr.from, new Point2D.Float(corrX, corrY));
               queue.add(tr.from);
            }
         }
      }

      // Fill in nominal positions for any unreached tiles
      for (Point t : tiles) {
         if (!origins.containsKey(t)) {
            origins.put(t, new Point2D.Float(t.x * dsStepX * scale, t.y * dsStepY * scale));
         }
      }

      // Apply displacement cutoff: reset any tile whose computed origin deviates from
      // its nominal position by more than maxDisplacementPx back to the nominal position.
      if (maxDisplacementPx >= 0) {
         float maxDist = maxDisplacementPx;
         for (Point t : tiles) {
            float nomX = t.x * dsStepX * scale;
            float nomY = t.y * dsStepY * scale;
            Point2D.Float o = origins.get(t);
            float dx = o.x - nomX;
            float dy = o.y - nomY;
            if (Math.sqrt(dx * dx + dy * dy) > maxDist) {
               origins.put(t, new Point2D.Float(nomX, nomY));
            }
         }
      }

      return origins;
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
            if (!channelValuesMatch(chName, storedCh)) {
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

   /**
    * Returns true when a channel name from the caller matches a channel value from storage.
    *
    * <p>Handles the case where unnamed channels are stored as {@code Integer} indices
    * but the caller passes the index as a {@code String} (e.g. {@code "0"}).</p>
    */
   private static boolean channelValuesMatch(String callerName, Object storedValue) {
      if (storedValue == null) {
         return false;
      }
      if (callerName.equals(storedValue)) {
         return true;
      }
      // Unnamed channel: storedValue may be an Integer index; callerName may be its string form.
      if (storedValue instanceof Integer) {
         try {
            return Integer.parseInt(callerName) == (Integer) storedValue;
         } catch (NumberFormatException e) {
            return false;
         }
      }
      return false;
   }
}
