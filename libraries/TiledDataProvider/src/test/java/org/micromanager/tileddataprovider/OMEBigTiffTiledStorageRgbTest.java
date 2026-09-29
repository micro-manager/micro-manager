package org.micromanager.tileddataprovider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.junit.Test;

/**
 * RGB round-trip tests for {@link OMEBigTiffTiledStorage}. The Stitch plugin composites RGB into
 * Micro-Manager's 4-byte-per-pixel BGRA {@code byte[]} tiles and writes them here; the viewer
 * expects the same 4-byte BGRA back on read. The underlying library stores 3-sample RGB on disk,
 * so the bridge must repack BGRA→RGB on write (in the library) and RGB→BGRA on read (here). This
 * pins that byte ordering so a channel swap can't sneak in.
 */
public class OMEBigTiffTiledStorageRgbTest {

   private static final String ROW = org.micromanager.ndtiffstorage.NDTiffStorage.ROW_AXIS;
   private static final String COL = org.micromanager.ndtiffstorage.NDTiffStorage.COL_AXIS;

   /** A single-tile canvas; the tile size must be a positive multiple of 16. */
   private static final int TILE = 16;

   /** MM BGRA gradient tile: for pixel i, (B,G,R) = (i, i+1, i+2) mod 256, A = 0xFF. */
   private static byte[] bgraTile(int w, int h) {
      byte[] p = new byte[w * h * 4];
      for (int i = 0; i < w * h; i++) {
         int s = i * 4;
         p[s]     = (byte) (i & 0xFF);        // B
         p[s + 1] = (byte) ((i + 1) & 0xFF);  // G
         p[s + 2] = (byte) ((i + 2) & 0xFF);  // R
         p[s + 3] = (byte) 0xFF;              // A (unused)
      }
      return p;
   }

   private static HashMap<String, Object> axes(int row, int col, int ch) {
      HashMap<String, Object> a = new HashMap<>();
      a.put(ROW, row);
      a.put(COL, col);
      a.put("channel", ch);
      return a;
   }

   @Test
   public void rgbTileRoundTripPreservesBgraOrder() throws Exception {
      File dir = Files.createTempDirectory("ometiff-rgb-test").toFile();
      dir.deleteOnExit();

      JSONObject summary = new JSONObject();
      summary.put("PixelType", "RGB32");
      summary.put("PixelSize_um", 1.0);

      OMEBigTiffTiledStorage store = new OMEBigTiffTiledStorage(
            dir.getAbsolutePath(), "rgb", summary,
            TILE, TILE, TILE, TILE,
            1 /* channels */, 1 /* z */, 1 /* times */,
            1 /* resLevels */, 30 /* queue */);

      byte[] tile = bgraTile(TILE, TILE);
      // rgb=true, bitDepth=8 — exactly what the Stitch export passes for RGB tiles.
      store.putImage(tile.clone(), new JSONObject(), axes(0, 0, 0), true, 8, TILE, TILE)
            .get();
      store.finishedWriting();

      // Read the whole tile back as it would be displayed: expect 4-byte BGRA, byte-for-byte.
      TaggedImage img = store.getDisplayImage(axes(0, 0, 0), 0, 0, 0, TILE, TILE);
      assertNotNull(img);
      assertTrue("expected byte[] RGB pixels", img.pix instanceof byte[]);
      byte[] out = (byte[]) img.pix;
      assertEquals(TILE * TILE * 4, out.length);
      for (int i = 0; i < TILE * TILE; i++) {
         int s = i * 4;
         assertEquals("B @" + i, tile[s],     out[s]);
         assertEquals("G @" + i, tile[s + 1], out[s + 1]);
         assertEquals("R @" + i, tile[s + 2], out[s + 2]);
         // Alpha is regenerated as opaque on read (the stored form drops it).
         assertEquals("A @" + i, (byte) 0xFF, out[s + 3]);
      }

      // Display tags advertise RGB32 so the viewer's stats/overlays work.
      assertEquals("RGB32", img.tags.optString("PixelType"));
      assertEquals(4, img.tags.optInt("BytesPerPixel"));
      assertEquals(3, img.tags.optInt("NumComponents"));
      // Width/Height must describe THIS returned buffer (not the stored source tile size), or
      // DefaultImage builds a mis-sized Image and the histogram gets no data.
      assertEquals(TILE, img.tags.optInt("Width"));
      assertEquals(TILE, img.tags.optInt("Height"));

      store.close();
   }

   @Test
   public void coarsestLevelImageTagsMatchReturnedBuffer() throws Exception {
      // A multi-tile canvas with a pyramid: getImage(axes) returns the COARSEST level, whose
      // dimensions differ from the stored source-tile Width/Height. The histogram-seed path
      // (getDownsampledImageByAxes -> getImage -> DefaultImage) needs the tags to match the
      // returned (downsampled) buffer, so assert that here — this is the histogram bug regression.
      File dir = Files.createTempDirectory("ometiff-rgb-coarse").toFile();
      dir.deleteOnExit();

      JSONObject summary = new JSONObject();
      summary.put("PixelType", "RGB32");

      int canvas = 64;         // 4x4 grid of 16px tiles
      int resLevels = 3;       // 64 -> 32 -> 16
      OMEBigTiffTiledStorage store = new OMEBigTiffTiledStorage(
            dir.getAbsolutePath(), "rgb", summary,
            canvas, canvas, TILE, TILE, 1, 1, 1, resLevels, 30);
      int across = canvas / TILE;
      for (int r = 0; r < across; r++) {
         for (int c = 0; c < across; c++) {
            store.putImage(bgraTile(TILE, TILE), new JSONObject(), axes(r, c, 0),
                  true, 8, TILE, TILE).get();
         }
      }
      store.finishedWriting();

      int coarsest = store.getNumResLevels() - 1;
      TaggedImage ti = store.getImage(axes(0, 0, 0), coarsest);
      assertNotNull(ti);
      int w = ti.tags.optInt("Width");
      int h = ti.tags.optInt("Height");
      assertTrue("coarsest level should be smaller than the canvas", w < canvas && w > 0);
      // Pixel buffer length must equal Width*Height*4 for a valid RGB32 DefaultImage.
      assertEquals(w * h * 4, ((byte[]) ti.pix).length);
      store.close();
   }

   @Test
   public void reopenedRgbDatasetReportsRgb() throws Exception {
      File dir = Files.createTempDirectory("ometiff-rgb-reopen").toFile();
      dir.deleteOnExit();

      JSONObject summary = new JSONObject();
      summary.put("PixelType", "RGB32");

      OMEBigTiffTiledStorage store = new OMEBigTiffTiledStorage(
            dir.getAbsolutePath(), "rgb", summary,
            TILE, TILE, TILE, TILE, 1, 1, 1, 1, 30);
      byte[] tile = bgraTile(TILE, TILE);
      store.putImage(tile, new JSONObject(), axes(0, 0, 0), true, 8, TILE, TILE).get();
      store.finishedWriting();
      String location = store.getDiskLocation();
      store.close();

      // Reopen for viewing; the bridge must rediscover RGB-ness (persisted by the library) so the
      // read path repacks to BGRA rather than serving raw 3-byte RGB.
      OMEBigTiffTiledStorage reopened = new OMEBigTiffTiledStorage(location);
      TaggedImage img = reopened.getDisplayImage(axes(0, 0, 0), 0, 0, 0, TILE, TILE);
      assertNotNull(img);
      assertEquals(TILE * TILE * 4, ((byte[]) img.pix).length);
      byte[] out = (byte[]) img.pix;
      // Spot-check the first pixel's BGR (alpha opaque).
      assertEquals(tile[0], out[0]);
      assertEquals(tile[1], out[1]);
      assertEquals(tile[2], out[2]);
      assertEquals((byte) 0xFF, out[3]);
      reopened.close();
   }
}
