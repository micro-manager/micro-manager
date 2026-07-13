package org.micromanager.tileddataprovider;

import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.mmomezarr.Compression;
import org.micromanager.mmomezarr.OMEZarrImage;
import org.micromanager.mmomezarr.OMEZarrStorage;
import org.micromanager.mmomezarr.OMEZarrStorageConfig;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.ImageWrittenListener;
import org.micromanager.ndtiffstorage.IndexEntryData;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;

/**
 * Bridges the {@code MM-OME-Zarr-Storage} library ({@link OMEZarrStorage}) to the
 * {@link MultiresNDTiffAPI} used throughout Micro-Manager's tiled-data stack (TiledDataProvider,
 * TiledDataViewer, Explorer). Because this implements the same interface the stack already
 * targets, existing consumers — including {@link NDTiffProviderAdapter} and
 * {@code ExplorerDataSource} — work against it unchanged; only the choice of which
 * implementation to instantiate differs.
 *
 * <h2>Tiled mosaics on OME-Zarr</h2>
 * The underlying library stores each frame as a plane addressed by an axes map, and downsamples
 * each plane independently to build the pyramid. This bridge treats the explore grid's
 * {@code row}/{@code column} as ordinary axes, so every tile is a separate plane, and composites
 * them on demand in {@link #getDisplayImage} (placing each tile at its grid offset, later tiles
 * overwriting the overlap seam). A per-tile pyramid downsampled by 2^level and placed on a
 * correspondingly scaled grid yields a valid mosaic pyramid for display.
 *
 * <h2>Axis value translation</h2>
 * OME-Zarr arrays are indexed by integers, but Micro-Manager uses string channel names. This
 * bridge maps non-integer axis values (notably {@code channel}) to integer indices (seeded from
 * the summary's {@code ChNames}), stores the mapping in custom metadata, and reverses it so
 * callers continue to see the original values.
 *
 * <p>Grayscale 8- and 16-bit only: RGB and 32-bit (float) images are rejected, because the
 * on-demand mosaic compositor works in {@code byte[]}/{@code short[]}.
 */
public final class OMEZarrMultiresStorage implements MultiresNDTiffAPI {

   private static final String ROW = org.micromanager.ndtiffstorage.NDTiffStorage.ROW_AXIS;
   private static final String COL = org.micromanager.ndtiffstorage.NDTiffStorage.COL_AXIS;
   private static final String AXIS_MAP_KEY = "mmTiledAxisIndex";
   private static final String DISPLAY_SETTINGS_KEY = "mmDisplaySettings";

   private final OMEZarrStorage store_;
   private final boolean readOnly_;
   private final JSONObject summary_;
   private final int overlapX_;
   private final int overlapY_;

   private volatile int tileWidth_ = -1;
   private volatile int tileHeight_ = -1;
   private volatile int bitDepth_ = 16;
   private volatile boolean rgb_ = false;
   private volatile JSONObject displaySettings_ = new JSONObject();

   /** Authoritative index of stored images keyed by the caller's original axes maps. */
   private final Set<HashMap<String, Object>> axesSet_ = ConcurrentHashMap.newKeySet();
   /**
    * Per-axis ordered value list; the (arrival-order) index into the list is the non-negative
    * integer used to address the OME-Zarr array. Every axis value is mapped this way — not just
    * string channel names — so that signed tile coordinates ({@code row}/{@code column} can be
    * negative when exploring above/left of the origin) become valid dense array indices.
    * Original values (with their type) are preserved for the reverse mapping.
    */
   private final Map<String, List<Object>> axisValues_ = new ConcurrentHashMap<>();

   // ---------------------------------------------------------------------------------------
   // Construction
   // ---------------------------------------------------------------------------------------

   /**
    * Create a new tiled OME-Zarr dataset for writing.
    *
    * @param dir             parent directory
    * @param name            dataset name (a {@code .ome.zarr} folder is created under it)
    * @param summaryMetadata summary metadata (should include {@code GridPixelOverlapX/Y},
    *                        {@code PixelSize_um}, {@code ChNames} when available)
    * @param overlapX        tile overlap in x (pixels)
    * @param overlapY        tile overlap in y (pixels)
    * @param savingQueueSize bounded writer-queue size (back-pressure)
    */
   public OMEZarrMultiresStorage(String dir, String name, JSONObject summaryMetadata,
                                 int overlapX, int overlapY, int savingQueueSize) {
      this.readOnly_ = false;
      this.summary_ = summaryMetadata != null ? summaryMetadata : new JSONObject();
      this.overlapX_ = overlapX;
      this.overlapY_ = overlapY;
      seedChannelNames();

      double pixelSizeUm = summary_.optDouble("PixelSize_um", 1.0);
      if (pixelSizeUm <= 0) {
         pixelSizeUm = 1.0;
      }
      OMEZarrStorageConfig cfg = new OMEZarrStorageConfig()
            .numResolutionLevels(1)
            .autoDownsample(true)
            .compression(Compression.ZSTD)
            .pixelSize(pixelSizeUm)
            .spatialUnit("micrometer")
            .savingQueueSize(savingQueueSize > 0 ? savingQueueSize : 50);
      this.store_ = new OMEZarrStorage(dir, name, summary_.toString(), cfg);
   }

   /** Open an existing OME-Zarr dataset for viewing. */
   public OMEZarrMultiresStorage(String dir) {
      this.readOnly_ = true;
      this.store_ = OMEZarrStorage.load(dir);
      String summaryJson = store_.getSummaryMetadata();
      this.summary_ = parseJson(summaryJson != null ? summaryJson : "{}");
      this.overlapX_ = summary_.optInt("GridPixelOverlapX", 0);
      this.overlapY_ = summary_.optInt("GridPixelOverlapY", 0);
      loadAxisMaps();
      loadDisplaySettings();
      rebuildIndexFromStore();
   }

   /** Returns true if {@code dir} looks like an OME-Zarr dataset (has a root {@code zarr.json}). */
   public static boolean isOMEZarrDataset(String dir) {
      return new File(dir, "zarr.json").exists();
   }

   // ---------------------------------------------------------------------------------------
   // Write path
   // ---------------------------------------------------------------------------------------

   @Override
   public Future<IndexEntryData> putImageMultiRes(Object pixels, JSONObject metadata,
                                                  HashMap<String, Object> axes, boolean rgb,
                                                  int bitDepth, int imageHeight, int imageWidth) {
      return putImage(pixels, metadata, axes, rgb, bitDepth, imageHeight, imageWidth);
   }

   @Override
   public Future<IndexEntryData> putImage(Object pixels, JSONObject metadata,
                                          HashMap<String, Object> axes, boolean rgb,
                                          int bitDepth, int imageHeight, int imageWidth) {
      if (rgb) {
         throw new UnsupportedOperationException(
               "The OME-Zarr backend does not support RGB images.");
      }
      if (bitDepth > 16) {
         // The on-demand mosaic compositor (getDisplayImage/blit) works in byte[]/short[]; a
         // 32-bit (GRAY32) image is float[] and would fail with an ArrayStoreException mid-render.
         throw new UnsupportedOperationException(
               "The OME-Zarr backend supports 8- and 16-bit grayscale only (got bitDepth="
               + bitDepth + ").");
      }
      this.bitDepth_ = bitDepth;
      this.rgb_ = rgb;
      if (tileWidth_ < 0) {
         tileWidth_ = imageWidth;
         tileHeight_ = imageHeight;
      }
      HashMap<String, Object> original = new HashMap<>(axes);
      axesSet_.add(original);
      Map<String, Object> translated = translate(original);
      String metaJson = metadata != null ? metadata.toString() : null;
      Future<Void> f = store_.putImage(pixels, metaJson, translated, rgb, bitDepth,
            imageHeight, imageWidth);
      return wrap(f);
   }

   @Override
   public void increaseMaxResolutionLevel(int newMaxResolutionLevel) {
      // NDTiff's argument is a max level *index*; OME-Zarr counts levels. Additional low-res levels
      // may be added on demand even for a reopened dataset (the viewer calls this while zooming),
      // but augmenting a reopened dataset is best-effort: if the data can't be written (e.g.
      // read-only media) swallow the error rather than letting it crash the viewer's event thread.
      try {
         store_.setMaxResolutionLevel(newMaxResolutionLevel + 1);
      } catch (RuntimeException e) {
         if (readOnly_) {
            return;
         }
         throw e;
      }
   }

   @Override
   public void finishedWriting() {
      persistAxisMaps();
      persistDisplaySettings();
      store_.finishedWriting();
   }

   // ---------------------------------------------------------------------------------------
   // Read path
   // ---------------------------------------------------------------------------------------

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes) {
      return getImage(axes, 0);
   }

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes, int resolutionLevel) {
      OMEZarrImage img = store_.getImage(translate(axes), resolutionLevel);
      if (img == null || img.pix == null) {
         return null;
      }
      // A genuinely stored plane always carries per-image metadata. When the metadata is
      // absent the requested axes did not match a stored image: the OME-Zarr backend
      // silently defaults any missing spatial axis (row/column) to 0 and returns plane
      // (0,0)'s pixels, but there is no metadata under the partial key. Returning that
      // orphan pixel array with empty tags makes convertTaggedImage() throw
      // ("Zero or negative image size") because Width/Height are missing. Treat it as
      // "not found" so callers fall through to their next lookup (e.g. getAnyImage()).
      // This matters for the overlay bridge, which queries a composite (partial-axes)
      // position to obtain a representative image for the scale bar and other overlays.
      String meta = img.metadataJson;
      if (meta == null || meta.isEmpty() || "{}".equals(meta.trim())) {
         return null;
      }
      return new TaggedImage(img.pix, parseJson(meta));
   }

   @Override
   public JSONObject getImageMetadata(HashMap<String, Object> axes) {
      String meta = store_.getImageMetadata(translate(axes));
      return meta == null ? null : parseJson(meta);
   }

   @Override
   public boolean hasImage(HashMap<String, Object> axes) {
      return store_.hasImage(translate(axes));
   }

   @Override
   public boolean hasImage(HashMap<String, Object> axes, int resolutionLevel) {
      return store_.hasImage(translate(axes), resolutionLevel);
   }

   @Override
   public EssentialImageMetadata getEssentialImageMetadata(HashMap<String, Object> axes) {
      return getEssentialImageMetadata(axes, 0);
   }

   @Override
   public EssentialImageMetadata getEssentialImageMetadata(HashMap<String, Object> axes,
                                                           int resolutionLevel) {
      org.micromanager.mmomezarr.EssentialImageMetadata e =
            store_.getEssentialImageMetadata(translate(axes), resolutionLevel);
      if (e == null) {
         return null;
      }
      return new EssentialImageMetadata(e.getWidth(), e.getHeight(), e.getBitDepth(), e.isRGB());
   }

   @Override
   public TaggedImage getSubImage(HashMap<String, Object> axes, int xOffset, int yOffset,
                                  int width, int height) {
      return getDisplayImage(axes, 0, xOffset, yOffset, width, height);
   }

   @Override
   public Set<HashMap<String, Object>> getAxesSet() {
      return new HashSet<>(axesSet_);
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return summary_;
   }

   @Override
   public boolean isFinished() {
      return store_.isFinished();
   }

   @Override
   public int getNumResLevels() {
      return store_.getNumResLevels();
   }

   @Override
   public String getDiskLocation() {
      return store_.getDiskLocation();
   }

   @Override
   public String getUniqueAcqName() {
      return store_.getUniqueAcqName();
   }

   @Override
   public int[] getImageBounds() {
      int strideX = Math.max(1, tileWidth_ - overlapX_);
      int strideY = Math.max(1, tileHeight_ - overlapY_);
      Integer minRow = null;
      Integer maxRow = null;
      Integer minCol = null;
      Integer maxCol = null;
      for (HashMap<String, Object> a : axesSet_) {
         Integer row = intOf(a.get(ROW));
         Integer col = intOf(a.get(COL));
         if (row == null || col == null) {
            continue;
         }
         minRow = minRow == null ? row : Math.min(minRow, row);
         maxRow = maxRow == null ? row : Math.max(maxRow, row);
         minCol = minCol == null ? col : Math.min(minCol, col);
         maxCol = maxCol == null ? col : Math.max(maxCol, col);
      }
      if (minRow == null) {
         return new int[]{0, 0, Math.max(0, tileWidth_), Math.max(0, tileHeight_)};
      }
      // Tiles are displayed trimmed to their effective (non-overlapping) footprint, so each tile
      // spans exactly one stride; the extent runs to (maxCol+1)*stride, not maxCol*stride+fullTile.
      int xMin = minCol * strideX;
      int yMin = minRow * strideY;
      int xMax = (maxCol + 1) * strideX;
      int yMax = (maxRow + 1) * strideY;
      return new int[]{xMin, yMin, xMax, yMax};
   }

   @Override
   public Set<Point> getTileIndicesWithDataAt(String axisName, int axisIndex) {
      Set<Point> points = new HashSet<>();
      for (HashMap<String, Object> a : axesSet_) {
         Integer atAxis = intOf(a.get(axisName));
         if (atAxis == null || atAxis != axisIndex) {
            continue;
         }
         Integer row = intOf(a.get(ROW));
         Integer col = intOf(a.get(COL));
         if (row != null && col != null) {
            points.add(new Point(col, row)); // (x=col, y=row), matching NDTiff
         }
      }
      return points;
   }

   @Override
   @Deprecated
   public Set<Point> getTileIndicesWithDataAt(int axisIndex) {
      return getTileIndicesWithDataAt("z", axisIndex);
   }

   @Override
   public TaggedImage getDisplayImage(HashMap<String, Object> axes, int resolutionLevel,
                                      int xOffset, int yOffset, int imageWidth, int imageHeight) {
      HashMap<String, Object> base = new HashMap<>(axes);
      base.remove(ROW);
      base.remove(COL);

      int strideX0 = Math.max(1, tileWidth_ - overlapX_);
      int strideY0 = Math.max(1, tileHeight_ - overlapY_);
      int strideXL = Math.max(1, strideX0 >> resolutionLevel);
      int strideYL = Math.max(1, strideY0 >> resolutionLevel);

      boolean isByte = bitDepth_ <= 8;
      int n = imageWidth * imageHeight;
      Object out = isByte ? new byte[n] : new short[n];
      JSONObject tags = null;

      for (HashMap<String, Object> a : axesSet_) {
         if (!matchesBase(a, base)) {
            continue;
         }
         Integer row = intOf(a.get(ROW));
         Integer col = intOf(a.get(COL));
         if (row == null || col == null) {
            continue;
         }
         TaggedImage tile = getImage(a, resolutionLevel);
         if (tile == null || tile.pix == null) {
            continue;
         }
         if (tags == null) {
            tags = tile.tags;
         }
         EssentialImageMetadata em = getEssentialImageMetadata(a, resolutionLevel);
         int tw = em != null ? em.width : (tileWidth_ >> resolutionLevel);
         int th = em != null ? em.height : (tileHeight_ >> resolutionLevel);
         // Display seamless (non-overlapping) tiles: show only the center "effective" region of
         // each stored tile, skipping overlap/2 on the top-left edge, exactly as NDTiff does. This
         // makes the painted tile the same size as the red stage-position square, which the viewer
         // draws at (tile - overlap). Blitting the full tile instead paints each frame ~overlap
         // wider/taller than that square.
         int overlapXL = Math.max(0, overlapX_ >> resolutionLevel);
         int overlapYL = Math.max(0, overlapY_ >> resolutionLevel);
         int srcX0 = overlapXL / 2;
         int srcY0 = overlapYL / 2;
         int copyW = Math.min(strideXL, tw - srcX0);
         int copyH = Math.min(strideYL, th - srcY0);
         int destX0 = col * strideXL - xOffset;
         int destY0 = row * strideYL - yOffset;
         blit(out, imageWidth, imageHeight, tile.pix, tw, th,
               srcX0, srcY0, copyW, copyH, destX0, destY0, isByte);
      }
      if (tags == null) {
         tags = new JSONObject();
      }
      return new TaggedImage(out, tags);
   }

   // ---------------------------------------------------------------------------------------
   // Display settings (opaque, persisted in custom metadata)
   // ---------------------------------------------------------------------------------------

   @Override
   public void setDisplaySettings(JSONObject displaySettings) {
      this.displaySettings_ = displaySettings != null ? displaySettings : new JSONObject();
      persistDisplaySettings();
   }

   @Override
   public JSONObject getDisplaySettings() {
      return displaySettings_;
   }

   // ---------------------------------------------------------------------------------------
   // Lifecycle / misc
   // ---------------------------------------------------------------------------------------

   @Override
   public void checkForWritingException() throws Exception {
      store_.checkForWritingException();
   }

   @Override
   public void close() {
      store_.close();
   }

   @Override
   public void closeAndWait() throws InterruptedException {
      store_.closeAndWait();
   }

   @Override
   public int getWritingQueueTaskSize() {
      return store_.getWritingQueueTaskSize();
   }

   @Override
   public int getWritingQueueTaskMaxSize() {
      return store_.getWritingQueueTaskMaxSize();
   }

   @Override
   @Deprecated
   public void addImageWrittenListener(ImageWrittenListener iwc) {
      // Not supported; callers await the Future returned by putImage instead.
   }

   // ---------------------------------------------------------------------------------------
   // Axis value translation
   // ---------------------------------------------------------------------------------------

   private void seedChannelNames() {
      JSONArray chNames = summary_.optJSONArray("ChNames");
      if (chNames == null) {
         return;
      }
      List<Object> values = new ArrayList<>();
      for (int i = 0; i < chNames.length(); i++) {
         values.add(String.valueOf(chNames.opt(i)));
      }
      if (!values.isEmpty()) {
         axisValues_.put("channel", new ArrayList<>(values));
      }
   }

   /**
    * Convert a caller axes map to the non-negative integer-indexed form OME-Zarr stores. Every
    * axis value is mapped to its arrival-order index (see {@link #axisValues_}), which turns
    * signed tile coordinates into valid dense array indices.
    */
   private Map<String, Object> translate(Map<String, Object> axes) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<String, Object> e : axes.entrySet()) {
         out.put(e.getKey(), indexFor(e.getKey(), normalizeValue(e.getValue())));
      }
      return out;
   }

   /** Inverse of {@link #translate}, used to rebuild original axes on reopen. */
   private HashMap<String, Object> reverseTranslate(Map<String, Object> intAxes) {
      HashMap<String, Object> out = new HashMap<>();
      for (Map.Entry<String, Object> e : intAxes.entrySet()) {
         List<Object> values = axisValues_.get(e.getKey());
         Integer idx = intOf(e.getValue());
         if (values != null && idx != null && idx >= 0 && idx < values.size()) {
            out.put(e.getKey(), values.get(idx));
         } else {
            out.put(e.getKey(), e.getValue());
         }
      }
      return out;
   }

   private synchronized int indexFor(String axis, Object value) {
      List<Object> values = axisValues_.computeIfAbsent(axis, k -> new ArrayList<>());
      int idx = values.indexOf(value);
      if (idx < 0) {
         idx = values.size();
         values.add(value);
      }
      return idx;
   }

   /** Normalize numeric axis values to Integer so equal indices compare equal across boxings. */
   private static Object normalizeValue(Object value) {
      return (value instanceof Number) ? ((Number) value).intValue() : value;
   }

   private void persistAxisMaps() {
      try {
         JSONObject obj = new JSONObject();
         for (Map.Entry<String, List<Object>> e : axisValues_.entrySet()) {
            JSONArray arr = new JSONArray();
            for (Object v : e.getValue()) {
               arr.put(v);
            }
            obj.put(e.getKey(), arr);
         }
         store_.setCustomMetadata(AXIS_MAP_KEY, obj.toString());
      } catch (Exception e) {
         // Best-effort persistence of the axis-index map; ignore serialization failures.
      }
   }

   private void loadAxisMaps() {
      String json = store_.getCustomMetadata(AXIS_MAP_KEY);
      if (json == null) {
         seedChannelNames();
         return;
      }
      JSONObject obj = parseJson(json);
      java.util.Iterator<String> keys = obj.keys();
      while (keys.hasNext()) {
         String axis = keys.next();
         JSONArray arr = obj.optJSONArray(axis);
         if (arr == null) {
            continue;
         }
         List<Object> values = new ArrayList<>();
         for (int i = 0; i < arr.length(); i++) {
            values.add(normalizeValue(arr.opt(i)));
         }
         axisValues_.put(axis, values);
      }
   }

   private void rebuildIndexFromStore() {
      for (Map<String, Object> intAxes : store_.getAxesSet()) {
         HashMap<String, Object> original = reverseTranslate(intAxes);
         axesSet_.add(original);
         if (tileWidth_ < 0) {
            org.micromanager.mmomezarr.EssentialImageMetadata e =
                  store_.getEssentialImageMetadata(intAxes, 0);
            if (e != null) {
               tileWidth_ = e.getWidth();
               tileHeight_ = e.getHeight();
               bitDepth_ = e.getBitDepth();
               rgb_ = e.isRGB();
            }
         }
      }
   }

   private void persistDisplaySettings() {
      if (!readOnly_) {
         store_.setCustomMetadata(DISPLAY_SETTINGS_KEY, displaySettings_.toString());
      }
   }

   private void loadDisplaySettings() {
      String json = store_.getCustomMetadata(DISPLAY_SETTINGS_KEY);
      if (json != null) {
         displaySettings_ = parseJson(json);
      }
   }

   // ---------------------------------------------------------------------------------------
   // Helpers
   // ---------------------------------------------------------------------------------------

   private static boolean matchesBase(Map<String, Object> candidate, Map<String, Object> base) {
      for (Map.Entry<String, Object> e : base.entrySet()) {
         Object v = candidate.get(e.getKey());
         if (v == null || !v.equals(e.getValue())) {
            return false;
         }
      }
      return true;
   }

   private static Integer intOf(Object o) {
      return (o instanceof Number) ? ((Number) o).intValue() : null;
   }

   /**
    * Copy the sub-rectangle ({@code srcX0,srcY0} .. +{@code copyW,copyH}) of a source tile into the
    * destination buffer at ({@code destX0,destY0}), clamped to both buffers. The source crop lets
    * the caller drop the overlap border so composited tiles are seamless.
    */
   private static void blit(Object dst, int dstW, int dstH, Object src, int srcW, int srcH,
                            int srcX0, int srcY0, int copyW, int copyH,
                            int destX0, int destY0, boolean isByte) {
      int xStart = Math.max(0, destX0);
      int yStart = Math.max(0, destY0);
      int xEnd = Math.min(dstW, destX0 + copyW);
      int yEnd = Math.min(dstH, destY0 + copyH);
      for (int y = yStart; y < yEnd; y++) {
         int srcY = srcY0 + (y - destY0);
         if (srcY < 0 || srcY >= srcH) {
            continue;
         }
         int srcRow = srcY * srcW;
         int dstRow = y * dstW;
         for (int x = xStart; x < xEnd; x++) {
            int srcX = srcX0 + (x - destX0);
            if (srcX < 0 || srcX >= srcW) {
               continue;
            }
            int srcIdx = srcRow + srcX;
            int dstIdx = dstRow + x;
            if (isByte) {
               ((byte[]) dst)[dstIdx] = ((byte[]) src)[srcIdx];
            } else {
               ((short[]) dst)[dstIdx] = ((short[]) src)[srcIdx];
            }
         }
      }
   }

   private static JSONObject parseJson(String s) {
      try {
         return new JSONObject(s);
      } catch (Exception e) {
         return new JSONObject();
      }
   }

   private static Future<IndexEntryData> wrap(Future<Void> f) {
      return new Future<IndexEntryData>() {
         @Override
         public boolean cancel(boolean mayInterruptIfRunning) {
            return f.cancel(mayInterruptIfRunning);
         }

         @Override
         public boolean isCancelled() {
            return f.isCancelled();
         }

         @Override
         public boolean isDone() {
            return f.isDone();
         }

         @Override
         public IndexEntryData get() throws InterruptedException, ExecutionException {
            f.get();
            return IndexEntryData.createFinishedEntry();
         }

         @Override
         public IndexEntryData get(long timeout, TimeUnit unit)
               throws InterruptedException, ExecutionException, TimeoutException {
            f.get(timeout, unit);
            return IndexEntryData.createFinishedEntry();
         }
      };
   }
}
