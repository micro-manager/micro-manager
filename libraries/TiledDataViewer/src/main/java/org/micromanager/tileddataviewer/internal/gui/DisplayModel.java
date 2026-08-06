package org.micromanager.tileddataviewer.internal.gui;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import mmcorej.org.json.JSONObject;
import org.micromanager.tileddataviewer.TiledDataViewerDataSource;
import org.micromanager.tileddataviewer.internal.TiledDataViewer;
import org.micromanager.tileddataviewer.internal.gui.contrast.DisplaySettings;


/**
 * This class keeps track of the information about how to display the data the viewer is showing.
 */
public class DisplayModel {

   /**
    * How far the view may zoom out past the known extent of the data. At the limit the data
    * spans roughly 1/MAX_ZOOM_OUT_FACTOR of the display in each dimension, i.e. about 10%.
    *
    * <p>This bounds more than just the visuals. Once zoomed out past the coarsest pyramid
    * level the resolution index saturates at getMaxResolutionIndex(), so the downsample factor
    * stops growing while the requested source size keeps growing. ImageMaker asks the storage
    * for (fullResSourceDataSize / downsampleFactor) pixels, so without a limit each further
    * zoom-out step requests a proportionally larger composite -- on a multi-gigapixel tiled
    * dataset that quickly becomes thousands of tile reads on the render thread and freezes
    * the UI.</p>
    */
   private static final double MAX_ZOOM_OUT_FACTOR = 10.0;

   /**
    * How far past the data the view may be panned, as a multiple of the data size, on top of
    * one full view's worth of travel. Generous on purpose: the point of the limit is only to
    * stop the data being lost entirely, never to make part of it hard to reach.
    */
   private static final double PAN_MARGIN_FACTOR = 2.0;

   /**
    * How far past the coarsest pyramid level the view may zoom out, when the data extent is
    * unknown and the pyramid depth is the only clue to the size of the dataset. Small,
    * because the coarsest level already spans the whole dataset -- this only allows a bit of
    * margin around it.
    */
   private static final double PYRAMID_ZOOM_OUT_SLACK = 2.0;

   private DisplaySettings displaySettings_;
   protected DataViewCoords viewCoords_;
   private TiledDataViewerDataSource data_;
   private TiledDataViewer display_;

   /**
    * Largest full-res data extent the viewer has been told about, used to bound zoom-out.
    * Needed because the data sources for the largest datasets (Stitch, Explorer explore mode,
    * Deskew) deliberately report null bounds to get free zoom-out, leaving the bounds-based
    * clamp in zoom() inactive for exactly the datasets that need it most.
    *
    * <p>Grows with the data (a live explore acquisition extends its bounds as tiles arrive)
    * but never shrinks. Only ever grown from an authoritative extent -- getBounds() or an
    * explicitly set source size -- never from the current zoomed source size, which would
    * ratchet the limit outward on every zoom-out step and defeat the clamp entirely.</p>
    */
   private double maxKnownDataWidth_ = 0;
   private double maxKnownDataHeight_ = 0;

   /**
    * Known full-res coordinate range of the data, when the source has reported real bounds.
    * Tracked separately from the width/height above because data coordinates are absolute
    * and need not start at the origin: Explorer derives tile positions from the stage, so
    * the sample can sit at large or negative pixel coordinates. Limiting the view offset
    * using only a size would then place the allowed range nowhere near the data.
    *
    * <p>NaN means no real bounds have been seen yet.</p>
    */
   private double knownDataMinX_ = Double.NaN;
   private double knownDataMinY_ = Double.NaN;
   private double knownDataMaxX_ = Double.NaN;
   private double knownDataMaxY_ = Double.NaN;

   // Axes may use integer or string positions. Keep track of which
   // uses which ones do this here, and which string values map to which
   // Integer positions (because these are needed for display)
   private ConcurrentHashMap<String, LinkedList<String>> stringAxes_ = new ConcurrentHashMap<>();
   // Active channel last applied by scrollbarsMoved(), used to skip redundant display
   // settings writes during playback.  Null means "unknown; apply on next call".
   private String lastActiveChannel_ = null;
   private final boolean rgb_;



   public DisplayModel(TiledDataViewer display, TiledDataViewerDataSource data, Preferences prefs,
                       boolean rgb) {
      rgb_ = rgb;
      display_ = display;
      displaySettings_ = new DisplaySettings(prefs);
      data_ = data;

      int[] bounds = data.getBounds();
      viewCoords_ = new DataViewCoords(data, 0, 0,
              bounds == null ? null : (double) (bounds[2] - bounds[0]),
              bounds == null ? null : (double) (bounds[3] - bounds[1]),
              data.getBounds(), rgb);
      growMaxKnownDataSizeFromBounds(bounds);
   }

   /**
    * Records a known full-res data extent, growing the zoom-out limit if this is the largest
    * seen so far. Ignores non-positive sizes so an uninitialized extent never sets the limit.
    */
   private void growMaxKnownDataSize(double width, double height) {
      if (width > 0 && height > 0) {
         maxKnownDataWidth_ = Math.max(maxKnownDataWidth_, width);
         maxKnownDataHeight_ = Math.max(maxKnownDataHeight_, height);
      }
   }

   /**
    * Largest full-res source size the view may zoom out to along one dimension, or 0 if no
    * limit can be determined.
    *
    * <p>Prefers the known data extent from getBounds(). Many datasets never report bounds --
    * a stitched NDTiff has no per-tile position tags, so Explorer returns null for the whole
    * session -- and for those the pyramid depth is used instead: the storage builds levels
    * until the whole dataset fits in roughly one tile, so the coarsest level's span,
    * (2^maxResIndex * canvas), is an upper bound on the data size and scales with it. The
    * canvas alone must NOT be used here; it says nothing about how big the data is, and a
    * fixed multiple of the window stops large datasets from ever being fully zoomed out.</p>
    *
    * <p>The limit is derived from the LARGER data dimension and applied to both axes. The
    * source size is inflated along one axis to match the canvas aspect ratio (see
    * onCanvasResize), so a per-axis limit would stop a wide sample from ever being framed in
    * a tall window -- the inflated dimension would hit its own axis' limit first.</p>
    *
    * @param knownDataSize largest data extent seen along either dimension, or 0 if unknown
    * @param displaySize largest canvas dimension
    * @param maxResIndex coarsest available pyramid level index
    * @return the maximum full-res source size, or 0 if nothing is known yet
    */
   private static double zoomOutLimit(double knownDataSize, double displaySize,
                                      int maxResIndex) {
      if (knownDataSize > 0) {
         return knownDataSize * MAX_ZOOM_OUT_FACTOR;
      }
      //No bounds available: infer the scale of the data from the pyramid instead. At the
      //coarsest level the whole dataset is small, so 2^maxResIndex canvases across is a
      //reasonable stand-in for "the whole dataset", and zooming a little past that is enough.
      if (maxResIndex > 0 && displaySize > 0) {
         return displaySize * Math.pow(2, maxResIndex) * PYRAMID_ZOOM_OUT_SLACK;
      }
      //Nothing known at all (single-resolution source): fall back to the canvas.
      return displaySize > 0 ? displaySize * MAX_ZOOM_OUT_FACTOR : 0;
   }

   /**
    * Records the dataset's full-resolution size, when the source can report it. This is the
    * authoritative answer for the zoom-out limit and is available even from sources that
    * return null bounds to keep navigation unclamped.
    */
   private void growMaxKnownDataSizeFromFullResolutionSize() {
      int[] size = data_.getFullResolutionSize();
      if (size != null && size.length >= 2) {
         growMaxKnownDataSize(size[0], size[1]);
      }
   }

   /**
    * Records the extent described by a bounds array, if it is present and initialized.
    * Bounds are {x_min, y_min, x_max, y_max}; unbounded sources use Integer.MIN_VALUE/MAX_VALUE
    * sentinels, which must not be taken as a real extent.
    */
   private void growMaxKnownDataSizeFromBounds(int[] bounds) {
      if (bounds == null || bounds[0] == Integer.MIN_VALUE) {
         return;
      }
      growMaxKnownDataSize(bounds[2] - bounds[0], bounds[3] - bounds[1]);
      //Remember where the data actually is, not just how big it is; these coordinates are
      //absolute and the data need not start at the origin.
      knownDataMinX_ = min(knownDataMinX_, bounds[0]);
      knownDataMinY_ = min(knownDataMinY_, bounds[1]);
      knownDataMaxX_ = max(knownDataMaxX_, bounds[2]);
      knownDataMaxY_ = max(knownDataMaxY_, bounds[3]);
   }

   /** Math.min that treats NaN (no value yet) as absent rather than poisoning the result. */
   private static double min(double current, double candidate) {
      return Double.isNaN(current) ? candidate : Math.min(current, candidate);
   }

   /** Math.max that treats NaN (no value yet) as absent rather than poisoning the result. */
   private static double max(double current, double candidate) {
      return Double.isNaN(current) ? candidate : Math.max(current, candidate);
   }

   /**
    * Need to call this when loading them from disk.
    */
   public void setDisplaySettings(DisplaySettings displaySettings) {
      this.displaySettings_ = displaySettings;
   }

   public DisplaySettings getDisplaySettings() {
      return displaySettings_;
   }

   public int getIntegerPositionFromStringPosition(String axisName, String axisPosition) {
      // A scroller can be queried before its string axis has been registered (e.g. the first
      // repaint races the image event that populates stringAxes_), so default to position 0
      // rather than dereferencing a missing list. The next image event corrects the position.
      LinkedList<String> values = stringAxes_.get(axisName);
      if (values == null) {
         return 0;
      }
      int idx = values.indexOf(axisPosition);
      return idx < 0 ? 0 : idx;
   }

   public String getStringPositionFromIntegerPosition(String axisName, int axisPosition) {
      // Never return null: ScrollerPanel.checkForImagePositionChanged() caches this value and
      // later calls .equals() on the cached entry, so a null would NPE on the next scroll during
      // an early-initialization race. Fall back to "" when the axis has no values yet, and clamp
      // an out-of-range index to the nearest valid position.
      LinkedList<String> values = stringAxes_.get(axisName);
      if (values == null || values.isEmpty()) {
         return "";
      }
      int clamped = Math.max(0, Math.min(axisPosition, values.size() - 1));
      return values.get(clamped);
   }

   public void channelWasSetActiveByCheckbox(String channelName, boolean selected) {
      if (!displaySettings_.isCompositeMode()) {
         if (selected) {
            viewCoords_.setAxisPosition(TiledDataViewer.CHANNEL_AXIS, channelName);

            //only one channel can be active so inacivate others
            for (String channel : display_.getDisplayModel().getDisplayedChannels()) {
               displaySettings_.setActive(channel, channel.equals(
                        viewCoords_.getAxisPosition(TiledDataViewer.CHANNEL_AXIS)));
            }
         } else {
            //if channel turns off, nothing will show, so dont let this happen
         }
         //make sure other checkboxes update if they autochanged
         display_.updateActiveChannelCheckboxes();
      } else {
         //composite mode
         displaySettings_.setActive(channelName, selected);
      }
   }

   public void pan(int dx, int dy) {
      //Fetch once and reuse: getBounds() can be O(tiles) and allocating (ExplorerDataSource
      //scans every tile position), and this runs on the EDT for every mouse-drag event.
      int[] bounds = data_.getBounds();
      growMaxKnownDataSizeFromBounds(bounds);
      Point2D.Double offset = viewCoords_.getViewOffset();
      double newX = offset.x + (dx / viewCoords_.getMagnificationFromResLevel())
              * viewCoords_.getDownsampleFactor();
      double newY = offset.y + (dy / viewCoords_.getMagnificationFromResLevel())
              * viewCoords_.getDownsampleFactor();

      if (bounds != null) {
         viewCoords_.setViewOffset(
                 Math.max(viewCoords_.xMin_, Math.min(newX, viewCoords_.xMax_
                         - viewCoords_.getFullResSourceDataSize().x)),
                 Math.max(viewCoords_.yMin_, Math.min(newY, viewCoords_.yMax_
                         - viewCoords_.getFullResSourceDataSize().y)));
      } else {
         //Unbounded source: keep the data within reach anyway. Zoomed far out the
         //magnification is tiny, so one pixel of drag moves the view a huge distance in
         //full-res coordinates -- without a limit a single drag lands far outside the data
         //(a black canvas with no way back) and asks the storage to composite a vast empty
         //region, which hangs the UI.
         Point2D.Double viewSize = viewCoords_.getFullResSourceDataSize();
         viewCoords_.setViewOffset(
                 clampOffsetForUnboundedData(newX, knownDataMinX_, knownDataMaxX_, viewSize.x),
                 clampOffsetForUnboundedData(newY, knownDataMinY_, knownDataMaxY_, viewSize.y));
      }
   }

   /**
    * Keeps a view offset within a sane distance of the data when the source reports no
    * bounds, without ever making part of the data unreachable.
    *
    * <p>Works in absolute full-res coordinates: the allowed range is the known data range
    * (which need not start at the origin) grown by a margin on each side. The margin scales
    * with the current view size as well as the data size, so at any zoom level the view can
    * always be moved clear across the data and somewhat beyond -- panning must never be able
    * to cut off the far edge of the sample.</p>
    *
    * @param offset requested view offset along one dimension, in full-res pixels
    * @param dataMin low edge of the known data range, or NaN if unknown
    * @param dataMax high edge of the known data range, or NaN if unknown
    * @param viewSize current source size of the view along that dimension
    * @return the offset, limited to the allowed range
    */
   private static double clampOffsetForUnboundedData(double offset, double dataMin,
                                                     double dataMax, double viewSize) {
      if (Double.isNaN(dataMin) || Double.isNaN(dataMax) || dataMax <= dataMin) {
         return offset; //nothing known about where the data is; leave panning unrestricted
      }
      //Margin covers a full view plus a multiple of the data size, so the view can always be
      //scrolled past either edge no matter how far out the zoom is.
      double margin = (dataMax - dataMin) * PAN_MARGIN_FACTOR + Math.max(viewSize, 0);
      return Math.max(dataMin - margin, Math.min(offset, dataMax + margin));
   }

   public void zoom(double factor, Point mouseLocation) {
      //get zoom center in full res pixel coords
      Point2D.Double viewOffset = viewCoords_.getViewOffset();
      Point2D.Double sourceDataSize = viewCoords_.getFullResSourceDataSize();
      Point2D.Double zoomCenter;
      //compute centroid of the zoom in full res coordinates
      if (mouseLocation == null) {
         //if mouse not over image zoom to center
         zoomCenter = new Point2D.Double(viewOffset.x + sourceDataSize.x / 2,
                 viewOffset.y + sourceDataSize.y / 2);
      } else {
         zoomCenter = new Point2D.Double(
                 (long) viewOffset.x + mouseLocation.x
                         / viewCoords_.getMagnificationFromResLevel()
                         * viewCoords_.getDownsampleFactor(),
                 (long) viewOffset.y + mouseLocation.y
                         / viewCoords_.getMagnificationFromResLevel()
                         * viewCoords_.getDownsampleFactor());
      }

      //Do zooming--update size of source data
      double newSourceDataWidth = sourceDataSize.x * factor;
      double newSourceDataHeight = sourceDataSize.y * factor;
      if (newSourceDataWidth < 5 || newSourceDataHeight < 5) {
         return; //constrain maximum zoom
      }
      //Pick up the data extent if it has become available since the last zoom. For a saved
      //dataset newImageArrived() never fires, so updateDisplayBounds() never runs and the
      //seeding hooks alone would leave the limit unarmed. Fetch the bounds once and reuse
      //them below: getBounds() can be O(tiles) and allocating (ExplorerDataSource scans every
      //tile position), and this runs on the EDT for every wheel event.
      growMaxKnownDataSizeFromFullResolutionSize();
      int[] bounds = data_.getBounds();
      growMaxKnownDataSizeFromBounds(bounds);
      //constrain maximum zoom out. Unlike the bounds-based clamp below this applies even when
      //the data source reports null bounds, which is the case for the large tiled datasets.
      //One isotropic limit from the larger dimension, so aspect-ratio inflation of the
      //smaller axis can never stop the whole sample from being framed.
      Point2D.Double displayImageSize = viewCoords_.getDisplayImageSize();
      double limit = zoomOutLimit(Math.max(maxKnownDataWidth_, maxKnownDataHeight_),
              Math.max(displayImageSize.x, displayImageSize.y),
              data_.getMaxResolutionIndex());
      if (limit > 0) {
         double excess = Math.max(newSourceDataWidth, newSourceDataHeight) / limit;
         if (excess > 1) {
            //scale both dimensions by the same factor to preserve the aspect ratio
            newSourceDataWidth = newSourceDataWidth / excess;
            newSourceDataHeight = newSourceDataHeight / excess;
         }
      }
      if (bounds != null) {
         //Don't zoom out past the point where the whole dataset is visible. Compare against
         //the source size that just frames the data at the current canvas aspect ratio, NOT
         //against the raw data extent: the source is inflated along one axis to match the
         //canvas (see onCanvasResize), so testing the inflated dimension against that axis'
         //raw extent makes a mismatched aspect look like overzoom when it is not. Doing so
         //scales BOTH dimensions down and leaves only a fraction of the data visible -- a
         //4:1 sample in a square window would show just 25% of its width.
         double dataWidth = viewCoords_.xMax_ - viewCoords_.xMin_;
         double dataHeight = viewCoords_.yMax_ - viewCoords_.yMin_;
         double framedWidth = dataWidth;
         double framedHeight = dataHeight;
         Point2D.Double canvas = viewCoords_.getDisplayImageSize();
         if (canvas.x > 0 && canvas.y > 0 && dataWidth > 0 && dataHeight > 0) {
            double canvasAspect = canvas.x / canvas.y;
            double dataAspect = dataWidth / dataHeight;
            if (canvasAspect > dataAspect) {
               framedWidth = dataHeight * canvasAspect; //letterboxed left/right
            } else {
               framedHeight = dataWidth / canvasAspect; //letterboxed top/bottom
            }
         }
         double overzoomXFactor = newSourceDataWidth / framedWidth;
         double overzoomYFactor = newSourceDataHeight / framedHeight;
         if (overzoomXFactor > 1 || overzoomYFactor > 1) {
            newSourceDataWidth = newSourceDataWidth / Math.max(overzoomXFactor, overzoomYFactor);
            newSourceDataHeight = newSourceDataHeight / Math.max(overzoomXFactor, overzoomYFactor);
         }
      }
      viewCoords_.setFullResSourceDataSize(newSourceDataWidth, newSourceDataHeight);

      double xOffset = (zoomCenter.x - (zoomCenter.x - viewOffset.x)
              * newSourceDataWidth / sourceDataSize.x);
      double yOffset = (zoomCenter.y - (zoomCenter.y - viewOffset.y)
              * newSourceDataHeight / sourceDataSize.y);
      //make sure view doesn't go outside image bounds
      if (bounds != null) {
         viewCoords_.setViewOffset(
                 Math.max(viewCoords_.xMin_, Math.min(xOffset,
                         viewCoords_.xMax_ - viewCoords_.getFullResSourceDataSize().x)),
                 Math.max(viewCoords_.yMin_, Math.min(yOffset,
                         viewCoords_.yMax_ - viewCoords_.getFullResSourceDataSize().y)));
      } else {
         //Same reachability limit as pan(): a zoom centred near the edge of a far-out view
         //can also throw the offset well beyond the data.
         Point2D.Double newViewSize = viewCoords_.getFullResSourceDataSize();
         viewCoords_.setViewOffset(
                 clampOffsetForUnboundedData(xOffset, knownDataMinX_, knownDataMaxX_,
                         newViewSize.x),
                 clampOffsetForUnboundedData(yOffset, knownDataMinY_, knownDataMaxY_,
                         newViewSize.y));
      }
   }

   public void onCanvasResize(int w, int h) {
      Point2D.Double displaySizeOld = viewCoords_.getDisplayImageSize();
      //reshape the source image to match canvas aspect ratio
      //expand it, unless it would put it out of range
      double canvasAspect = w / (double) h;
      Point2D.Double source = viewCoords_.getFullResSourceDataSize();
      double sourceAspect = source.x / source.y;
      double newSourceX;
      double newSourceY;
      if (data_.getBounds() != null) {
         if (canvasAspect > sourceAspect) {
            newSourceX = canvasAspect / sourceAspect * source.x;
            newSourceY = source.y;
            //check that still within image bounds
         } else {
            newSourceX = source.x;
            newSourceY = source.y / (canvasAspect / sourceAspect);
         }

         double overzoomXFactor = newSourceX / (viewCoords_.xMax_ - viewCoords_.xMin_);
         double overzoomYFactor = newSourceY / (viewCoords_.yMax_ - viewCoords_.yMin_);
         if (overzoomXFactor > 1 || overzoomYFactor > 1) {
            newSourceX = newSourceX / Math.max(overzoomXFactor, overzoomYFactor);
            newSourceY = newSourceY / Math.max(overzoomXFactor, overzoomYFactor);
         }
      } else if (displaySizeOld.x != 0 && displaySizeOld.y != 0) {
         newSourceX = source.x * (w / displaySizeOld.x);
         newSourceY = source.y * (h / displaySizeOld.y);
      } else {
         newSourceX = source.x / sourceAspect * canvasAspect;
         newSourceY = source.y;
      }
      //move into visible area
      viewCoords_.setViewOffset(
              Math.max(viewCoords_.xMin_, Math.min(viewCoords_.xMax_
                      - newSourceX, viewCoords_.getViewOffset().x)),
              Math.max(viewCoords_.yMin_, Math.min(viewCoords_.yMax_
                      - newSourceY, viewCoords_.getViewOffset().y)));

      //set the size of the display iamge
      viewCoords_.setDisplayImageSize(w, h);
      //and the size of the source pixels from which it derives
      viewCoords_.setFullResSourceDataSize(newSourceX, newSourceY);
   }

   public void setViewOffset(double newX, double newY) {
      viewCoords_.setViewOffset(newX, newY);
   }

   public void setFullResSourceDataSize(double width, double height) {
      //This is how a source with null bounds (e.g. Stitch) tells the viewer its canvas size,
      //so it is the only extent information available for the zoom-out limit in that case.
      growMaxKnownDataSize(width, height);
      viewCoords_.setFullResSourceDataSize(width, height);
   }

   /**
    * Sets the full-res source data size adjusted for the current canvas aspect ratio,
    * preserving the requested zoom area. Use this instead of setFullResSourceDataSize()
    * when the canvas is already sized, to avoid aspect-ratio mismatch.
    */
   public void setFullResSourceDataSizeAspectCorrected(double requestedWidth,
                                                       double requestedHeight) {
      Point2D.Double canvasSize = viewCoords_.getDisplayImageSize();
      if (canvasSize.x == 0 || canvasSize.y == 0) {
         growMaxKnownDataSize(requestedWidth, requestedHeight);
         viewCoords_.setFullResSourceDataSize(requestedWidth, requestedHeight);
         return;
      }
      double canvasAspect = canvasSize.x / canvasSize.y;
      double sourceAspect = requestedWidth / requestedHeight;
      double newW;
      double newH;
      if (canvasAspect > sourceAspect) {
         newW = canvasAspect / sourceAspect * requestedWidth;
         newH = requestedHeight;
      } else {
         newW = requestedWidth;
         newH = requestedHeight / (canvasAspect / sourceAspect);
      }
      int[] bounds = viewCoords_.getBounds();
      if (bounds != null && bounds[0] != Integer.MIN_VALUE) {
         double ovX = newW / (bounds[2] - bounds[0]);
         double ovY = newH / (bounds[3] - bounds[1]);
         if (ovX > 1 || ovY > 1) {
            double scale = Math.max(ovX, ovY);
            newW /= scale;
            newH /= scale;
         }
      }
      //Record the requested extent rather than newW/newH: the latter is inflated to fill the
      //canvas aspect ratio and would overstate how big the data actually is.
      growMaxKnownDataSize(requestedWidth, requestedHeight);
      viewCoords_.setFullResSourceDataSize(newW, newH);
   }

   public Point2D.Double getFullResSourceDataSize() {
      return viewCoords_.getFullResSourceDataSize();
   }

   public DataViewCoords copyViewCoords() {
      return viewCoords_.copy();
   }

   public Point2D.Double getDisplayImageSize() {
      return viewCoords_.getDisplayImageSize();
   }

   public void setCompositeMode(boolean selected) {
      displaySettings_.setCompositeMode(selected);
      // Forget the memoized active channel: leaving composite mode must re-apply the
      // active flags even if the channel position itself has not changed.
      lastActiveChannel_ = null;
      //select all channels if composite mode is being turned on
      if (selected) {
         for (String channel : getDisplayedChannels()) {
            displaySettings_.setActive(channel, true);
            display_.updateActiveChannelCheckboxes();
         }
      } else {
         for (String channel : getDisplayedChannels()) {
            if (viewCoords_.getAxesPositions().containsKey(TiledDataViewer.CHANNEL_AXIS)) {
               displaySettings_.setActive(channel, viewCoords_.getAxesPositions()
                        .get(TiledDataViewer.CHANNEL_AXIS).equals(channel));
               display_.updateActiveChannelCheckboxes();
            }
         }
      }
   }

   /**
    * Displayed channels are the actual channels, or if there are no channels, a dummy one is added.
    *
    * @return Displayed Channel names, or a dummy if there are no channels.
    */
   public List<String> getDisplayedChannels() {
      List<String> channels = new LinkedList<>();
      if (stringAxes_.containsKey(TiledDataViewer.CHANNEL_AXIS)) {
         channels = stringAxes_.get(TiledDataViewer.CHANNEL_AXIS);
      }
      if (channels.size() == 0) {
         channels.add(TiledDataViewer.NO_CHANNEL);
      }
      return channels;
   }

   /**
    * Called upon a new image arriving.
    */
   public void parseNewAxesToUpdateDisplayModel(HashMap<String, Object> axesPositions)  {
      // Update string valued axes, including channels
      for (String axis : axesPositions.keySet()) {
         if (!(axesPositions.get(axis) instanceof String)) {
            continue;
         }
         if (!stringAxes_.containsKey(axis)) {
            stringAxes_.put(axis, new LinkedList<String>());
         }
         if (!stringAxes_.get(axis).contains(axesPositions.get(axis))) {
            stringAxes_.get(axis).add((String) axesPositions.get(axis));
            if (axis.equals(TiledDataViewer.CHANNEL_AXIS)) {
               Runnable channelSetup = new Runnable() {
                  @Override
                  public void run() {
                     // make sure GUI and display settings are in sync
                     display_.readHistogramControlsStateFromGUI();
                     String channelName = (String) axesPositions
                              .get(TiledDataViewer.CHANNEL_AXIS);

                     if (!channelName.equals(TiledDataViewer.NO_CHANNEL)
                              && displaySettings_.containsChannel(TiledDataViewer.NO_CHANNEL)) {
                        // remove the dummy channel
                        displaySettings_.removeChannel(TiledDataViewer.NO_CHANNEL);
                     }

                     int bitDepth = display_.getDataSource().getImageBitDepth(axesPositions);
                     //Add contrast controls and display settings
                     if (!displaySettings_.containsChannel(channelName)) {
                        displaySettings_.addChannel(channelName, bitDepth);
                     }
                     if (!displaySettings_.isCompositeMode()) {
                        // set only this new channel active; deactivate all others
                        for (String cName : stringAxes_.get(TiledDataViewer.CHANNEL_AXIS)) {
                           displaySettings_.setActive(cName, cName.equals(channelName));
                        }
                     }
                     display_.getGUIManager().addContrastControlsIfNeeded(channelName);
                  }
               };
               try {
                  if (SwingUtilities.isEventDispatchThread()) {
                     channelSetup.run();
                  } else {
                     SwingUtilities.invokeAndWait(channelSetup);
                  }
               } catch (Exception e) {
                  throw new RuntimeException(e);
               }
            }
         }
      }
   }

   public int[] getBounds() {
      return viewCoords_.getBounds();
   }

   public void setImageBounds(int[] newBounds) {
      //A live explore acquisition grows its bounds as tiles arrive; without this the zoom-out
      //limit would stay pinned to the extent of the first tile.
      growMaxKnownDataSizeFromBounds(newBounds);
      viewCoords_.setImageBounds(newBounds);
   }

   public Point2D.Double getViewOffset() {
      return viewCoords_.getViewOffset();
   }

   public Object getAxisPosition(String axis) {
      return viewCoords_.getAxisPosition(axis);
   }

   public double getMagnification() {
      return viewCoords_.getMagnification();
   }

   public void setAxisPosition(String axis, Object o) {
      viewCoords_.setAxisPosition(axis, o);
   }

   public void scrollbarsMoved(HashMap<String, Object> axes) {
      // If the viewer is not in composite mode (i.e. one channel is shown at a time
      // then when the scrollbars are moved, the active channel should be changed
      // so that the checkbox changes
      if (!displaySettings_.isCompositeMode()) {
         //set all channels inactive except current one
         if (viewCoords_.getAxesPositions().containsKey(TiledDataViewer.CHANNEL_AXIS)) {
            String activeChannel = (String) viewCoords_.getAxesPositions()
                     .get(TiledDataViewer.CHANNEL_AXIS);
            // Only rewrite the active flags when the active channel actually changed.
            // This runs on every image event, including every frame of playback along
            // an unrelated axis, and each write drives a GUI refresh: doing it
            // unconditionally makes the display flash during fast playback.
            if (activeChannel != null && !activeChannel.equals(lastActiveChannel_)) {
               lastActiveChannel_ = activeChannel;
               for (String c : getDisplayedChannels()) {
                  displaySettings_.setActive(c, activeChannel.equals(c));
               }
               // Refresh once, not once per channel.
               display_.getGUIManager().updateGUIFromDisplaySettings();
            }
         }
      }
   }

   public void updateDisplayBounds() {
      // Check for changed bounds of the underlying data
      if (display_.getDataSource().getBounds() != null) {
         int[] newBounds = display_.getDataSource().getBounds();
         int[] oldBounds = display_.getDisplayModel().getBounds();
         double xResize = (oldBounds[2] - oldBounds[0]) / (double) (newBounds[2] - newBounds[0]);
         double yResize = (oldBounds[3] - oldBounds[1]) / (double) (newBounds[3] - newBounds[1]);
         setImageBounds(newBounds);
         if (xResize < 1 || yResize < 1) {
            zoom(1 / Math.min(xResize, yResize), null);
         }
      }
   }

   public JSONObject getDisplaySettingsJSON() {
      if (displaySettings_ == null) {
         return null;
      }
      return displaySettings_.toJSON();
   }

   public DisplaySettings getDisplaySettingsObject() {
      return displaySettings_;
   }

   public boolean isCompositeMode() {
      return displaySettings_.isCompositeMode();
   }

   public double getPlaybackFPS() {
      return displaySettings_.getPlaybackFPS();
   }

   public void setPlaybackFPS(double fps) {
      displaySettings_.setPlaybackFPS(fps);
   }

   public boolean isIntegerAxis(String axis) {
      return !stringAxes_.containsKey(axis);
   }
}
