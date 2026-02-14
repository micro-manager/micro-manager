package org.micromanager.display.ndviewer2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImageStatsRequest;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
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

   private final TreeMap<Integer, DataViewerListener> listeners_ =
         new TreeMap<>();

   // The most recent images and stats (for getCurrentImagesAndStats)
   private volatile ImagesAndStats currentImagesAndStats_;

   // Feedback loop guard: true while we are pushing MM settings to NDViewer,
   // so that the resulting NDViewer repaint does not trigger a reverse sync.
   private volatile boolean updatingFromInspector_ = false;

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
      // We use reflection to access the GuiManager's private displayWindow_
      // field, since there is no public API for this yet.
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

      // Initialize display position
      setDisplayPosition(Coordinates.builder().build());
   }

   // ---- AbstractDataViewer abstract methods ----

   @Override
   protected DisplaySettings handleDisplaySettings(
         DisplaySettings requestedSettings) {
      // Apply MM settings to NDViewer contrast model
      updatingFromInspector_ = true;
      try {
         displaySettingsBridge_.applyToNDViewer(
               requestedSettings, ndViewer_.getDisplaySettingsObject());
         ndViewer_.update();
      } finally {
         updatingFromInspector_ = false;
      }
      return requestedSettings;
   }

   @Override
   protected Coords handleDisplayPosition(Coords position) {
      // Convert to NDViewer axes and move the viewer
      HashMap<String, Object> axes = axesBridge_.coordsToNDViewer(position);
      for (Map.Entry<String, Object> entry : axes.entrySet()) {
         if (!NDViewer.CHANNEL_AXIS.equals(entry.getKey())
               && entry.getValue() instanceof Number) {
            ndViewer_.setAxisPosition(
                  entry.getKey(), ((Number) entry.getValue()).intValue());
         }
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
      currentImagesAndStats_ = result;
      // Post the event on the EDT
      SwingUtilities.invokeLater(() -> {
         postEvent(ImageStatsChangedEvent.create(result));
      });
      return MIN_REPAINT_PERIOD_NS;
   }

   // ---- NDViewer image hook ----

   /**
    * Called by NDViewer whenever a new image is set (scrollbar moved, etc.).
    */
   private void onNDViewerImageChanged(HashMap<String, Object> axes) {
      // Convert axes to Coords and update display position
      Coords newPosition = axesBridge_.ndViewerToCoords(axes);
      setDisplayPosition(newPosition);

      // If not in the middle of applying Inspector settings,
      // sync NDViewer contrast back to MM DisplaySettings
      if (!updatingFromInspector_) {
         DisplaySettings currentDS = getDisplaySettings();
         DisplaySettings fromNDViewer = displaySettingsBridge_.readFromNDViewer(
               ndViewer_.getDisplaySettingsObject(), currentDS);
         if (!fromNDViewer.equals(currentDS)) {
            compareAndSetDisplaySettings(currentDS, fromNDViewer);
         }
      }

      // Submit stats computation request
      submitStatsRequest(newPosition);
   }

   private void submitStatsRequest(Coords position) {
      try {
         List<Image> images = getDisplayedImages();
         if (!images.isEmpty()) {
            ImageStatsRequest request = ImageStatsRequest.create(
                  position, images, BoundsRectAndMask.unselected());
            computeQueue_.submitRequest(request);
         }
      } catch (IOException e) {
         // Non-critical — stats won't update for this frame
      }
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
      try {
         computeQueue_.shutdown();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      studio_.displays().removeViewer(this);
      ndViewer_.close();
      dispose();
   }
}
