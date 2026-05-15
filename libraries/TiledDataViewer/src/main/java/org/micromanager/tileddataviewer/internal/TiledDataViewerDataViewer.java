package org.micromanager.tileddataviewer.internal;

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
import org.micromanager.display.internal.event.DefaultDisplayDidShowImageEvent;
import org.micromanager.display.internal.event.DisplayWindowDidAddOverlayEvent;
import org.micromanager.display.internal.event.DisplayWindowDidRemoveOverlayEvent;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ComponentStats;
import org.micromanager.display.internal.imagestats.ImageStats;
import org.micromanager.display.internal.imagestats.ImageStatsRequest;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.display.internal.imagestats.StatsComputeQueue;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.display.overlay.OverlayListener;
import org.micromanager.display.overlay.OverlaySupport;
import org.micromanager.tileddataviewer.TiledDataViewerAcqInterface;
import org.micromanager.tileddataviewer.TiledDataViewerDataSource;
import org.micromanager.tileddataviewer.TiledDataViewerDataViewerAPI;
import org.micromanager.tileddataviewer.TiledDataViewerOverlayerPlugin;
import org.micromanager.tileddataviewer.internal.gui.ChannelRenderSettings;
import org.micromanager.tileddataviewer.internal.gui.ContrastUpdateCallback;
import org.micromanager.tileddataviewer.internal.gui.GlobalRenderSettings;
import org.micromanager.tileddataviewer.overlay.Roi;

/**
 * NDViewer2 data viewer: combines NDViewer's pyramidal canvas with MM's
 * Inspector panel system.
 *
 * <p>Extends AbstractDataViewer so the MM Inspector can discover and attach to
 * it. Implements ImageStatsPublisher so the Inspector histogram panel receives
 * histogram data. Implements StatsComputeQueue.Listener for background
 * histogram computation callbacks.</p>
 */
public final class TiledDataViewerDataViewer extends AbstractDataViewer
      implements TiledDataViewerDataViewerAPI, ImageStatsPublisher, StatsComputeQueue.Listener,
      ContrastUpdateCallback, OverlaySupport {

   private static final long MIN_REPAINT_PERIOD_NS = Math.round(1e9 / 60.0);

   private final Studio studio_;
   private final TiledDataViewer ndViewer2_;
   private final TiledDataViewerDataProvider dataProvider_;
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

   // Last autostretch-computed contrast bounds per NDViewer channel name.
   // Updated by onContrastUpdated with ImageMaker's actual rendered values.
   // int[]{min, max}
   private final java.util.concurrent.ConcurrentHashMap<String, int[]> lastStretchedContrast_ =
         new java.util.concurrent.ConcurrentHashMap<>();

   // Set to true while onContrastUpdated() is executing its CAS, so that
   // handleDisplaySettings() knows the incoming change is an internal ImageMaker
   // update and should be accepted (not overridden) even when autostretch is off.
   private volatile boolean inOnContrastUpdated_ = false;




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
   private volatile TiledDataViewerOverlayerPlugin externalOverlayerPlugin_ = null;
   // Last rendered BufferedImage of MM overlays — read by the persistent mmOverlayRoi_
   private volatile BufferedImage mmOverlayBuf_ = null;
   // Single persistent Roi that draws mmOverlayBuf_ onto the canvas each repaint
   private final Roi mmOverlayRoi_ =
         new Roi(0, 0, 1, 1) {
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
   public TiledDataViewerDataViewer(Studio studio,
                                    TiledDataViewerDataSource dataSource,
                                    TiledDataViewerAcqInterface acqInterface,
                                    TiledDataViewerDataProvider dataProvider,
                                    JSONObject summaryMetadata,
                                    double pixelSizeUm,
                                    boolean rgb) {
      super(studio.displays().displaySettingsBuilder().build());
      studio_ = studio;
      dataProvider_ = dataProvider;
      axesBridge_ = dataProvider.getAxesBridge();

      // Create NDViewer (the canvas/scrollbar viewer)
      ndViewer2_ = new TiledDataViewer(dataSource, acqInterface,
            summaryMetadata, pixelSizeUm, rgb);

      // Register the internal bridge overlayer plugin that chains MM overlays
      // into NDViewer's render pipeline.
      ndViewer2_.setOverlayerPlugin(createBridgeOverlayerPlugin());

      // Set up stats computation
      computeQueue_.addListener(this);

      // Hook into NDViewer to detect image changes
      ndViewer2_.addSetImageHook(axes -> onNDViewerImageChanged(axes));

      // After every render, post histogram stats derived from ImageMaker's own
      // pixel histogram so the Inspector indicators always match the display.
      ndViewer2_.setPostRenderCallback(this::postImageMakerStats);

      // Register with Studio so Inspector discovers us
      studio_.displays().addViewer(this);

      // Post active event whenever the NDViewer window gains OS focus,
      // so the Inspector "Frontmost Window" mode follows this viewer.
      ndViewer2_.getGUIManager().setWindowActivatedCallback(
            () -> postEvent(DataViewerDidBecomeActiveEvent.create(this)));

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
         DisplaySettings effectiveSettings = requestedSettings;
         if (!requestedSettings.isAutostretchEnabled() && !lastStretchedContrast_.isEmpty()) {
            // Autostretch is off. Any scaling change in requestedSettings may come
            // from a spurious handleAutoscale() call (e.g. when the ignore-percentile
            // spinner changes). We override with lastStretchedContrast_ — the values
            // ImageMaker actually rendered — unless the request differs from those
            // values, in which case it is a deliberate user handle-drag that we
            // accept and remember.
            effectiveSettings = enforceOrAcceptScaling(requestedSettings);
         }
         pushRenderSettings(effectiveSettings);
         ndViewer2_.update();
         return effectiveSettings;
      } catch (NullPointerException e) {
         studio_.logs().logDebugMessage("NDViewer2: NPE in handleDisplaySettings "
               + "(likely async close race): " + e.getMessage());
      }
      return requestedSettings;
   }

   /**
    * When autostretch is off, ensures the stored scaling stays at the last
    * ImageMaker-rendered values. If requestedSettings carries different scaling
    * for a channel, it is treated as a deliberate user handle-drag: the new
    * values are accepted and written into lastStretchedContrast_ so future
    * calls keep those values.
    *
    * <p>Returns the DisplaySettings that should actually be stored and rendered.
    */
   private DisplaySettings enforceOrAcceptScaling(DisplaySettings requested) {
      // If this change originated from our own onContrastUpdated() CAS, always accept.
      if (inOnContrastUpdated_) {
         return requested;
      }

      List<String> channelNames = axesBridge_.getChannelNames();
      if (channelNames.isEmpty()) {
         channelNames = new ArrayList<>();
         channelNames.add(TiledDataViewer.NO_CHANNEL);
      }
      DisplaySettings.Builder builder = requested.copyBuilder();
      boolean anyOverridden = false;
      for (int i = 0; i < channelNames.size(); i++) {
         if (i >= requested.getNumberOfChannels()) {
            continue;
         }
         String name = channelNames.get(i);
         int[] known = lastStretchedContrast_.get(name);
         if (known == null) {
            continue;
         }
         ChannelDisplaySettings reqCh = requested.getChannelSettings(i);
         ComponentDisplaySettings reqComp = reqCh.getComponentSettings(0);
         long reqMin = reqComp.getScalingMinimum();
         long reqMax = reqComp.getScalingMaximum();
         if (reqMin == known[0] && reqMax == known[1]) {
            continue; // Already the correct ImageMaker values, nothing to do
         }
         // Since postImageMakerStats() and submitStatsRequest() are suppressed when
         // autostretch is off, handleAutoscale() always recomputes from frozen stats
         // and produces values matching known[]. So any change that differs from known[]
         // must be a genuine handle drag. Allow it and remember the new range.
         boolean minChanged = (reqMin != known[0]);
         boolean maxChanged = (reqMax != known[1]);
         if (minChanged != maxChanged) {
            // Exactly one endpoint differs — genuine handle drag. Accept and remember.
            lastStretchedContrast_.put(name, new int[]{(int) reqMin, (int) reqMax});
         } else {
            // Both differ — spurious rewrite. Restore known-good values.
            ComponentDisplaySettings fixed = reqComp.copyBuilder()
                  .scalingMinimum(known[0])
                  .scalingMaximum(known[1])
                  .build();
            builder.channel(i, reqCh.copyBuilder().component(0, fixed).build());
            anyOverridden = true;
         }
      }
      return anyOverridden ? builder.build() : requested;
   }


   /**
    * Build render settings from MM DisplaySettings and push them into NDViewer's ImageMaker.
    */
   private void pushRenderSettings(DisplaySettings ds) {
      List<String> channelNames = axesBridge_.getChannelNames();
      if (channelNames.isEmpty()) {
         // Single channel — use NO_CHANNEL
         channelNames = new ArrayList<>();
         channelNames.add(TiledDataViewer.NO_CHANNEL);
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
            DisplaySettings.ColorMode mode = ds.getColorMode();
            boolean grayscale = mode == DisplaySettings.ColorMode.GRAYSCALE;
            color = grayscale ? Color.white : ch.getColor();
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
            if (!TiledDataViewer.CHANNEL_AXIS.equals(entry.getKey())
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
         // Post DisplayDidShowImageEvent so the Inspector "Plane Metadata" panel updates.
         List<Image> imgs = finalResult.getRequest().getImages();
         if (!imgs.isEmpty()) {
            postEvent(DefaultDisplayDidShowImageEvent.create(this, imgs, imgs.get(0)));
         }
      });
      return MIN_REPAINT_PERIOD_NS;
   }

   // ---- Export helpers ----

   @Override
   public List<String> getExportChannelNames() {
      List<String> names = axesBridge_.getChannelNames();
      if (names.isEmpty()) {
         return Collections.singletonList(null);
      }
      return names;
   }

   /**
    * Build display settings JSON from the current MM DisplaySettings.
    * This is the authoritative source for contrast values — it reflects
    * autostretch-computed values because onContrastUpdated() writes them back
    * to MM's DisplaySettings before this is called.
    */
   @Override
   public JSONObject buildExportDisplaySettingsJSON() {
      DisplaySettings ds = getDisplaySettings();
      List<String> channelNames = axesBridge_.getChannelNames();
      if (channelNames.isEmpty()) {
         channelNames = new ArrayList<>();
         channelNames.add(TiledDataViewer.NO_CHANNEL);
      }
      JSONObject result = new JSONObject();
      try {
         for (int i = 0; i < channelNames.size(); i++) {
            String name = channelNames.get(i);
            int min = 0;
            int max = 65535;
            double gamma = 1.0;
            int colorRgb = Color.white.getRGB();
            if (i < ds.getNumberOfChannels()) {
               ChannelDisplaySettings ch = ds.getChannelSettings(i);
               ComponentDisplaySettings comp = ch.getComponentSettings(0);
               min = (int) comp.getScalingMinimum();
               max = (int) comp.getScalingMaximum();
               gamma = comp.getScalingGamma();
               // If autostretch is on, the CAS in onContrastUpdated may have lost
               // the race against incoming frames. Use the directly-stored values.
               if (ds.isAutostretchEnabled()) {
                  int[] stretched = lastStretchedContrast_.get(name);
                  if (stretched != null) {
                     min = stretched[0];
                     max = stretched[1];
                  }
               }
               DisplaySettings.ColorMode mode = ds.getColorMode();
               boolean grayscale = mode == DisplaySettings.ColorMode.GRAYSCALE;
               colorRgb = (grayscale ? Color.white : ch.getColor()).getRGB();
            }
            JSONObject ch = new JSONObject();
            ch.put("Min", min);
            ch.put("Max", max);
            ch.put("Gamma", gamma);
            ch.put("Color", colorRgb);
            result.put(name, ch);
         }
      } catch (Exception e) {
         studio_.logs().logError(e, "TiledDataViewerDataViewer: failed to build export JSON");
      }
      return result;
   }

   // ---- ContrastUpdateCallback ----

   /**
    * Called by ImageMaker when autostretch computes new contrast bounds.
    *
    * <p>Updates MM DisplaySettings with the new min/max (retry loop so the
    * write always succeeds). Also posts an ImageStatsChangedEvent whose
    * ComponentStats are built from ImageMaker's own raw pixel histogram, so
    * the Inspector indicators read values in the same pixel-value space that
    * ImageMaker uses — not the downsampled/bin-indexed MM stats pipeline.</p>
    */
   @Override
   public void onContrastUpdated(String channelName, int newMin, int newMax) {
      lastStretchedContrast_.put(channelName, new int[]{newMin, newMax});

      int idx = axesBridge_.getChannelIndex(channelName);
      if (idx < 0 && TiledDataViewer.NO_CHANNEL.equals(channelName)) {
         idx = 0;
      }
      if (idx < 0) {
         return;
      }
      // Signal that the CAS we are about to do is an internal ImageMaker update
      // so handleDisplaySettings() does not override it.
      inOnContrastUpdated_ = true;
      try {
         DisplaySettings current;
         DisplaySettings updated;
         do {
            current = getDisplaySettings();
            if (idx >= current.getNumberOfChannels()) {
               return;
            }
            ChannelDisplaySettings ch = current.getChannelSettings(idx);
            ComponentDisplaySettings comp = ch.getComponentSettings(0).copyBuilder()
                  .scalingMinimum(newMin).scalingMaximum(newMax).build();
            updated = current.copyBuilder()
                  .channel(idx, ch.copyBuilder().component(0, comp).build()).build();
         } while (!compareAndSetDisplaySettings(current, updated));
      } finally {
         inOnContrastUpdated_ = false;
      }
   }

   /**
    * Builds ComponentStats from ImageMaker's raw pixel histograms and posts
    * an ImageStatsChangedEvent so the Inspector histogram panel reflects exactly
    * what ImageMaker rendered.
    *
    * <p>Uses the images from the previous successful stats result (or
    * getDisplayedImages()) for channel-routing in the request, since
    * dataProvider_.getImage() may return null for tiled/Explorer datasets.</p>
    */
   private void postImageMakerStats() {
      try {
         // When autostretch is off, the histogram doesn't change the displayed
         // contrast. Skip posting new stats to avoid feeding stale/updated histogram
         // data into handleAutoscale() (called by IntensityInspectorPanelController
         // whenever the ignore-percentile spinner changes, even with autostretch off).
         if (!getDisplaySettings().isAutostretchEnabled()) {
            return;
         }
         HashMap<String, int[]> rawHists = ndViewer2_.getHistograms();
         if (rawHists == null || rawHists.isEmpty()) {
            studio_.logs().logDebugMessage(
                  "TiledDataViewerDataViewer.postImageMakerStats: no histograms available");
            return;
         }

         // Obtain images for channel-routing. Prefer the previous request's images
         // (already resolved), fall back to getDisplayedImages().
         List<Image> routingImages = null;
         ImagesAndStats prev = currentImagesAndStats_;
         if (prev != null && prev.getRequest().getNumberOfImages() > 0) {
            routingImages = prev.getRequest().getImages();
         }
         if (routingImages == null || routingImages.isEmpty()) {
            routingImages = getDisplayedImages();
         }
         if (routingImages == null || routingImages.isEmpty()) {
            studio_.logs().logDebugMessage(
                  "TiledDataViewerDataViewer.postImageMakerStats: no routing images available");
            return;
         }

         // Build per-image stats from the raw histograms, matched by channel index.
         List<Image> validImages = new ArrayList<>();
         List<ImageStats> validStats = new ArrayList<>();
         for (int i = 0; i < routingImages.size(); i++) {
            Image img = routingImages.get(i);
            if (img == null) {
               continue;
            }
            int channelIndex = img.getCoords().getChannel();
            // Look up channel name from the index to find the right histogram.
            List<String> allNames = axesBridge_.getChannelNames();
            String channelName = (channelIndex < allNames.size())
                  ? allNames.get(channelIndex) : TiledDataViewer.NO_CHANNEL;
            if (allNames.isEmpty()) {
               channelName = TiledDataViewer.NO_CHANNEL;
            }
            int[] rawHist = rawHists.get(channelName);
            if (rawHist == null) {
               studio_.logs().logDebugMessage(
                     "TiledDataViewerDataViewer.postImageMakerStats: no histogram for channel "
                           + channelName);
               continue;
            }
            ComponentStats cs = buildComponentStatsFromRawHistogram(rawHist);
            validImages.add(img);
            validStats.add(ImageStats.create(i, cs));
         }

         if (validImages.isEmpty()) {
            studio_.logs().logDebugMessage(
                  "TiledDataViewerDataViewer.postImageMakerStats: no valid images after matching");
            return;
         }

         Coords nominalPos = validImages.get(0).getCoords();
         ImageStatsRequest req = ImageStatsRequest.create(
               nominalPos, validImages, BoundsRectAndMask.unselected());
         ImagesAndStats result = ImagesAndStats.create(
               -1L, req, validStats.toArray(new ImageStats[0]));
         currentImagesAndStats_ = result;
         final ImagesAndStats finalResult = result;
         SwingUtilities.invokeLater(
               () -> postEvent(ImageStatsChangedEvent.create(finalResult)));
      } catch (Exception e) {
         studio_.logs().logError(e, "TiledDataViewerDataViewer.postImageMakerStats failed");
      }
   }

   /**
    * Builds a ComponentStats from a raw pixel histogram (one entry per pixel value).
    * binWidthPowerOf2=0 means bin width=1, so getAutoscaleMinMaxForQuantile returns
    * actual pixel values matching ImageMaker's pixel-value space.
    */
   private static ComponentStats buildComponentStatsFromRawHistogram(int[] rawHist) {
      // fullHist has out-of-range bins at indices 0 and length-1 (ComponentStats convention).
      // Zero pixel values (unacquired/black tiles) go into fullHist[1] (rawHist[0]).
      // We zero that bin out so that getQuantile() ignores them when computing the
      // autoscale dark point — otherwise black tiles drive the dark point to 0.
      long[] fullHist = new long[rawHist.length + 2];
      long pixelCount = 0;
      long pixelCountExcludingZeros = 0;
      long minimum = -1;
      long minimumExcludingZeros = -1;
      long maximum = 0;
      for (int v = 0; v < rawHist.length; v++) {
         long count = rawHist[v];
         pixelCount += count;
         if (v == 0) {
            // Zero-valued pixels: count them in pixelCount but omit from fullHist
            // so quantile computation ignores them (same as "ignore zeros" mode).
            continue;
         }
         fullHist[v + 1] = count;
         if (count > 0) {
            maximum = v;
            if (minimum < 0) {
               minimum = v;
            }
            if (minimumExcludingZeros < 0) {
               minimumExcludingZeros = v;
            }
            pixelCountExcludingZeros += count;
         }
      }
      if (minimum < 0) {
         minimum = 0;
      }
      if (minimumExcludingZeros < 0) {
         minimumExcludingZeros = minimum;
      }
      // Use pixelCountExcludingZeros so quantile fractions are relative to
      // non-zero pixels only, matching ImageMaker's autoscale behaviour.
      long effectiveCount = pixelCountExcludingZeros > 0 ? pixelCountExcludingZeros : pixelCount;
      return ComponentStats.builder()
            .histogram(fullHist, 0)
            .pixelCount(effectiveCount)
            .pixelCountExcludingZeros(pixelCountExcludingZeros)
            .minimum(minimum)
            .minimumExcludingZeros(minimumExcludingZeros)
            .maximum(maximum)
            .build();
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
         // When autostretch is off, don't update stats — the Inspector histogram
         // shows fixed contrast and handleAutoscale() should not recompute from
         // a fresh histogram (which would change the displayed image when the
         // ignore-percentile spinner is adjusted).
         if (!getDisplaySettings().isAutostretchEnabled()) {
            return;
         }
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
            Object chValue = axesList.get(i).get(TiledDataViewer.CHANNEL_AXIS);
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
               Object chValue = axes.get(TiledDataViewer.CHANNEL_AXIS);
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
      ComponentStats[] merged = new ComponentStats[nComponents];
      for (int c = 0; c < nComponents; c++) {
         merged[c] = ComponentStats.merge(
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
   public TiledDataViewer getNDViewer() {
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
   private TiledDataViewerOverlayerPlugin createBridgeOverlayerPlugin() {
      return new TiledDataViewerOverlayerPlugin() {
         @Override
         public void drawOverlay(org.micromanager.tileddataviewer.overlay.Overlay defaultOverlay,
                                 Point2D.Double displayImageSize, double downsampleFactor,
                                 java.awt.Graphics g, HashMap<String, Object> axes,
                                 double magnification, Point2D.Double viewOffset)
                  throws InterruptedException {
            // Chain to external plugin first (e.g. DeskewExploreDataSource tile grid).
            // The external plugin adds ROIs to defaultOverlay; setOverlay is called once below.
            TiledDataViewerOverlayerPlugin external = externalOverlayerPlugin_;
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
            org.micromanager.tileddataviewer.overlay.Overlay combined =
                  new org.micromanager.tileddataviewer.overlay.Overlay();
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
   public void setOverlayerPlugin(TiledDataViewerOverlayerPlugin plugin) {
      externalOverlayerPlugin_ = plugin;
   }

   @Override
   public TiledDataViewerOverlayerPlugin getOverlayerPlugin() {
      return externalOverlayerPlugin_;
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
