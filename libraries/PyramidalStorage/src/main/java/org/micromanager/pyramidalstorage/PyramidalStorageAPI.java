package org.micromanager.pyramidalstorage;

import java.util.HashMap;
import java.util.Set;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;

/**
 * Narrow read-only interface for storage backends used by NDViewer2 and ExportTiles.
 *
 * <h2>Axes maps</h2>
 *
 * <p>Images are addressed by an <em>axes map</em>: a {@code HashMap<String, Object>}
 * where each key names a dimension and the value identifies the position along it.
 * Every map passed to or returned from this interface follows the same conventions:</p>
 *
 * <h3>Standard axis names and value types</h3>
 * <table border="1">
 *   <tr><th>Key (axis name)</th><th>Value type</th><th>Description</th></tr>
 *   <tr><td>{@code "channel"}</td><td>{@code String} <em>or</em> {@code Integer}</td>
 *       <td>Channel identifier.  String names (e.g. {@code "DAPI"}, {@code "GFP"}) are
 *           typical for named channels; integer indices are used when no names are
 *           provided.  When no channel axis exists in the dataset, this key is absent
 *           from every axes map.</td></tr>
 *   <tr><td>{@code "z"}</td><td>{@code Integer}</td>
 *       <td>Z-slice index (0-based).</td></tr>
 *   <tr><td>{@code "time"}</td><td>{@code Integer}</td>
 *       <td>Time-point index (0-based).</td></tr>
 *   <tr><td>{@code "position"}</td><td>{@code Integer}</td>
 *       <td>Stage-position index (0-based), used in multi-position acquisitions.</td></tr>
 *   <tr><td>{@code NDTiffStorage.ROW_AXIS} ({@code "row"})</td><td>{@code Integer}</td>
 *       <td>Tile row index, present only in tiled/explore datasets.</td></tr>
 *   <tr><td>{@code NDTiffStorage.COL_AXIS} ({@code "column"})</td><td>{@code Integer}</td>
 *       <td>Tile column index, present only in tiled/explore datasets.</td></tr>
 * </table>
 *
 * <p>Only axes that are actually present in the dataset appear in the maps.
 * A simple single-z, single-time acquisition with two named channels would produce
 * maps containing only {@code "channel"}.  A tiled explore acquisition would add
 * {@code "row"} and {@code "column"}.</p>
 *
 * <p>Axis names are not fixed by this interface — a dataset may contain arbitrary
 * additional axes written by the acquisition engine.  Callers should inspect the
 * keys returned by {@link #getAxesSet()} to discover what axes are present, and
 * copy an existing map before modifying row/column values (as ExportTiles does)
 * rather than constructing maps from scratch.</p>
 *
 * <h2>Write access</h2>
 *
 * <p>This interface intentionally omits write-path methods ({@code putImage},
 * {@code finishedWriting}, {@code close}, etc.).  Callers that need write access
 * retain their own {@code MultiresNDTiffAPI} reference for that purpose.
 * Use {@code new NDTiffStorageAdapter(storage)}
 * to obtain a {@code PyramidalStorageAPI} view of an existing {@code MultiresNDTiffAPI}.</p>
 */
public interface PyramidalStorageAPI {

   /**
    * Returns the set of axes maps for every image currently in the storage.
    *
    * <p>Each element of the returned set is a complete axes map that can be passed
    * directly to {@link #getImage(HashMap)} to retrieve that image.
    * The set grows as new images are written; it is safe to call this method
    * concurrently with ongoing writes, though a snapshot is returned and may be
    * stale by the time it is used.</p>
    *
    * <p>For tiled datasets every map will contain {@code NDTiffStorage.ROW_AXIS}
    * and {@code NDTiffStorage.COL_AXIS} in addition to any channel / z / time axes.
    * Do not modify the returned maps; copy them before making changes.</p>
    *
    * @return an unmodifiable-or-defensive snapshot of all stored image keys
    */
   Set<HashMap<String, Object>> getAxesSet();

   /**
    * Retrieves the full-resolution image at the given axes position.
    *
    * @param axes complete axes map identifying the image (see class-level Javadoc)
    * @return the {@link TaggedImage}, or {@code null} if no image exists at those axes
    */
   TaggedImage getImage(HashMap<String, Object> axes);

   /**
    * Retrieves the image at the given axes position and pyramid resolution level.
    *
    * <p>Resolution level 0 is full resolution; each successive level halves the
    * pixel dimensions (2&times; downsampling in X and Y).  For tiled datasets the
    * tile row and column indices in the axes map must refer to the <em>full-resolution</em>
    * tile grid even when requesting a higher level — the storage maps them internally.
    * Do not divide row/col by 2^level before passing them in.</p>
    *
    * @param axes            complete axes map identifying the image
    * @param resolutionLevel 0 = full resolution, 1 = half resolution, etc.
    *                        Must be in the range {@code [0, getNumResLevels()-1]}.
    * @return the {@link TaggedImage} at the requested level, or {@code null} if absent
    */
   TaggedImage getImage(HashMap<String, Object> axes, int resolutionLevel);

   /**
    * Returns {@code true} if an image exists at the given axes position (full resolution).
    *
    * @param axes complete axes map
    * @return {@code true} if storage contains an image for those axes
    */
   boolean hasImage(HashMap<String, Object> axes);

   /**
    * Returns the dataset's summary metadata JSON.
    *
    * <p>Commonly queried fields include:</p>
    * <ul>
    *   <li>{@code "Width"} / {@code "Height"} — nominal full-resolution tile size in pixels
    *       (may be unreliable; always verify against actual pixel counts from
    *       {@link #getImage}).</li>
    *   <li>{@code "GridPixelOverlapX"} / {@code "GridPixelOverlapY"} — tile overlap in pixels
    *       for tiled datasets.</li>
    *   <li>{@code "ChNames"} — JSON array of channel name strings.</li>
    *   <li>{@code "PixelSize_um"} — pixel size in micrometers.</li>
    * </ul>
    *
    * @return the summary metadata, or {@code null} if not available
    */
   JSONObject getSummaryMetadata();

   /**
    * Returns {@code true} once the dataset has been closed for writing.
    *
    * <p>A finished dataset will not receive new images.  NDViewer uses this to
    * decide whether to keep polling for new data.</p>
    *
    * @return {@code true} if writing is complete
    */
   boolean isFinished();

   /**
    * Returns the number of resolution levels in the image pyramid.
    *
    * <p>Level 0 is full resolution.  Each additional level is 2&times; downsampled.
    * A value of 1 means no pyramid exists (only full resolution is available).</p>
    *
    * @return number of resolution levels, always &ge; 1
    */
   int getNumResLevels();

   /**
    * Returns the absolute path to the directory where this dataset is stored on disk.
    *
    * @return disk path, or {@code null} if the dataset is in-memory only
    */
   String getDiskLocation();

   /**
    * Returns a pre-composited display tile suitable for rendering on screen.
    *
    * <p>This is the preferred retrieval path for the viewer canvas — the storage
    * composites multiple tiles and sub-tiles as needed to fill the requested
    * viewport region at the given resolution level, so the caller does not need
    * to handle tiling logic itself.</p>
    *
    * <p>The returned image has dimensions {@code width × height} pixels at the
    * requested resolution level.  Pixel values are 16-bit ({@code short[]}).</p>
    *
    * @param axes            axes map identifying the current viewing position
    *                        (channel, z, time, etc. — but <em>not</em> row/col,
    *                        which the storage resolves from the offset parameters)
    * @param resolutionLevel 0 = full resolution, 1 = half resolution, etc.
    * @param xOffset         left edge of the viewport in pixels at {@code resolutionLevel}
    * @param yOffset         top edge of the viewport in pixels at {@code resolutionLevel}
    * @param width           viewport width in pixels at {@code resolutionLevel}
    * @param height          viewport height in pixels at {@code resolutionLevel}
    * @return composited {@link TaggedImage}, or {@code null} if no data covers this region
    */
   TaggedImage getDisplayImage(HashMap<String, Object> axes, int resolutionLevel,
                               int xOffset, int yOffset, int width, int height);

   /**
    * Returns the bounding box of the full dataset in full-resolution pixel coordinates.
    *
    * <p>The array layout is {@code [xMin, xMax, yMin, yMax]}, where the values are
    * full-resolution pixel coordinates of the outermost tile edges.  May return
    * {@code null} if the bounds are not yet known (e.g. before any images are written)
    * or if the storage does not track bounds.</p>
    *
    * @return {@code int[4]} as {@code {xMin, xMax, yMin, yMax}}, or {@code null}
    */
   int[] getImageBounds();
}
