package org.micromanager.ndviewer2.internal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.display.AbstractDataViewer;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplaySettings;
// API gaps: the following are internal mmstudio classes that would need to be
// promoted to public API for this library to be fully independent:
//   - ImageStatsPublisher (display.inspector.internal.panels.intensity)
//   - StatsComputeQueue and imagestats classes (display.internal.imagestats)
//   - DataViewerWillCloseEvent, DataViewerDidBecomeActiveEvent,
//     DataViewerDidBecomeVisibleEvent (display.internal.event)
import org.micromanager.display.inspector.internal.panels.intensity.ImageStatsPublisher;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeVisibleEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImageStats;
import org.micromanager.display.internal.imagestats.ImageStatsRequest;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.display.internal.imagestats.IntegerComponentStats;
import org.micromanager.display.internal.imagestats.StatsComputeQueue;
import org.micromanager.display.internal.event.DisplayWindowDidAddOverlayEvent;
import org.micromanager.display.internal.event.DisplayWindowDidRemoveOverlayEvent;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.display.overlay.OverlayListener;
import org.micromanager.display.overlay.OverlaySupport;
import org.micromanager.ndviewer2.NDViewer2AcqInterface;
import org.micromanager.ndviewer2.NDViewer2DataSource;
import org.micromanager.ndviewer2.NDViewer2DataViewerAPI;
import org.micromanager.ndviewer2.NDViewer2OverlayerPlugin;
import org.micromanager.ndviewer2.internal.NDViewer2;
import org.micromanager.ndviewer2.internal.gui.ChannelRenderSettings;
import org.micromanager.ndviewer2.internal.gui.ContrastUpdateCallback;
import org.micromanager.ndviewer2.internal.gui.GlobalRenderSettings;

/**
 * NDViewer2 data viewer: combines NDViewer's pyramidal canvas with MM's
 * Inspector panel system.
 *
 * <p>Extends AbstractDataViewer so the MM Inspector can discover and attach to
 * it. Implements ImageStatsPublisher so the Inspector histogram panel receives
 * histogram data. Implements StatsComputeQueue.Listener for background
 * histogram computation callbacks.</p>
 */
public final class NDViewer2DataViewer extends AbstractDataViewer
      implements NDViewer2DataViewerAPI, ImageStatsPublisher, StatsComputeQueue.Listener,
      ContrastUpdateCallback, OverlaySupport {

   private static final long MIN_REPAINT_PERIOD_NS = Math.round(1e9 / 60.0);

   private final Studio studio_;
   private final NDViewer2 ndViewer2_;
   private final NDViewer2DataProvider dataProvider_;
   private final AxesBridge axesBridge_;
   private final StatsComputeQueue computeQueue_ = StatsComputeQueue.create();

   // Single-threaded executor for off-render-thread stats fetching.
   // Using a size-1 queue so in-flight fetches are superseded by the latest position.
   private final ExecutorService statsExecutor_ =
         Executors.newSingleThreadExecutor(
               (Runnable r) -> new Thread(r, "NDViewer2 stats fetch thread"));

   private final TreeMap<Integer, DataViewerListener> listeners_ =
         new TreeMap<>();

   // The most recent images and stats (for getCurrentImagesAndStats)
   private volatile ImagesAndStats currentImagesAndStats_;

   // Feedback loop guard for position: true while we are pushing position
   // to NDViewer, so that the resulting setImageHook does not recurse.
   private volatile boolean updatingPosition_ = false;

   // Accumulated stats per channel across all tiles.
   // Used by newImageArrived() path to build per-channel histograms.
   // Key is channel index, value is accumulated ImageStats for that channel.
   private final Map<Integer, ImageStats> accumulatedStatsPerChannel_ = new HashMap<>();

   // When true, newImageArrived() stats are merged into accumulatedStatsPerChannel_
   // and ALL stats results (including scrollbar navigation) post the
   // accumulated histogram. Set to true for the entire explore session.
   private volatile boolean accumulateMode_ = false;

   // Set of channel indices whose next stats result should be merged into the
   // accumulator. Replaces the old single boolean to correctly handle multiple
   // channels arriving in rapid succession without flag aliasing.
   private final java.util.Set<Integer> accumulateNextChannels_ =
         java.util.Collections.synchronizedSet(new java.util.HashSet<>());

   // Closed state
   private volatile boolean closed_ = false;

   // Set to true once the first real image has been rendered (initializeViewerToLoaded
   // or first newImageArrived). Used to suppress premature redrawOverlay() calls at
   // startup before any image is available.
   private volatile boolean viewerInitialized_ = false;

   // MM Inspector overlay support
   private final List<Overlay> mmOverlays_ = new CopyOnWriteArrayList<>();
   // Listeners registered on each overlay so we can remove them on removeOverlay()
   private final Map<Overlay, OverlayListener> mmOverlayListeners_ = new HashMap<>();
   // External overlayer plugin slot (e.g. DeskewExploreDataSource tile grid)
   private volatile NDViewer2OverlayerPlugin externalOverlayerPlugin_ = null;
   // Last rendered BufferedImage of MM overlays — read by the persistent mmOverlayRoi_
   private volatile BufferedImage mmOverlayBuf_ = null;
   // Single persistent Roi that draws mmOverlayBuf_ onto the canvas each repaint
   private final org.micromanager.ndviewer2.overlay.Roi mmOverlayRoi_ =
         new org.micromanager.ndviewer2.overlay.Roi(0, 0, 1, 1) {
            @Override
            public void drawOverlay(java.awt.Graphics gr) {
               BufferedImage buf = mmOverlayBuf_;
               if (buf != null) {
                  gr.drawImage(buf, 0, 0, null);
               }
            }
         };

   /**
    * Create an NDViewer2DataViewer.
    *
    * @param studio          the MM Studio instance
    * @param dataSource      NDViewer data source (typically NDTiffAndViewerAdapter)
    * @param acqInterface    acquisition control interface (may be null)
    * @param dataProvider    the MM DataProvider wrapping NDTiff storage
    * @param summaryMetadata NDTiff summary metadata JSON
    * @param pixelSizeUm     pixel size in micrometers
    * @param rgb             whether images are RGB
    */
   public NDViewer2DataViewer(Studio studio,
                               NDViewer2DataSource dataSource,
                               NDViewer2AcqInterface acqInterface,
                               NDViewer2DataProvider dataProvider,
                               JSONObject summaryMetadata,
                               double pixelSizeUm,
                               boolean rgb) {
      super(studio.displays().displaySettingsBuilder().build());
      studio_ = studio;
      dataProvider_ = dataProvider;
      axesBridge_ = dataProvider.getAxesBridge();

      // Create NDViewer (the canvas/scrollbar viewer)
      ndViewer2_ = new NDViewer2(dataSource, acqInterface,
            summaryMetadata, pixelSizeUm, rgb);

      // Register the internal bridge overlayer plugin that chains MM overlays
      // into NDViewer's render pipeline.
      ndViewer2_.setOverlayerPlugin(createBridgeOverlayerPlugin());

      // Set up stats computation
      computeQueue_.addListener(this);

      // Hook into NDViewer to detect image changes
      ndViewer2_.addSetImageHook(axes -> onNDViewerImageChanged(axes));

      // Register with Studio so Inspector discovers us
      studio_.displays().addViewer(this);

      // Notify the Inspector that this viewer is visible and active
      postEvent(DataViewerDidBecomeVisibleEvent.create(this));
      postEvent(DataViewerDidBecomeActiveEvent.create(this));

      // Initialize display position
      setDisplayPosition(Coordinates.builder().build());
   }

   // ---- AbstractDataViewer abstract methods ----

   @Override
   protected DisplaySettings handleDisplaySettings(
         DisplaySettings requestedSettings) {
      if (closed_) {
         return requestedSettings;
      }
      try {
         // Push MM settings into NDViewer's ImageMaker as render parameters
         pushRenderSettings(requestedSettings);
         ndViewer2_.update();
      } catch (NullPointerException e) {
         studio_.logs().logDebugMessage("NDViewer2: NPE in handleDisplaySettings "
               + "(likely async close race): " + e.getMessage());
      }
      return requestedSettings;
   }

   /**
    * Build render settings from MM DisplaySettings and push them into NDViewer's ImageMaker.
    */
   private void pushRenderSettings(DisplaySettings ds) {
      List<String> channelNames = axesBridge_.getChannelNames();
      if (channelNames.isEmpty()) {
         // Single channel — use NO_CHANNEL
         channelNames = new ArrayList<>();
         channelNames.add(NDViewer2.NO_CHANNEL);
      }

      Map<String, ChannelRenderSettings> channelSettings = new HashMap<>();
      for (int i = 0; i < channelNames.size(); i++) {
         String name = channelNames.get(i);
         int min = 0;
         int max = 65535;
         double gamma = 1.0;
         Color color = Color.white;
         boolean active = true;

         if (i < ds.getNumberOfChannels()) {
            ChannelDisplaySettings ch = ds.getChannelSettings(i);
            ComponentDisplaySettings comp = ch.getComponentSettings(0);
            min = (int) comp.getScalingMinimum();
            max = (int) comp.getScalingMaximum();
            gamma = comp.getScalingGamma();
            color = ch.getColor();
            active = ch.isVisible();
         }
         channelSettings.put(name, new ChannelRenderSettings(min, max, gamma, color, active));
      }

      boolean composite = ds.getColorMode() == DisplaySettings.ColorMode.COMPOSITE;
      boolean autostretch = ds.isAutostretchEnabled();
      boolean logHist = ds.isHistogramLogarithmic();
      double percentile = ds.getAutoscaleIgnoredPercentile();
      boolean ignoreOutliers = percentile > 0;

      GlobalRenderSettings globalSettings = new GlobalRenderSettings(
            autostretch, ignoreOutliers, percentile, composite, logHist);

      ndViewer2_.setRenderSettings(channelSettings, globalSettings, this);

      // Keep NDViewer's internal composite mode in sync (needed for scrollbar/checkbox logic)
      ndViewer2_.setCompositeMode(composite);
   }

   @Override
   protected Coords handleDisplayPosition(Coords position) {
      if (closed_) {
         return position;
      }
      // Convert to NDViewer axes and move the viewer.
      // Guard against recursion: setAxisPosition triggers setImageEvent
      // which fires our setImageHook, calling setDisplayPosition again.
      updatingPosition_ = true;
      try {
         HashMap<String, Object> axes = axesBridge_.coordsToNDViewer(position);
         for (Map.Entry<String, Object> entry : axes.entrySet()) {
            if (!NDViewer2.CHANNEL_AXIS.equals(entry.getKey())
                  && entry.getValue() instanceof Number) {
               ndViewer2_.setAxisPosition(
                     entry.getKey(), ((Number) entry.getValue()).intValue());
            }
         }
      } finally {
         updatingPosition_ = false;
      }
      return position;
   }

   // ---- DataViewer interface ----

   @Override
   public DataProvider getDataProvider() {
      return dataProvider_;
   }

   @Override
   public List<Image> getDisplayedImages() throws IOException {
      List<Image> images = new ArrayList<>();
      Coords position = getDisplayPosition();
      if (position == null) {
         return images;
      }
      // Fetch one image per active channel
      List<String> channels = axesBridge_.getChannelNames();
      DisplaySettings ds = getDisplaySettings();
      for (int i = 0; i < channels.size(); i++) {
         boolean visible = (i < ds.getNumberOfChannels())
               ? ds.isChannelVisible(i) : true;
         if (!visible) {
            continue;
         }
         Coords chCoords = position.copyBuilder().channel(i).build();
         Image img = dataProvider_.getImage(chCoords);
         if (img != null) {
            images.add(img);
         }
      }
      // If no multi-channel data, try getting the image at exact position
      if (images.isEmpty()) {
         Image img = dataProvider_.getImage(position);
         if (img != null) {
            images.add(img);
         }
      }
      return images;
   }

   @Override
   public boolean isVisible() {
      // NDViewer window visibility
      try {
         return ndViewer2_.getCanvasJPanel() != null
               && ndViewer2_.getCanvasJPanel().isShowing();
      } catch (Exception e) {
         return false;
      }
   }

   @Override
   public boolean isClosed() {
      return closed_;
   }

   @Override
   public String getName() {
      return dataProvider_.getName();
   }

   @Override
   public void addListener(DataViewerListener listener, int priority) {
      synchronized (listeners_) {
         listeners_.put(priority, listener);
      }
   }

   @Override
   public void removeListener(DataViewerListener listener) {
      synchronized (listeners_) {
         listeners_.values().remove(listener);
      }
   }

   // ---- ImageStatsPublisher ----

   @Override
   public ImagesAndStats getCurrentImagesAndStats() {
      return currentImagesAndStats_;
   }

   // ---- StatsComputeQueue.Listener ----

   @Override
   public long imageStatsReady(ImagesAndStats result) {
      if (accumulateMode_ && result.isRealStats()) {
         // Merge each per-channel ImageStats into the accumulator, keyed by the
         // channel index of the corresponding image in the request.
         List<ImageStats> newStats = result.getResult();
         ImageStatsRequest req = result.getRequest();
         synchronized (accumulatedStatsPerChannel_) {
            for (int i = 0; i < newStats.size(); i++) {
               ImageStats tileStats = newStats.get(i);
               // Channel index comes from the image's own Coords.
               int channelIndex = (req.getNumberOfImages() > i)
                     ? req.getImage(i).getCoords().getChannel() : i;
               if (!accumulateNextChannels_.contains(channelIndex)) {
                  continue; // not a new-image result for this channel
               }
               ImageStats existing = accumulatedStatsPerChannel_.get(channelIndex);
               accumulatedStatsPerChannel_.put(channelIndex,
                     existing == null ? tileStats : mergeImageStats(existing, tileStats));
            }
         }
         accumulateNextChannels_.clear();

         // Build a replacement result using accumulated stats for every channel,
         // re-wrapping each image with the accumulated ImageStats.
         // This ensures scrollbar-triggered recomputes also show the full
         // accumulated histogram rather than just the current tile's stats.
         synchronized (accumulatedStatsPerChannel_) {
            if (!accumulatedStatsPerChannel_.isEmpty()) {
               List<ImageStats> accumulated = new ArrayList<>();
               List<ImageStats> rawStats = result.getResult();
               ImageStatsRequest rawReq = result.getRequest();
               for (int i = 0; i < rawStats.size(); i++) {
                  int channelIndex = (rawReq.getNumberOfImages() > i)
                        ? rawReq.getImage(i).getCoords().getChannel() : i;
                  ImageStats acc = accumulatedStatsPerChannel_.get(channelIndex);
                  accumulated.add(acc != null ? acc : rawStats.get(i));
               }
               result = ImagesAndStats.create(
                     result.getStatsSequenceNumber(),
                     result.getRequest(),
                     accumulated.toArray(new ImageStats[0]));
            }
         }
      }
      final ImagesAndStats finalResult = result;
      currentImagesAndStats_ = finalResult;
      // Post the event on the EDT
      SwingUtilities.invokeLater(() -> {
         postEvent(ImageStatsChangedEvent.create(finalResult));
      });
      return MIN_REPAINT_PERIOD_NS;
   }

   // ---- ContrastUpdateCallback ----

   /**
    * Called by ImageMaker when autostretch computes new contrast bounds.
    * Updates MM DisplaySettings with the new min/max for the given channel.
    */
   @Override
   public void onContrastUpdated(String channelName, int newMin, int newMax) {
      int idx = axesBridge_.getChannelIndex(channelName);
      // For single-channel data (NO_CHANNEL), use index 0
      if (idx < 0 && NDViewer2.NO_CHANNEL.equals(channelName)) {
         idx = 0;
      }
      if (idx < 0) {
         return;
      }
      DisplaySettings current = getDisplaySettings();
      if (idx >= current.getNumberOfChannels()) {
         return;
      }
      ChannelDisplaySettings ch = current.getChannelSettings(idx);
      ComponentDisplaySettings comp = ch.getComponentSettings(0).copyBuilder()
            .scalingMinimum(newMin).scalingMaximum(newMax).build();
      DisplaySettings updated = current.copyBuilder()
            .channel(idx, ch.copyBuilder().component(0, comp).build()).build();
      compareAndSetDisplaySettings(current, updated);
   }

   // ---- NDViewer image hook ----

   /**
    * Called by NDViewer whenever a new image is set (scrollbar moved, etc.).
    */
   private void onNDViewerImageChanged(HashMap<String, Object> axes) {
      // Skip if we triggered this via handleDisplayPosition to avoid recursion
      if (updatingPosition_) {
         return;
      }
      viewerInitialized_ = true;
      // Convert axes to Coords and update display position
      Coords newPosition = axesBridge_.ndViewerToCoords(axes);
      setDisplayPosition(newPosition);

      // Skip redundant stats when accumulate mode has a pending new-image
      // request for the current channel (newImageArrived already submitted one)
      if (accumulateMode_) {
         int ch = newPosition != null ? newPosition.getChannel() : 0;
         if (accumulateNextChannels_.contains(ch)) {
            return;
         }
      }
      // Submit stats computation request asynchronously so disk I/O does not
      // block NDViewer's render thread
      final Coords statsPosition = newPosition;
      statsExecutor_.submit(() -> submitStatsRequest(statsPosition));
   }

   private void submitStatsRequest(Coords position) {
      try {
         List<Image> images = getDownsampledImages(position);
         if (!images.isEmpty()) {
            ImageStatsRequest request = ImageStatsRequest.create(
                  position, images, BoundsRectAndMask.unselected());
            computeQueue_.submitRequest(request);
         }
      } catch (IOException e) {
         // Non-critical — stats won't update for this frame
      }
   }

   /**
    * Fetch downsampled images for histogram computation at the given position.
    * Mirrors getDisplayedImages() but uses the coarsest pyramid level.
    */
   private List<Image> getDownsampledImages(Coords position) throws IOException {
      List<Image> images = new ArrayList<>();
      if (position == null) {
         return images;
      }
      List<String> channels = axesBridge_.getChannelNames();
      DisplaySettings ds = getDisplaySettings();
      for (int i = 0; i < channels.size(); i++) {
         boolean visible = (i < ds.getNumberOfChannels())
               ? ds.isChannelVisible(i) : true;
         if (!visible) {
            continue;
         }
         Coords chCoords = position.copyBuilder().channel(i).build();
         Image img = dataProvider_.getDownsampledImage(chCoords);
         if (img != null) {
            images.add(img);
         }
      }
      if (images.isEmpty()) {
         Image img = dataProvider_.getDownsampledImage(position);
         if (img != null) {
            images.add(img);
         }
      }
      return images;
   }

   // ---- New image notification ----

   /**
    * Notify this viewer that a new tile has arrived with images for all channels.
    * All images are submitted as a single stats request so the Inspector
    * receives one result with all channel histograms in one callback.
    * StatsComputeQueue uses a global sequence number, so separate per-channel
    * requests would cause older-sequence results to be silently discarded.
    *
    * <p>Images and axes lists must correspond 1-to-1. The axes map for each
    * image must contain a "channel" key whose value AxesBridge can map to an
    * integer channel index.</p>
    *
    * @param images list of images (one per channel)
    * @param axesList list of NDViewer axes maps (one per image, same order)
    */
   @Override
   public void newTileArrived(final List<Image> images,
                              final List<HashMap<String, Object>> axesList) {
      if (images == null || images.isEmpty()) {
         return;
      }
      // Resolve channel indices and re-wrap images with correct Coords
      // before queuing the EDT task — registerChannel is thread-safe.
      final List<Image> tagged = new ArrayList<>(images.size());
      for (int i = 0; i < images.size(); i++) {
         Image img = images.get(i);
         if (img == null) {
            continue;
         }
         int channelIndex = 0;
         if (i < axesList.size() && axesList.get(i) != null) {
            Object chValue = axesList.get(i).get(NDViewer2.CHANNEL_AXIS);
            if (chValue != null) {
               channelIndex = axesBridge_.registerChannel(chValue);
            }
         }
         Coords coords = Coordinates.builder().channel(channelIndex).build();
         tagged.add(img.copyWith(coords, img.getMetadata()));
         if (accumulateMode_) {
            accumulateNextChannels_.add(channelIndex);
         }
      }
      if (tagged.isEmpty()) {
         return;
      }
      viewerInitialized_ = true;
      // Delay submission until after pending EDT tasks (e.g. histogram panel
      // creation from DataProviderHasNewImageEvent) so panels exist first.
      SwingUtilities.invokeLater(() -> {
         // Use channel=0 as the nominal display position for the request;
         // the Inspector routes each ImageStats to its panel via the individual
         // image coords, not the request's nominal position.
         Coords nominalPosition = tagged.get(0).getCoords();
         setDisplayPosition(nominalPosition);
         ImageStatsRequest request = ImageStatsRequest.create(
               nominalPosition, tagged, BoundsRectAndMask.unselected());
         computeQueue_.submitRequest(request);
      });
   }

   /**
    * Notify this viewer that a new image has arrived at the given axes.
    * Fetches the image directly by axes (avoiding the Coords round-trip
    * that drops axes with value 0) and submits a stats computation request.
    *
    * <p>Prefer {@link #newTileArrived} when images for all channels of a tile
    * are available at the same time, to avoid sequence-number conflicts in the
    * stats queue.</p>
    *
    * @param axes the NDViewer axes of the new image
    */
   public void newImageArrived(HashMap<String, Object> axes) {
      try {
         final Image image = dataProvider_.getDownsampledImageByAxes(axes);
         if (image != null) {
            int channelIndex = 0;
            if (axes != null) {
               Object chValue = axes.get(NDViewer2.CHANNEL_AXIS);
               if (chValue != null) {
                  channelIndex = axesBridge_.registerChannel(chValue);
               }
            }
            List<Image> imgs = new ArrayList<>();
            List<HashMap<String, Object>> axesList = new ArrayList<>();
            imgs.add(image);
            axesList.add(axes);
            newTileArrived(imgs, axesList);
         }
      } catch (IOException e) {
         // Non-critical — stats won't update for this image
      }
   }

   /**
    * Notify this viewer that a new image has arrived.
    * Uses the provided Image directly and derives the correct MM channel index
    * from the NDViewer axes map via AxesBridge.
    *
    * @param image the image
    * @param axes  the NDViewer axes of the image (e.g. {channel: "DAPI", ...})
    */
   public void newImageArrived(Image image, HashMap<String, Object> axes) {
      if (image != null) {
         List<Image> imgs = new ArrayList<>();
         List<HashMap<String, Object>> axesList = new ArrayList<>();
         imgs.add(image);
         axesList.add(axes);
         newTileArrived(imgs, axesList);
      }
   }

   /**
    * Notify this viewer that a new image has arrived.
    *
    * @param image the image (channel index taken from its own Coords)
    */
   public void newImageArrived(Image image) {
      if (image != null) {
         newImageArrived(image, null);
      }
   }

   // ---- Histogram accumulation ----

   private static ImageStats mergeImageStats(ImageStats a, ImageStats b) {
      int nComponents = Math.min(
            a.getNumberOfComponents(), b.getNumberOfComponents());
      IntegerComponentStats[] merged = new IntegerComponentStats[nComponents];
      for (int c = 0; c < nComponents; c++) {
         merged[c] = IntegerComponentStats.merge(
               a.getComponentStats(c), b.getComponentStats(c));
      }
      return ImageStats.create(a.getIndex(), merged);
   }

   /**
    * Enable accumulation mode and reset accumulated stats.
    * While enabled, newImageArrived() stats are merged into a running
    * total, and all stats results (including scrollbar-triggered ones)
    * post the accumulated histogram to the Inspector.
    *
    * @param enabled true to enable accumulation, false to disable
    */
   @Override
   public void setAccumulateStats(boolean enabled) {
      accumulateMode_ = enabled;
      if (!enabled) {
         accumulateNextChannels_.clear();
         synchronized (accumulatedStatsPerChannel_) {
            accumulatedStatsPerChannel_.clear();
         }
      }
   }

   /**
    * Reset the accumulated histogram stats without changing the mode.
    */
   public void resetAccumulatedStats() {
      accumulateNextChannels_.clear();
      synchronized (accumulatedStatsPerChannel_) {
         accumulatedStatsPerChannel_.clear();
      }
   }

   // ---- Viewer control ----

   /**
    * Get the underlying NDViewer instance.
    *
    * @return the NDViewer
    */
   @Override
   public NDViewer2 getNDViewer() {
      return ndViewer2_;
   }

   /**
    * Set the window title.
    *
    * @param title the title to set
    */
   public void setWindowTitle(String title) {
      ndViewer2_.setWindowTitle(title);
   }

   // ---- OverlaySupport / NDViewer2DataViewerAPI overlay methods ----

   /**
    * Create the internal bridge overlayer plugin that renders MM overlays onto the NDViewer canvas.
    * This plugin also chains to any external overlayer plugin set via setOverlayerPlugin().
    */
   private NDViewer2OverlayerPlugin createBridgeOverlayerPlugin() {
      return new NDViewer2OverlayerPlugin() {
         @Override
         public void drawOverlay(org.micromanager.ndviewer2.overlay.Overlay defaultOverlay,
               Point2D.Double displayImageSize, double downsampleFactor,
               java.awt.Graphics g, HashMap<String, Object> axes,
               double magnification, Point2D.Double viewOffset) throws InterruptedException {
            // Chain to external plugin first (e.g. DeskewExploreDataSource tile grid).
            // The external plugin adds ROIs to defaultOverlay; setOverlay is called once below.
            NDViewer2OverlayerPlugin external = externalOverlayerPlugin_;
            List<Overlay> overlays = new ArrayList<>(mmOverlays_);

            if (external != null) {
               external.drawOverlay(defaultOverlay, displayImageSize, downsampleFactor,
                     g, axes, magnification, viewOffset);
            }

            // Check if any MM overlay is actually visible before doing any work
            boolean anyVisible = false;
            for (Overlay overlay : overlays) {
               if (overlay.isVisible()) {
                  anyVisible = true;
                  break;
               }
            }
            if (!anyVisible) {
               mmOverlayBuf_ = null;
               ndViewer2_.setOverlay(defaultOverlay);
               return;
            }

            // Render MM overlays into mmOverlayBuf_.
            // mmOverlayRoi_ reads that field on every canvas repaint.
            int w = Math.max(1, (int) displayImageSize.x);
            int h = Math.max(1, (int) displayImageSize.y);
            Rectangle screenRect = new Rectangle(0, 0, w, h);
            // viewport dimensions in full-res pixels: use magnification (continuous zoom ratio)
            // rather than downsampleFactor (discrete power-of-2), so the scale bar stays
            // accurate at all zoom levels including deep zoom where resIndex == 0.
            Rectangle2D.Float viewPort = new Rectangle2D.Float(
                  (float) viewOffset.x,
                  (float) viewOffset.y,
                  (float) (displayImageSize.x / magnification),
                  (float) (displayImageSize.y / magnification));
            DisplaySettings ds = getDisplaySettings();
            // Fetch a representative image so overlays can read pixel metadata
            // (e.g. ScaleBarOverlay reads pixelSizeUm from primaryImage,
            //  PatternOverlay requires non-null primaryImage).
            Image primaryImage = null;
            try {
               primaryImage = dataProvider_.getImageByAxes(axes);
               if (primaryImage == null) {
                  Coords pos = axesBridge_.ndViewerToCoords(axes);
                  if (pos != null) {
                     primaryImage = dataProvider_.getImage(pos);
                  }
               }
               // Last resort: grab any image from the dataset
               if (primaryImage == null) {
                  primaryImage = dataProvider_.getAnyImage();
               }
            } catch (Exception ex) {
               // Ignore — primaryImage stays null
            }

            // Many overlays (ScaleBarOverlay, PatternOverlay) require a non-null primaryImage
            // to read pixel metadata.  If no image is available yet (e.g. before the first
            // tile arrives in a new acquisition), skip MM overlays entirely.
            if (primaryImage == null) {
               mmOverlayBuf_ = null;
               ndViewer2_.setOverlay(defaultOverlay);
               return;
            }
            final Image finalPrimaryImage = primaryImage;

            // Paint all visible MM overlays into a fresh transparent BufferedImage
            BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = buf.createGraphics();
            boolean anyPainted = false;
            try {
               for (Overlay overlay : overlays) {
                  if (overlay.isVisible()) {
                     try {
                        overlay.paintOverlay(bg, screenRect, ds,
                              finalPrimaryImage != null
                                    ? Collections.singletonList(finalPrimaryImage)
                                    : Collections.emptyList(),
                              finalPrimaryImage, viewPort);
                        anyPainted = true;
                     } catch (Exception ex) {
                        studio_.logs().logError(ex,
                              "NDViewer2 bridge: error painting " + overlay.getTitle());
                     }
                  }
               }
            } finally {
               bg.dispose();
            }

            if (!anyPainted) {
               // All overlays failed to paint — fall back to default overlay only
               mmOverlayBuf_ = null;
               ndViewer2_.setOverlay(defaultOverlay);
               return;
            }

            // Atomic update — mmOverlayRoi_.drawOverlay reads this volatile field
            mmOverlayBuf_ = buf;

            // Single setOverlay call: defaultOverlay ROIs + mmOverlayRoi_ combined.
            org.micromanager.ndviewer2.overlay.Overlay combined =
                  new org.micromanager.ndviewer2.overlay.Overlay();
            for (int i = 0; i < defaultOverlay.size(); i++) {
               combined.add(defaultOverlay.get(i));
            }
            combined.add(mmOverlayRoi_);
            ndViewer2_.setOverlay(combined);
         }
      };
   }

   @Override
   public void addOverlay(Overlay overlay) {
      if (overlay == null) {
         return;
      }
      studio_.logs().logMessage("NDViewer2: addOverlay called: " + overlay.getTitle());
      mmOverlays_.add(overlay);
      // Listen for repaint requests from the overlay so the canvas redraws
      // immediately when settings or visibility change.
      OverlayListener listener = new OverlayListener() {
         @Override
         public void overlayTitleChanged(Overlay o) {
         }

         @Override
         public void overlayConfigurationChanged(Overlay o) {
            ndViewer2_.redrawOverlay();
         }

         @Override
         public void overlayVisibleChanged(Overlay o) {
            ndViewer2_.redrawOverlay();
         }
      };
      synchronized (mmOverlayListeners_) {
         mmOverlayListeners_.put(overlay, listener);
      }
      overlay.addOverlayListener(listener);
      // Fire event so the Inspector controller can add the config panel.
      // Use null for the DisplayWindow arg — the controller only uses getOverlay().
      postEvent(DisplayWindowDidAddOverlayEvent.create(null, overlay));
      // Only trigger redraw if the viewer has already rendered its first image;
      // otherwise makeOrGetImage returns null and sets currentImage_ = null → grey canvas.
      if (viewerInitialized_) {
         ndViewer2_.redrawOverlay();
      }
   }

   @Override
   public void removeOverlay(Overlay overlay) {
      if (overlay == null) {
         return;
      }
      mmOverlays_.remove(overlay);
      // Unregister the overlay listener we registered in addOverlay()
      OverlayListener listener;
      synchronized (mmOverlayListeners_) {
         listener = mmOverlayListeners_.remove(overlay);
      }
      if (listener != null) {
         overlay.removeOverlayListener(listener);
      }
      // Fire event so the Inspector controller removes the config panel.
      postEvent(DisplayWindowDidRemoveOverlayEvent.create(null, overlay));
      if (viewerInitialized_) {
         ndViewer2_.redrawOverlay();
      }
   }

   @Override
   public List<Overlay> getOverlays() {
      return Collections.unmodifiableList(new ArrayList<>(mmOverlays_));
   }

   /**
    * Set an external overlayer plugin (e.g. for tile grid display in Deskew Explore).
    * The plugin is chained inside the internal bridge plugin so MM overlays are still painted.
    *
    * @param plugin the external overlayer plugin, or null to clear
    */
   @Override
   public void setOverlayerPlugin(NDViewer2OverlayerPlugin plugin) {
      externalOverlayerPlugin_ = plugin;
   }

   /**
    * Close the viewer and release resources.
    */
   @Override
   public void close() {
      if (closed_) {
         return;
      }
      closed_ = true;
      shutdownMM2Resources();
      try {
         ndViewer2_.close();
      } catch (Exception e) {
         // NDViewer may already be closing/disposed — not critical
      }
   }

   /**
    * Shut down MM2-specific resources without touching NDViewer.
    * Use this when NDViewer itself initiated the close (e.g. user clicked X)
    * to avoid calling ndViewer_.close() a second time, which can queue
    * additional EDT runnables that NPE on partially-torn-down state.
    */
   @Override
   public void closeWithoutNDViewer() {
      if (closed_) {
         return;
      }
      closed_ = true;
      shutdownMM2Resources();
   }

   private void shutdownMM2Resources() {
      statsExecutor_.shutdownNow();
      try {
         computeQueue_.shutdown();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      // Post close event so the Inspector detaches and DataViewerCollection
      // removes us. Defer to end of EDT queue so any already-queued Inspector
      // events (e.g. newDisplaySettings iterating channelControllers_) finish
      // before the detach modifies that list.
      SwingUtilities.invokeLater(() -> {
         postEvent(DataViewerWillCloseEvent.create(this));
         dispose();
      });
   }
}
