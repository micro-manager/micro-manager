package org.micromanager.tileddataprovider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import mmcorej.org.json.JSONObject;
import org.junit.Test;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.ndtiffstorage.NDTiffStorage;

/**
 * Tests the format detection that decides whether a dataset opens in the tiled viewer or through
 * Micro-Manager's standard loading path.
 *
 * <p>The case that matters most is the negative one: an ordinary NDTiff acquisition must keep
 * opening in the standard viewer. Detection cannot rely on the {@code TiledImageStorage} summary
 * flag for this, because NDTiffStorage defaults that flag to <em>true</em> when the key is absent,
 * which would pull older and foreign-written datasets into the tiled viewer.
 */
public class DatasetPathUtilsTest {

   private static final int TILE = 16;
   private static final int SAVING_QUEUE_SIZE = 30;

   private static HashMap<String, Object> tileAxes(int row, int col) {
      HashMap<String, Object> axes = new HashMap<>();
      axes.put(NDTiffStorage.ROW_AXIS, row);
      axes.put(NDTiffStorage.COL_AXIS, col);
      return axes;
   }

   private static HashMap<String, Object> planeAxes(int channel) {
      HashMap<String, Object> axes = new HashMap<>();
      axes.put("channel", channel);
      return axes;
   }

   private static short[] tilePixels() {
      return new short[TILE * TILE];
   }

   /**
    * NDTiff reads an image's axes from its tags, not from the axes map alone, and rejects the
    * write with "couldnt create axes" if they are missing.
    */
   private static JSONObject tagsFor(HashMap<String, Object> axes) {
      JSONObject tags = new JSONObject();
      AcqEngMetadata.createAxes(tags);
      for (java.util.Map.Entry<String, Object> entry : axes.entrySet()) {
         AcqEngMetadata.setAxisPosition(tags, entry.getKey(), entry.getValue());
      }
      return tags;
   }

   /** Writes an NDTiff dataset, tiled or flat, and returns its root directory. */
   private static File writeNDTiff(String prefix, boolean tiled) throws Exception {
      File parent = Files.createTempDirectory(prefix).toFile();
      parent.deleteOnExit();
      JSONObject summary = new JSONObject();
      summary.put("PixelType", "GRAY16");
      NDTiffStorage store = new NDTiffStorage(parent.getAbsolutePath(), "data", summary,
            0, 0, tiled, null, SAVING_QUEUE_SIZE, null, true);
      if (tiled) {
         HashMap<String, Object> axes = tileAxes(0, 0);
         store.putImageMultiRes(tilePixels(), tagsFor(axes), axes, false, 16, TILE, TILE).get();
      } else {
         HashMap<String, Object> axes = planeAxes(0);
         store.putImage(tilePixels(), tagsFor(axes), axes, false, 16, TILE, TILE).get();
      }
      store.finishedWriting();
      String location = store.getDiskLocation();
      store.close();
      return new File(location);
   }

   @Test
   public void plainNDTiffIsNotTiled() throws Exception {
      File dir = writeNDTiff("ndtiff-flat", false);
      assertTrue("test dataset should be recognized as NDTiff",
            DatasetPathUtils.isNDTiffDataset(dir.getAbsolutePath()));
      assertFalse("a plain NDTiff acquisition must keep opening in the standard viewer",
            DatasetPathUtils.isTiledNDTiffDataset(dir.getAbsolutePath()));
      assertFalse(DatasetPathUtils.isTiledDataset(dir.getAbsolutePath()));
   }

   @Test
   public void tiledNDTiffIsTiled() throws Exception {
      File dir = writeNDTiff("ndtiff-tiled", true);
      // A multi-resolution dataset keeps its index in "Full resolution", so the root-only probe
      // does not see it -- which is exactly why detection cannot rely on isNDTiffDataset alone.
      assertFalse("multi-res NDTiff hides its index one level down",
            DatasetPathUtils.isNDTiffDataset(dir.getAbsolutePath()));
      assertTrue("the dataset root must still be recognized",
            DatasetPathUtils.isDatasetDir(dir.getAbsolutePath()));
      assertTrue("row/column axes mark this dataset as tiled",
            DatasetPathUtils.isTiledNDTiffDataset(dir.getAbsolutePath()));
      assertTrue(DatasetPathUtils.isTiledDataset(dir.getAbsolutePath()));
   }

   @Test
   public void omeBigTiffIsTiledWithoutOpeningIt() throws Exception {
      File parent = Files.createTempDirectory("ome-bigtiff-detect").toFile();
      parent.deleteOnExit();
      JSONObject summary = new JSONObject();
      summary.put("PixelType", "GRAY16");
      OMEBigTiffTiledStorage store = new OMEBigTiffTiledStorage(parent.getAbsolutePath(), "bt",
            summary, TILE, TILE, TILE, TILE, 1, 1, 1, 1, SAVING_QUEUE_SIZE);
      store.putImage(tilePixels(), new JSONObject(), tileAxes(0, 0), false, 16, TILE, TILE).get();
      store.finishedWriting();
      // The storage creates a subdirectory named after the dataset, so the root it actually
      // wrote to is the one to probe, not the temp directory handed in.
      String dir = store.getDiskLocation();
      store.close();

      assertTrue("mm-bigtiff.json marks an OME-BigTIFF dataset",
            OMEBigTiffMultiresStorage.isOMEBigTiffDataset(dir));
      assertTrue(DatasetPathUtils.isTiledDataset(dir));
      assertFalse("OME-BigTIFF is not NDTiff", DatasetPathUtils.isNDTiffDataset(dir));
   }

   @Test
   public void nonDatasetDirectoriesAreRejected() throws Exception {
      File empty = Files.createTempDirectory("not-a-dataset").toFile();
      empty.deleteOnExit();
      assertFalse(DatasetPathUtils.isTiledDataset(empty.getAbsolutePath()));
      assertFalse(DatasetPathUtils.isTiledNDTiffDataset(empty.getAbsolutePath()));
      assertFalse(DatasetPathUtils.isDatasetDir(empty.getAbsolutePath()));
      assertNull(DatasetPathUtils.normalizeDatasetPath(empty.getAbsolutePath()));
   }

   @Test
   public void nullAndMissingPathsAreRejected() {
      assertFalse(DatasetPathUtils.isTiledDataset(null));
      assertFalse(DatasetPathUtils.isTiledNDTiffDataset(null));
      assertFalse(DatasetPathUtils.isTiledDataset("/no/such/directory/anywhere"));
      assertNull(DatasetPathUtils.normalizeDatasetPath(null));
   }

   /**
    * The chooser and drag-and-drop both let the user pick a file inside a dataset. For NDTiff the
    * planes sit one level below the root, in "Full resolution", so resolving the root takes more
    * than stepping up to the parent directory.
    */
   @Test
   public void datasetRootIsResolvedFromAFileInside() throws Exception {
      File dir = writeNDTiff("ndtiff-normalize", true);
      File fullRes = new File(dir, "Full resolution");
      assertTrue("NDTiff writes planes into a Full resolution subdirectory", fullRes.isDirectory());
      File[] planes = fullRes.listFiles((d, name) -> name.endsWith(".tif"));
      assertTrue("expected at least one plane on disk", planes != null && planes.length > 0);

      assertEquals("a plane inside the dataset must resolve to the dataset root",
            dir.getAbsolutePath(),
            DatasetPathUtils.normalizeDatasetPath(planes[0].getAbsolutePath()));
      assertEquals("the dataset root must resolve to itself",
            dir.getAbsolutePath(),
            DatasetPathUtils.normalizeDatasetPath(dir.getAbsolutePath()));
   }
}
