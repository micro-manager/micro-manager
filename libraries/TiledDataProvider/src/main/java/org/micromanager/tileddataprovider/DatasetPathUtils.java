package org.micromanager.tileddataprovider;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.micromanager.ndtiffstorage.NDTiffStorage;

/**
 * Helpers for turning a user's file-chooser selection into the root directory of a dataset.
 *
 * <p>Micro-Manager's "open dataset" choosers ({@code FileDialogs.openDir}) run in
 * {@code FILES_AND_DIRECTORIES} mode with no suffix filter, so a user can just as easily pick a
 * file <em>inside</em> a dataset as the dataset folder itself. Every storage backend, however,
 * expects the dataset root. {@link #normalizeDatasetPath} bridges that gap.
 *
 * <p>A plain "if it is not a directory, take the parent" is not sufficient: it is right for
 * OME-BigTIFF and OME-Zarr, whose planes sit directly in the dataset folder, but wrong for NDTiff,
 * where a plane lives in a {@code Full resolution} (or {@code Downsampled_x<N>}) subdirectory one
 * level below the root. This class therefore walks up until it recognizes a dataset, rather than
 * assuming a fixed depth.
 */
public final class DatasetPathUtils {

   /**
    * How many levels above the selection to search. NDTiff needs two
    * ({@code <root>/Full resolution/plane.tif}); one extra gives margin without letting a stray
    * selection walk all the way to the drive root.
    */
   private static final int MAX_LEVELS_UP = 3;

   private static final String NDTIFF_INDEX_FILE = "NDTiff.index";

   /** Prefix of NDTiff's downsampled resolution-level directories ({@code Downsampled_x2} etc). */
   private static final String DOWNSAMPLED_DIR_PREFIX = "Downsampled_x";

   /** Subdirectory holding the full-resolution level of a multi-resolution NDTiff dataset. */
   private static final String FULL_RESOLUTION_DIR = "Full resolution";

   private DatasetPathUtils() {
   }

   /**
    * Returns true if {@code dir} is the root of a dataset one of the tiled-data backends can read
    * (OME-Zarr, OME-BigTIFF, or NDTiff).
    *
    * @param dir directory to probe; may be null or a file, in which case false is returned
    * @return true if a known dataset descriptor was found directly in {@code dir}
    */
   public static boolean isDatasetDir(String dir) {
      if (dir == null || !new File(dir).isDirectory()) {
         return false;
      }
      return OMEZarrMultiresStorage.isOMEZarrDataset(dir)
            || OMEBigTiffMultiresStorage.isOMEBigTiffDataset(dir)
            || isAnyNDTiffLayout(dir);
   }

   /**
    * Returns true if {@code dir} holds an NDTiff dataset. Mirrors
    * {@code NDTiffAdapter.isNDTiffDataSet} in mmstudio; duplicated here so this library does not
    * have to depend on mmstudio.
    *
    * @param dir directory to probe
    * @return true if an NDTiff index file is present
    */
   public static boolean isNDTiffDataset(String dir) {
      return new File(dir, NDTIFF_INDEX_FILE).exists();
   }

   /**
    * Returns true if {@code dir} is the root of an NDTiff dataset in either of its two layouts.
    *
    * <p>A flat NDTiff dataset keeps its index at the root, but a multi-resolution one puts the
    * index inside a {@code Full resolution} subdirectory instead. {@link #isNDTiffDataset} only
    * looks at the root - matching {@code NDTiffAdapter.isNDTiffDataSet} in mmstudio, which is
    * what decides whether the standard loading path claims a dataset - so it returns false for
    * exactly the multi-resolution datasets this class needs to recognize.
    *
    * @param dir directory to probe
    * @return true if an NDTiff index is present at the root or one level down
    */
   private static boolean isAnyNDTiffLayout(String dir) {
      if (new File(new File(dir, FULL_RESOLUTION_DIR), NDTIFF_INDEX_FILE).exists()) {
         return true;
      }
      // "Full resolution" itself holds an index, but it is a resolution level, not the dataset
      // root. Rejecting it here keeps normalizeDatasetPath walking up to the real root.
      return isNDTiffDataset(dir) && !FULL_RESOLUTION_DIR.equals(new File(dir).getName());
   }

   /**
    * Returns true if {@code dir} holds a dataset that should be shown in a tiled viewer.
    *
    * <p>OME-Zarr and OME-BigTIFF are always tiled formats. NDTiff is not: the same container is
    * used for ordinary acquisitions, which belong in the standard display window. Only NDTiff
    * datasets that actually carry tile axes or a resolution pyramid are claimed here, so that
    * plain NDTiff data keeps opening the way it always has.
    *
    * @param dir directory to probe; may be null or a file, in which case false is returned
    * @return true if this dataset should be opened in a tiled viewer
    */
   public static boolean isTiledDataset(String dir) {
      if (dir == null || !new File(dir).isDirectory()) {
         return false;
      }
      // Both OME probes are a single file-existence check, so they are tried before the NDTiff
      // probe, which has to open the dataset.
      return OMEZarrMultiresStorage.isOMEZarrDataset(dir)
            || OMEBigTiffMultiresStorage.isOMEBigTiffDataset(dir)
            || isTiledNDTiffDataset(dir);
   }

   /**
    * Returns true if {@code dir} holds an NDTiff dataset that is tiled or multi-resolution.
    *
    * <p>There is no reliable way to tell this from the directory listing alone. The
    * {@code TiledImageStorage} flag in the summary metadata cannot be used either: NDTiffStorage
    * defaults it to <em>true</em> when the key is absent, which would pull older and
    * foreign-written NDTiff datasets into the tiled viewer. The dataset is therefore opened
    * briefly and its axes inspected. That is bounded work - the index and one image header, not
    * pixel data - and only NDTiff directories ever reach it.
    *
    * @param dir directory to probe
    * @return true if the dataset has tile axes or more than one resolution level
    */
   public static boolean isTiledNDTiffDataset(String dir) {
      if (dir == null || !isAnyNDTiffLayout(dir)) {
         return false;
      }
      // A downsampled level on disk means the dataset is tiled, and is far cheaper to spot than
      // opening the storage. Its absence proves nothing, though: a tiled dataset that was never
      // zoomed out has no downsampled level at all, so fall through to the full check.
      File[] children = new File(dir).listFiles();
      if (children != null) {
         for (File child : children) {
            if (child.isDirectory() && child.getName().startsWith(DOWNSAMPLED_DIR_PREFIX)) {
               return true;
            }
         }
      }
      NDTiffStorage storage = null;
      try {
         storage = new NDTiffStorage(dir);
         if (storage.getNumResLevels() > 1) {
            return true;
         }
         for (HashMap<String, Object> axes : storage.getAxesSet()) {
            if (axes.containsKey(NDTiffStorage.ROW_AXIS)
                  || axes.containsKey(NDTiffStorage.COL_AXIS)) {
               return true;
            }
         }
         return false;
      } catch (IOException | RuntimeException e) {
         // Unreadable or not actually NDTiff: leave it to the standard loading path, which will
         // report the problem in its own terms.
         return false;
      } finally {
         if (storage != null) {
            try {
               storage.close();
            } catch (RuntimeException e) {
               // Nothing useful to do; this was only a probe.
            }
         }
      }
   }

   /**
    * Resolves a user's selection to the root directory of the dataset containing it.
    *
    * <p>Accepts the dataset folder itself, a file inside it (such as the {@code .ome.tif} plane
    * inside a {@code .ome.tiff} folder), or a file in one of NDTiff's resolution-level
    * subdirectories. Returns the selection unchanged when it is already a dataset root.
    *
    * @param selected path the user chose; may be null
    * @return absolute path of the dataset root, or null if no dataset was found at or above
    *         {@code selected}
    */
   public static String normalizeDatasetPath(String selected) {
      if (selected == null) {
         return null;
      }
      File candidate = new File(selected).getAbsoluteFile();
      for (int level = 0; level <= MAX_LEVELS_UP && candidate != null; level++) {
         if (isDatasetDir(candidate.getPath())) {
            return candidate.getPath();
         }
         candidate = candidate.getParentFile();
      }
      return null;
   }
}
