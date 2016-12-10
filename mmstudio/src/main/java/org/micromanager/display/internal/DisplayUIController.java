// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display.internal;

import com.bulenkov.iconloader.IconLoader;
import ij.ImagePlus;
import ij.Menus;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.MenuBar;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.internal.imagej.ImageJBridge;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.MustCallOnEDT;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.internal.utils.performance.PerformanceMonitor;
import org.micromanager.internal.utils.performance.TimeIntervalRunningQuantile;

/**
 * Manages the JFrame(s) for image displays.
 *
 * This is a controller object that coordinates and routes activity. It also
 * creates and destroys the actual JFrame.
 *
 * This is the object that holds the state describing which images are
 * currently displayed (as opposed to {@code DisplayController}'s notion of
 * the current display position, which may update faster than the actual UI.
 *
 * @author Mark A. Tsuchida
 */
public final class DisplayUIController implements Closeable, WindowListener,
      NDScrollBarPanel.Listener
{
   private final DisplayController displayController_;

   // All fields must only be accessed from the EDT unless otherwise noted.

   private final ControlsFactory controlsFactory_;

   private JFrame frame_; // Not null iff not closed
   private JFrame fullScreenFrame_; // Not null iff in full-screen mode

   // We place all components in a JPanel, so that they can be transferred
   // en bloc to and from the full screen window.
   private final JPanel contentPanel_;

   // Other UI components to which we need access after creation
   private JLabel noImagesMessageLabel_;
   private JPanel canvasPanel_;
   private JPanel canvasBorderPanel_;
   private JComponent topControlPanel_;
   private JComponent bottomControlPanel_;
   private JButton fullScreenButton_;
   private JButton zoomInButton_;
   private JButton zoomOutButton_;
   private JLabel pixelInfoLabel_;
   private JLabel fpsLabel_;
   private NDScrollBarPanel scrollBarPanel_;

   private ImageJBridge ijBridge_;

   // Data display state of the UI (which may lag behind the display
   // controller's notion of what's current)
   private final List<String> displayedAxes_ = new ArrayList<String>();
   private final List<Integer> displayedAxisLengths_ = new ArrayList<Integer>();
   private ImagesAndStats displayedImages_;

   private final ScheduledExecutorService scheduledExecutor_ =
         Executors.newSingleThreadScheduledExecutor(ThreadFactoryFactory.
               createThreadFactory("DisplayUIController"));

   // Display rate estimation
   private static final int DISPLAY_INTERVAL_SMOOTH_N_SAMPLES = 100;
   private static final int FPS_DISPLAY_DURATION_MS = 500;
   private static final int FPS_DISPLAY_UPDATE_INTERVAL_MS = 250;
   private final AtomicBoolean repaintScheduledForNewImages_ =
         new AtomicBoolean(false);
   private final AtomicReference<TimeIntervalRunningQuantile>
         displayIntervalEstimator_ =
         new AtomicReference<TimeIntervalRunningQuantile>(
               TimeIntervalRunningQuantile.create(
                     DISPLAY_INTERVAL_SMOOTH_N_SAMPLES));
   private ScheduledFuture<?> fpsDismissFuture_;

   private PerformanceMonitor perfMon_;

   private static final int MIN_CANVAS_HEIGHT = 100;
   private static final int BORDER_THICKNESS = 2;

   private static final class UpdatePixelInfoTag {}


   @MustCallOnEDT
   static DisplayUIController create(DisplayController parent,
         ControlsFactory controlsFactory)
   {
      DisplayUIController instance = new DisplayUIController(parent,
            controlsFactory);
      instance.frame_.addWindowListener(instance);
      return instance;
   }

   @MustCallOnEDT
   private DisplayUIController(DisplayController parent,
         ControlsFactory controlsFactory)
   {
      displayController_ = parent;
      controlsFactory_ = controlsFactory;
      frame_ = makeFrame(false);
      contentPanel_ = buildInitialUI();
      frame_.add(contentPanel_);
      frame_.validate();
   }

   public void setPerformanceMonitor(PerformanceMonitor perfMon) {
      perfMon_ = perfMon;
   }

   @MustCallOnEDT
   private JFrame makeFrame(boolean fullScreen) {
      JFrame frame;
      if (!fullScreen) {
         // TODO LATER Eliminate MMFrame
         frame = new MMFrame("iamge display window", false);
         frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
         ((MMFrame) frame).loadPosition(320, 320, 480, 320);

         // TODO Determine initial window bounds using a CascadingWindowPositioner:
         // - (Setting canvas zoom has been handled by DisplayController (ImageJLink))
         // - Determine the frame size to show the entire image at current zoom
         // - Fetch last-used top-left from profile
         // - If no other viewer frames are open, use the last-used top-left (but
         //   see below for adjustment when window doesn't fit in screen)
         // - Add cascading offset to top-left if any other viewer frames are open
         //   - Normally, offset is x=y=d, where d = frame_.getInsets().top
         //   - But if that offset would cause window to go off bottom (not right),
         //     then set absolute y to top of available screen area. In this case,
         //     the x position should be computed as 2*d plus the intersection of
         //     the top of the available screen area with the 45-degree line
         //     extending upper-left from the top-left of the previous (x, y) (with
         //     a minimum x of 0)
         //   - If frame's x is <100 from the right edge of available screen area,
         //     shift down by d and set absolute x to left edge of monitor
         //   - Finally, if the previous step leaves the vert overlap <100,
         //     set absolute x and y to top-left of available screen area.
         // - Having determined the top-left position, set the width and height
         //   such that bottom of frame does not extend beyond available screen
         //   area and width of frame is no more than the width of available screen
         //   area.
         // - In any case, the screen to use is the screen in which a viewer window
         //   was last found (not created).
      }
      else {
         frame = new JFrame();
         frame.setUndecorated(true);
         frame.setResizable(false);
         frame.setBounds(
               GUIUtils.getFullScreenBounds(frame.getGraphicsConfiguration()));
         frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
      }
      frame.setTitle("TODO TITLE"); // TODO Listen for changes to name, zoom, and disk/memory/save state
      return frame;
   }

   @MustCallOnEDT
   @Override
   public void close() {
      setVisible(false);
      if (ijBridge_ != null) {
         ijBridge_.mm2ijWindowClosed();
         ijBridge_ = null;
      }
      if (fullScreenFrame_ != null) {
         fullScreenFrame_.dispose();
         fullScreenFrame_ = null;
      }
      if (frame_ != null) {
         frame_.dispose();
         frame_ = null;
      }
   }

   @MustCallOnEDT
   public JFrame getFrame() {
      return fullScreenFrame_ == null ? frame_ : fullScreenFrame_;
   }

   @MustCallOnEDT
   public ImagePlus getIJImagePlus() {
      return ijBridge_.getIJImagePlus();
   }

   @MustCallOnEDT
   private JPanel buildInitialUI() {
      JPanel contentPanel = new JPanel();
      contentPanel.setLayout(new MigLayout("insets 1, gap 1 1, fill",
            "[grow, fill]",
            "[] 0 [grow, fill] related []"));

      canvasPanel_ = new JPanel(new MigLayout("insets 0, fill"));
      noImagesMessageLabel_ = new JLabel("Waiting for Images to Display...");
      noImagesMessageLabel_.setEnabled(false);
      canvasPanel_.add(noImagesMessageLabel_, "align center");

      topControlPanel_ = buildTopControls();
      bottomControlPanel_ = buildBottomControls();

      contentPanel.add(topControlPanel_, "growx, wrap");
      contentPanel.add(canvasPanel_, "align center, grow, wrap");
      contentPanel.add(bottomControlPanel_, "align center, growx, wrap");

      // Prevent controls from getting obscured by shrinking the frame
      int minWidth = Math.max(topControlPanel_.getMinimumSize().width,
            bottomControlPanel_.getMinimumSize().width);
      int minHeight = topControlPanel_.getMinimumSize().height +
            MIN_CANVAS_HEIGHT +
            bottomControlPanel_.getMinimumSize().height;
      Insets frameInsets = frame_.getInsets();
      minWidth += frameInsets.left + frameInsets.right;
      minHeight += frameInsets.top + frameInsets.bottom;
      frame_.setMinimumSize(new Dimension(minWidth, minHeight));

      return contentPanel;
   }

   @MustCallOnEDT
   private void setupDisplayUI() {
      if (ijBridge_ != null) {
         return;
      }

      JFrame frame = fullScreenFrame_ == null ? frame_ : fullScreenFrame_;

      ijBridge_ = ImageJBridge.create(this);

      canvasPanel_.removeAll();
      noImagesMessageLabel_ = null;

      canvasBorderPanel_ = new JPanel(new MigLayout("insets 0, fill"));
      canvasBorderPanel_.setBorder(BorderFactory.createLineBorder(
            Color.BLACK, BORDER_THICKNESS));
      canvasBorderPanel_.add(ijBridge_.getIJImageCanvas());
      canvasPanel_.add(canvasBorderPanel_, "align center");

      // Allow canvas to shrink when user resizes window
      ijBridge_.getIJImageCanvas().setMinimumSize(new Dimension(1, 1));

      updateZoomUIState();
      canvasDidChangeSize();
   }

   @MustCallOnEDT
   private JPanel makeValidationRootJPanel(LayoutManager layoutManager) {
      // Create a JPanel that doesn't propagate layout invalidation upward.
      // This should reduce repaings of the canvas, which lives outside of the
      // panel created here. However, it is not clear how much this helps in
      // practice: a revalidation within a JPanel created here still seems to
      // interfere with smooth canvas animation (OS X 10.10/JDK 1.6).
      return new JPanel(layoutManager) {
         @Override
         public boolean isValidateRoot() {
            return true;
         }
      };
   }

   @MustCallOnEDT
   private JComponent buildTopControls() {
      JPanel panel = makeValidationRootJPanel(
            new MigLayout("insets 0, gap 1 1, fillx"));
      JPanel buttonPanel = new JPanel(new MigLayout("insets 0, gap 1 1"));

      fullScreenButton_ = new JButton();
      fullScreenButton_.setFont(GUIUtils.buttonFont);
      fullScreenButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setFullScreenMode(!isFullScreenMode());
         }
      });
      setFullScreenMode(isFullScreenMode()); // Sync button state
      buttonPanel.add(fullScreenButton_);

      zoomInButton_ = new JButton(
            IconLoader.getIcon("/org/micromanager/icons/zoom_in.png"));
      zoomInButton_.setToolTipText("Zoom in");
      zoomInButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomIn();
         }
      });
      zoomInButton_.setEnabled(false); // No canvas yet
      buttonPanel.add(zoomInButton_);

      zoomOutButton_ = new JButton(
            IconLoader.getIcon("/org/micromanager/icons/zoom_out.png"));
      zoomOutButton_.setToolTipText("Zoom out");
      zoomOutButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomOut();
         }
      });
      zoomOutButton_.setEnabled(false); // No canvas yet
      buttonPanel.add(zoomOutButton_);

      panel.add(buttonPanel, "split 3");
      panel.add(new JPanel(), "growx");
      panel.add(new JLabel("INFO HERE"), "wrap");

      return panel;
   }

   @MustCallOnEDT
   private JComponent buildBottomControls() {
      JPanel panel = makeValidationRootJPanel(
            new MigLayout("insets 0, gap 1 1, fillx"));

      pixelInfoLabel_ = new JLabel(" ");
      panel.add(pixelInfoLabel_, "split 3");
      panel.add(new JPanel(), "growx");
      fpsLabel_ = new JLabel(" ");
      panel.add(fpsLabel_, "wrap");

      scrollBarPanel_ = NDScrollBarPanel.create();
      scrollBarPanel_.addListener(this);
      panel.add(scrollBarPanel_, "growx, wrap");

      JPanel customControlsPanel =
            new JPanel(new MigLayout("insets 0, gap 1 0"));
      if (controlsFactory_ != null) {
         for (Component c : controlsFactory_.makeControls(displayController_)) {
            customControlsPanel.add(c);
         }
      }

      panel.add(customControlsPanel, "split 3");
      panel.add(new JPanel(), "growx");
      panel.add(new JLabel("[SAVE] [GEAR]"));

      return panel;
   }

   @MustCallOnEDT
   void expandDisplayedRangeToInclude(Collection<Coords> coords) {
      if (noImagesMessageLabel_ != null) {
         noImagesMessageLabel_.setText("Found Image(s); Preparing Display...");
      }

      for (Coords c : coords) {
         for (String axis : c.getAxes()) {
            int index = c.getIndex(axis);
            int axisIndex = displayedAxes_.indexOf(axis);
            if (axisIndex == -1) {
               displayedAxes_.add(axis);
               displayedAxisLengths_.add(index + 1);
            }
            else {
               int oldLength = displayedAxisLengths_.get(axisIndex);
               int newLength = Math.max(oldLength, index + 1);
               displayedAxisLengths_.set(axisIndex, newLength);
            }
         }
      }

      // TODO XXX Reorder scrollable axes to match axis order of data provider

      List<String> scrollableAxes = new ArrayList<String>();
      List<Integer> scrollableLengths = new ArrayList<Integer>();
      for (int i = 0; i < displayedAxes_.size(); ++i) {
         if (displayedAxisLengths_.get(i) > 1) {
            scrollableAxes.add(displayedAxes_.get(i));
            scrollableLengths.add(displayedAxisLengths_.get(i));
         }
      }
      scrollBarPanel_.setAxes(scrollableAxes);
      for (int i = 0; i < scrollableAxes.size(); ++i) {
         scrollBarPanel_.setAxisLength(scrollableAxes.get(i),
               scrollableLengths.get(i));
      }

      if (ijBridge_ != null) {
         ijBridge_.mm2ijEnsureDisplayAxisExtents();
      }
   }

   @MustCallOnEDT
   void displayImages(ImagesAndStats images) {
      displayedImages_ = images;

      setupDisplayUI();

      // A display request may come in ahead of an expand-range request, so
      // make sure to update our range first
      expandDisplayedRangeToInclude(getAllDisplayedCoords());

      for (String axis : scrollBarPanel_.getAxes()) {
         Coords c = images.getRequest().getImage(0).getCoords();
         if (c.hasAxis(axis)) {
            scrollBarPanel_.setAxisPosition(axis, c.getIndex(axis));
         }
      }

      // TODO Set LUT

      ijBridge_.mm2ijSetDisplayPosition(getMMPrincipalDisplayedCoords());
      repaintScheduledForNewImages_.set(true);
   }

   @MustCallOnEDT
   public void setVisible(boolean visible) {
      frame_.setVisible(visible);
      // TODO Full screen
   }

   @MustCallOnEDT
   public void toFront() {
      frame_.toFront();
      // TODO Full screen
      // TODO XXX Tell ImageJ
   }

   @MustCallOnEDT
   boolean isFullScreenMode() {
      return fullScreenFrame_ != null;
   }

   @MustCallOnEDT
   void setFullScreenMode(boolean fullScreen) {
      if (fullScreen) {
         if (!isFullScreenMode()) {
            frame_.setVisible(false);
            fullScreenFrame_ = makeFrame(true);
            fullScreenFrame_.add(contentPanel_);
            fullScreenFrame_.setVisible(true);
            fullScreenFrame_.addWindowListener(this);
         }
         fullScreenButton_.setText("Exit Full Screen");
         fullScreenButton_.setIcon(IconLoader.getIcon(
               "/org/micromanager/icons/windowed.png"));
         fullScreenButton_.setToolTipText("Exit full screen mode");
      }
      else {
         if (isFullScreenMode()) {
            fullScreenFrame_.removeWindowListener(this);
            fullScreenFrame_.setVisible(false);
            frame_.add(contentPanel_);
            contentPanel_.invalidate();
            frame_.validate();
            frame_.setVisible(true);
            fullScreenFrame_.dispose();
            fullScreenFrame_ = null;
         }
         fullScreenButton_.setText(null);
         fullScreenButton_.setIcon(IconLoader.getIcon(
               "/org/micromanager/icons/fullscreen.png"));
         fullScreenButton_.setToolTipText("View in full screen mode");
      }
   }

   public void zoomIn() {
      ijBridge_.mm2ijZoomIn();
   }

   public void zoomOut() {
      ijBridge_.mm2ijZoomOut();
   }

   public void canvasNeedsSwap() {
      canvasBorderPanel_.removeAll();
      canvasBorderPanel_.add(ijBridge_.getIJImageCanvas());
      if (isFullScreenMode()) {
         fullScreenFrame_.validate();
      }
      else {
         frame_.validate();
      }
   }

   public void uiDidSetZoom(double factor) {
      updateZoomUIState();
      // TODO Update in DisplayController's DisplaySettings
   }

   public void canvasDidChangeSize() {
      // TODO XXX The following should also execute when we detect that the
      // frame has been moved from one monitor to another

      // We can rely on our sanitized canvas to report the max image size as
      // its max size
      Dimension maxCanvasSize = ijBridge_.getIJImageCanvas().getMaximumSize();
      if (isFullScreenMode()) {
         Insets frameInsets = fullScreenFrame_.getInsets();
         final int MARGIN = 16;
         int newCanvasWidth = Math.min(maxCanvasSize.width,
               fullScreenFrame_.getWidth() -
                     frameInsets.left - frameInsets.right - MARGIN);
         int newCanvasHeight = Math.min(maxCanvasSize.height,
               canvasPanel_.getHeight() - MARGIN);
         ijBridge_.getIJImageCanvas().setPreferredSize(
               new Dimension(newCanvasWidth, newCanvasHeight));
         ijBridge_.getIJImageCanvas().invalidate();
         // Although we don't want to pack, it is essential to call validate
         // here, despite the call to invalidate; otherwise the AWT component
         // (canvas) is sporadically drawn incorrectly
         fullScreenFrame_.validate();
      }
      else {
         GraphicsConfiguration gConfig = frame_.getGraphicsConfiguration();
         Rectangle screenBounds = gConfig.getBounds();
         Insets screenInsets =
               Toolkit.getDefaultToolkit().getScreenInsets(gConfig);
         screenBounds.x += screenInsets.left;
         screenBounds.y += screenInsets.top;
         screenBounds.width -= screenInsets.left + screenInsets.right;
         screenBounds.height -= screenInsets.top + screenInsets.bottom;

         Insets frameInsets = frame_.getInsets();
         int newCanvasWidth = Math.min(maxCanvasSize.width,
               screenBounds.width - frameInsets.left - frameInsets.right -
                     2 * BORDER_THICKNESS);
         int newCanvasHeight = Math.min(maxCanvasSize.height,
               screenBounds.height - frameInsets.top - frameInsets.bottom -
                     2 * BORDER_THICKNESS -
                     topControlPanel_.getMinimumSize().height -
                     bottomControlPanel_.getMinimumSize().height);
         ijBridge_.getIJImageCanvas().setPreferredSize(
               new Dimension(newCanvasWidth, newCanvasHeight));
         ijBridge_.getIJImageCanvas().invalidate();

         frame_.pack(); // Includes validation

         // If we extended beyond bottom or right of the screen, move up/left
         int newFrameX = Math.min(frame_.getX(),
               screenBounds.x + screenBounds.width - frame_.getWidth());
         int newFrameY = Math.min(frame_.getY(),
               screenBounds.y + screenBounds.height - frame_.getHeight());
         frame_.setLocation(newFrameX, newFrameY);
      }

      ijBridge_.repaint();
   }

   private void updateZoomUIState() {
      if (zoomInButton_ == null || zoomOutButton_ == null) {
         return;
      }
      if (ijBridge_ == null) {
         return;
      }
      zoomInButton_.setEnabled(!ijBridge_.isIJZoomedAllTheWayIn());
      zoomOutButton_.setEnabled(!ijBridge_.isIJZoomedAllTheWayOut());
   }

   /**
    * Updates the pixel position and intensity indicator.
    *
    * If {@code imageLocation} is null or empty, the indicator is hidden. The
    * {@code imageLocation} parameter can be a rectangle containing more than
    * 1 pixel, for example if the point comes from a zoomed-out canvas.
    *
    * @param imageLocation the image coordinates of the pixel for which
    * information should be displayed
    */
   public void updatePixelInfoUI(Rectangle imageLocation) {
      // TODO We need to split this into a set-location call and an update-only
      // call, so that animation can update the intensity indicators
      if (imageLocation == null ||
            imageLocation.width == 0 || imageLocation.height == 0)
      {
         if (pixelInfoLabel_ != null) {
            pixelInfoLabel_.setText(" ");
         }
         // TODO Broadcast (to hide indicators in histograms)
         return;
      }

      // Perhaps we could compute the mean or median pixel info for the rect.
      // But for now we just use the center point.
      final Point center = new Point(imageLocation.x + imageLocation.width / 2,
            imageLocation.y + imageLocation.height / 2);

      // TODO Setting the label text interferes with canvas animation, despite
      // the label being in a different valudate root from the canvas. Fixing
      // the minimum and preferred size of the label did not appear to help.
      // There might be some hacks (like overriding revalidate or invalidate
      // somewhere) to get around this. Also should test on Windows.
      pixelInfoLabel_.setText(String.format("%d, %d", center.x, center.y));

      // TODO Show physical units and pixel intensity
      // TODO Broadcast (to show indicators in histograms)
   }

   public void paintDidFinish() {
      perfMon_.sampleTimeInterval("Repaint completed");

      // Paints occur both by our requesting a new image to be displayed and
      // for other reasons. To compute the frame rate, we want to count only
      // the former case.
      boolean countAsNewDisplayedImage =
            repaintScheduledForNewImages_.compareAndSet(true, false);
      if (countAsNewDisplayedImage) {
         // TODO XXX If we have been quiescent for a long time, we should
         // probably reset the interval estimator, at least for display
         // purposes. Or we should autoscale the history length for the
         // moving quantile so that the oldest samples are no older than some
         // threshold (2500 ms?)
         displayIntervalEstimator_.get().sample();
         showFPS();
      }
   }

   private void showFPS() {
      if (fpsDismissFuture_ != null) {
         if (fpsDismissFuture_.getDelay(TimeUnit.MILLISECONDS) >
               FPS_DISPLAY_DURATION_MS - FPS_DISPLAY_UPDATE_INTERVAL_MS)
         {
            return;
         }
         fpsDismissFuture_.cancel(true);
      }
      double medianIntervalMs =
            displayIntervalEstimator_.get().getQuantile(0.5);
      if (medianIntervalMs <= 1e-3) {
         return; // Not computed yet.
      }
      if (medianIntervalMs > 0.99 * FPS_DISPLAY_DURATION_MS) {
         return;
      }
      double displayFPS = 1000.0 / medianIntervalMs;
      fpsLabel_.setText(String.format("Display: %.2g fps", displayFPS));
      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("FPS indicator updated");
      }
      fpsDismissFuture_ = scheduledExecutor_.schedule(new Runnable() {
         @Override
         public void run() {
            SwingUtilities.invokeLater(new Runnable() {
               @Override
               public void run() {
                  fpsLabel_.setText(" ");
                  fpsDismissFuture_ = null;
               }
            });
         }
      }, FPS_DISPLAY_DURATION_MS, TimeUnit.MILLISECONDS);
   }

   public double getDisplayIntervalQuantile(double q) {
      return displayIntervalEstimator_.get().getQuantile(q);
   }

   public void resetDisplayIntervalEstimate() {
      displayIntervalEstimator_.set(TimeIntervalRunningQuantile.create(
            DISPLAY_INTERVAL_SMOOTH_N_SAMPLES));
   }


   //
   // Interface exposed for use by ImageJBridge and its associated objects,
   // presenting what ImageJ should think the current state of the display is.
   //

   public DisplayController getDisplayController() {
      return displayController_;
   }

   public boolean isAxisDisplayed(String axis) {
      return displayedAxes_.contains(axis);
   }

   public int getDisplayedAxisLength(String axis) {
      int axisIndex = displayedAxes_.indexOf(axis);
      if (axisIndex == -1) {
         return 0;
      }
      return displayedAxisLengths_.get(axisIndex);
   }

   public List<Coords> getAllDisplayedCoords() {
      if (displayedImages_ == null) {
         return Collections.emptyList();
      }

      List<Coords> ret = new ArrayList<Coords>();
      for (Image image : displayedImages_.getRequest().getImages()) {
         ret.add(image.getCoords());
      }
      return ret;
   }

   /**
    * Return the coords of selected channel among the displayed images.
    * @return coords of the selected channel
    */
   public Coords getMMPrincipalDisplayedCoords() {
      List<Coords> allCoords = getAllDisplayedCoords();
      if (allCoords.isEmpty()) {
         return null;
      }

      // Select the smallest channel for now
      // TODO XXX Reflect the channel selection in the UI
      int minChannel = Integer.MAX_VALUE;
      Coords coordsWithMinChannel = null;
      for (Coords c : allCoords) {
         if (c.hasAxis(Coords.CHANNEL)) {
            int ch = c.getChannel();
            if (ch < minChannel) {
               minChannel = ch;
               coordsWithMinChannel = c;
            }
         }
      }
      if (coordsWithMinChannel != null) {
         return coordsWithMinChannel;
      }
      // If channel axis is not used, return the first image (which, at the
      // time of this implementation, is expected to be the only image).
      return allCoords.get(0);
   }

   public int getImageWidth() {
      return displayController_.getDataProvider().getAnyImage().getWidth();
   }

   public int getImageHeight() {
      return displayController_.getDataProvider().getAnyImage().getHeight();
   }

   public List<Image> getDisplayedImages() {
      return displayedImages_.getRequest().getImages();
   }


   //
   // WindowListener for the standard and full-screen frames
   //

   @Override
   public void windowActivated(WindowEvent e) {
      // TODO Why do we need this? can we handle it without going to
      // display controller?
      DefaultDisplayManager.getInstance().raisedToTop(displayController_);

      // On Mac OS X, where the menu bar is not attached to windows, we need to
      // switch to ImageJ's menu bar when a viewer window has focus.
      if (JavaUtils.isMac()) {
         MenuBar ijMenuBar = Menus.getMenuBar();
         MenuBar curMenuBar = ((JFrame) e.getWindow()).getMenuBar();
         // Avoid call to setMenuBar() if our JFrame already has the right
         // menu bar (e.g. because we are switching between MM and IJ windows),
         // because setMenuBar() is very, very, slow in at least some Java
         // versions on OS X. See
         // http://imagej.1557.x6.nabble.com/java-8-and-OSX-td5016839.html and
         // links therein (although I note that the slowness is seen even with
         // Java 6 on Yosemite (10.10)).
         if (ijMenuBar != null && ijMenuBar != curMenuBar) {
            ((JFrame) e.getWindow()).setMenuBar(ijMenuBar);
         }
      }

      if (ijBridge_ != null) {
         ijBridge_.mm2ijWindowActivated();
      }
   }

   @Override
   public void windowDeactivated(WindowEvent e) {
   }

   @Override
   public void windowOpened(WindowEvent e) {
   }

   @Override
   public void windowClosing(WindowEvent e) {
      if (e.getWindow() == frame_) {
         displayController_.requestToClose();
      }
      else if (e.getWindow() == fullScreenFrame_) {
         setFullScreenMode(false);
      }
   }

   @Override
   public void windowClosed(WindowEvent e) {
      scheduledExecutor_.shutdownNow();
   }

   @Override
   public void windowIconified(WindowEvent e) {
   }

   @Override
   public void windowDeiconified(WindowEvent e) {
   }


   //
   // NDScrollBarPanel.Listener implementation
   //

   @Override
   public void scrollBarPanelHeightWillChange(NDScrollBarPanel panel,
         int currentHeight)
   {
      panel.setVisible(false);
   }

   @Override
   public void scrollBarPanelHeightDidChange(NDScrollBarPanel panel,
         int oldHeight, int newHeight)
   {
      if (isFullScreenMode()) {
         // Canvas height will auto-adjust
         panel.setVisible(true);
         fullScreenFrame_.validate();
      }
      else {
         // Adjust window height
         frame_.setSize(frame_.getWidth(),
               frame_.getHeight() - oldHeight + newHeight);
         panel.setVisible(true);
         frame_.validate();
         // TODO Move frame up if bottom beyond screen bottom (which means we
         // also need to shrink window if too tall for screen)
      }
   }

   @Override
   public void scrollBarPanelDidChangePositionInUI(NDScrollBarPanel panel) {
      // TODO We might want to leave out animated axes when setting the display
      // position based on user input. Also consider disabling scroll bars that
      // are being animated.
      Coords.CoordsBuilder builder = new DefaultCoords.Builder();
      for (String axis : panel.getAxes()) {
         builder.index(axis, panel.getAxisPosition(axis));
      }
      Coords position = builder.build();
      displayController_.setDisplayPosition(position, false);
   }
}
