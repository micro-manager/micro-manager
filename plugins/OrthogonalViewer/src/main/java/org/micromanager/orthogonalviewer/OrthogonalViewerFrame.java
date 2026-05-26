package org.micromanager.orthogonalviewer;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JViewport;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.Image;
import org.micromanager.display.AbstractDataViewer;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
// Internal classes - accepted pattern per TiledDataViewerDataViewer
import org.micromanager.display.inspector.internal.panels.intensity.ImageStatsPublisher;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.display.internal.event.DisplayWindowDidAddOverlayEvent;
import org.micromanager.display.internal.event.DisplayWindowDidRemoveOverlayEvent;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImageStatsRequest;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.display.internal.imagestats.StatsComputeQueue;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.display.overlay.OverlayListener;
import org.micromanager.display.overlay.OverlaySupport;

/**
 * Orthogonal viewer: shows XY, XZ and YZ slices of a Z-stack dataset.
 *
 * <p>Extends {@link AbstractDataViewer} (not JFrame - the JFrame is held as a field)
 * so that {@code postEvent()} and {@code dispose()} are accessible within this class.
 * Implements {@link ImageStatsPublisher} so Inspector histogram panels work.
 * Crosshair position is controlled by clicking any panel or using X/Y/Z sliders.
 * Mouse-wheel zoom scales all three panels together, centred on the crosshair.</p>
 */
public class OrthogonalViewerFrame extends AbstractDataViewer
      implements ImageStatsPublisher, StatsComputeQueue.Listener, OverlaySupport {

   private static final Color DARK_GREY = new Color(50, 50, 50);

   private static final double ZOOM_STEP = 1.25;
   private static final double ZOOM_MIN = 0.05;
   private static final double ZOOM_MAX = 32.0;

   private final Studio studio_;
   private final DataProvider dataProvider_;
   private final JFrame frame_;

   // Cached image dimensions, probed at construction
   private int imageWidth_ = 1;
   private int imageHeight_ = 1;
   private int numZSlices_ = 1;
   private boolean hasZ_ = false;

   // Base scale computed once at construction so the layout fits the screen
   private double pixelScale_ = 1.0;
   // Current zoom multiplier (1.0 = initial fit)
   private double zoomFactor_ = 1.0;

   // Physical calibration: z-step and pixel size in microns (from SummaryMetadata / image Metadata)
   // aspectRatioZtoXY = zStepUm / pixelSizeUm - how many XY pixels tall one Z-step is
   private double aspectRatioZtoXY_ = 1.0;

   // Crosshair state (EDT only)
   private int crosshairX_ = 0;
   private int crosshairY_ = 0;
   private int crosshairZ_ = 0;

   // Current non-Z display position axes
   private int currentChannel_ = 0;
   private int currentTime_ = 0;
   private int currentPosition_ = 0;

   private volatile boolean closed_ = false;

   // Image grid panel and scroll pane (needed for revalidate and scroll-on-zoom)
   private JPanel grid_;
   private JScrollPane scrollPane_;

   // Panels
   private final OrthogonalSlicePanel xyPanel_;
   private final OrthogonalSlicePanel xzPanel_;
   private final OrthogonalSlicePanel yzPanel_;

   // Labeled wrappers around each panel (MigLayout lays these out, not the panels directly)
   private JPanel xyWrapper_;
   private JPanel xzWrapper_;
   private JPanel yzWrapper_;

   // Number of channels (probed at construction, updated on new images)
   private int numChannels_ = 1;

   // Controls
   private JSlider xSlider_;
   private JSlider ySlider_;
   private JSlider zSlider_;
   private JSpinner xSpinner_;
   private JSpinner ySpinner_;
   private JSpinner zSpinner_;
   private JPanel zControlRow_;
   private JScrollBar cScrollBar_;
   private JLabel cPositionLabel_;
   private JPanel cControlRow_;

   private boolean updatingControls_ = false;

   // Only one SwingWorker refresh at a time
   private final AtomicBoolean refreshPending_ = new AtomicBoolean(false);

   // Inspector stats
   private final StatsComputeQueue computeQueue_ = StatsComputeQueue.create();
   private volatile ImagesAndStats currentImagesAndStats_;

   // DataViewerListener support
   private final TreeMap<Integer, DataViewerListener> listeners_ =
         new TreeMap<Integer, DataViewerListener>();

   // Overlay support
   private final java.util.concurrent.CopyOnWriteArrayList<Overlay> overlays_ =
         new java.util.concurrent.CopyOnWriteArrayList<Overlay>();
   private final java.util.HashMap<Overlay, OverlayListener> overlayListeners_ =
         new java.util.HashMap<Overlay, OverlayListener>();

   public OrthogonalViewerFrame(Studio studio, DisplayWindow sourceDisplay) {
      super(sourceDisplay.getDisplaySettings());
      studio_ = studio;
      dataProvider_ = sourceDisplay.getDataProvider();

      // Probe dimensions
      probeDimensions();

      // Compute base pixel scale so the whole layout fits the screen at 1:1
      pixelScale_ = computeScale();

      // Initialise crosshair
      crosshairX_ = imageWidth_ / 2;
      crosshairY_ = imageHeight_ / 2;
      Coords srcPos = sourceDisplay.getDisplayPosition();
      crosshairZ_ = (srcPos != null)
            ? Math.max(0, Math.min(srcPos.getZ(), numZSlices_ - 1)) : 0;

      // Extract initial non-Z axes from source position.
      // currentChannel_ starts at 0 regardless of source — the C scroll bar owns it.
      if (srcPos != null) {
         currentTime_ = srcPos.getTimePoint();
         currentPosition_ = srcPos.getStagePosition();
      }

      // Build panels (preferred sizes set after buildUI so wrappers exist)
      xyPanel_ = new OrthogonalSlicePanel();
      xzPanel_ = new OrthogonalSlicePanel();
      yzPanel_ = new OrthogonalSlicePanel();
      setUpPanelListeners();
      setUpMouseWheelZoom();

      // Build window — creates xyWrapper_/xzWrapper_/yzWrapper_ fields
      frame_ = new JFrame("Orthogonal Views - " + sourceDisplay.getName());
      frame_.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      buildUI();
      // Now wrappers exist — set preferred sizes on both panels and wrappers
      applyPanelSizes();

      frame_.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      frame_.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            close();
         }

         @Override
         public void windowActivated(WindowEvent e) {
            postEvent(DataViewerDidBecomeActiveEvent.create(OrthogonalViewerFrame.this));
         }
      });

      frame_.pack();
      frame_.setLocationRelativeTo(null);
      frame_.setVisible(true);

      // Register with Inspector AFTER becoming visible
      studio_.displays().addViewer(this);

      // Subscribe to incoming images and stats queue
      dataProvider_.registerForEvents(this);
      computeQueue_.addListener(this);

      // Initial render
      scheduleRefresh();
   }

   // ---- AbstractDataViewer abstract methods ----

   @Override
   protected DisplaySettings handleDisplaySettings(DisplaySettings requestedSettings) {
      if (closed_) {
         return requestedSettings;
      }
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            scheduleRefresh();
         }
      });
      return requestedSettings;
   }

   @Override
   protected Coords handleDisplayPosition(Coords position) {
      if (closed_) {
         return position;
      }
      if (position != null) {
         currentTime_ = position.getTimePoint();
         currentPosition_ = position.getStagePosition();
         // currentChannel_ is owned by the C scroll bar; do not override it here.
      }
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            scheduleRefresh();
         }
      });
      return position;
   }

   // ---- DataViewer interface ----

   @Override
   public DataProvider getDataProvider() {
      return dataProvider_;
   }

   @Override
   public List<Image> getDisplayedImages() throws IOException {
      DisplaySettings settings = getDisplaySettings();
      boolean composite = settings.getColorMode() == DisplaySettings.ColorMode.COMPOSITE;
      if (composite && numChannels_ > 1) {
         List<Image> result = new java.util.ArrayList<Image>();
         for (int c = 0; c < numChannels_; c++) {
            List<Image> stack = fetchZStack(c, currentTime_, currentPosition_);
            Image img = getZImage(stack, crosshairZ_);
            if (img != null) {
               result.add(img);
            }
         }
         return result;
      }
      return fetchXYImages(currentChannel_, currentTime_, currentPosition_, crosshairZ_);
   }

   @Override
   public boolean isVisible() {
      return frame_.isVisible();
   }

   @Override
   public boolean isClosed() {
      return closed_;
   }

   @Override
   public String getName() {
      return frame_.getTitle();
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

   // ---- OverlaySupport ----

   @Override
   public void addOverlay(Overlay overlay) {
      if (overlay == null) {
         return;
      }
      overlays_.add(overlay);
      OverlayListener listener = new OverlayListener() {
         @Override
         public void overlayTitleChanged(Overlay o) {
         }

         @Override
         public void overlayConfigurationChanged(Overlay o) {
            xyPanel_.repaint();
         }

         @Override
         public void overlayVisibleChanged(Overlay o) {
            xyPanel_.repaint();
         }
      };
      synchronized (overlayListeners_) {
         overlayListeners_.put(overlay, listener);
      }
      overlay.addOverlayListener(listener);
      postEvent(DisplayWindowDidAddOverlayEvent.create(null, overlay));
      xyPanel_.repaint();
   }

   @Override
   public void removeOverlay(Overlay overlay) {
      if (overlay == null) {
         return;
      }
      overlays_.remove(overlay);
      OverlayListener listener;
      synchronized (overlayListeners_) {
         listener = overlayListeners_.remove(overlay);
      }
      if (listener != null) {
         overlay.removeOverlayListener(listener);
      }
      postEvent(DisplayWindowDidRemoveOverlayEvent.create(null, overlay));
      xyPanel_.repaint();
   }

   @Override
   public java.util.List<Overlay> getOverlays() {
      return java.util.Collections.unmodifiableList(new java.util.ArrayList<Overlay>(overlays_));
   }

   // ---- ImageStatsPublisher ----

   @Override
   public ImagesAndStats getCurrentImagesAndStats() {
      return currentImagesAndStats_;
   }

   // ---- StatsComputeQueue.Listener ----

   @Override
   public long imageStatsReady(final ImagesAndStats result) {
      currentImagesAndStats_ = result;
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            postEvent(ImageStatsPublisher.ImageStatsChangedEvent.create(result));
         }
      });
      return 0L;
   }

   // ---- New image events ----

   @Subscribe
   public void onNewImage(DataProviderHasNewImageEvent event) {
      final int newZ = Math.max(1, dataProvider_.getNextIndex(Coords.Z_SLICE));
      final int newC = Math.max(1, dataProvider_.getNextIndex(Coords.CHANNEL));
      if (newZ != numZSlices_ || newC != numChannels_) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               numZSlices_ = newZ;
               hasZ_ = numZSlices_ > 1;
               numChannels_ = newC;
               updateSliderRanges();
               applyPanelSizes();
               if (grid_ != null) {
                  grid_.revalidate();
               }
               scheduleRefresh();
            }
         });
      }
   }

   // ---- Crosshair ----

   private void setCrosshairAndRefresh(int x, int y, int z) {
      crosshairX_ = Math.max(0, Math.min(x, imageWidth_ - 1));
      crosshairY_ = Math.max(0, Math.min(y, imageHeight_ - 1));
      crosshairZ_ = Math.max(0, Math.min(z, numZSlices_ - 1));

      updatingControls_ = true;
      try {
         xSlider_.setValue(crosshairX_);
         ySlider_.setValue(crosshairY_);
         if (hasZ_) {
            zSlider_.setValue(crosshairZ_);
         }
         ((SpinnerNumberModel) xSpinner_.getModel()).setValue(crosshairX_);
         ((SpinnerNumberModel) ySpinner_.getModel()).setValue(crosshairY_);
         if (hasZ_) {
            ((SpinnerNumberModel) zSpinner_.getModel()).setValue(crosshairZ_);
         }
      } finally {
         updatingControls_ = false;
      }

      scheduleRefresh();
   }

   // ---- Zoom ----

   /**
    * Compute a single base scale factor so the XY panel fits within ~55% of screen height/width,
    * capped to 1.0 (never zoom in beyond actual pixels).
    *
    * <p>The scale is intentionally based only on the XY image dimensions, NOT on the physical
    * Z extent. This ensures that aspectRatioZtoXY_ (derived from pixel size and z-step) actually
    * changes the visible XZ/YZ panel sizes rather than being silently cancelled by a smaller
    * base scale.</p>
    */
   private double computeScale() {
      Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
      // Fit the XY panel into ~55% of the screen, leaving room for XZ/YZ panels alongside it
      double sx = screen.width * 0.55 / imageWidth_;
      double sy = screen.height * 0.55 / imageHeight_;
      return Math.min(Math.min(sx, sy), 1.0);
   }

   /**
    * Set preferred sizes of the three view panels based on pixelScale_ * zoomFactor_.
    *
    * <p>XZ panel: imageWidth wide, numZSlices * aspectRatioZtoXY tall (in XY pixels).
    * YZ panel: numZSlices * aspectRatioZtoXY wide, imageHeight tall.</p>
    */
   private void applyPanelSizes() {
      double s = pixelScale_ * zoomFactor_;
      int nz = Math.max(1, numZSlices_);
      int zPx = Math.max(4, (int) Math.round(nz * aspectRatioZtoXY_ * s));
      int xyW = Math.max(16, (int) Math.round(imageWidth_ * s));
      int xyH = Math.max(16, (int) Math.round(imageHeight_ * s));
      int lblH = 16;
      int gap = 2;

      // With a null-layout grid we set absolute bounds directly — no layout manager interference.
      if (xyWrapper_ != null) {
         xyWrapper_.setBounds(gap, gap, xyW, xyH + lblH);
      }
      if (yzWrapper_ != null) {
         yzWrapper_.setBounds(gap + xyW + gap, gap, zPx, xyH + lblH);
      }
      if (xzWrapper_ != null) {
         xzWrapper_.setBounds(gap, gap + xyH + lblH + gap, xyW, zPx + lblH);
      }

      // The grid panel's preferred size drives the scroll pane content size.
      int totalW = gap + xyW + gap + zPx + gap;
      int totalH = gap + xyH + lblH + gap + zPx + lblH + gap;
      if (grid_ != null) {
         grid_.setPreferredSize(new Dimension(totalW, totalH));
      }
   }

   /**
    * Apply a new zoom factor and scroll the viewport to keep the crosshair centred.
    *
    * <p>After resizing panels we revalidate the grid so Swing updates the preferred sizes,
    * then adjust the scroll pane viewport so the crosshair pixel stays in the middle
    * of the visible area.</p>
    */
   private void applyZoom(double newFactor) {
      zoomFactor_ = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, newFactor));
      applyPanelSizes();
      if (grid_ == null || scrollPane_ == null) {
         return;
      }
      grid_.revalidate();
      grid_.repaint();

      // Scroll so that the crosshair stays centred in the viewport.
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            double s = pixelScale_ * zoomFactor_;
            int lblH = 16;
            int gap = 2;
            // XY panel content starts at (gap, gap + lblH) inside the grid
            int crossPx = gap + (int) Math.round(crosshairX_ * s);
            int crossPy = gap + lblH + (int) Math.round(crosshairY_ * s);

            JViewport vp = scrollPane_.getViewport();
            int vpW = vp.getWidth();
            int vpH = vp.getHeight();

            // Desired top-left of viewport so crosshair is centred
            int newVx = crossPx - vpW / 2;
            int newVy = crossPy - vpH / 2;

            // Clamp to valid range
            Dimension viewSize = vp.getViewSize();
            newVx = Math.max(0, Math.min(newVx, Math.max(0, viewSize.width - vpW)));
            newVy = Math.max(0, Math.min(newVy, Math.max(0, viewSize.height - vpH)));

            vp.setViewPosition(new Point(newVx, newVy));
         }
      });
   }

   private void setUpMouseWheelZoom() {
      MouseWheelListener wheelListener = new MouseWheelListener() {
         @Override
         public void mouseWheelMoved(MouseWheelEvent e) {
            e.consume();
            if (e.getWheelRotation() < 0) {
               applyZoom(zoomFactor_ * ZOOM_STEP);
            } else {
               applyZoom(zoomFactor_ / ZOOM_STEP);
            }
         }
      };
      xyPanel_.addMouseWheelListener(wheelListener);
      xzPanel_.addMouseWheelListener(wheelListener);
      yzPanel_.addMouseWheelListener(wheelListener);
   }

   // ---- Refresh logic ----

   private void scheduleRefresh() {
      if (closed_) {
         return;
      }
      if (!refreshPending_.compareAndSet(false, true)) {
         return;
      }

      final DisplaySettings settings = getDisplaySettings();
      final int cx = crosshairX_;
      final int cy = crosshairY_;
      final int cz = crosshairZ_;
      final int ch = (cScrollBar_ != null) ? cScrollBar_.getValue() : currentChannel_;
      final int t = currentTime_;
      final int p = currentPosition_;
      final int w = imageWidth_;
      final int h = imageHeight_;
      final int numZ = numZSlices_;
      final boolean hasZ = hasZ_;
      final int numCh = numChannels_;

      SwingWorker<RenderResult, Void> worker = new SwingWorker<RenderResult, Void>() {
         @Override
         protected RenderResult doInBackground() {
            return renderAllSlices(settings, cx, cy, cz, ch, t, p, w, h, numZ, hasZ, numCh);
         }

         @Override
         protected void done() {
            refreshPending_.set(false);
            if (closed_) {
               return;
            }
            RenderResult result;
            try {
               result = get();
            } catch (InterruptedException ex) {
               Thread.currentThread().interrupt();
               return;
            } catch (ExecutionException ex) {
               return;
            }
            if (result == null) {
               return;
            }

            xyPanel_.setImage(result.xy);
            xyPanel_.setCrosshairFractions(
                  (w > 1) ? (double) cx / (w - 1) : 0.5,
                  (h > 1) ? (double) cy / (h - 1) : 0.5);
            // Update overlay context so overlays paint with the correct image/settings.
            // Pass all channel images so composite-mode overlays (e.g. Text channel names)
            // see every channel, and use the first image as primaryImage.
            Image primaryXY = (result.xyImages != null && !result.xyImages.isEmpty())
                  ? result.xyImages.get(0) : null;
            xyPanel_.setOverlayContext(overlays_, result.xyImages, primaryXY, settings);

            if (hasZ && result.xz != null) {
               xzPanel_.setImage(result.xz);
               xzPanel_.setCrosshairFractions(
                     (w > 1) ? (double) cx / (w - 1) : 0.5,
                     (numZ > 1) ? (double) cz / (numZ - 1) : 0.5);

               yzPanel_.setImage(result.yz);
               yzPanel_.setCrosshairFractions(
                     (numZ > 1) ? (double) cz / (numZ - 1) : 0.5,
                     (h > 1) ? (double) cy / (h - 1) : 0.5);
            }

            if (result.xyImages != null && !result.xyImages.isEmpty()) {
               Coords nominalPos = result.xyImages.get(0).getCoords();
               ImageStatsRequest req = ImageStatsRequest.create(
                     nominalPos, result.xyImages, BoundsRectAndMask.unselected());
               computeQueue_.submitRequest(req);
            }
         }
      };
      worker.execute();
   }

   private static class RenderResult {
      BufferedImage xy;
      BufferedImage xz;
      BufferedImage yz;
      List<Image> xyImages;
   }

   private RenderResult renderAllSlices(DisplaySettings settings,
                                        int cx, int cy, int cz,
                                        int ch, int t, int p,
                                        int w, int h, int numZ, boolean hasZ, int numChannels) {
      RenderResult result = new RenderResult();

      DisplaySettings.ColorMode colorMode = settings.getColorMode();
      boolean grayscale = colorMode == DisplaySettings.ColorMode.GRAYSCALE;
      final boolean composite = colorMode == DisplaySettings.ColorMode.COMPOSITE;
      final boolean multiChannel = numChannels > 1;

      // Fetch z-stack for every channel
      java.util.List<List<Image>> allChannelStacks = new java.util.ArrayList<List<Image>>();
      for (int c = 0; c < numChannels; c++) {
         try {
            allChannelStacks.add(fetchZStack(c, t, p));
         } catch (IOException ex) {
            allChannelStacks.add(Collections.<Image>emptyList());
         }
      }

      // Collect XY images at crosshair-Z for all channels (for Inspector stats)
      result.xyImages = new java.util.ArrayList<Image>();
      for (int c = 0; c < numChannels; c++) {
         Image img = getZImage(allChannelStacks.get(c), cz);
         if (img != null) {
            result.xyImages.add(img);
         }
      }

      int zPhysH = Math.max(1, (int) Math.round(numZ * aspectRatioZtoXY_));

      if (!multiChannel) {
         // Single channel path
         List<Image> zStack = allChannelStacks.get(0);
         if (zStack.isEmpty()) {
            return result;
         }
         ChannelDisplaySettings cs = settings.getChannelSettings(ch);
         ComponentDisplaySettings comp = cs.getComponentSettings(0);
         long min = comp.getScalingMinimum();
         long max = comp.getScalingMaximum();
         double gamma = comp.getScalingGamma();
         Color color = grayscale ? Color.WHITE : cs.getColor();

         Image xyAtZ = getZImage(zStack, cz);
         if (xyAtZ != null) {
            int[] pixels = OrthogonalLutRenderer.toIntArray(xyAtZ.getRawPixels(), w * h);
            result.xy = OrthogonalLutRenderer.render(pixels, w, h, min, max, gamma, color);
         } else {
            result.xy = OrthogonalLutRenderer.render(new int[w * h], w, h, 0, 1, 1.0, Color.WHITE);
         }

         if (hasZ && numZ >= 2) {
            int[] xzPixels = OrthogonalSliceExtractor.extractXZ(zStack, cy, w);
            result.xz = scaleImage(
                  OrthogonalLutRenderer.render(xzPixels, w, numZ, min, max, gamma, color),
                  w, zPhysH);

            int[] yzPixels = OrthogonalSliceExtractor.extractYZ(zStack, cx, h);
            result.yz = scaleImage(
                  OrthogonalLutRenderer.render(yzPixels, numZ, h, min, max, gamma, color),
                  zPhysH, h);
         }
      } else if (!composite) {
         // Grayscale or Color mode with multi-channel: show only the selected channel
         List<Image> zStack = allChannelStacks.get(ch);
         ChannelDisplaySettings cs = settings.getChannelSettings(ch);
         ComponentDisplaySettings comp = cs.getComponentSettings(0);
         long min = comp.getScalingMinimum();
         long max = comp.getScalingMaximum();
         double gamma = comp.getScalingGamma();
         Color color = grayscale ? Color.WHITE : cs.getColor();

         Image xyAtZ = getZImage(zStack, cz);
         if (xyAtZ != null) {
            int[] pixels = OrthogonalLutRenderer.toIntArray(xyAtZ.getRawPixels(), w * h);
            result.xy = OrthogonalLutRenderer.render(pixels, w, h, min, max, gamma, color);
         } else {
            result.xy = OrthogonalLutRenderer.render(new int[w * h], w, h, 0, 1, 1.0, Color.WHITE);
         }

         if (hasZ && numZ >= 2) {
            int[] xzPixels = OrthogonalSliceExtractor.extractXZ(zStack, cy, w);
            result.xz = scaleImage(
                  OrthogonalLutRenderer.render(xzPixels, w, numZ, min, max, gamma, color),
                  w, zPhysH);
            int[] yzPixels = OrthogonalSliceExtractor.extractYZ(zStack, cx, h);
            result.yz = scaleImage(
                  OrthogonalLutRenderer.render(yzPixels, numZ, h, min, max, gamma, color),
                  zPhysH, h);
         }
      } else {
         // Multi-channel composite path
         java.util.List<int[]> xyChPixels = new java.util.ArrayList<int[]>();
         java.util.List<int[]> xzChPixels = new java.util.ArrayList<int[]>();
         java.util.List<int[]> yzChPixels = new java.util.ArrayList<int[]>();

         for (int c = 0; c < numChannels; c++) {
            List<Image> zStack = allChannelStacks.get(c);
            if (zStack.isEmpty()) {
               xyChPixels.add(null);
               xzChPixels.add(null);
               yzChPixels.add(null);
               continue;
            }
            Image xyAtZ = getZImage(zStack, cz);
            xyChPixels.add(xyAtZ != null
                  ? OrthogonalLutRenderer.toIntArray(xyAtZ.getRawPixels(), w * h)
                  : null);

            if (hasZ && numZ >= 2) {
               xzChPixels.add(OrthogonalSliceExtractor.extractXZ(zStack, cy, w));
               yzChPixels.add(OrthogonalSliceExtractor.extractYZ(zStack, cx, h));
            } else {
               xzChPixels.add(null);
               yzChPixels.add(null);
            }
         }

         result.xy = OrthogonalLutRenderer.renderComposite(xyChPixels, w, h, settings);

         if (hasZ && numZ >= 2) {
            result.xz = scaleImage(
                  OrthogonalLutRenderer.renderComposite(xzChPixels, w, numZ, settings),
                  w, zPhysH);
            result.yz = scaleImage(
                  OrthogonalLutRenderer.renderComposite(yzChPixels, numZ, h, settings),
                  zPhysH, h);
         }
      }

      return result;
   }


   private static BufferedImage scaleImage(BufferedImage src, int targetW, int targetH) {
      if (src.getWidth() == targetW && src.getHeight() == targetH) {
         return src;
      }
      BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
      java.awt.Graphics2D g2 = dst.createGraphics();
      g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g2.drawImage(src, 0, 0, targetW, targetH, null);
      g2.dispose();
      return dst;
   }

   // ---- Data access helpers ----

   private List<Image> fetchZStack(int channel, int time, int position) throws IOException {
      Coords.CoordsBuilder b = studio_.data().coordsBuilder();
      List<String> axes = dataProvider_.getAxes();
      // Always pin channel axis when the dataset has one, even for channel 0.
      // Ignoring channel=0 causes getImagesIgnoringAxes to return images from ALL channels.
      // We use getImagesIgnoringAxes with Z ignored but all other axes fixed.
      boolean hasChannel = axes.contains(Coords.CHANNEL);
      if (hasChannel && channel > 0) {
         b = b.channel(channel);
      }
      if (axes.contains(Coords.TIME_POINT) && time > 0) {
         b = b.time(time);
      }
      if (axes.contains(Coords.STAGE_POSITION) && position > 0) {
         b = b.stagePosition(position);
      }
      Coords fixedCoords = b.build();

      List<Image> raw = dataProvider_.getImagesIgnoringAxes(fixedCoords, Coords.Z_SLICE);
      // Wrap in a mutable ArrayList so we can sort regardless of what the storage returns.
      // Also guards against null return from some storage implementations.
      java.util.List<Image> images = (raw != null)
            ? new java.util.ArrayList<Image>(raw)
            : new java.util.ArrayList<Image>();

      // If the dataset has a channel axis, filter to only the requested channel
      // (necessary for channel 0 since we could not encode it in the Coords builder)
      if (hasChannel) {
         java.util.List<Image> filtered = new java.util.ArrayList<Image>();
         for (Image img : images) {
            if (img.getCoords().getChannel() == channel) {
               filtered.add(img);
            }
         }
         images = filtered;
      }

      Collections.sort(images, new Comparator<Image>() {
         @Override
         public int compare(Image a, Image b2) {
            return Integer.compare(a.getCoords().getZ(), b2.getCoords().getZ());
         }
      });
      return images;
   }

   private List<Image> fetchXYImages(int channel, int time, int position, int z)
         throws IOException {
      Coords.CoordsBuilder b = studio_.data().coordsBuilder().z(z);
      List<String> axes = dataProvider_.getAxes();
      if (axes.contains(Coords.CHANNEL) && channel > 0) {
         b = b.channel(channel);
      }
      if (axes.contains(Coords.TIME_POINT) && time > 0) {
         b = b.time(time);
      }
      if (axes.contains(Coords.STAGE_POSITION) && position > 0) {
         b = b.stagePosition(position);
      }
      Image img = dataProvider_.getImage(b.build());
      if (img != null && (channel == 0 || img.getCoords().getChannel() == channel)) {
         return Collections.singletonList(img);
      }
      // Fallback: scan the z-stack (handles channel-0 axis bug)
      List<Image> all = fetchZStack(channel, time, position);
      img = getZImage(all, z);
      if (img == null) {
         return Collections.<Image>emptyList();
      }
      return Collections.singletonList(img);
   }

   private static Image getZImage(List<Image> zStack, int z) {
      for (Image img : zStack) {
         if (img.getCoords().getZ() == z) {
            return img;
         }
      }
      return null;
   }

   // ---- Dimension probing ----

   private void probeDimensions() {
      numZSlices_ = dataProvider_.getNextIndex(Coords.Z_SLICE);
      if (numZSlices_ < 1) {
         numZSlices_ = 1;
      }
      hasZ_ = numZSlices_ > 1;
      numChannels_ = dataProvider_.getNextIndex(Coords.CHANNEL);
      if (numChannels_ < 1) {
         numChannels_ = 1;
      }

      double zStepUm = 1.0;
      double pixelSizeUm = 1.0;

      try {
         Image anyImage = dataProvider_.getAnyImage();
         if (anyImage != null) {
            imageWidth_ = anyImage.getWidth();
            imageHeight_ = anyImage.getHeight();
            // Per-image pixel size
            Double ps = anyImage.getMetadata().getPixelSizeUm();
            if (ps != null && ps > 0.0) {
               pixelSizeUm = ps;
            }
         }
      } catch (IOException ex) {
         // leave defaults
      }

      // Z-step from summary metadata (getSummaryMetadata does not throw checked exceptions)
      org.micromanager.data.SummaryMetadata sm = dataProvider_.getSummaryMetadata();
      if (sm != null) {
         Double zs = sm.getZStepUm();
         if (zs != null && zs > 0.0) {
            zStepUm = zs;
         }
      }

      // Physical aspect ratio: how many XY pixels tall is one Z-step?
      // aspect > 1: z-step is larger than one pixel (XZ/YZ panels are taller than wide per slice)
      // aspect < 1: z-step is smaller than one pixel (XZ/YZ panels are shallower)
      aspectRatioZtoXY_ = zStepUm / pixelSizeUm;
      if (aspectRatioZtoXY_ <= 0.0) {
         aspectRatioZtoXY_ = 1.0;
      }
   }

   // ---- UI construction ----

   private void buildUI() {
      JPanel contentPanel = new JPanel(new MigLayout("fill, insets 0, gap 0"));
      contentPanel.setBackground(DARK_GREY);

      // Toolbar at the top (like the standard MM viewer)
      JPanel toolbar = buildToolbar();
      contentPanel.add(toolbar, "growx, wrap");

      // Image grid: null layout with explicit bounds set by applyPanelSizes()
      grid_ = new JPanel(null);
      grid_.setBackground(DARK_GREY);

      xyWrapper_ = makeLabeledPanel("XY", xyPanel_);
      yzWrapper_ = makeLabeledPanel("YZ", yzPanel_);
      xzWrapper_ = makeLabeledPanel("XZ", xzPanel_);
      grid_.add(xyWrapper_);
      grid_.add(yzWrapper_);
      grid_.add(xzWrapper_);

      // Wrap grid in a scroll pane so zooming in shows scrollbars
      scrollPane_ = new JScrollPane(grid_);
      scrollPane_.setBackground(DARK_GREY);
      scrollPane_.getViewport().setBackground(DARK_GREY);
      scrollPane_.setBorder(null);

      contentPanel.add(scrollPane_, "grow, push, wrap");

      JPanel controls = buildControlsPanel();
      contentPanel.add(controls, "growx");

      frame_.setContentPane(contentPanel);
   }

   private JPanel buildToolbar() {
      JPanel toolbar = new JPanel(new MigLayout("insets 2 4 2 4, gap 1 1"));
      toolbar.setBackground(DARK_GREY);

      JButton zoomInBtn = new JButton(
            IconLoader.getIcon("/org/micromanager/icons/zoom_in.png"));
      zoomInBtn.setToolTipText("Zoom in (mouse wheel also works)");
      zoomInBtn.setBackground(Color.WHITE);
      zoomInBtn.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            applyZoom(zoomFactor_ * ZOOM_STEP);
         }
      });

      JButton zoomOutBtn = new JButton(
            IconLoader.getIcon("/org/micromanager/icons/zoom_out.png"));
      zoomOutBtn.setToolTipText("Zoom out (mouse wheel also works)");
      zoomOutBtn.setBackground(Color.WHITE);
      zoomOutBtn.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            applyZoom(zoomFactor_ / ZOOM_STEP);
         }
      });

      toolbar.add(zoomInBtn);
      toolbar.add(zoomOutBtn);
      return toolbar;
   }

   private JPanel makeLabeledPanel(String label, OrthogonalSlicePanel panel) {
      JPanel wrapper = new JPanel(new BorderLayout(0, 2));
      wrapper.setBackground(DARK_GREY);
      JLabel lbl = new JLabel(label, JLabel.CENTER);
      lbl.setForeground(Color.LIGHT_GRAY);
      wrapper.add(lbl, BorderLayout.NORTH);
      wrapper.add(panel, BorderLayout.CENTER);
      return wrapper;
   }

   private JPanel buildControlsPanel() {
      JPanel panel = new JPanel(new MigLayout("fillx, insets 2 4 2 4, gap 2 2", "[][][grow]"));
      panel.setBackground(DARK_GREY);
      panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

      // X row
      xSpinner_ = new JSpinner(new SpinnerNumberModel(crosshairX_, 0, imageWidth_ - 1, 1));
      xSlider_ = new JSlider(0, imageWidth_ - 1, crosshairX_);
      styleSlider(xSlider_);
      panel.add(makeLabel("X:"));
      panel.add(xSpinner_);
      panel.add(xSlider_, "growx, wrap");

      // Y row
      ySpinner_ = new JSpinner(new SpinnerNumberModel(crosshairY_, 0, imageHeight_ - 1, 1));
      ySlider_ = new JSlider(0, imageHeight_ - 1, crosshairY_);
      styleSlider(ySlider_);
      panel.add(makeLabel("Y:"));
      panel.add(ySpinner_);
      panel.add(ySlider_, "growx, wrap");

      // Z row
      int zMax = Math.max(0, numZSlices_ - 1);
      zSpinner_ = new JSpinner(new SpinnerNumberModel(crosshairZ_, 0, zMax, 1));
      zSlider_ = new JSlider(0, zMax, crosshairZ_);
      styleSlider(zSlider_);

      zControlRow_ = new JPanel(new MigLayout("fillx, insets 0", "[][][grow]"));
      zControlRow_.setBackground(DARK_GREY);
      zControlRow_.add(makeLabel("Z:"));
      zControlRow_.add(zSpinner_);
      zControlRow_.add(zSlider_, "growx");
      panel.add(zControlRow_, "span 3, growx, wrap");
      zControlRow_.setVisible(hasZ_);

      // C row — styled like the standard MM viewer scroll bars
      int cMax = Math.max(1, numChannels_);
      int initCh = Math.max(0, Math.min(currentChannel_, cMax - 1));
      cScrollBar_ = new JScrollBar(JScrollBar.HORIZONTAL, initCh, 1, 0, cMax);
      cPositionLabel_ = new JLabel(channelPositionText(initCh, numChannels_));
      cPositionLabel_.setForeground(Color.LIGHT_GRAY);
      cPositionLabel_.setFont(cPositionLabel_.getFont().deriveFont(10.0f));

      cControlRow_ = new JPanel(new MigLayout("fillx, insets 0, gap 2 0", "[][grow][]"));
      cControlRow_.setBackground(DARK_GREY);
      cControlRow_.add(makeLabel("C:"));
      cControlRow_.add(cScrollBar_, "growx");
      cControlRow_.add(cPositionLabel_);
      panel.add(cControlRow_, "span 3, growx, wrap");
      cControlRow_.setVisible(numChannels_ > 1);

      wireListeners();
      return panel;
   }

   private static String channelPositionText(int ch, int total) {
      int digits = Integer.toString(total).length();
      return String.format("%" + digits + "d/%" + digits + "d", ch + 1, total);
   }

   private void wireListeners() {
      xSlider_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            if (!updatingControls_) {
               updatingControls_ = true;
               ((SpinnerNumberModel) xSpinner_.getModel()).setValue(xSlider_.getValue());
               updatingControls_ = false;
               setCrosshairAndRefresh(xSlider_.getValue(), crosshairY_, crosshairZ_);
            }
         }
      });
      ySlider_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            if (!updatingControls_) {
               updatingControls_ = true;
               ((SpinnerNumberModel) ySpinner_.getModel()).setValue(ySlider_.getValue());
               updatingControls_ = false;
               setCrosshairAndRefresh(crosshairX_, ySlider_.getValue(), crosshairZ_);
            }
         }
      });
      zSlider_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            if (!updatingControls_) {
               updatingControls_ = true;
               ((SpinnerNumberModel) zSpinner_.getModel()).setValue(zSlider_.getValue());
               updatingControls_ = false;
               setCrosshairAndRefresh(crosshairX_, crosshairY_, zSlider_.getValue());
            }
         }
      });
      xSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            if (!updatingControls_) {
               int val = ((Number) xSpinner_.getValue()).intValue();
               updatingControls_ = true;
               xSlider_.setValue(val);
               updatingControls_ = false;
               setCrosshairAndRefresh(val, crosshairY_, crosshairZ_);
            }
         }
      });
      ySpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            if (!updatingControls_) {
               int val = ((Number) ySpinner_.getValue()).intValue();
               updatingControls_ = true;
               ySlider_.setValue(val);
               updatingControls_ = false;
               setCrosshairAndRefresh(crosshairX_, val, crosshairZ_);
            }
         }
      });
      zSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            if (!updatingControls_) {
               int val = ((Number) zSpinner_.getValue()).intValue();
               updatingControls_ = true;
               zSlider_.setValue(val);
               updatingControls_ = false;
               setCrosshairAndRefresh(crosshairX_, crosshairY_, val);
            }
         }
      });
      cScrollBar_.addAdjustmentListener(new AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(AdjustmentEvent e) {
            if (!updatingControls_) {
               int ch = cScrollBar_.getValue();
               currentChannel_ = ch;
               cPositionLabel_.setText(channelPositionText(ch, numChannels_));
               scheduleRefresh();
            }
         }
      });
   }

   private void styleSlider(JSlider slider) {
      slider.setBackground(DARK_GREY);
      slider.setForeground(Color.LIGHT_GRAY);
   }

   private JLabel makeLabel(String text) {
      JLabel lbl = new JLabel(text);
      lbl.setForeground(Color.LIGHT_GRAY);
      return lbl;
   }

   private void updateSliderRanges() {
      if (zSlider_ == null) {
         return;
      }
      int zMax = Math.max(0, numZSlices_ - 1);
      zSlider_.setMaximum(zMax);
      ((SpinnerNumberModel) zSpinner_.getModel()).setMaximum(zMax);
      crosshairZ_ = Math.min(crosshairZ_, zMax);
      zControlRow_.setVisible(hasZ_);

      if (cScrollBar_ == null) {
         return;
      }
      int cMax = Math.max(1, numChannels_);
      cScrollBar_.setMaximum(cMax);
      currentChannel_ = Math.min(currentChannel_, cMax - 1);
      cPositionLabel_.setText(channelPositionText(currentChannel_, numChannels_));
      cControlRow_.setVisible(numChannels_ > 1);
   }

   // ---- Panel click listeners ----

   private void setUpPanelListeners() {
      xyPanel_.setClickListener(new OrthogonalSlicePanel.ClickListener() {
         @Override
         public void onClick(double fracA, double fracB) {
            int x = (int) Math.round(fracA * (imageWidth_ - 1));
            int y = (int) Math.round(fracB * (imageHeight_ - 1));
            setCrosshairAndRefresh(x, y, crosshairZ_);
         }
      });

      xzPanel_.setClickListener(new OrthogonalSlicePanel.ClickListener() {
         @Override
         public void onClick(double fracA, double fracB) {
            int x = (int) Math.round(fracA * (imageWidth_ - 1));
            int z = (int) Math.round(fracB * Math.max(0, numZSlices_ - 1));
            setCrosshairAndRefresh(x, crosshairY_, z);
         }
      });

      yzPanel_.setClickListener(new OrthogonalSlicePanel.ClickListener() {
         @Override
         public void onClick(double fracA, double fracB) {
            int z = (int) Math.round(fracA * Math.max(0, numZSlices_ - 1));
            int y = (int) Math.round(fracB * (imageHeight_ - 1));
            setCrosshairAndRefresh(crosshairX_, y, z);
         }
      });
   }

   // ---- Close / lifecycle ----

   private void close() {
      if (closed_) {
         return;
      }
      closed_ = true;
      dataProvider_.unregisterForEvents(this);
      try {
         computeQueue_.shutdown();
      } catch (InterruptedException ignore) {
         Thread.currentThread().interrupt();
      }
      frame_.dispose();
      // Post WillClose before dispose() so asyncEventPoster_ is still alive,
      // deferred to end of EDT queue so any pending Inspector events finish first.
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            postEvent(DataViewerWillCloseEvent.create(OrthogonalViewerFrame.this));
            dispose();
         }
      });
   }
}
