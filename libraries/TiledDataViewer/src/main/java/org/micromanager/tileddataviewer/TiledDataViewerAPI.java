package org.micromanager.tileddataviewer;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JPanel;
import mmcorej.org.json.JSONObject;
import org.micromanager.tileddataviewer.overlay.Overlay;

/**
 * Interface for external methods of the NDViewer2 viewer.
 * The only requirement is to call {@link #newImageArrived(HashMap)} newImageArrived}
 * method each time a new image is available.
 *
 */
public interface TiledDataViewerAPI {

   /**
    * Call this whenever a new image arrives to optionally show it, and also so
    * that the size of the scrollbars expands.
    *
    * @param axes map of axis labels to integer positions or String labels.
    */
   void newImageArrived(HashMap<String, Object> axes);

   /**
    * Add a hook function that runs whenever setImage gets called,
    * either programatically or by someone moving the scrollbars.
    *
    * @param hook
    */
   void addSetImageHook(Consumer<HashMap<String, Object>> hook);

   /**
    * Set the scrollbar with a given axis label to a position.
    *
    * @param axis axis name
    * @param pos position to set it to
    */
   void setAxisPosition(String axis, int pos);

   /**
    * Get the position that the scrollbar with the given label is currently at.
    *
    * @param axis
    * @return
    */
   Object getAxisPosition(String axis);

   /**
    * Initialize all controls needed for a dataset loaded from disk
    * where you're not calling newImageArrived each time.
    *
    * @param channelNames names of all channels
    * @param axisMins map of axis names to miniumum extents (can be negative)
    * @param axisMaxs map of axis names to maximum extents
    */
   @Deprecated
    void initializeViewerToLoaded(List<String> channelNames,
              JSONObject displaySettings,
              HashMap<String, Object> axisMins,
              HashMap<String, Object> axisMaxs);

   /**
    * Initialize the viewer for a dataset already on disk (no live newImageArrived calls).
    * Reads all image keys from the data source, registers channels, and sets up scrollbars.
    *
    * @param displaySettings NDViewer display settings JSON (may be null/empty)
    */
   void initializeViewerToLoaded(JSONObject displaySettings);


   /**
    * Set the text in the windows frame.
    *
    * @param string
    */
   void setWindowTitle(String string);

   /**
    * Optional: Pass in a function that tells how to extract elapsed ms from
    * image metadata, for display purposes.
    *
    * @param fn
    */
   void setReadTimeMetadataFunction(Function<JSONObject, Long> fn);

   /**
    * Optional: Pass in a function that tells how to extract z position in µm
    * from image tags, for display purposes.
    *
    * @param fn
    */
   void setReadZMetadataFunction(Function<JSONObject, Double> fn);

   /**
    * Get the JSON represenation of the internal data structure use for saving
    * display and contrast settings. This can be used to save them and then re
    * load them later on.
    *
    * @return
    */
   JSONObject getDisplaySettingsJSON();

   /**
    * Forces the viewer to close, automatically aborting any acquisition in the
    * process.
    */
   void close();

   /**
    * Redraw current image and any overlay
    */
   void update();

   /**
    * get the offset of the top left displayed pixel to relative to top left of
    * the full image (in full resolution coordinates).
    *
    * @return
    */
   Point2D.Double getViewOffset();

   /**
    * Pixel size of the region currently being displayed in full resolution
    * pixels.
    *
    * @return
    */
   Point2D.Double getFullResSourceDataSize();

   /**
    * Return ratio between size of pixels on screen and size of full resolution
    * pixels in the imamge.
    *
    * @return
    */
   double getMagnification();

   /**
    * Change the view offset by the specified amount.
    *
    * @param dx
    * @param dy
    */
   void pan(int dx, int dy);

   /**
    * multiply zoom by given factor, centered at location.
    *
    * @param factor
    * @param location location in display pixel coordinates, or null to zoom in
    *     on center
    */
   void zoom(double factor, Point location);

   /**
    * set the offset of the top left displayed pixel to relative to top left of
    * the full image (in full resolution coordinates).
    *
    */
   void setViewOffset(double newX, double newY);

   /**
    * Set the size (in full-resolution pixels) of the region currently being
    * displayed. This controls the zoom level.
    */
   void setFullResSourceDataSize(double width, double height);

   /**
    * Like {@link #setFullResSourceDataSize}, but automatically applies the
    * canvas aspect-ratio correction so the image fills the canvas correctly.
    * Prefer this over setFullResSourceDataSize() when setting zoom from
    * UI actions (Fit, view-state restore, etc.).
    */
   void setFullResSourceDataSizeAspectCorrected(double width, double height);

   /**
    * Add a custom object to respond to different types of mouse events on the
    * canvas.
    *
    * @param m
    */
   void setCustomCanvasMouseListener(TiledDataViewerCanvasMouseListenerInterface m);

   /**
    * Get the size of the ge displayed on screen.
    *
    * @return
    */
   Point2D.Double getDisplayImageSize();

   /**
    * Set a custom overlay object to be displayed on top of the image. This
    * method can be called an arbitrary number of times by a custom.
    * {@link TiledDataViewerOverlayerPlugin}
    *
    * @param overlay
    */
   void setOverlay(Overlay overlay);

   /**
    * Set a custom object to provide overlays.
    *
    * @param overlayer
    */
   void setOverlayerPlugin(TiledDataViewerOverlayerPlugin overlayer);

   /**
    * trigger redraw of the image overlay.
    */
   void redrawOverlay();

   /**
    * Return the full-resolution pixel bounds of the dataset [xMin, yMin, xMax, yMax],
    * or null if the dataset is unbounded (e.g. a live explore acquisition with no tiles yet).
    */
   int[] getBounds();

   /**
    * Return the canvas JPanel, for cursor and focus manipulation.
    */
   JPanel getCanvasJPanel();

   /**
    * Remove any custom canvas mouse listener and restore the default pan/zoom listener.
    */
   void resetCanvasMouseListener();

}
