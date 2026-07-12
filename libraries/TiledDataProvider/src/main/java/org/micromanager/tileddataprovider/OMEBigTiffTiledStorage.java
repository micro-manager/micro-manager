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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.mmomebigtiff.AxisInfo;
import org.micromanager.mmomebigtiff.Compression;
import org.micromanager.mmomebigtiff.DimensionType;
import org.micromanager.mmomebigtiff.OMEBigTiffImage;
import org.micromanager.mmomebigtiff.OMEBigTiffStorage;
import org.micromanager.mmomebigtiff.OMEBigTiffStorageConfig;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.ImageWrittenListener;
import org.micromanager.ndtiffstorage.IndexEntryData;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;

/**
 * Bridges the <em>tiled</em> mode of the {@code MM-OME-BigTiff-Storage} library to the
 * {@link MultiresNDTiffAPI} used by Micro-Manager's tiled-data stack. Unlike
 * {@link OMEBigTiffMultiresStorage} — which folds the explore grid's {@code row}/{@code column}
 * into the OME position axis (one {@code .ome.tif} per tile) — this bridge stores the whole canvas
 * as a <b>single tiled OME-BigTIFF plane</b> per {@code (channel, z, time)}: each incoming
 * {@code row}/{@code column} tile becomes one TIFF tile within that plane, streamed in via
 * {@link OMEBigTiffStorage#putTile}, and readers pull sub-regions with
 * {@link OMEBigTiffStorage#getRegion} without materialising the whole (possibly multi-gigapixel)
 * frame.
 *
 * <p>This requires the canvas geometry and the plane grid to be known up front, so it fits the
 * <b>Stitch</b> plugin (fixed output canvas, known channel/z/time counts) rather than the
 * Explorer's dynamically growing explore canvas. The tile size must be a positive multiple of 16,
 * edge tiles
 * are zero-padded by the caller, and RGB is not supported (grayscale 8/16/32-bit only).
 *
 * <p>String channel names are passed straight through: the underlying library assigns each a dense
 * integer index (first-appearance order), persists the mapping in {@code mm-bigtiff.json}, and
 * restores it on reopen, so callers keep seeing the original names.
 */
public final class OMEBigTiffTiledStorage implements MultiresNDTiffAPI {

   private static final String ROW = org.micromanager.ndtiffstorage.NDTiffStorage.ROW_AXIS;
   private static final String COL = org.micromanager.ndtiffstorage.NDTiffStorage.COL_AXIS;
   private static final String DISPLAY_SETTINGS_KEY = "mmDisplaySettings";
   private static final String DESCRIPTOR_FILE = "mm-bigtiff.json";
   private static final long MAX_ARRAY = Integer.MAX_VALUE - 8L;

   private final OMEBigTiffStorage store_;
   private final boolean readOnly_;
   private final JSONObject summary_;

   private volatile int bitDepth_ = 16;
   private volatile JSONObject displaySettings_ = new JSONObject();

   // ---------------------------------------------------------------------------------------
   // Construction
   // ---------------------------------------------------------------------------------------

   /**
    * Create a new tiled-canvas OME-BigTIFF dataset for writing.
    *
    * @param dir             parent directory
    * @param name            dataset name (a {@code .ome.tiff} folder is created under it)
    * @param summaryMetadata summary metadata (should include {@code PixelSize_um},
    *                        {@code ChNames} when available)
    * @param canvasWidth     full plane (canvas) width in pixels
    * @param canvasHeight    full plane (canvas) height in pixels
    * @param tileWidth       tile width in pixels (positive multiple of 16)
    * @param tileHeight      tile height in pixels (positive multiple of 16)
    * @param numChannels     number of channels (>= 1)
    * @param numZ            number of z slices (>= 1)
    * @param numTimes        number of time points (>= 1)
    * @param numResLevels    number of pyramid levels (>= 1; fixed for the file's lifetime)
    * @param savingQueueSize bounded writer-queue size (back-pressure)
    */
   public OMEBigTiffTiledStorage(String dir, String name, JSONObject summaryMetadata,
                                 long canvasWidth, long canvasHeight, int tileWidth, int tileHeight,
                                 int numChannels, int numZ, int numTimes,
                                 int numResLevels, int savingQueueSize) {
      this.readOnly_ = false;
      this.summary_ = summaryMetadata != null ? summaryMetadata : new JSONObject();

      double pixelSizeUm = summary_.optDouble("PixelSize_um", 1.0);
      if (pixelSizeUm <= 0) {
         pixelSizeUm = 1.0;
      }
      OMEBigTiffStorageConfig cfg = new OMEBigTiffStorageConfig()
            .fullPlaneSize(canvasWidth, canvasHeight)
            .tileSize(tileWidth, tileHeight)
            .numResolutionLevels(Math.max(1, numResLevels))
            .autoDownsample(true)
            .compression(Compression.DEFLATE)
            .pixelSize(pixelSizeUm)
            .spatialUnit("micrometer")
            .savingQueueSize(savingQueueSize > 0 ? savingQueueSize : 50);
      // Tiled mode preallocates one IFD per (z,c,t) plane, so every declared axis needs a fixed
      // count. Declare channel always, and z/time only when they actually vary (matching the axes
      // the caller sends); omitted dimensions default to size 1.
      cfg.addAxis(AxisInfo.builder("channel").type(DimensionType.CHANNEL)
            .count(Math.max(1, numChannels)).build());
      if (numZ > 1) {
         cfg.addAxis(AxisInfo.builder("z").type(DimensionType.SPACE).count(numZ).build());
      }
      if (numTimes > 1) {
         cfg.addAxis(AxisInfo.builder("time").type(DimensionType.TIME).count(numTimes).build());
      }
      this.store_ = new OMEBigTiffStorage(dir, name, summary_.toString(), cfg);
   }

   /** Open an existing tiled OME-BigTIFF dataset for viewing. */
   public OMEBigTiffTiledStorage(String dir) {
      this.readOnly_ = true;
      this.store_ = OMEBigTiffStorage.load(dir);
      String summaryJson = store_.getSummaryMetadata();
      this.summary_ = parseJson(summaryJson != null ? summaryJson : "{}");
      loadDisplaySettings();
      // Seed bit depth from any stored plane.
      for (Map<String, Object> axes : store_.getAxesSet()) {
         org.micromanager.mmomebigtiff.EssentialImageMetadata e =
               store_.getEssentialImageMetadata(axes, 0);
         if (e != null) {
            bitDepth_ = e.getBitDepth();
            break;
         }
      }
   }

   /** True if {@code dir} is an MM-OME-BigTIFF dataset written in tiled (single-plane) mode. */
   public static boolean isTiledDataset(String dir) {
      File descriptor = new File(dir, DESCRIPTOR_FILE);
      if (!descriptor.exists()) {
         return false;
      }
      try {
         byte[] bytes = java.nio.file.Files.readAllBytes(descriptor.toPath());
         JSONObject d = new JSONObject(
               new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
         return d.optBoolean("tiled", false);
      } catch (Exception e) {
         return false;
      }
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
               "The tiled OME-BigTIFF backend does not support RGB images.");
      }
      this.bitDepth_ = bitDepth;
      Integer col = intOf(axes.get(COL));
      Integer row = intOf(axes.get(ROW));
      int tileCol = col != null ? col : 0;
      int tileRow = row != null ? row : 0;
      Map<String, Object> plane = planeAxes(axes);
      String metaJson = metadata != null ? metadata.toString() : null;
      Future<Void> f = store_.putTile(pixels, metaJson, plane, tileCol, tileRow, rgb, bitDepth);
      return wrap(f);
   }

   @Override
   public void increaseMaxResolutionLevel(int newMaxResolutionLevel) {
      // Tiled OME-BigTIFF fixes its pyramid depth at creation, so this is a best-effort no-op:
      // the level count was already sized for the canvas. Swallow the "pyramid fixed" error.
      try {
         store_.setMaxResolutionLevel(newMaxResolutionLevel + 1);
      } catch (RuntimeException e) {
         // Pyramid depth is fixed after writing has begun (or the dataset is read-only).
      }
   }

   @Override
   public void finishedWriting() {
      try {
         persistDisplaySettings();
      } catch (RuntimeException e) {
         // Best-effort metadata; never block finalizing the image data below.
      }
      store_.finishedWriting();
   }

   // ---------------------------------------------------------------------------------------
   // Read path
   // ---------------------------------------------------------------------------------------

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes) {
      // Representative image for overlays / seed histograms: the finest pyramid level whose whole
      // plane fits in a Java array (level 0 for a modest canvas, coarser for a huge one), so a
      // multi-gigapixel plane never triggers an oversized allocation or the store's array-limit
      // error.
      Map<String, Object> plane = planeAxes(axes);
      for (int level = 0; level < getNumResLevels(); level++) {
         long[] wh = levelDims(plane, level);
         if (wh != null && wh[0] * wh[1] <= MAX_ARRAY) {
            return regionImage(plane, level, 0, 0, (int) wh[0], (int) wh[1]);
         }
      }
      return null;
   }

   @Override
   public TaggedImage getImage(HashMap<String, Object> axes, int resolutionLevel) {
      Map<String, Object> plane = planeAxes(axes);
      long[] wh = levelDims(plane, resolutionLevel);
      if (wh == null || wh[0] * wh[1] > MAX_ARRAY) {
         return null;
      }
      return regionImage(plane, resolutionLevel, 0, 0, (int) wh[0], (int) wh[1]);
   }

   @Override
   public JSONObject getImageMetadata(HashMap<String, Object> axes) {
      String meta = store_.getImageMetadata(planeAxes(axes));
      return meta == null ? null : parseJson(meta);
   }

   @Override
   public boolean hasImage(HashMap<String, Object> axes) {
      return store_.hasImage(planeAxes(axes));
   }

   @Override
   public boolean hasImage(HashMap<String, Object> axes, int resolutionLevel) {
      return store_.hasImage(planeAxes(axes), resolutionLevel);
   }

   @Override
   public EssentialImageMetadata getEssentialImageMetadata(HashMap<String, Object> axes) {
      return getEssentialImageMetadata(axes, 0);
   }

   @Override
   public EssentialImageMetadata getEssentialImageMetadata(HashMap<String, Object> axes,
                                                           int resolutionLevel) {
      org.micromanager.mmomebigtiff.EssentialImageMetadata e =
            store_.getEssentialImageMetadata(planeAxes(axes), resolutionLevel);
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
   public TaggedImage getDisplayImage(HashMap<String, Object> axes, int resolutionLevel,
                                      int xOffset, int yOffset, int imageWidth, int imageHeight) {
      // The library assembles the requested region from the covering tiles at this level.
      TaggedImage ti = regionImage(planeAxes(axes), resolutionLevel,
            xOffset, yOffset, imageWidth, imageHeight);
      if (ti != null) {
         return ti;
      }
      // Return an all-zero buffer of the requested size when the plane/region is not (yet)
      // available, matching the compositing bridges (the viewer expects a non-null tile).
      boolean isByte = bitDepth_ <= 8;
      int n = imageWidth * imageHeight;
      Object out = isByte ? new byte[n] : new short[n];
      return new TaggedImage(out, new JSONObject());
   }

   @Override
   public Set<HashMap<String, Object>> getAxesSet() {
      Set<HashMap<String, Object>> out = new HashSet<>();
      for (Map<String, Object> a : store_.getAxesSet()) {
         out.add(new HashMap<>(a));
      }
      return out;
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
      return store_.getImageBounds();
   }

   @Override
   public Set<Point> getTileIndicesWithDataAt(String axisName, int axisIndex) {
      // Stitched output is a single tiled plane, not an explore grid of addressable row/col
      // positions, so there are no per-tile indices to report.
      return new HashSet<>();
   }

   @Override
   @Deprecated
   public Set<Point> getTileIndicesWithDataAt(int axisIndex) {
      return getTileIndicesWithDataAt("z", axisIndex);
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
   // Helpers
   // ---------------------------------------------------------------------------------------

   /** Copy of {@code axes} without the tile-grid row/column keys, leaving the plane axes. */
   private static Map<String, Object> planeAxes(Map<String, Object> axes) {
      Map<String, Object> out = new LinkedHashMap<>(axes);
      out.remove(ROW);
      out.remove(COL);
      return out;
   }

   /** Level-{@code level} canvas dimensions of a plane, or null if it does not exist. */
   private long[] levelDims(Map<String, Object> plane, int level) {
      org.micromanager.mmomebigtiff.EssentialImageMetadata e =
            store_.getEssentialImageMetadata(plane, level);
      if (e == null) {
         return null;
      }
      return new long[]{e.getWidth(), e.getHeight()};
   }

   private TaggedImage regionImage(Map<String, Object> plane, int level,
                                   int x, int y, int w, int h) {
      OMEBigTiffImage img = store_.getRegion(plane, level, x, y, w, h);
      if (img == null || img.pix == null) {
         return null;
      }
      String meta = img.metadataJson;
      JSONObject tags = (meta == null || meta.isEmpty()) ? new JSONObject() : parseJson(meta);
      return new TaggedImage(img.pix, tags);
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

   private static Integer intOf(Object o) {
      return (o instanceof Number) ? ((Number) o).intValue() : null;
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
