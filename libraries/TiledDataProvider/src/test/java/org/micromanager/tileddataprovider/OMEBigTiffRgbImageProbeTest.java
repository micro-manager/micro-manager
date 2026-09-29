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
import org.micromanager.data.internal.DefaultImage;

/**
 * Diagnostic: builds a {@link DefaultImage} from the bridge's RGB {@link TaggedImage} exactly as
 * the viewer does when seeding histograms, and asserts it resolves to a 3-component RGB32 image.
 * If getNumComponents() != 3, the ImageStatsProcessor produces no per-component histograms and the
 * Inspector shows NO DATA — which is the reported symptom.
 */
public class OMEBigTiffRgbImageProbeTest {

   private static final String ROW = org.micromanager.ndtiffstorage.NDTiffStorage.ROW_AXIS;
   private static final String COL = org.micromanager.ndtiffstorage.NDTiffStorage.COL_AXIS;
   private static final int TILE = 16;

   private static byte[] bgraTile(int w, int h) {
      byte[] p = new byte[w * h * 4];
      for (int i = 0; i < w * h; i++) {
         int s = i * 4;
         p[s]     = (byte) (i & 0xFF);
         p[s + 1] = (byte) ((i + 1) & 0xFF);
         p[s + 2] = (byte) ((i + 2) & 0xFF);
         p[s + 3] = (byte) 0xFF;
      }
      return p;
   }

   private static HashMap<String, Object> axes(int row, int col) {
      HashMap<String, Object> a = new HashMap<>();
      a.put(ROW, row);
      a.put(COL, col);
      return a;
   }

   @Test
   public void bridgeRgbImageResolvesToThreeComponents() throws Exception {
      File dir = Files.createTempDirectory("ometiff-rgb-probe").toFile();
      dir.deleteOnExit();
      JSONObject summary = new JSONObject();
      summary.put("PixelType", "RGB32");

      OMEBigTiffTiledStorage store = new OMEBigTiffTiledStorage(
            dir.getAbsolutePath(), "rgb", summary,
            TILE, TILE, TILE, TILE, 1, 1, 1, 1, 30);
      store.putImage(bgraTile(TILE, TILE), new JSONObject(), axes(0, 0), true, 8, TILE, TILE).get();
      store.finishedWriting();

      // The viewer's histogram-seed path: storage.getImage(axes) -> DefaultImage(TaggedImage).
      TaggedImage ti = store.getImage(axes(0, 0));
      assertNotNull(ti);
      DefaultImage img = new DefaultImage(ti);
      assertEquals("pixel width", TILE, img.getWidth());
      assertEquals("pixel height", TILE, img.getHeight());
      assertEquals("RGB must resolve to 3 components", 3, img.getNumComponents());
      assertEquals("bytes per pixel", 4, img.getBytesPerPixel());

      // A channel-less RGB dataset has an empty plane-axes map (no channel key). This is why the
      // viewer discovers zero channels; the Inspector fix (getNextIndex(CHANNEL) >= 1 when images
      // exist) ensures a histogram panel is still created for such datasets.
      for (java.util.HashMap<String, Object> a : store.getAxesSet()) {
         assertTrue("channel-less plane axes should not carry a channel key",
               !a.containsKey("channel"));
      }
      store.close();
   }

   /**
    * Reproduce the Stitch seed path at realistic parameters: a 2048px output tile, a multi-level
    * pyramid, and a canvas several tiles wide. The seed loop calls getImage(axes) (coarsest level)
    * and builds a DefaultImage; if that returns null or throws, the histogram is never seeded.
    */
   @Test
   public void realisticSeedPathYieldsValidRgbImage() throws Exception {
      File dir = Files.createTempDirectory("ometiff-rgb-real").toFile();
      dir.deleteOnExit();
      JSONObject summary = new JSONObject();
      summary.put("PixelType", "RGB32");

      int outTile = 2048;              // NDTIFF_OUTPUT_TILE_SIZE
      int canvasW = 5000;              // ~2.4 tiles wide (non-multiple, edge tiles padded)
      int canvasH = 4000;
      int resLevels = 4;               // maxResLevel+1, coarsest ~ a few hundred px
      OMEBigTiffTiledStorage store = new OMEBigTiffTiledStorage(
            dir.getAbsolutePath(), "rgb", summary,
            canvasW, canvasH, outTile, outTile, 1, 1, 1, resLevels, 30);
      int across = (canvasW + outTile - 1) / outTile;
      int down = (canvasH + outTile - 1) / outTile;
      for (int r = 0; r < down; r++) {
         for (int c = 0; c < across; c++) {
            store.putImage(bgraTile(outTile, outTile), new JSONObject(), axes(r, c),
                  true, 8, outTile, outTile).get();
         }
      }
      store.finishedWriting();

      // Exactly what the seed loop does: getImage(axes) -> DefaultImage.
      TaggedImage ti = store.getImage(axes(0, 0));
      assertNotNull("seed image must not be null", ti);
      DefaultImage img = new DefaultImage(ti);
      assertEquals("RGB must resolve to 3 components", 3, img.getNumComponents());
      // Width*Height*4 must equal the pixel buffer length, or DefaultImage/stats mis-read it.
      assertEquals(img.getWidth() * img.getHeight() * 4, ((byte[]) ti.pix).length);
      store.close();
   }
}
