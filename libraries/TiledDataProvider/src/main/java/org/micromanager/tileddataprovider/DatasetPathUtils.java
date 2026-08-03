package org.micromanager.tileddataprovider;

import java.io.File;

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
            || isNDTiffDataset(dir);
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
