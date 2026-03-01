package org.micromanager.display.internal.ndviewer2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.inspector.internal.panels.intensity.ImageStatsPublisher;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeVisibleEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImageStats;
import org.micromanager.display.internal.imagestats.ImageStatsRequest;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.display.internal.imagestats.IntegerComponentStats;
import org.micromanager.display.internal.imagestats.StatsComputeQueue;
import org.micromanager.ndviewer.api.NDViewerAcqInterface;
import org.micromanager.ndviewer.api.NDViewerDataSource;
import org.micromanager.ndviewer.main.NDViewer;

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
      implements ImageStatsPublisher, StatsComputeQueue.Listener {

   private static final long MIN_REPAINT_PERIOD_NS = Math.round(1e9 / 60.0);

   private final Studio studio_;
   private final NDViewer ndViewer_;
   private final NDViewer2DataProvider dataProvider_;
   private final AxesBridge axesBridge_;
   private final DisplaySettingsBridge displaySettingsBridge_;
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

   // Feedback loop guard: true while we are pushing MM settings to NDViewer,
   // so that the resulting NDViewer repaint does not trigger a reverse sync.
   private volatile boolean updatingFromInspector_ = false;

   // Feedback loop guard for position: true while we are pushing position
   // to NDViewer, so that the resulting setImageHook does not recurse.
   private volatile boolean updatingPosition_ = false;

   // Accumulated stats across all tiles (null until first image arrives).
   // Used by newImageArrived() path to build a global histogram.
   private volatile ImageStats accumulatedStats_;

   // When true, newImageArrived() stats are merged into accumulatedStats_
   // and ALL stats results (including scrollbar navigation) post the
   // accumulated histogram. Set to true for the entire explore session.
   private volatile boolean accumulateMode_ = false;

   // True when the current stats computation is for a newly arrived image
   // (should be merged into the accumulator). Reset after processing.
   private volatile boolean accumulateNext_ = false;

   // Closed state
   private volatile boolean closed_ = false;

   /**
    * Create an NDViewer2DataViewer.
    *
    * @param studio          the MM Studio instance
    * @param dataSource      NDViewer data source (typically NDTiffAndViewerAdapter)
    * @param acqInterface    acquisition control interface (may be null)
    * @param dataProvider    the MM DataProvider wrapping NDTiff storage
    * @param axesBridge      shared axes bridge
    * @param summaryMetadata NDTiff summary metadata JSON
    * @param pixelSizeUm     pixel size in micrometers
    * @param rgb             whether images are RGB
    */
   public NDViewer2DataViewer(Studio studio,
                               NDViewerDataSource dataSource,
                               NDViewerAcqInterface acqInterface,
                               NDViewer2DataProvider dataProvider,
                               AxesBridge axesBridge,
                               JSONObject summaryMetadata,
                               double pixelSizeUm,
                               boolean rgb) {
      super(DefaultDisplaySettings.builder().build());
      studio_ = studio;
      dataProvider_ = dataProvider;
      axesBridge_ = axesBridge;
      displaySettingsBridge_ = new DisplaySettingsBridge(axesBridge_);

      // Create NDViewer (the canvas/scrollbar viewer)
      ndViewer_ = new NDViewer(dataSource, acqInterface,
            summaryMetadata, pixelSizeUm, rgb);

      // Hide NDViewer's side controls (histogram/metadata panels).
      // We use reflection because NDViewer does not yet expose a public API
      // for this. This is coupled to NDViewer's internal GuiManager layout —
      // if field/method names change in a future NDViewer version, the
      // reflection will fail gracefully (side controls remain visible).
      // TODO: add a public API to NDViewer and remove this reflection.
      try {
         Object guiManager = ndViewer_.getGUIManager();
         java.lang.reflect.Field displayWindowField =
               guiManager.getClass().getDeclaredField("displayWindow_");
         displayWindowField.setAccessible(true);
         Object displayWindow = displayWindowField.get(guiManager);
         java.lang.reflect.Method collapse =
               displayWindow.getClass().getMethod(
                     "collapseOrExpandSideControls", boolean.class);
         collapse.invoke(displayWindow, false);
      } catch (Exception e) {
         // If reflection fails, side controls remain visible — not critical
         studio_.logs().logError(e,
               "Could not hide NDViewer side controls");
      }

      // Set up stats computation
      computeQueue_.addListener(this);

      // Hook into NDViewer to detect image changes
      ndViewer_.addSetImageHook(axes -> onNDViewerImageChanged(axes));

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
      updatingFromInspector_ = true;
      try {
         // Read current NDViewer state BEFORE applying
         DisplaySettings currentNDViewerSettings =
               displaySettingsBridge_.readFromNDViewer(
                     ndViewer_.getDisplaySettingsObject(), requestedSettings);

         // Apply MM settings to NDViewer contrast model
         displaySettingsBridge_.applyToNDViewer(
               requestedSettings, ndViewer_.getDisplaySettingsObject());

         if (!requestedSettings.equals(currentNDViewerSettings)) {
            ndViewer_.update();
         }
      } catch (NullPointerException e) {
         studio_.logs().logDebugMessage("NDViewer2: NPE in handleDisplaySettings "
               + "(likely async close race): " + e.getMessage());
      } finally {
         updatingFromInspector_ = false;
      }
      return requestedSettings;
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
            if (!NDViewer.CHANNEL_AXIS.equals(entry.getKey())
                  && entry.getValue() instanceof Number) {
               ndViewer_.setAxisPosition(
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
         return ndViewer_.getCanvasJPanel() != null
               && ndViewer_.getCanvasJPanel().isShowing();
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
      if (accumulateMode_) {
         // Merge new-image stats into the accumulator
         if (accumulateNext_ && result.isRealStats()) {
            accumulateNext_ = false;
            List<ImageStats> newStats = result.getResult();
            if (!newStats.isEmpty()) {
               ImageStats tileStats = newStats.get(0);
               if (accumulatedStats_ == null) {
                  accumulatedStats_ = tileStats;
               } else {
                  accumulatedStats_ = mergeImageStats(
                        accumulatedStats_, tileStats);
               }
            }
         }
         // Always post accumulated stats while in accumulate mode,
         // so scrollbar-triggered recomputes don't reset the histogram.
         if (accumulatedStats_ != null) {
            result = ImagesAndStats.create(
                  result.getStatsSequenceNumber(),
                  result.getRequest(),
                  accumulatedStats_);
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

   // ---- NDViewer image hook ----

   /**
    * Called by NDViewer whenever a new image is set (scrollbar moved, etc.).
    */
   private void onNDViewerImageChanged(HashMap<String, Object> axes) {
      // Skip if we triggered this via handleDisplayPosition to avoid recursion
      if (updatingPosition_) {
         return;
      }
      // Convert axes to Coords and update display position
      Coords newPosition = axesBridge_.ndViewerToCoords(axes);
      setDisplayPosition(newPosition);

      // If not in the middle of applying Inspector settings,
      // sync NDViewer contrast back to MM DisplaySettings.
      // Capture the snapshot on the render thread; the actual sync runs async.
      if (!updatingFromInspector_) {
         final DisplaySettings capturedDS = getDisplaySettings();
         statsExecutor_.submit(() -> {
            DisplaySettings fromNDViewer = displaySettingsBridge_.readFromNDViewer(
                  ndViewer_.getDisplaySettingsObject(), capturedDS);
            if (!fromNDViewer.equals(capturedDS)) {
               compareAndSetDisplaySettings(capturedDS, fromNDViewer);
            }
         });
      }

      // Skip redundant stats when accumulate mode has a pending new-image
      // request (newImageArrived already submitted one for this tile)
      if (accumulateMode_ && accumulateNext_) {
         return;
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
    * Notify this viewer that a new image has arrived at the given axes.
    * Fetches the image directly by axes (avoiding the Coords round-trip
    * that drops axes with value 0) and submits a stats computation request.
    *
    * <p>Call this from external code (e.g. DeskewExploreManager) after
    * storing a new image, so the Inspector histogram updates.</p>
    *
    * @param axes the NDViewer axes of the new image
    */
   public void newImageArrived(HashMap<String, Object> axes) {
      try {
         final Image image = dataProvider_.getDownsampledImageByAxes(axes);
         if (image != null) {
            submitImageForStats(image);
         }
      } catch (IOException e) {
         // Non-critical — stats won't update for this image
      }
   }

   /**
    * Notify this viewer that a new image has arrived.
    * Uses the provided Image directly instead of reading from storage.
    *
    * @param image the image
    */
   public void newImageArrived(Image image) {
      if (image != null) {
         submitImageForStats(image);
      }
   }

   private void submitImageForStats(final Image image) {
      // Delay stats submission to run after any pending EDT tasks
      // (e.g. the histogram panel creation triggered by
      // DataProviderHasNewImageEvent). This ensures the histogram
      // panel exists before stats arrive.
      SwingUtilities.invokeLater(() -> {
         List<Image> images = new ArrayList<>();
         images.add(image);
         Coords position = image.getCoords();
         setDisplayPosition(position);
         accumulateNext_ = true;
         ImageStatsRequest request = ImageStatsRequest.create(
               position, images, BoundsRectAndMask.unselected());
         computeQueue_.submitRequest(request);
      });
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
   public void setAccumulateStats(boolean enabled) {
      accumulateMode_ = enabled;
      if (!enabled) {
         accumulatedStats_ = null;
      }
   }

   /**
    * Reset the accumulated histogram stats without changing the mode.
    */
   public void resetAccumulatedStats() {
      accumulatedStats_ = null;
   }

   // ---- Viewer control ----

   /**
    * Get the underlying NDViewer instance.
    *
    * @return the NDViewer
    */
   public NDViewer getNDViewer() {
      return ndViewer_;
   }

   /**
    * Set the window title.
    *
    * @param title the title to set
    */
   public void setWindowTitle(String title) {
      ndViewer_.setWindowTitle(title);
   }

   /**
    * Close the viewer and release resources.
    */
   public void close() {
      if (closed_) {
         return;
      }
      closed_ = true;
      shutdownMM2Resources();
      try {
         ndViewer_.close();
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
