package org.micromanager.orthogonalviewer;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import ij.ImagePlus;
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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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
import org.micromanager.display.internal.imagestats.ComponentStats;
import org.micromanager.display.internal.imagestats.ImageStats;
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
      implements DisplayWindow, ImageStatsPublisher, StatsComputeQueue.Listener, OverlaySupport {

   private static final Color DARK_GREY = new Color(50, 50, 50);

   private static final double ZOOM_STEP = 1.25;
   private static final double ZOOM_MIN = 0.05;
   private static final double ZOOM_MAX = 32.0;
   private static final int PANEL_GAP = 6;
   private static final int LABEL_H = 16;

   private final Studio studio_;
   private final DataProvider dataProvider_;
   private final JFrame frame_;

   // Cached image dimensions, probed at construction
   private int imageWidth_ = 1;
   private int imageHeight_ = 1;
   private int numZSlices_ = 1;
   private boolean hasZ_ = false;
   private int numTimePoints_ = 1;
   private int numPositions_ = 1;

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

   // Number of channels/timepoints/positions (probed at construction, updated on new images)
   private int numChannels_ = 1;

   // Controls
   private JScrollBar zScrollBar_;
   private JLabel zPositionLabel_;
   private JPanel zControlRow_;
   private JScrollBar cScrollBar_;
   private JLabel cPositionLabel_;
   private JPanel cControlRow_;
   private JScrollBar tScrollBar_;
   private JLabel tPositionLabel_;
   private JPanel tControlRow_;
   private JScrollBar pScrollBar_;
   private JLabel pPositionLabel_;
   private JPanel pControlRow_;

   // Status line
   private JLabel pixelInfoLabel_;
   // Most-recently-rendered XY images; updated on the EDT by the SwingWorker done() callback.
   private List<Image> lastXYImages_;

   private boolean updatingControls_ = false;

   // Only one SwingWorker refresh runs at a time; dirty means another is needed after it.
   private final AtomicBoolean refreshPending_ = new AtomicBoolean(false);
   private final AtomicBoolean refreshDirty_ = new AtomicBoolean(false);

   // Inspector stats
   private final StatsComputeQueue computeQueue_ = StatsComputeQueue.create();
   private volatile ImagesAndStats currentImagesAndStats_;
   // Coords of the last position for which we triggered an autostretch re-render.
   // Cleared when Z changes; set when the stats-triggered re-render fires.
   // Prevents the render→stats→re-render infinite loop (one re-render per Z position).
   private Coords lastAutostretchRerenderedCoords_;

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
      setUpMouseInfoListeners();

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
            doClose();
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
         final int newT = position.getTimePoint();
         final int newP = position.getStagePosition();
         if (newT != currentTime_ || newP != currentPosition_) {
            currentTime_ = newT;
            currentPosition_ = newP;
            SwingUtilities.invokeLater(new Runnable() {
               @Override
               public void run() {
                  syncScrollBarPosition(tScrollBar_, tPositionLabel_,
                        newT, numTimePoints_);
                  syncScrollBarPosition(pScrollBar_, pPositionLabel_,
                        newP, numPositions_);
                  scheduleRefresh();
               }
            });
            return position;
         }
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

   private void syncScrollBarPosition(JScrollBar bar, JLabel label, int value, int total) {
      if (bar == null || label == null) {
         return;
      }
      int clamped = Math.max(0, Math.min(value, total - 1));
      updatingControls_ = true;
      try {
         bar.setValue(clamped);
         label.setText(positionText(clamped, total));
      } finally {
         updatingControls_ = false;
      }
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
            repaintOverlayPanel();
         }

         @Override
         public void overlayVisibleChanged(Overlay o) {
            repaintOverlayPanel();
         }
      };
      synchronized (overlayListeners_) {
         overlayListeners_.put(overlay, listener);
      }
      overlay.addOverlayListener(listener);
      postEvent(DisplayWindowDidAddOverlayEvent.create(null, overlay));
      repaintOverlayPanel();
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
      repaintOverlayPanel();
   }

   /** Repaint whichever panel currently holds the overlay context. */
   private void repaintOverlayPanel() {
      if (xzPanel_.hasOverlayContext()) {
         xzPanel_.repaint();
      } else if (yzPanel_.hasOverlayContext()) {
         yzPanel_.repaint();
      } else {
         xyPanel_.repaint();
      }
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
            // When autostretch is on, trigger a re-render for each Z position at most
            // ONCE with its own stats (so the image reflects the correct autostretch for
            // that plane). After the re-render, the same Coords will be submitted again;
            // we detect this and skip the second re-render, breaking the loop.
            // This also prevents an infinite loop when the Inspector fails to clear
            // autostretch (e.g. NPE in handleAutoscale): autostretch=true stays set
            // but no spurious re-renders occur since no new Coords arrive.
            if (getDisplaySettings().isAutostretchEnabled()) {
               Coords statsCoords = (result.getRequest().getNumberOfImages() > 0)
                     ? result.getRequest().getImage(0).getCoords() : null;
               if (statsCoords != null
                     && !statsCoords.equals(lastAutostretchRerenderedCoords_)) {
                  lastAutostretchRerenderedCoords_ = statsCoords;
                  scheduleRefresh();
               }
            } else {
               lastAutostretchRerenderedCoords_ = null;
            }
         }
      });
      return 0L;
   }

   // ---- New image events ----

   @Subscribe
   public void onNewImage(DataProviderHasNewImageEvent event) {
      final int newZ = Math.max(1, dataProvider_.getNextIndex(Coords.Z_SLICE));
      final int newC = Math.max(1, dataProvider_.getNextIndex(Coords.CHANNEL));
      final int newT = Math.max(1, dataProvider_.getNextIndex(Coords.TIME_POINT));
      final int newP = Math.max(1, dataProvider_.getNextIndex(Coords.STAGE_POSITION));
      if (newZ != numZSlices_ || newC != numChannels_
            || newT != numTimePoints_ || newP != numPositions_) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               numZSlices_ = newZ;
               hasZ_ = numZSlices_ > 1;
               numChannels_ = newC;
               numTimePoints_ = newT;
               numPositions_ = newP;
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

      if (hasZ_) {
         syncScrollBarPosition(zScrollBar_, zPositionLabel_, crosshairZ_, numZSlices_);
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

      // With a null-layout grid we set absolute bounds directly — no layout manager interference.
      if (xyWrapper_ != null) {
         xyWrapper_.setBounds(PANEL_GAP, PANEL_GAP, xyW, xyH + LABEL_H);
      }
      if (yzWrapper_ != null) {
         yzWrapper_.setBounds(PANEL_GAP + xyW + PANEL_GAP, PANEL_GAP, zPx, xyH + LABEL_H);
      }
      if (xzWrapper_ != null) {
         xzWrapper_.setBounds(PANEL_GAP, PANEL_GAP + xyH + LABEL_H + PANEL_GAP,
               xyW, zPx + LABEL_H);
      }

      // The grid panel's preferred size drives the scroll pane content size.
      int totalW = PANEL_GAP + xyW + PANEL_GAP + zPx + PANEL_GAP;
      int totalH = PANEL_GAP + xyH + LABEL_H + PANEL_GAP + zPx + LABEL_H + PANEL_GAP;
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
            // XY panel content starts at (PANEL_GAP, PANEL_GAP + LABEL_H) inside the grid
            int crossPx = PANEL_GAP + (int) Math.round(crosshairX_ * s);
            int crossPy = PANEL_GAP + LABEL_H + (int) Math.round(crosshairY_ * s);

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

   /**
    * Add mouse-motion listeners to all three panels so the pixel-info status line updates
    * as the cursor moves over the XY, XZ, or YZ views.
    *
    * <p>Only XY pixel intensities are reported (using the images at the current z/t/p/c).
    * When the cursor is over the XZ or YZ panels, the x or y coordinate is derived from
    * the crosshair position (the axis that panel doesn't control).</p>
    */
   private void setUpMouseInfoListeners() {
      java.awt.event.MouseAdapter clearInfo = new java.awt.event.MouseAdapter() {
         @Override
         public void mouseExited(java.awt.event.MouseEvent e) {
            if (pixelInfoLabel_ != null) {
               pixelInfoLabel_.setText(" ");
            }
         }
      };

      // XY panel: both x and y come from the mouse position.
      xyPanel_.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
         @Override
         public void mouseMoved(java.awt.event.MouseEvent e) {
            double[] frac = xyPanel_.toImageFraction(e.getPoint());
            if (frac == null) {
               return;
            }
            int imgX = (int) Math.round(frac[0] * (imageWidth_ - 1));
            int imgY = (int) Math.round(frac[1] * (imageHeight_ - 1));
            updatePixelInfo(imgX, imgY);
         }
      });
      xyPanel_.addMouseListener(clearInfo);

      // XZ panel: x from mouse, y from crosshair.
      xzPanel_.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
         @Override
         public void mouseMoved(java.awt.event.MouseEvent e) {
            double[] frac = xzPanel_.toImageFraction(e.getPoint());
            if (frac == null) {
               return;
            }
            int imgX = (int) Math.round(frac[0] * (imageWidth_ - 1));
            updatePixelInfo(imgX, crosshairY_);
         }
      });
      xzPanel_.addMouseListener(clearInfo);

      // YZ panel: y from mouse, x from crosshair.
      yzPanel_.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
         @Override
         public void mouseMoved(java.awt.event.MouseEvent e) {
            double[] frac = yzPanel_.toImageFraction(e.getPoint());
            if (frac == null) {
               return;
            }
            int imgY = (int) Math.round(frac[1] * (imageHeight_ - 1));
            updatePixelInfo(crosshairX_, imgY);
         }
      });
      yzPanel_.addMouseListener(clearInfo);
   }

   /**
    * Update the pixel-info status label for the given XY image coordinate.
    * Reads intensity from the most-recently-rendered XY images (one per channel).
    * Called on the EDT from mouse-motion listeners.
    */
   private void updatePixelInfo(int x, int y) {
      if (pixelInfoLabel_ == null) {
         return;
      }
      List<Image> images = lastXYImages_;
      if (images == null || images.isEmpty()) {
         pixelInfoLabel_.setText(" ");
         return;
      }

      String intensityStr;
      if (images.size() == 1) {
         intensityStr = images.get(0).getIntensityStringAt(x, y);
      } else {
         StringBuilder sb = new StringBuilder("[");
         for (int i = 0; i < images.size(); i++) {
            if (i > 0) {
               sb.append(", ");
            }
            sb.append(images.get(i).getIntensityStringAt(x, y));
         }
         sb.append("]");
         intensityStr = sb.toString();
      }

      String text = String.format("%d, %d = %s", x, y, intensityStr);
      pixelInfoLabel_.setText(text);
      // Expand minimum width so it doesn't shrink when values change
      if (pixelInfoLabel_.getSize().width > pixelInfoLabel_.getMinimumSize().width) {
         pixelInfoLabel_.setMinimumSize(
               new Dimension(pixelInfoLabel_.getSize().width, 10));
      }
   }

   // ---- Refresh logic ----

   private void scheduleRefresh() {
      if (closed_) {
         return;
      }
      if (!refreshPending_.compareAndSet(false, true)) {
         // A worker is already running; mark dirty so it re-schedules when done.
         refreshDirty_.set(true);
         return;
      }
      refreshDirty_.set(false);

      final DisplaySettings settings = getDisplaySettings();
      final ImagesAndStats statsSnapshot = currentImagesAndStats_;
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
            return renderAllSlices(settings, statsSnapshot,
                  cx, cy, cz, ch, t, p, w, h, numZ, hasZ, numCh);
         }

         @Override
         protected void done() {
            refreshPending_.set(false);
            if (closed_) {
               return;
            }
            // If state changed while this worker was running, render again immediately.
            if (refreshDirty_.getAndSet(false)) {
               scheduleRefresh();
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

            // Route overlay context to the largest panel so corner-anchored overlays
            // have the most room and are clearly visible.
            // Note: overlays receive the XY images regardless of which panel is chosen;
            // overlay plugins should be robust to coordinate space differences.
            Image primaryXY = (result.xyImages != null && !result.xyImages.isEmpty())
                  ? result.xyImages.get(0) : null;
            double zPhys = numZ * aspectRatioZtoXY_;
            long xyArea = (long) w * h;
            long xzArea = (long) w * Math.max(1, Math.round(zPhys));
            long yzArea = Math.max(1, Math.round(zPhys)) * h;
            OrthogonalSlicePanel overlayPanel;
            if (hasZ && xzArea >= xyArea && xzArea >= yzArea) {
               overlayPanel = xzPanel_;
            } else if (hasZ && yzArea >= xyArea) {
               overlayPanel = yzPanel_;
            } else {
               overlayPanel = xyPanel_;
            }
            xyPanel_.setOverlayContext(
                  overlayPanel == xyPanel_ ? overlays_ : null,
                  result.xyImages, primaryXY, settings);
            xzPanel_.setOverlayContext(
                  overlayPanel == xzPanel_ ? overlays_ : null,
                  result.xyImages, primaryXY, settings);
            yzPanel_.setOverlayContext(
                  overlayPanel == yzPanel_ ? overlays_ : null,
                  result.xyImages, primaryXY, settings);

            lastXYImages_ = result.xyImages;

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

   /**
    * If autostretch is enabled and stats are available for the given channel, return the
    * [min, max] derived from the stats quantile. Otherwise return null (use DisplaySettings).
    */
   private long[] autostretchMinMax(DisplaySettings settings, ImagesAndStats stats, int channel) {
      if (!settings.isAutostretchEnabled() || stats == null) {
         return null;
      }
      double q = settings.getAutoscaleIgnoredQuantile();
      boolean ignoreZeros = settings.isAutoscaleIgnoringZeros();
      // Find the stats entry for this channel
      for (int i = 0; i < stats.getResult().size(); i++) {
         int statsCh = 0;
         if (i < stats.getRequest().getNumberOfImages()) {
            Coords c = stats.getRequest().getImage(i).getCoords();
            if (c.hasAxis(Coords.CHANNEL)) {
               statsCh = c.getChannel();
            }
         }
         if (statsCh != channel) {
            continue;
         }
         ComponentStats cStats = stats.getResult().get(i).getComponentStats(0);
         long min;
         long max;
         if (ignoreZeros) {
            min = 0L;
            max = cStats.getAutoscaleMaxForQuantileIgnoringZeros(q);
         } else {
            long[] minMax = new long[2];
            cStats.getAutoscaleMinMaxForQuantile(q, minMax);
            min = minMax[0];
            max = minMax[1];
         }
         return new long[]{min, Math.max(max, min + 1)};
      }
      return null;
   }

   /**
    * Determine whether the first image in the given list has float (GRAY32) pixels.
    */
   private static boolean isFloatStack(List<Image> zStack) {
      for (Image img : zStack) {
         if (img != null) {
            return img.getRawPixels() instanceof float[];
         }
      }
      return false;
   }

   /**
    * Return [fMin, fMax] for rendering a single-channel float image.
    *
    * <p>For autostretch: use the quantile double values from stats (accurate for float).
    * For manual: convert stored bin indices to actual pixel values using stats bin geometry.
    * Fallback when no stats: return [0, 1] so the image is at least visible.</p>
    *
    * @param settings current display settings
    * @param stats    current stats snapshot (may be null)
    * @param channel  channel index to look up
    * @return double[]{fMin, fMax}
    */
   private double[] floatScalingMinMax(DisplaySettings settings,
                                       ImagesAndStats stats, int channel) {
      ComponentDisplaySettings comp =
            settings.getChannelSettings(channel).getComponentSettings(0);

      // Find this channel's ComponentStats
      org.micromanager.display.internal.imagestats.ComponentStats cStats = null;
      if (stats != null) {
         for (int i = 0; i < stats.getResult().size(); i++) {
            int statsCh = 0;
            if (i < stats.getRequest().getNumberOfImages()) {
               Coords c = stats.getRequest().getImage(i).getCoords();
               if (c.hasAxis(Coords.CHANNEL)) {
                  statsCh = c.getChannel();
               }
            }
            if (statsCh == channel) {
               cStats = stats.getResult().get(i).getComponentStats(0);
               break;
            }
         }
      }

      if (settings.isAutostretchEnabled() && cStats != null) {
         double q = settings.getAutoscaleIgnoredQuantile();
         double fMin;
         double fMax;
         if (settings.isAutoscaleIgnoringZeros()) {
            // getAutoscale*IgnoringZeros return bin indices; convert to actual values
            fMin = 0.0;
            fMax = cStats.getHistogramRangeMinDouble()
                  + cStats.getAutoscaleMaxForQuantileIgnoringZeros(q) * cStats.getBinWidthDouble();
         } else {
            // getQuantile returns actual float pixel values for float images
            fMin = cStats.getQuantile(q);
            fMax = cStats.getQuantile(1.0 - q);
         }
         if (fMax <= fMin) {
            fMax = fMin + Math.max(cStats.getBinWidthDouble(), 1.0);
         }
         return new double[]{fMin, fMax};
      }

      if (cStats != null) {
         // Convert stored bin indices to actual float values
         int binCount = cStats.getHistogramBinCount();
         double binWidth = cStats.getBinWidthDouble();
         double rangeMin = cStats.getHistogramRangeMinDouble();
         long storedMin = comp.getScalingMinimum();
         long storedMax = comp.getScalingMaximum();
         long clampedMax = (storedMax == Long.MAX_VALUE) ? binCount
               : Math.min(binCount, storedMax);
         long clampedMin = Math.max(0, Math.min(clampedMax - 1, storedMin));
         double fMin = rangeMin + clampedMin * binWidth;
         double fMax = rangeMin + clampedMax * binWidth;
         if (fMax <= fMin) {
            fMax = fMin + Math.max(binWidth, 1.0);
         }
         return new double[]{fMin, fMax};
      }

      // No stats: fall back to [0, 1] so image is at least visible
      return new double[]{0.0, 1.0};
   }

   /**
    * Build a DisplaySettings with per-channel min/max overridden from autostretch stats.
    * Returns the original settings unchanged if autostretch is off or stats are null.
    */
   private DisplaySettings buildAutostretchSettings(DisplaySettings settings,
                                                    ImagesAndStats stats, int numChannels) {
      if (!settings.isAutostretchEnabled() || stats == null) {
         return settings;
      }
      DisplaySettings.Builder sb = settings.copyBuilder();
      for (int c = 0; c < numChannels; c++) {
         long[] stretch = autostretchMinMax(settings, stats, c);
         if (stretch == null) {
            continue;
         }
         ChannelDisplaySettings cs = settings.getChannelSettings(c);
         ComponentDisplaySettings comp = cs.getComponentSettings(0).copyBuilder()
               .scalingMinimum(stretch[0]).scalingMaximum(stretch[1]).build();
         sb = sb.channel(c, cs.copyBuilder().component(0, comp).build());
      }
      return sb.build();
   }

   private RenderResult renderAllSlices(DisplaySettings settings, ImagesAndStats stats,
                                        int cx, int cy, int cz,
                                        int ch, int t, int p,
                                        int w, int h, int numZ, boolean hasZ, int numChannels) {
      RenderResult result = new RenderResult();

      DisplaySettings.ColorMode colorMode = settings.getColorMode();
      boolean grayscale = colorMode == DisplaySettings.ColorMode.GRAYSCALE;
      final boolean composite = colorMode == DisplaySettings.ColorMode.COMPOSITE;
      final boolean multiChannel = numChannels > 1;

      int zPhysH = Math.max(1, (int) Math.round(numZ * aspectRatioZtoXY_));

      if (!multiChannel) {
         // Single channel path — fetch only channel 0
         List<Image> zStack;
         try {
            zStack = fetchZStack(0, t, p);
         } catch (IOException ex) {
            zStack = Collections.<Image>emptyList();
         }
         // Collect XY image for Inspector stats
         Image xyImg = getZImage(zStack, cz);
         result.xyImages = new java.util.ArrayList<Image>();
         if (xyImg != null) {
            result.xyImages.add(xyImg);
         }
         if (zStack.isEmpty()) {
            return result;
         }
         ChannelDisplaySettings cs = settings.getChannelSettings(ch);
         ComponentDisplaySettings comp = cs.getComponentSettings(0);
         double gamma = comp.getScalingGamma();
         Color color = grayscale ? Color.WHITE : cs.getColor();

         boolean isFloat = isFloatStack(zStack);
         if (isFloat) {
            double[] fScale = floatScalingMinMax(settings, stats, ch);
            float[] xyF = (xyImg != null) ? (float[]) xyImg.getRawPixels() : new float[w * h];
            result.xy = OrthogonalLutRenderer.renderFloat(xyF, w, h, fScale[0], fScale[1],
                  gamma, color);
            if (hasZ && numZ >= 2) {
               result.xz = scaleImage(OrthogonalLutRenderer.renderFloat(
                     OrthogonalSliceExtractor.extractXZFloat(zStack, cy, w, numZ),
                     w, numZ, fScale[0], fScale[1], gamma, color), w, zPhysH);
               result.yz = scaleImage(OrthogonalLutRenderer.renderFloat(
                     OrthogonalSliceExtractor.extractYZFloat(zStack, cx, h, numZ),
                     numZ, h, fScale[0], fScale[1], gamma, color), zPhysH, h);
            }
         } else {
            long[] stretch = autostretchMinMax(settings, stats, ch);
            long min = (stretch != null) ? stretch[0] : comp.getScalingMinimum();
            long max = (stretch != null) ? stretch[1] : comp.getScalingMaximum();
            if (xyImg != null) {
               int[] pixels = OrthogonalLutRenderer.toIntArray(xyImg.getRawPixels(), w * h);
               result.xy = OrthogonalLutRenderer.render(pixels, w, h, min, max, gamma, color);
            } else {
               result.xy = OrthogonalLutRenderer.render(new int[w * h], w, h,
                     0, 1, 1.0, Color.WHITE);
            }
            if (hasZ && numZ >= 2) {
               int[] xzPixels = OrthogonalSliceExtractor.extractXZ(zStack, cy, w, numZ);
               result.xz = scaleImage(
                     OrthogonalLutRenderer.render(xzPixels, w, numZ, min, max, gamma, color),
                     w, zPhysH);
               int[] yzPixels = OrthogonalSliceExtractor.extractYZ(zStack, cx, h, numZ);
               result.yz = scaleImage(
                     OrthogonalLutRenderer.render(yzPixels, numZ, h, min, max, gamma, color),
                     zPhysH, h);
            }
         }
      } else if (!composite) {
         // Grayscale or Color mode with multi-channel: show only the selected channel
         List<Image> zStack;
         try {
            zStack = fetchZStack(ch, t, p);
         } catch (IOException ex) {
            zStack = Collections.<Image>emptyList();
         }
         // Collect XY image for Inspector stats (reused below for rendering)
         Image xyImg = getZImage(zStack, cz);
         result.xyImages = new java.util.ArrayList<Image>();
         if (xyImg != null) {
            result.xyImages.add(xyImg);
         }
         ChannelDisplaySettings cs = settings.getChannelSettings(ch);
         ComponentDisplaySettings comp = cs.getComponentSettings(0);
         double gamma = comp.getScalingGamma();
         Color color = grayscale ? Color.WHITE : cs.getColor();

         boolean isFloat = isFloatStack(zStack);
         if (isFloat) {
            double[] fScale = floatScalingMinMax(settings, stats, ch);
            float[] xyF = (xyImg != null) ? (float[]) xyImg.getRawPixels() : new float[w * h];
            result.xy = OrthogonalLutRenderer.renderFloat(xyF, w, h, fScale[0], fScale[1],
                  gamma, color);
            if (hasZ && numZ >= 2) {
               result.xz = scaleImage(OrthogonalLutRenderer.renderFloat(
                     OrthogonalSliceExtractor.extractXZFloat(zStack, cy, w, numZ),
                     w, numZ, fScale[0], fScale[1], gamma, color), w, zPhysH);
               result.yz = scaleImage(OrthogonalLutRenderer.renderFloat(
                     OrthogonalSliceExtractor.extractYZFloat(zStack, cx, h, numZ),
                     numZ, h, fScale[0], fScale[1], gamma, color), zPhysH, h);
            }
         } else {
            long[] stretch = autostretchMinMax(settings, stats, ch);
            long min = (stretch != null) ? stretch[0] : comp.getScalingMinimum();
            long max = (stretch != null) ? stretch[1] : comp.getScalingMaximum();
            if (xyImg != null) {
               int[] pixels = OrthogonalLutRenderer.toIntArray(xyImg.getRawPixels(), w * h);
               result.xy = OrthogonalLutRenderer.render(pixels, w, h, min, max, gamma, color);
            } else {
               result.xy = OrthogonalLutRenderer.render(new int[w * h], w, h,
                     0, 1, 1.0, Color.WHITE);
            }
            if (hasZ && numZ >= 2) {
               int[] xzPixels = OrthogonalSliceExtractor.extractXZ(zStack, cy, w, numZ);
               result.xz = scaleImage(
                     OrthogonalLutRenderer.render(xzPixels, w, numZ, min, max, gamma, color),
                     w, zPhysH);
               int[] yzPixels = OrthogonalSliceExtractor.extractYZ(zStack, cx, h, numZ);
               result.yz = scaleImage(
                     OrthogonalLutRenderer.render(yzPixels, numZ, h, min, max, gamma, color),
                     zPhysH, h);
            }
         }
      } else {
         // Multi-channel composite path — fetch all channels
         java.util.List<List<Image>> allChannelStacks = new java.util.ArrayList<List<Image>>();
         for (int c = 0; c < numChannels; c++) {
            try {
               allChannelStacks.add(fetchZStack(c, t, p));
            } catch (IOException ex) {
               allChannelStacks.add(Collections.<Image>emptyList());
            }
         }
         // Collect XY images for Inspector stats
         result.xyImages = new java.util.ArrayList<Image>();
         for (int c = 0; c < numChannels; c++) {
            Image img = getZImage(allChannelStacks.get(c), cz);
            if (img != null) {
               result.xyImages.add(img);
            }
         }

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
               xzChPixels.add(OrthogonalSliceExtractor.extractXZ(zStack, cy, w, numZ));
               yzChPixels.add(OrthogonalSliceExtractor.extractYZ(zStack, cx, h, numZ));
            } else {
               xzChPixels.add(null);
               yzChPixels.add(null);
            }
         }

         DisplaySettings renderSettings = buildAutostretchSettings(settings, stats, numChannels);
         result.xy = OrthogonalLutRenderer.renderComposite(xyChPixels, w, h, renderSettings);

         if (hasZ && numZ >= 2) {
            result.xz = scaleImage(
                  OrthogonalLutRenderer.renderComposite(xzChPixels, w, numZ, renderSettings),
                  w, zPhysH);
            result.yz = scaleImage(
                  OrthogonalLutRenderer.renderComposite(yzChPixels, numZ, h, renderSettings),
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
      // Build fixed coords for all axes except Z.
      // We cannot use coordsBuilder().channel(0) etc. because DefaultCoords drops index-0 axes,
      // which would cause getImagesIgnoringAxes to match images from all channel/T/P values.
      // Instead we get a representative image (which has all axes encoded), override the axes
      // we care about, then strip Z so it is treated as the wildcard axis.
      Coords fixedCoords = buildFixedCoords(channel, time, position);

      List<Image> raw = dataProvider_.getImagesIgnoringAxes(fixedCoords, Coords.Z_SLICE);
      // Wrap in a mutable ArrayList so we can sort regardless of what the storage returns.
      // Also guards against null return from some storage implementations.
      java.util.List<Image> images = (raw != null)
            ? new java.util.ArrayList<Image>(raw)
            : new java.util.ArrayList<Image>();

      Collections.sort(images, new Comparator<Image>() {
         @Override
         public int compare(Image a, Image b2) {
            return Integer.compare(a.getCoords().getZ(), b2.getCoords().getZ());
         }
      });
      return images;
   }

   /**
    * Build a Coords with channel/time/position pinned to the requested values and Z omitted,
    * so it can be passed to {@code getImagesIgnoringAxes(coords, Z_SLICE)}.
    *
    * <p>Starting from a representative image's coords preserves any zero-valued axes
    * (which DefaultCoords.Builder drops when set explicitly to 0).</p>
    */
   private Coords buildFixedCoords(int channel, int time, int position) throws IOException {
      List<String> axes = dataProvider_.getAxes();
      // Start from an existing image's coords so zero-valued axes are retained.
      Image anyImage = dataProvider_.getAnyImage();
      Coords.Builder b;
      if (anyImage != null) {
         b = anyImage.getCoords().copyBuilder();
      } else {
         b = studio_.data().coordsBuilder();
      }
      if (axes.contains(Coords.CHANNEL)) {
         b = b.channel(channel);
      }
      if (axes.contains(Coords.TIME_POINT)) {
         b = b.timePoint(time);
      }
      if (axes.contains(Coords.STAGE_POSITION)) {
         b = b.stagePosition(position);
      }
      // Strip Z so it is treated as the wildcard by getImagesIgnoringAxes.
      return b.build().copyRemovingAxes(Coords.Z_SLICE);
   }

   private List<Image> fetchXYImages(int channel, int time, int position, int z)
         throws IOException {
      // Use the same axis-pinning strategy as fetchZStack, then add Z back.
      Coords coords = buildFixedCoords(channel, time, position).copyBuilder().z(z).build();
      Image img = dataProvider_.getImage(coords);
      if (img != null) {
         return Collections.singletonList(img);
      }
      // Fallback: scan the z-stack (covers sparse datasets where exact coord lookup fails)
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
      numTimePoints_ = dataProvider_.getNextIndex(Coords.TIME_POINT);
      if (numTimePoints_ < 1) {
         numTimePoints_ = 1;
      }
      numPositions_ = dataProvider_.getNextIndex(Coords.STAGE_POSITION);
      if (numPositions_ < 1) {
         numPositions_ = 1;
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

      // Zoom buttons at top (gear moved to bottom-right)
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

   private JButton buildGearButton() {
      JPopupMenu gearMenu = new JPopupMenu();

      JMenuItem inspectorItem = new JMenuItem("Image Inspector...");
      inspectorItem.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.displays().createInspectorForDataViewer(OrthogonalViewerFrame.this);
         }
      });
      gearMenu.add(inspectorItem);

      JMenuItem exportItem = new JMenuItem("Export Images as Displayed...");
      exportItem.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new OrthogonalExportDlg(OrthogonalViewerFrame.this, studio_).setVisible(true);
         }
      });
      gearMenu.add(exportItem);

      JButton gearBtn = new JButton(
            IconLoader.getIcon("/org/micromanager/icons/gear.png"));
      gearBtn.setToolTipText("Image tools");
      gearBtn.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            gearMenu.show(gearBtn, 0, gearBtn.getHeight());
         }
      });
      return gearBtn;
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
      // Each slider row is a sub-panel with identical column constraints, so when they all
      // fill the parent width the position labels and sliders align across rows.
      // Column spec: [axis label pref] [position text 80lp fixed, right-aligned]
      //              [slider grow] [8lp right padding]
      // "80lp" is wide enough for "9999/9999" at the small font size.
      final String rowCols = "[pref][80lp, right][grow][8lp]";
      final String rowLayout = "fillx, insets 0, gap 2 0";

      JPanel panel = new JPanel(new MigLayout("fillx, insets 2 4 2 4, gap 2 2"));
      panel.setBackground(DARK_GREY);
      panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

      // Pixel info status line
      pixelInfoLabel_ = new JLabel(" ");
      pixelInfoLabel_.setForeground(Color.LIGHT_GRAY);
      pixelInfoLabel_.setFont(pixelInfoLabel_.getFont().deriveFont(10.0f));
      pixelInfoLabel_.setMinimumSize(new Dimension(0, 10));
      panel.add(pixelInfoLabel_, "growx, wrap");

      // Z row
      int zMax = Math.max(1, numZSlices_);
      int initZ = Math.max(0, Math.min(crosshairZ_, zMax - 1));
      zScrollBar_ = new JScrollBar(JScrollBar.HORIZONTAL, initZ, 1, 0, zMax);
      zPositionLabel_ = makePositionLabel(positionText(initZ, numZSlices_));
      zControlRow_ = new JPanel(new MigLayout(rowLayout, rowCols));
      zControlRow_.setBackground(DARK_GREY);
      zControlRow_.add(makeLabel("Z:"));
      zControlRow_.add(zPositionLabel_);
      zControlRow_.add(zScrollBar_, "growx");
      zControlRow_.add(new JLabel()); // right-padding column
      panel.add(zControlRow_, "growx, wrap");
      zControlRow_.setVisible(hasZ_);

      // C row
      int cMax = Math.max(1, numChannels_);
      int initCh = Math.max(0, Math.min(currentChannel_, cMax - 1));
      cScrollBar_ = new JScrollBar(JScrollBar.HORIZONTAL, initCh, 1, 0, cMax);
      cPositionLabel_ = makePositionLabel(positionText(initCh, numChannels_));
      cControlRow_ = new JPanel(new MigLayout(rowLayout, rowCols));
      cControlRow_.setBackground(DARK_GREY);
      cControlRow_.add(makeLabel("C:"));
      cControlRow_.add(cPositionLabel_);
      cControlRow_.add(cScrollBar_, "growx");
      cControlRow_.add(new JLabel());
      panel.add(cControlRow_, "growx, wrap");
      cControlRow_.setVisible(numChannels_ > 1);

      // T row
      int tMax = Math.max(1, numTimePoints_);
      int initT = Math.max(0, Math.min(currentTime_, tMax - 1));
      tScrollBar_ = new JScrollBar(JScrollBar.HORIZONTAL, initT, 1, 0, tMax);
      tPositionLabel_ = makePositionLabel(positionText(initT, numTimePoints_));
      tControlRow_ = new JPanel(new MigLayout(rowLayout, rowCols));
      tControlRow_.setBackground(DARK_GREY);
      tControlRow_.add(makeLabel("T:"));
      tControlRow_.add(tPositionLabel_);
      tControlRow_.add(tScrollBar_, "growx");
      tControlRow_.add(new JLabel());
      panel.add(tControlRow_, "growx, wrap");
      tControlRow_.setVisible(numTimePoints_ > 1);

      // P row
      int pMax = Math.max(1, numPositions_);
      int initP = Math.max(0, Math.min(currentPosition_, pMax - 1));
      pScrollBar_ = new JScrollBar(JScrollBar.HORIZONTAL, initP, 1, 0, pMax);
      pPositionLabel_ = makePositionLabel(positionText(initP, numPositions_));
      pControlRow_ = new JPanel(new MigLayout(rowLayout, rowCols));
      pControlRow_.setBackground(DARK_GREY);
      pControlRow_.add(makeLabel("P:"));
      pControlRow_.add(pPositionLabel_);
      pControlRow_.add(pScrollBar_, "growx");
      pControlRow_.add(new JLabel());
      panel.add(pControlRow_, "growx, wrap");
      pControlRow_.setVisible(numPositions_ > 1);

      // Gear button — always visible, pinned to bottom-right
      JButton gearBtn = buildGearButton();
      panel.add(gearBtn, "right");

      wireListeners();
      return panel;
   }

   private JLabel makePositionLabel(String text) {
      JLabel lbl = new JLabel(text);
      lbl.setForeground(Color.LIGHT_GRAY);
      lbl.setFont(lbl.getFont().deriveFont(10.0f));
      return lbl;
   }

   private static String positionText(int index, int total) {
      int digits = Integer.toString(total).length();
      return String.format("%" + digits + "d/%" + digits + "d", index + 1, total);
   }

   private void wireListeners() {
      zScrollBar_.addAdjustmentListener(new AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(AdjustmentEvent e) {
            if (!updatingControls_) {
               int z = zScrollBar_.getValue();
               zPositionLabel_.setText(positionText(z, numZSlices_));
               setCrosshairAndRefresh(crosshairX_, crosshairY_, z);
            }
         }
      });
      cScrollBar_.addAdjustmentListener(new AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(AdjustmentEvent e) {
            if (!updatingControls_) {
               int ch = cScrollBar_.getValue();
               currentChannel_ = ch;
               cPositionLabel_.setText(positionText(ch, numChannels_));
               scheduleRefresh();
            }
         }
      });
      tScrollBar_.addAdjustmentListener(new AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(AdjustmentEvent e) {
            if (!updatingControls_) {
               int t = tScrollBar_.getValue();
               currentTime_ = t;
               tPositionLabel_.setText(positionText(t, numTimePoints_));
               scheduleRefresh();
            }
         }
      });
      pScrollBar_.addAdjustmentListener(new AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(AdjustmentEvent e) {
            if (!updatingControls_) {
               int p = pScrollBar_.getValue();
               currentPosition_ = p;
               pPositionLabel_.setText(positionText(p, numPositions_));
               scheduleRefresh();
            }
         }
      });
   }

   private JLabel makeLabel(String text) {
      JLabel lbl = new JLabel(text);
      lbl.setForeground(Color.LIGHT_GRAY);
      return lbl;
   }

   private void updateSliderRanges() {
      if (zScrollBar_ == null) {
         return;
      }
      int zMax = Math.max(1, numZSlices_);
      zScrollBar_.setMaximum(zMax);
      crosshairZ_ = Math.min(crosshairZ_, zMax - 1);
      zPositionLabel_.setText(positionText(crosshairZ_, numZSlices_));
      zControlRow_.setVisible(hasZ_);

      if (cScrollBar_ == null) {
         return;
      }
      int cMax = Math.max(1, numChannels_);
      cScrollBar_.setMaximum(cMax);
      currentChannel_ = Math.min(currentChannel_, cMax - 1);
      cPositionLabel_.setText(positionText(currentChannel_, numChannels_));
      cControlRow_.setVisible(numChannels_ > 1);

      int tMax = Math.max(1, numTimePoints_);
      tScrollBar_.setMaximum(tMax);
      currentTime_ = Math.min(currentTime_, tMax - 1);
      tPositionLabel_.setText(positionText(currentTime_, numTimePoints_));
      tControlRow_.setVisible(numTimePoints_ > 1);

      int pMax = Math.max(1, numPositions_);
      pScrollBar_.setMaximum(pMax);
      currentPosition_ = Math.min(currentPosition_, pMax - 1);
      pPositionLabel_.setText(positionText(currentPosition_, numPositions_));
      pControlRow_.setVisible(numPositions_ > 1);
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

   // ---- DisplayWindow implementation ----

   @Override
   public java.awt.Window getWindow() {
      if (closed_) {
         throw new IllegalStateException("Display has closed");
      }
      return frame_;
   }

   @Override
   @SuppressWarnings("deprecation")
   public java.awt.Window getAsWindow() {
      return closed_ ? null : frame_;
   }

   @Override
   public void toFront() {
      if (!closed_) {
         frame_.toFront();
      }
   }

   @Override
   public void show() {
      if (!closed_) {
         frame_.setVisible(true);
      }
   }

   @Override
   public boolean requestToClose() {
      if (closed_) {
         return true;
      }
      close();
      return true;
   }

   @Override
   public void close() {
      if (SwingUtilities.isEventDispatchThread()) {
         doClose();
      } else {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               doClose();
            }
         });
      }
   }

   @Override
   public void displayStatusString(String status) {
      // no-op — we have no dedicated status bar beyond the pixel info label
   }

   @Override
   public double getZoom() {
      return pixelScale_ * zoomFactor_;
   }

   @Override
   @SuppressWarnings("deprecation")
   public double getMagnification() {
      return getZoom();
   }

   @Override
   public void setZoom(double ratio) {
      if (!closed_ && pixelScale_ > 0) {
         applyZoom(ratio / pixelScale_);
      }
   }

   @Override
   @SuppressWarnings("deprecation")
   public void setMagnification(double ratio) {
      setZoom(ratio);
   }

   @Override
   public void adjustZoom(double factor) {
      if (!closed_) {
         applyZoom(zoomFactor_ * factor);
      }
   }

   @Override
   public void autostretch() {
      if (!closed_) {
         setDisplaySettings(getDisplaySettings().copyBuilder()
               .autostretch(true).build());
      }
   }

   @Override
   @SuppressWarnings("deprecation")
   public ImagePlus getImagePlus() {
      return null;
   }

   @Override
   public void setFullScreen(boolean enable) {
      // not supported
   }

   @Override
   public boolean isFullScreen() {
      return false;
   }

   @Override
   @SuppressWarnings("deprecation")
   public void toggleFullScreen() {
      // not supported — use setFullScreen(boolean)
   }

   @Override
   public DisplayWindow duplicate() {
      throw new UnsupportedOperationException("OrthogonalViewer does not support duplication");
   }

   @Override
   public void setCustomTitle(String title) {
      if (!closed_ && title != null) {
         frame_.setTitle(title);
      }
   }

   @Override
   public void setDisplaySettingsProfileKey(String key) {
      // not supported
   }

   @Override
   public void setWindowPositionKey(String key) {
      // not supported
   }

   // ---- Export support ----

   private static final int GAP_PX = 4;

   /**
    * Build a DisplaySettings with fresh per-frame autostretch values baked in for integer
    * channels. Float (GRAY32) channels are skipped because converting their stored bin-index
    * scaling values to actual intensities requires {@code ComponentStats} histogram geometry,
    * which is not available per-frame during export. For those channels autostretch is left
    * enabled so that {@code renderAllSlices} uses the {@code statsSnapshot} passed by the
    * caller as a best-effort fallback.
    *
    * @param settings base settings (autostretch=true on entry)
    * @param z        z-slice index
    * @param t        time-point index
    * @param p        stage-position index
    * @param ch       currently selected channel (used when not in composite mode)
    * @param numCh    total number of channels
    * @param w        image width (unused; kept for signature consistency)
    * @param h        image height (unused; kept for signature consistency)
    * @return DisplaySettings with fresh quantile min/max baked per integer channel;
    *         autostretch is disabled globally except when any channel is float, in which
    *         case it stays enabled so the caller's stats snapshot is used for those channels
    */
   private DisplaySettings buildExportAutostretchSettings(
         DisplaySettings settings, int z, int t, int p,
         int ch, int numCh, int w, int h) {
      double q = settings.getAutoscaleIgnoredQuantile();
      boolean ignoreZeros = settings.isAutoscaleIgnoringZeros();

      DisplaySettings.ColorMode colorMode = settings.getColorMode();
      boolean composite = colorMode == DisplaySettings.ColorMode.COMPOSITE;

      int firstCh = composite ? 0 : ch;
      int lastCh = composite ? numCh - 1 : ch;

      DisplaySettings.Builder sb = settings.copyBuilder();
      boolean anyFloat = false;

      for (int c = firstCh; c <= lastCh; c++) {
         Image img = null;
         try {
            List<Image> zStack = fetchZStack(c, t, p);
            img = getZImage(zStack, z);
            if (img == null && !zStack.isEmpty()) {
               img = zStack.get(0);
            }
         } catch (IOException ex) {
            // leave img null — keep current settings for this channel
         }
         if (img == null) {
            continue;
         }

         Object raw = img.getRawPixels();
         if (raw instanceof float[]) {
            // Cannot compute fresh float autostretch without ComponentStats bin geometry.
            // Leave this channel's settings unchanged; renderAllSlices will use the
            // statsSnapshot (passed by the caller) for the autostretch path.
            anyFloat = true;
            continue;
         }
         ChannelDisplaySettings cs = settings.getChannelSettings(c);
         int[] pixels = OrthogonalLutRenderer.toIntArray(raw, img.getWidth() * img.getHeight());
         long[] minMax = computeQuantileMinMax(pixels, q, ignoreZeros);
         if (minMax == null) {
            continue;
         }
         ComponentDisplaySettings comp = cs.getComponentSettings(0).copyBuilder()
               .scalingMinimum(minMax[0]).scalingMaximum(minMax[1]).build();
         sb = sb.channel(c, cs.copyBuilder().component(0, comp).build());
      }

      // Disable autostretch for integer channels (values are now baked above).
      // Keep it enabled if any channel is float so renderAllSlices takes the
      // autostretch path for those channels using the caller's stats snapshot.
      if (!anyFloat) {
         sb = sb.autostretch(false);
      }

      return sb.build();
   }

   /**
    * Compute quantile-based [min, max] from a flat int pixel array.
    *
    * <p>Values are treated as unsigned. For byte (0–255) and short (0–65535) ranges
    * a histogram-based O(n) algorithm is used. Wider values fall back to sorting.
    * The quantile {@code q} is the fraction of pixels to ignore at each tail.
    * If {@code ignoreZeros} is true, zeros are excluded before computing quantiles.
    *
    * @param pixels      raw pixel values (unsigned — values &gt; 65535 trigger sort fallback)
    * @param q           fraction to ignore at each tail [0, 0.5)
    * @param ignoreZeros if true, exclude zero-valued pixels from the computation
    * @return long[]{min, max} with max &gt; min, or null if not enough data
    */
   private static long[] computeQuantileMinMax(int[] pixels, double q, boolean ignoreZeros) {
      if (pixels == null || pixels.length == 0) {
         return null;
      }

      // Determine value range to decide whether histogram approach is feasible.
      long maxVal = 0L;
      for (int pixel : pixels) {
         long v = pixel & 0xFFFFFFFFL;
         if (v > maxVal) {
            maxVal = v;
         }
      }

      if (maxVal <= 65535L) {
         return computeQuantileMinMaxHistogram(pixels, q, ignoreZeros, (int) maxVal);
      }

      // Fallback for unusual wide values (e.g. raw 32-bit int data): sort-based O(n log n).
      int count = 0;
      for (int pixel : pixels) {
         long v = pixel & 0xFFFFFFFFL;
         if (!ignoreZeros || v != 0L) {
            count++;
         }
      }
      if (count == 0) {
         return null;
      }
      long[] vals = new long[count];
      int idx = 0;
      for (int pixel : pixels) {
         long v = pixel & 0xFFFFFFFFL;
         if (!ignoreZeros || v != 0L) {
            vals[idx++] = v;
         }
      }
      java.util.Arrays.sort(vals);

      int loIdx = (int) Math.floor(q * count);
      int hiIdx = (int) Math.ceil((1.0 - q) * count) - 1;
      loIdx = Math.max(0, Math.min(loIdx, count - 1));
      hiIdx = Math.max(loIdx, Math.min(hiIdx, count - 1));

      long min = ignoreZeros ? 0L : vals[loIdx];
      long max = vals[hiIdx];
      if (max <= min) {
         max = min + 1;
      }
      return new long[]{min, max};
   }

   /**
    * Histogram-based O(n) quantile computation for pixel values in [0, maxVal].
    */
   private static long[] computeQuantileMinMaxHistogram(int[] pixels, double q,
                                                        boolean ignoreZeros, int maxVal) {
      int[] hist = new int[maxVal + 1];
      int count = 0;
      for (int pixel : pixels) {
         int v = (int) (pixel & 0xFFFFFFFFL);
         if (!ignoreZeros || v != 0) {
            hist[v]++;
            count++;
         }
      }
      if (count == 0) {
         return null;
      }

      int loTarget = (int) Math.floor(q * count);
      int hiTarget = (int) Math.ceil((1.0 - q) * count) - 1;
      loTarget = Math.max(0, Math.min(loTarget, count - 1));
      hiTarget = Math.max(loTarget, Math.min(hiTarget, count - 1));

      long min = 0;
      long max = maxVal;
      int cumulative = 0;
      boolean minFound = false;
      for (int v = ignoreZeros ? 1 : 0; v <= maxVal; v++) {
         int prev = cumulative;
         cumulative += hist[v];
         if (!minFound && cumulative > loTarget) {
            min = ignoreZeros ? 0L : v;
            minFound = true;
         }
         if (prev <= hiTarget && cumulative > hiTarget) {
            max = v;
            break;
         }
      }

      if (max <= min) {
         max = min + 1;
      }
      return new long[]{min, max};
   }

   /** The number of Z slices in the current dataset. */
   public int getNumZSlices() {
      return numZSlices_;
   }

   /** The number of time points in the current dataset. */
   public int getNumTimePoints() {
      return numTimePoints_;
   }

   /** The number of stage positions in the current dataset. */
   public int getNumPositions() {
      return numPositions_;
   }

   /** Whether the dataset has a Z axis with more than one slice. */
   public boolean hasZ() {
      return hasZ_;
   }

   /**
    * Render a single composite export frame at the given (z, t, p) position.
    *
    * <p>Tiles XY (top-left), YZ (top-right), XZ (bottom-left) into one BufferedImage,
    * then paints overlays over the appropriate region. Uses the current crosshair
    * x/y position and the current channel. Runs on the calling thread (not EDT).</p>
    *
    * @param z z-slice index
    * @param t time-point index
    * @param p stage-position index
    * @return tiled ARGB BufferedImage, or null if rendering produced no data
    */
   public BufferedImage renderCompositeForExport(int z, int t, int p) {
      // Snapshot all EDT-owned fields at the start so the export thread sees a
      // consistent view even if the user adjusts controls concurrently.
      DisplaySettings settings = getDisplaySettings();
      int ch = currentChannel_;
      int cx = crosshairX_;
      int cy = crosshairY_;
      int w = imageWidth_;
      int h = imageHeight_;
      int numZ = numZSlices_;
      boolean hasZ = hasZ_;
      int numCh = numChannels_;

      // Snapshot stats; used both for the float rendering path (which needs histogram
      // geometry to convert bin-index display values to actual float intensities) and
      // as a best-effort fallback when fresh pixel data cannot be fetched.
      ImagesAndStats statsSnapshot = currentImagesAndStats_;

      if (settings.isAutostretchEnabled()) {
         // Bake fresh per-frame quantile min/max into settings for integer channels.
         // Float channels cannot be baked (need ComponentStats for bin-index→float
         // conversion); for those, autostretch is kept enabled in the returned settings
         // so renderAllSlices uses statsSnapshot (stale, but better than [0,1]).
         settings = buildExportAutostretchSettings(settings, z, t, p, ch, numCh, w, h);
         // If all channels were integer, autostretch is now false — the snapshot is no
         // longer needed (baked values are used directly). Clear it to avoid renderAllSlices
         // re-applying autostretch from stale stats on top of the baked values.
         if (!settings.isAutostretchEnabled()) {
            statsSnapshot = null;
         }
      }

      RenderResult result = renderAllSlices(settings, statsSnapshot,
            cx, cy, z, ch, t, p, w, h, numZ, hasZ, numCh);
      if (result == null || result.xy == null) {
         return null;
      }

      // Tile layout: XY top-left, YZ top-right, XZ bottom-left
      // XZ and YZ are only present when hasZ.
      BufferedImage xz = result.xz;
      BufferedImage yz = result.yz;

      int xyW = result.xy.getWidth();
      int xyH = result.xy.getHeight();
      int yzW = (yz != null) ? yz.getWidth() : 0;
      int xzH = (xz != null) ? xz.getHeight() : 0;

      int totalW = xyW + (yz != null ? GAP_PX + yzW : 0);
      int totalH = xyH + (xz != null ? GAP_PX + xzH : 0);
      totalW = Math.max(totalW, 1);
      totalH = Math.max(totalH, 1);

      BufferedImage composite = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
      java.awt.Graphics2D g2 = composite.createGraphics();
      g2.setColor(DARK_GREY);
      g2.fillRect(0, 0, totalW, totalH);

      // XY panel — top-left
      g2.drawImage(result.xy, 0, 0, null);

      // YZ panel — top-right (only if hasZ)
      if (yz != null) {
         g2.drawImage(yz, xyW + GAP_PX, 0, null);
      }

      // XZ panel — bottom-left (only if hasZ)
      if (xz != null) {
         g2.drawImage(xz, 0, xyH + GAP_PX, null);
      }

      // Paint overlays over the XY region (overlays work in XY image coordinates)
      List<Image> overlayImgs = result.xyImages;
      Image primaryImg = (overlayImgs != null && !overlayImgs.isEmpty())
            ? overlayImgs.get(0) : null;
      if (!overlays_.isEmpty() && primaryImg != null) {
         java.util.List<Overlay> overlayList =
               new java.util.ArrayList<Overlay>(overlays_);
         java.awt.Graphics2D og = (java.awt.Graphics2D) g2.create();
         try {
            og.setClip(0, 0, xyW, xyH);
            og.scale((double) xyW / w, (double) xyH / h);
            java.awt.Rectangle screenRect = new java.awt.Rectangle(0, 0, w, h);
            java.awt.geom.Rectangle2D.Float imageViewPort =
                  new java.awt.geom.Rectangle2D.Float(0, 0, w, h);
            for (Overlay overlay : overlayList) {
               if (overlay.isVisible()) {
                  try {
                     overlay.paintOverlay(og, screenRect, settings,
                           overlayImgs, primaryImg, imageViewPort);
                  } catch (Exception ex) {
                     // ignore overlay paint errors
                  }
               }
            }
         } finally {
            og.dispose();
         }
      }

      g2.dispose();
      return composite;
   }

   /** Current crosshair Z slice index (0-based). */
   public int getCrosshairZ() {
      return crosshairZ_;
   }

   /** Current time-point index (0-based). */
   public int getCurrentTime() {
      return currentTime_;
   }

   /** Current stage-position index (0-based). */
   public int getCurrentPosition() {
      return currentPosition_;
   }

   public BufferedImage getXYImage() {
      return xyPanel_.getCurrentImage();
   }

   public BufferedImage getXZImage() {
      return xzPanel_.getCurrentImage();
   }

   public BufferedImage getYZImage() {
      return yzPanel_.getCurrentImage();
   }

   // ---- Close / lifecycle ----

   private void doClose() {
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
