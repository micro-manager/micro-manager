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
import org.micromanager.mmomebigtiff.Compression;
import org.micromanager.mmomebigtiff.OMEBigTiffImage;
import org.micromanager.mmomebigtiff.OMEBigTiffStorage;
import org.micromanager.mmomebigtiff.OMEBigTiffStorageConfig;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.ImageWrittenListener;
import org.micromanager.ndtiffstorage.IndexEntryData;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;

/**
 * Bridges the {@code MM-OME-BigTiff-Storage} library ({@link OMEBigTiffStorage}) to the
 * {@link MultiresNDTiffAPI} used throughout Micro-Manager's tiled-data stack (TiledDataProvider,
 * TiledDataViewer, Explorer). Like {@link OMEZarrMultiresStorage}, it implements the same interface
 * the stack already targets, so existing consumers — including {@link NDTiffProviderAdapter} and
 * {@code ExplorerDataSource} — work against it unchanged; only which implementation is instantiated
 * differs.
 *
 * <h2>Tiled mosaics on OME-BigTIFF</h2>
 * OME-TIFF's dimension model is exactly {@code Z}/{@code C}/{@code T}; there is no plane dimension
 * for the explore grid. The one axis OME-BigTIFF adds is <em>position</em>, stored as a separate
 * self-contained OME-BigTIFF file per position. This bridge therefore <b>folds the grid's
 * {@code row}/{@code column} into a single position index</b>: every tile becomes one
 * {@code .ome.tif} file (independently readable by QuPath, Bio-Formats, tifffile, ImageJ), and the
 * mosaic is composited on demand in {@link #getDisplayImage} (each tile placed at its grid offset,
 * later tiles overwriting the overlap seam). This differs from {@link OMEZarrMultiresStorage},
 * which can carry {@code row}/{@code column} as ordinary array axes; OME-TIFF cannot.
 *
 * <h2>Axis value translation</h2>
 * The library indexes axes by integers, but Micro-Manager uses string channel names and signed
 * tile coordinates. This bridge (a) maps each ({@code row},{@code column}) pair to an arrival-order
 * position index, and (b) maps non-integer axis values (notably {@code channel}, seeded from the
 * summary's {@code ChNames}) to integer indices. Both mappings are persisted in custom metadata and
 * reversed on reopen so callers continue to see their original values.
 *
 * <h2>Pyramid depth</h2>
 * An OME-BigTIFF plane's SubIFD array is written inline, so the number of resolution levels is
 * fixed once writing begins. The pyramid depth is therefore configured up front (covering the
 * Explorer's zoom range) and {@link #increaseMaxResolutionLevel} is best-effort afterwards.
 *
 * <p>Grayscale 8- and 16-bit only: RGB and 32-bit (float) images are rejected, because the
 * on-demand mosaic compositor works in {@code byte[]}/{@code short[]}.
 */
public final class OMEBigTiffMultiresStorage implements MultiresNDTiffAPI {

   private static final String ROW = org.micromanager.ndtiffstorage.NDTiffStorage.ROW_AXIS;
   private static final String COL = org.micromanager.ndtiffstorage.NDTiffStorage.COL_AXIS;
   private static final String POSITION = "position";
   private static final String CHANNEL = "channel";
   private static final String AXIS_MAP_KEY = "mmTiledAxisIndex";
   private static final String POSITION_MAP_KEY = "mmTilePositionMap";
   private static final String DISPLAY_SETTINGS_KEY = "mmDisplaySettings";
   private static final String DESCRIPTOR_FILE = "mm-bigtiff.json";

   // Explorer caps the pyramid at max resolution-level index 4 (see increaseMaxResolutionLevel);
   // that is 5 levels. Fixed up front because OME-BigTIFF cannot grow a file's pyramid after the
   // first plane is written.
   private static final int NUM_RES_LEVELS = 5;

   private final OMEBigTiffStorage store_;
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
    * Per-axis ordered value list; the arrival-order index into the list is the non-negative integer
    * used to address the OME-TIFF plane. Used for string axis values such as {@code channel}
    * ({@code row}/{@code column} are handled separately via {@link #positions_}). Original values
    * (with their type) are preserved for the reverse mapping.
    */
   private final Map<String, List<Object>> axisValues_ = new ConcurrentHashMap<>();

   /**
    * Arrival-ordered list of grid tiles; the index is the OME-BigTIFF position for that tile.
    * {@code positions_.get(p) == {row, col}}.
    */
   private final List<int[]> positions_ = new ArrayList<>();

   // ---------------------------------------------------------------------------------------
   // Construction
   // ---------------------------------------------------------------------------------------

   /**
    * Create a new tiled OME-BigTIFF dataset for writing.
    *
    * @param dir             parent directory
    * @param name            dataset name (a {@code .ome.tiff} folder is created under it)
    * @param summaryMetadata summary metadata (should include {@code GridPixelOverlapX/Y},
    *                        {@code PixelSize_um}, {@code ChNames} when available)
    * @param overlapX        tile overlap in x (pixels)
    * @param overlapY        tile overlap in y (pixels)
    * @param savingQueueSize bounded writer-queue size (back-pressure)
    */
   public OMEBigTiffMultiresStorage(String dir, String name, JSONObject summaryMetadata,
                                    int overlapX, int overlapY, int savingQueueSize) {
      this(dir, name, summaryMetadata, overlapX, overlapY, savingQueueSize, NUM_RES_LEVELS);
   }

   /**
    * Create a new tiled OME-BigTIFF dataset for writing with an explicit pyramid depth. Because
    * OME-BigTIFF writes a plane's SubIFD array inline, the level count is fixed for the life of
    * the file and must be chosen up front (unlike NDTiff/OME-Zarr, which grow the pyramid on
    * demand). Callers with a large output canvas (e.g. the Stitch plugin) pass enough levels for
    * the coarsest zoomed-out view; the Explorer uses the {@code NUM_RES_LEVELS} default.
    *
    * @param numResolutionLevels number of pyramid levels (>= 1); each level halves y and x
    */
   public OMEBigTiffMultiresStorage(String dir, String name, JSONObject summaryMetadata,
                                    int overlapX, int overlapY, int savingQueueSize,
                                    int numResolutionLevels) {
      this.readOnly_ = false;
      this.summary_ = summaryMetadata != null ? summaryMetadata : new JSONObject();
      this.overlapX_ = overlapX;
      this.overlapY_ = overlapY;
      seedChannelNames();

      double pixelSizeUm = summary_.optDouble("PixelSize_um", 1.0);
      if (pixelSizeUm <= 0) {
         pixelSizeUm = 1.0;
      }
      OMEBigTiffStorageConfig cfg = new OMEBigTiffStorageConfig()
            .positionAxis(POSITION)
            .numResolutionLevels(Math.max(1, numResolutionLevels))
            .autoDownsample(true)
            .compression(Compression.DEFLATE)
            .pixelSize(pixelSizeUm)
            .spatialUnit("micrometer")
            .savingQueueSize(savingQueueSize > 0 ? savingQueueSize : 50);
      this.store_ = new OMEBigTiffStorage(dir, name, summary_.toString(), cfg);
   }

   /** Open an existing OME-BigTIFF dataset for viewing. */
   public OMEBigTiffMultiresStorage(String dir) {
      this.readOnly_ = true;
      this.store_ = OMEBigTiffStorage.load(dir);
      String summaryJson = store_.getSummaryMetadata();
      this.summary_ = parseJson(summaryJson != null ? summaryJson : "{}");
      this.overlapX_ = summary_.optInt("GridPixelOverlapX", 0);
      this.overlapY_ = summary_.optInt("GridPixelOverlapY", 0);
      loadAxisMaps();
      loadPositionMap();
      loadDisplaySettings();
      rebuildIndexFromStore();
   }

   /** Returns true if {@code dir} looks like an MM-OME-BigTIFF dataset. */
   public static boolean isOMEBigTiffDataset(String dir) {
      return new File(dir, DESCRIPTOR_FILE).exists();
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
               "The OME-BigTIFF backend does not support RGB images.");
      }
      if (bitDepth > 16) {
         // The on-demand mosaic compositor (getDisplayImage/blit) works in byte[]/short[]; a
         // 32-bit (GRAY32) image is float[] and would fail with an ArrayStoreException mid-render.
         throw new UnsupportedOperationException(
               "The OME-BigTIFF backend supports 8- and 16-bit grayscale only (got bitDepth="
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
      // NDTiff's argument is a max level *index*; the library counts levels. OME-BigTIFF fixes the
      // level count once writing begins, so this only takes effect before the first image and is
      // otherwise a no-op. Swallow the "pyramid fixed" error rather than crashing the caller.
      try {
         store_.setMaxResolutionLevel(newMaxResolutionLevel + 1);
      } catch (RuntimeException e) {
         // Pyramid depth is fixed after writing has begun (or the dataset is read-only).
      }
   }

   @Override
   public void finishedWriting() {
      // Persist bridge metadata best-effort: a failure here must never prevent store_ from
      // finalizing the image data (OME-XML footer + TIFF flush), or the dataset is left corrupt.
      try {
         persistAxisMaps();
         persistPositionMap();
         persistDisplaySettings();
      } catch (RuntimeException e) {
         // Ignore; the dataset is still finalized below. Bridge maps are a best-effort convenience.
      }
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
      OMEBigTiffImage img = store_.getImage(translate(axes), resolutionLevel);
      if (img == null || img.pix == null) {
         return null;
      }
      // A genuinely stored plane always carries per-image metadata. Absent metadata means the
      // requested axes did not match a stored plane (e.g. a composite partial-axes lookup from the
      // overlay bridge, which folds to position 0). Treat that as "not found" so callers fall
      // through to their next lookup (e.g. getAnyImage()); see OMEZarrMultiresStorage#getImage.
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
      org.micromanager.mmomebigtiff.EssentialImageMetadata e =
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
         axisValues_.put(CHANNEL, new ArrayList<>(values));
      }
   }

   /**
    * Convert a caller axes map to the integer-indexed form the library stores: the
    * ({@code row},{@code column}) pair collapses to a single {@code position} index, and every
    * remaining non-integer axis value is mapped to its arrival-order index.
    */
   private Map<String, Object> translate(Map<String, Object> axes) {
      Map<String, Object> out = new LinkedHashMap<>();
      Integer row = intOf(axes.get(ROW));
      Integer col = intOf(axes.get(COL));
      if (row != null || col != null) {
         out.put(POSITION, positionFor(row == null ? 0 : row, col == null ? 0 : col));
      }
      for (Map.Entry<String, Object> e : axes.entrySet()) {
         if (ROW.equals(e.getKey()) || COL.equals(e.getKey())) {
            continue;
         }
         Object v = e.getValue();
         if (v instanceof Number) {
            out.put(e.getKey(), ((Number) v).intValue());
         } else {
            out.put(e.getKey(), indexFor(e.getKey(), v));
         }
      }
      return out;
   }

   /** Inverse of {@link #translate}, used to rebuild original axes on reopen. */
   private HashMap<String, Object> reverseTranslate(Map<String, Object> intAxes) {
      HashMap<String, Object> out = new HashMap<>();
      for (Map.Entry<String, Object> e : intAxes.entrySet()) {
         if (POSITION.equals(e.getKey())) {
            Integer pos = intOf(e.getValue());
            int[] rc = pos != null ? rowColForPosition(pos) : null;
            if (rc != null) {
               out.put(ROW, rc[0]);
               out.put(COL, rc[1]);
            }
            continue;
         }
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

   private synchronized int positionFor(int row, int col) {
      for (int i = 0; i < positions_.size(); i++) {
         int[] rc = positions_.get(i);
         if (rc[0] == row && rc[1] == col) {
            return i;
         }
      }
      positions_.add(new int[]{row, col});
      return positions_.size() - 1;
   }

   private synchronized int[] rowColForPosition(int position) {
      if (position >= 0 && position < positions_.size()) {
         int[] rc = positions_.get(position);
         return new int[]{rc[0], rc[1]};
      }
      return null;
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

   // The library's descriptor serializer parses every custom-metadata value as a JSON *object*,
   // so the position list is wrapped in an object ({"positions": [[row,col], ...]}) rather than
   // stored as a bare top-level array.
   private void persistPositionMap() {
      try {
         JSONArray arr = new JSONArray();
         synchronized (this) {
            for (int[] rc : positions_) {
               JSONArray pair = new JSONArray();
               pair.put(rc[0]);
               pair.put(rc[1]);
               arr.put(pair);
            }
         }
         JSONObject obj = new JSONObject();
         obj.put("positions", arr);
         store_.setCustomMetadata(POSITION_MAP_KEY, obj.toString());
      } catch (Exception e) {
         // Best-effort persistence of the tile-position map; ignore serialization failures.
      }
   }

   private void loadPositionMap() {
      String json = store_.getCustomMetadata(POSITION_MAP_KEY);
      if (json == null) {
         return;
      }
      try {
         JSONArray arr = parseJson(json).optJSONArray("positions");
         if (arr == null) {
            return;
         }
         synchronized (this) {
            positions_.clear();
            for (int i = 0; i < arr.length(); i++) {
               JSONArray pair = arr.optJSONArray(i);
               if (pair != null && pair.length() >= 2) {
                  positions_.add(new int[]{pair.optInt(0), pair.optInt(1)});
               }
            }
         }
      } catch (Exception e) {
         // Ignore a malformed position map; tiles without a mapping simply won't composite.
      }
   }

   private void rebuildIndexFromStore() {
      for (Map<String, Object> intAxes : store_.getAxesSet()) {
         HashMap<String, Object> original = reverseTranslate(intAxes);
         axesSet_.add(original);
         if (tileWidth_ < 0) {
            org.micromanager.mmomebigtiff.EssentialImageMetadata e =
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
