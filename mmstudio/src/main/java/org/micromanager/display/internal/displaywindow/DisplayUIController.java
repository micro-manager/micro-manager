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

package org.micromanager.display.internal.displaywindow;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import ij.ImagePlus;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.RememberedSettings;
import org.micromanager.display.internal.animate.AnimationController;
import org.micromanager.display.internal.displaywindow.imagej.ImageJBridge;
import org.micromanager.display.internal.event.DisplayKeyPressEvent;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImageStats;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.events.LiveModeEvent;
import org.micromanager.display.DisplayWindowControlsFactory;
import org.micromanager.display.internal.DefaultComponentDisplaySettings;
import org.micromanager.display.internal.displaywindow.imagej.MMImageCanvas;
import org.micromanager.display.internal.event.DisplayMouseEvent;
import org.micromanager.display.internal.event.DisplayMouseWheelEvent;
import org.micromanager.display.internal.event.DataViewerMousePixelInfoChangedEvent;
import org.micromanager.display.internal.gearmenu.GearButton;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.Geometry;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.CoalescentEDTRunnablePool;
import org.micromanager.internal.utils.CoalescentEDTRunnablePool.CoalescentRunnable;
import org.micromanager.internal.utils.MustCallOnEDT;
import org.micromanager.internal.utils.PopupButton;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.internal.utils.performance.PerformanceMonitor;
import org.micromanager.internal.utils.performance.TimeIntervalRunningQuantile;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ColorMaps;

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
      MDScrollBarPanel.Listener
{
   private final Studio studio_;
   private final DisplayController displayController_;
   private final AnimationController animationController_;

   // All fields must only be accessed from the EDT unless otherwise noted.

   private final DisplayWindowControlsFactory controlsFactory_;

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
   private JLabel imageInfoLabel_;
   private JLabel newImageIndicator_;
   private JLabel fpsLabel_;
   private JLabel cameraFpsLabel_;
   private JLabel infoLabel_;
   private PopupButton playbackFpsButton_;
   private JSpinner playbackFpsSpinner_;
   private MDScrollBarPanel scrollBarPanel_;

   // Subcomponents of the N-dimensional scroll bar panel
   // We need to look up in both directions, and don't need the efficiency of
   // a map for the small number of elements, so use lists of pairs.
   private final List<Map.Entry<String, JToggleButton>> axisAnimationButtons_ =
         new ArrayList<>();
   private final List<Map.Entry<String, PopupButton>> axisPositionButtons_ =
         new ArrayList<>();
   private final List<Map.Entry<String, PopupButton>> axisLinkButtons_ =
         new ArrayList<>();
   private final List<Map.Entry<String, JButton>> axisLockButtons_ = 
           new ArrayList<>();

   private static final Icon PLAY_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/play.png");
   private static final Icon PAUSE_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/pause.png");
   private static final Icon UNLOCKED_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/lock_open.png");
   // TODO: Make Icons same size so that they are not moving around when changing
   private static final Icon BLACK_LOCKED_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/lock_locked.png");
   private static final Icon RED_LOCKED_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/lock_super.png");
   
   private final Insets buttonInsets_ = new Insets(0, 5, 0, 5);

   private ImageJBridge ijBridge_;

   // Data display state of the UI (which may lag behind the display
   // controller's notion of what's current)
   private final List<String> displayedAxes_ = new ArrayList<>();
   private final List<Integer> displayedAxisLengths_ = new ArrayList<>();
   private ImagesAndStats displayedImages_;
   private Double cachedPixelSize_ = -1.0;
   private boolean isPreview_ = false;

   private BoundsRectAndMask lastSeenSelection_;

   private Rectangle mouseLocationOnImage_; // null if mouse outside of canvas

   private long lastAnimationIntervalAdjustmentNs_;

   private final ScheduledExecutorService scheduledExecutor_ =
         Executors.newSingleThreadScheduledExecutor(ThreadFactoryFactory.
               createThreadFactory("DisplayUIController"));

   // Display rate estimation
   private static final int DISPLAY_INTERVAL_SMOOTH_N_SAMPLES = 50;
   private static final int FPS_DISPLAY_DURATION_MS = 500;
   private static final int FPS_DISPLAY_UPDATE_INTERVAL_MS = 250;
   private final AtomicBoolean repaintScheduledForNewImages_ =
         new AtomicBoolean(false);
   private final AtomicReference<TimeIntervalRunningQuantile>
         displayIntervalEstimator_ =
         new AtomicReference<>( TimeIntervalRunningQuantile.create(
                     DISPLAY_INTERVAL_SMOOTH_N_SAMPLES) );
   private long fpsDisplayedTimeNs_;

   private final CoalescentEDTRunnablePool runnablePool_ =
         CoalescentEDTRunnablePool.create();

   private PerformanceMonitor perfMon_;
   
   private double startTime_ = 0.0; // Elapsed Time for frame #0
   private long nrLiveFramesReceived_ = 0;
   private long lastImageNumber_ = 0;

   private static final int MIN_CANVAS_HEIGHT = 100;
   private static final int BORDER_THICKNESS = 2;


   @MustCallOnEDT
   static DisplayUIController create(Studio studio, 
         DisplayController parent,
         DisplayWindowControlsFactory controlsFactory,
         AnimationController animationController)
   {
      DisplayUIController instance = new DisplayUIController(studio, parent,
            controlsFactory, animationController);
      parent.registerForEvents(instance);
      studio.events().registerForEvents(instance);
      instance.frame_.addWindowListener(instance);
      return instance;
   }

   @MustCallOnEDT
   private DisplayUIController(Studio studio, 
         DisplayController parent,
         DisplayWindowControlsFactory controlsFactory,
         AnimationController animationController)
   {
      studio_ = studio;
      displayController_ = parent;
      animationController_ = animationController;
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
         frame = new MMFrame("image display window", false);
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
      setTitle(frame);
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
   
   /**
    * Not ideal, but the Image Exporter needs access to the canvas to 
    * grab images
    * 
    * @return 
    */
   public MMImageCanvas getIJImageCanvas() {
      return ijBridge_.getIJImageCanvas();
   }

   @MustCallOnEDT
   private JPanel buildInitialUI() {
      JPanel contentPanel = new JPanel();
      contentPanel.setLayout(new MigLayout("insets 1, gap 1 1, fill",
            "[grow, fill]",
            "[] 0 [grow, fill] related []"));

      canvasPanel_ = new JPanel(new MigLayout("insets 0, fill"));
      noImagesMessageLabel_ = new JLabel("Waiting for Image...");
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
   private void setupDisplayUI(ImagesAndStats images) {
      if (ijBridge_ != null) {
         return;
      }

      ijBridge_ = ImageJBridge.create(this, images);
      Double zoomRatio = getDisplayController().getDisplaySettings().getZoomRatio();
      if (zoomRatio <= 0) {
         zoomRatio = 1.0;
      }
      ijBridge_.mm2ijSetZoom(zoomRatio);

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
      fullScreenButton_.addActionListener((ActionEvent e) -> {
         setFullScreenMode(!isFullScreenMode());
      });
      setFullScreenMode(isFullScreenMode()); // Sync button state
      buttonPanel.add(fullScreenButton_);

      zoomInButton_ = new JButton(
            IconLoader.getIcon("/org/micromanager/icons/zoom_in.png"));
      zoomInButton_.setToolTipText("Zoom in");
      zoomInButton_.addActionListener((ActionEvent e) -> {
         zoomIn();
      });
      zoomInButton_.setEnabled(false); // No canvas yet
      buttonPanel.add(zoomInButton_);

      zoomOutButton_ = new JButton(
            IconLoader.getIcon("/org/micromanager/icons/zoom_out.png"));
      zoomOutButton_.setToolTipText("Zoom out");
      zoomOutButton_.addActionListener((ActionEvent e) -> {
         zoomOut();
      });
      zoomOutButton_.setEnabled(false); // No canvas yet
      buttonPanel.add(zoomOutButton_);

      panel.add(buttonPanel, "split 3");
      panel.add(new JPanel(), "growx");

      infoLabel_ = new JLabel("No Image yet");
      panel.add(infoLabel_, "wrap");
      
      return panel;
   }

   @MustCallOnEDT
   private JComponent buildBottomControls() {
      JPanel panel = makeValidationRootJPanel(
            new MigLayout(new LC().insets("1").gridGap("0", "0").fillX()));

      
      pixelInfoLabel_ = new JLabel(" ");
      pixelInfoLabel_.setFont(pixelInfoLabel_.getFont().deriveFont(10.0f));
      pixelInfoLabel_.setMinimumSize(new Dimension(0, 10));
      panel.add(pixelInfoLabel_, new CC().split(3));
      imageInfoLabel_ = new JLabel("Image Info here");
      imageInfoLabel_.setFont(pixelInfoLabel_.getFont().deriveFont(10.0f));
      panel.add(imageInfoLabel_, new CC().growX());
          
      SpinnerModel fpsModel = new FpsSpinnerNumberModel(10.0, 1.0, 1000.0);
      playbackFpsSpinner_ = new JSpinner(fpsModel);
      playbackFpsSpinner_.addChangeListener((ChangeEvent e) -> {
         handlePlaybackFpsSpinner(e);
      });
      playbackFpsButton_ = PopupButton.create("", playbackFpsSpinner_);
      playbackFpsButton_.setFont(playbackFpsButton_.getFont().deriveFont(10.0f));
      int width = 24 + playbackFpsButton_.getFontMetrics(
            playbackFpsButton_.getFont()).stringWidth("Playback: 9999.0 fps");
      Dimension fpsButtonSize = new Dimension(width,
            pixelInfoLabel_.getPreferredSize().height + 12);
      playbackFpsButton_.setMinimumSize(fpsButtonSize);
      playbackFpsButton_.setMaximumSize(fpsButtonSize);
      playbackFpsButton_.setPreferredSize(fpsButtonSize);
      playbackFpsButton_.setMargin(buttonInsets_);
      playbackFpsButton_.addPopupButtonListener((PopupButton button) -> {
         playbackFpsSpinner_.setValue(displayController_.getPlaybackSpeedFps());
      });
      setPlaybackFpsIndicator(displayController_.getPlaybackSpeedFps());
      playbackFpsButton_.setVisible(false);
      panel.add(playbackFpsButton_, new CC().hideMode(2).wrap());

      MDScrollBarPanel.ControlsFactory leftFactory =
            (String axis, int height) -> makeScrollBarLeftControls(axis, height);
      MDScrollBarPanel.ControlsFactory rightFactory =
            (String axis, int height) -> makeScrollBarRightControls(axis, height);
      scrollBarPanel_ = MDScrollBarPanel.create(leftFactory, rightFactory);
      scrollBarPanel_.addListener(this);
      panel.add(scrollBarPanel_, new CC().growX().pushX().split(2).wrap());

      
      JPanel customControlsPanel =
            new JPanel(new MigLayout(new LC().insets("0").gridGap("1", "0")));
      if (controlsFactory_ != null) {
         for (Component c : controlsFactory_.makeControls(displayController_)) {
            customControlsPanel.add(c);
         }
      }

      panel.add(customControlsPanel, new CC().split());
      
      JPanel tmp2Panel = new JPanel();
      newImageIndicator_ = new JLabel("NEW IMAGE");
      newImageIndicator_.setFont(newImageIndicator_.getFont().
            deriveFont(10.0f).deriveFont(Font.BOLD));
      newImageIndicator_.setVisible(false);
      tmp2Panel.add(newImageIndicator_, new CC().hideMode(2));
      fpsLabel_ = new JLabel(" ");
      fpsLabel_.setFont(fpsLabel_.getFont().deriveFont(10.0f));
      tmp2Panel.add(fpsLabel_, new CC());
      cameraFpsLabel_ = new JLabel(" ");
      cameraFpsLabel_.setFont(fpsLabel_.getFont().deriveFont(10.0f));
      tmp2Panel.add(cameraFpsLabel_, new CC());
      panel.add(tmp2Panel, new CC().growX());
      
      panel.add(new SaveButton(studio_, displayController_));
      panel.add(new GearButton(displayController_, studio_));
      
      // automatic calculation of minimum size of bottom panel
      // can be misleading because no minimum size for the scrollbars is included.
      // So, help out a bit by setting a reasonable minimum
      panel.setMinimumSize(new Dimension(345, 10));
      
      return panel;
   }

   @MustCallOnEDT
   private JComponent makeScrollBarLeftControls(final String axis, final int height) {
      JPanel ret = new JPanel(new MigLayout(
            new LC().insets("0").gridGap("0", "0").fillX()));

      JToggleButton animateButton = null;
      for (Map.Entry<String, JToggleButton> e : axisAnimationButtons_) {
         if (axis.equals(e.getKey())) {
            animateButton = e.getValue();
            break;
         }
      }
      if (animateButton == null) {
         animateButton = new JToggleButton(axis.substring(0, 1));
         animateButton.setFont(animateButton.getFont().deriveFont(10.0f));
         int fontWidth = animateButton .getFontMetrics(animateButton .getFont()).
               stringWidth("z");
         int offset = 28;
         if (JavaUtils.isWindows()) {
            offset = 22;
            animateButton.setBorderPainted(false);
         }
         int width = offset + PLAY_ICON.getIconWidth() + fontWidth;
         Dimension size = new Dimension(width, height);
         animateButton.setMinimumSize(size);
         animateButton.setMaximumSize(size);
         animateButton.setPreferredSize(size);
         animateButton.setHorizontalAlignment(SwingConstants.LEFT);
         animateButton.setHorizontalTextPosition(SwingConstants.RIGHT);
         animateButton.setIcon(PLAY_ICON);
         animateButton.setSelectedIcon(PAUSE_ICON);
         animateButton.setMargin(buttonInsets_);
         animateButton.addActionListener((ActionEvent e) -> {
            handleAxisAnimateButton(e);
         });
         axisAnimationButtons_.add(new AbstractMap.SimpleEntry(
               axis, animateButton));
      }
      ret.add(animateButton, new CC());

      PopupButton positionButton = null;
      for (Map.Entry<String, PopupButton> e : axisPositionButtons_) {
         if (axis.equals(e.getKey())) {
            positionButton = e.getValue();
            break;
         }
      }
      if (positionButton == null) {

         SpinnerModel axisModel = new SpinnerNumberModel(1, 1, 1, 1);
         final JSpinner axisSpinner = new JSpinner(axisModel);
         axisSpinner.addChangeListener((ChangeEvent e) -> {
            handleAxisSpinner(axis, axisSpinner, e);
         });
         axisSpinner.setFont(axisSpinner.getFont().deriveFont(10.0f));
         // setting a minimum size on the spinner does not seem to have any effects
         positionButton = PopupButton.create("", axisSpinner);
         positionButton.setFont(positionButton.getFont().deriveFont(10.0f));
         int offset = 0;
         if (JavaUtils.isWindows()) {
            offset = 8;
            positionButton.setBorderPainted(false);
         }
         int width = offset + positionButton.getFontMetrics(positionButton .getFont()).
               stringWidth("99999/99999");
         positionButton.setHorizontalAlignment(SwingConstants.RIGHT);
         Dimension size = new Dimension(width, height);
         positionButton.setMinimumSize(size);
         positionButton.setMaximumSize(size);
         positionButton.setPreferredSize(size);
         positionButton.setMargin(buttonInsets_);
         axisPositionButtons_.add(new AbstractMap.SimpleEntry(
               axis, positionButton));
      }
      ret.add(positionButton, new CC());

      return ret;
   }

   @MustCallOnEDT
   private JComponent makeScrollBarRightControls(final String axis, int height) {
      JPanel ret = new JPanel(new MigLayout(
            new LC().insets("0").gridGap("0", "0").fillX()));

      PopupButton linkButton = null;
      for (Map.Entry<String, PopupButton> e : axisLinkButtons_) {
         if (axis.equals(e.getKey())) {
            linkButton = e.getValue();
            break;
         }
      }
      if (linkButton == null) {
         final AxisLinker linker = AxisLinker.create(
               displayController_.getLinkManager(),
               displayController_, axis);
         final JPopupMenu linkPopup = new JPopupMenu();
         linkButton = PopupButton.create(IconLoader.getIcon(
                 "/org/micromanager/icons/linkflat.png"), linkPopup);
         linkButton.addPopupButtonListener((PopupButton button) -> {
            linker.updatePopupMenu(linkPopup);
         });
         linkButton.setDisabledIcon(IconLoader.getDisabledIcon(linkButton.getIcon()));
         Dimension size = new Dimension(3 * height / 2, height);
         linkButton.setMinimumSize(size);
         linkButton.setMaximumSize(size);
         linkButton.setPreferredSize(size);
         axisLinkButtons_.add(new AbstractMap.SimpleEntry<>(axis, linkButton));
      }
      ret.add(linkButton, new CC());
      
      JButton lockButton = null;
      for (Map.Entry<String, JButton> e : axisLockButtons_) {
         if (axis.equals(e.getKey())) {
            lockButton = e.getValue();
            break;
         }
      }
      if (lockButton == null) {

         lockButton = new JButton();
         Dimension size = new Dimension(UNLOCKED_ICON.getIconWidth() + 6, MDScrollBarPanel.ROW_HEIGHT);
         lockButton.setMinimumSize(size);
         lockButton.setPreferredSize(size);
         lockButton.setIcon(UNLOCKED_ICON);
         lockButton.addActionListener((ActionEvent e) -> {
            handleLockButton(axis, e);
         });
         // TODO: Right-click menu
         // lockButton.setComponentPopupMenu(new JPopupMenu());
         axisLockButtons_.add(new AbstractMap.SimpleEntry<>(
               axis, lockButton));
      }
      
      ret.add(lockButton, new CC());
      
      return ret;
   }

   @MustCallOnEDT
   void expandDisplayedRangeToInclude(Coords... coords) {
      expandDisplayedRangeToInclude(Arrays.asList(coords));
   }

   @MustCallOnEDT
   void expandDisplayedRangeToInclude(Collection<Coords> coords) {
      if (noImagesMessageLabel_ != null) {
         noImagesMessageLabel_.setText("Preparing to Display...");
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

      List<String> scrollableAxes = new ArrayList<>();
      Map<String, Integer> scrollableLengths = new HashMap<>();
      for (int i = 0; i < displayedAxes_.size(); ++i) {
         if (displayedAxisLengths_.get(i) > 1) {
            scrollableAxes.add(displayedAxes_.get(i));
            scrollableLengths.put(displayedAxes_.get(i), displayedAxisLengths_.get(i));
         }
      }
      // Reorder scrollable axes to match axis order of data provider
      Collections.sort(scrollableAxes, (String o1, String o2) -> {
         if (o1.equals(o2)) {
            return 0;
         }
         List<String> ordered = displayController_.getOrderedAxes();
         Map<String, Integer> axisMap = new HashMap<>(ordered.size());
         for (int i = 0; i < ordered.size(); i++) {
            axisMap.put(ordered.get(i), i);
         }
         if (axisMap.containsKey(o1) && axisMap.containsKey(o2)) {
            return axisMap.get(o1) > axisMap.get(o2) ? 1 : -1;
         }
         return 0; // Ugly, TODO: Report?
      });
      scrollBarPanel_.setAxes(scrollableAxes);
      for (int i = 0; i < scrollableAxes.size(); ++i) {
         final String currentAxis = scrollableAxes.get(i);
         scrollBarPanel_.setAxisLength(currentAxis, 
                 scrollableLengths.get(currentAxis));
         updateAxisPositionIndicator(currentAxis, -1, 
                 scrollableLengths.get(currentAxis));
      }

      playbackFpsButton_.setVisible(!scrollableAxes.isEmpty());

      if (ijBridge_ != null) {
         ijBridge_.mm2ijEnsureDisplayAxisExtents();
      }
   }

   @MustCallOnEDT
   void displayImages(ImagesAndStats images) {
      boolean firstTime = false;
      if (ijBridge_ == null) {
         firstTime = true;
         setupDisplayUI(images);  // creates ijBridge amongst other things
      }

      displayedImages_ = images;
      Coords nominalCoords = images.getRequest().getNominalCoords();

      // A display request may come in ahead of an expand-range request, so
      // make sure to update our range first
      expandDisplayedRangeToInclude(nominalCoords);
      expandDisplayedRangeToInclude(getAllDisplayedCoords());

      updateSliders(images);
      
      if (firstTime) {
         // We need to set the displaySettings after the ijBridge was created
         // (in the setupDisplayUI function), and after the display range
         // has been expanded to include all Coords in the "images"
         // If we do not do so, only one channel will be shown
         // If we call applyDisplaySettings every time, the Display fps 
         // will never be shown
         applyDisplaySettings(displayController_.getDisplaySettings());
      }
      
      // info label: The only aspect that can change is pixel size.  To avoid
      // redrawing the info line (which may be expensive), check if pixelsize
      // changed (which can happen for the snap/live window) and only redraw the 
      // info label if it changed.
      if (!cachedPixelSize_.equals(images.getRequest().getImage(0).getMetadata().
              getPixelSizeUm())) {
         infoLabel_.setText(this.getInfoString(images));
         ijBridge_.mm2ijSetMetadata();
         cachedPixelSize_ = images.getRequest().getImage(0).getMetadata().
                 getPixelSizeUm();
      } 
      
      ijBridge_.mm2ijSetDisplayPosition(nominalCoords);
      applyAutostretch(images, displayController_.getDisplaySettings());

      if (mouseLocationOnImage_ != null) {
         updatePixelInformation(); // TODO Can skip if identical images
      }
      
      imageInfoLabel_.setText(getImageInfoLabel(images));
      
      repaintScheduledForNewImages_.set(true);
   }

   void setImageInfoLabel(ImagesAndStats images) {
      imageInfoLabel_.setText(getImageInfoLabel(images));
   }

   void updateSliders(ImagesAndStats images) {
      Coords nominalCoords = images.getRequest().getNominalCoords();
      for (String axis : scrollBarPanel_.getAxes()) {
         if (nominalCoords.hasAxis(axis)) {
            scrollBarPanel_.setAxisPosition(axis, nominalCoords.getIndex(axis));
            updateAxisPositionIndicator(axis, nominalCoords.getIndex(axis), -1);
         }
      }
   }

   /**
    * Creates the string in the bottom of the viewer showing the time, z-height,
    * channel and position the image shown was taken
    * 
    * @param images
    * @return 
    */
   private String getImageInfoLabel(ImagesAndStats images) {
      StringBuilder sb = new StringBuilder();
      // feeble and ugly way of getting the correct metadata
      Coords nominalCoords = images.getRequest().getNominalCoords();
      Metadata metadata = null;
      if (nominalCoords.hasC()) {
         for (Image image : images.getRequest().getImages()) {
            if (image.getCoords().getC() == nominalCoords.getC()) {
               metadata = image.getMetadata();
            }
         }
      } else {
         metadata = images.getRequest().getImages().get(0).getMetadata();
      }
      if (metadata == null) {
         return "No Image";
      }
      for (int i = 0; i < displayedAxes_.size(); ++i) {
         if (displayedAxisLengths_.get(i) > 1) {
            switch (displayedAxes_.get(i)) {
               case Coords.P:
                  String positionName = metadata.getPositionName("");
                  sb.append(positionName).append(" ");
                  break;
               case Coords.T:
                  double elapsedTimeMs = metadata.getElapsedTimeMs(-1.0);
                  if (elapsedTimeMs < 0) {
                     sb.append(" t=").append(nominalCoords.getT()).append(" ");
                  } else if (elapsedTimeMs > 3600000) {
                     int hrs = (int) (elapsedTimeMs / 3600000);
                     sb.append(NumberUtils.doubleToDisplayString(hrs, 0)).
                            append(":").
                            append(NumberUtils.doubleToDisplayString(
                                     (elapsedTimeMs % (hrs * 3600000)) / 60000.0, 0)).
                             append("hr ");
                  } else if (elapsedTimeMs > 60000) {
                     int mins = (int) (elapsedTimeMs / 60000); 
                     sb.append(NumberUtils.doubleToDisplayString(mins, 0)).
                             append(":").
                             append(NumberUtils.doubleToDisplayString(
                                     (elapsedTimeMs % (mins * 60000)) / 1000.0, 0)).
                             append("min ");
                  } else if (elapsedTimeMs > 10000) {
                     sb.append(NumberUtils.doubleToDisplayString(
                             (elapsedTimeMs / 1000), 1)).append("s ");
                  } else {
                     sb.append(elapsedTimeMs).append("ms ");
                  }  break;
               case Coords.Z:
                  Double zPositionUm = metadata.getZPositionUm();
                  if (zPositionUm != null) {
                     sb.append(NumberUtils.doubleToDisplayString(zPositionUm, 2)).
                             append("um ");
                  }
                  break;
               case Coords.C:
                  int channelIndex = nominalCoords.getC();
                  sb.append(displayController_.
                          getChannelName(nominalCoords.getC())).append(" ");
                  break;
               default:
                  break;
            }
         }
      }
      
      return sb.toString();
   }
   
   @MustCallOnEDT
   public void applyDisplaySettings(DisplaySettings settings) {
      // Note: This applies to color settings, zoom and playback fps
      // Note that this function will be called every time the 
      // uiController_.setDisplaySettings function is called, so make sure that
      // function will not be called from within here, as an infinite loop will
      // ensue.

      if (ijBridge_ == null) {
         return;
      }

      int nChannels = ijBridge_.getIJNumberOfChannels();
      boolean autostretch = settings.isAutostretchEnabled()
            && displayedImages_ != null;
      
      // NS 2018-02-05: RGB display is quite broken, and it is unclear to me
      // what the intentions are.  For now, at least get images to display, and
      // someone with more interest in RGB can try to fix things.
      
      boolean isRGB = ijBridge_.isIJRGB();

      if (!isRGB) {
         switch (settings.getColorMode()) {
            case COLOR:
               if (!ijBridge_.isIJColorModeColor()) {
                  ijBridge_.mm2ijSetColorModeColor(settings.getAllChannelColors());
               }
               break;
            case COMPOSITE:
               if (!ijBridge_.isIJColorModeComposite()) {
                  ijBridge_.mm2ijSetColorModeComposite(settings.getAllChannelColors());
               }
               break;
            case GRAYSCALE:
               if (!ijBridge_.isIJColorModeGrayscale()) {
                  ijBridge_.mm2ijSetColorModeGrayscale();
               }
               ijBridge_.mm2ijSetHighlightSaturatedPixels(false);
               break;
            case HIGHLIGHT_LIMITS:
               if (!ijBridge_.isIJColorModeGrayscale()) {
                  ijBridge_.mm2ijSetColorModeGrayscale();
               }
               ijBridge_.mm2ijSetHighlightSaturatedPixels(true);
               break;
            case FIRE:
               ijBridge_.mm2ijSetColorModeLUT(ColorMaps.fireColorMap());
               break;
            case RED_HOT:
               ijBridge_.mm2ijSetColorModeLUT(ColorMaps.redHotColorMap());
               break;
            case SPECTRUM:
            default:
               ijBridge_.mm2ijSetColorModeGrayscale(); // Fallback
               break;
         }

         for (int i = 0; i < nChannels; ++i) {
            ChannelDisplaySettings channelSettings
                    = settings.getChannelSettings(i);
            // Update RememberedSettings for this channel.
            // We update color only, but unfortunately, we get called here
            // also when other things change
            // TODO: should all channeldisplaysetting changes be remembered?
            ChannelDisplaySettings rememberedSettings =
                    RememberedSettings.loadChannel(studio_,
                            channelSettings.getGroupName(), channelSettings.getName());
            RememberedSettings.storeChannel(studio_, channelSettings.getGroupName(), channelSettings.getName(),
                    rememberedSettings.copyBuilder().color(channelSettings.getColor()).build());

            ComponentDisplaySettings componentSettings =
                  channelSettings.getComponentSettings(0);
            // TODO: Remember changes in component display settings?
            ijBridge_.mm2ijSetChannelColor(i, channelSettings.getColor());
            if (!autostretch) {
               int max = Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                     componentSettings.getScalingMaximum()));
               int min = (int) Math.min(max - 1,
                     componentSettings.getScalingMinimum());
               ijBridge_.mm2ijSetIntensityScaling(i, min, max);
            }
            double gamma = componentSettings.getScalingGamma();
            ijBridge_.mm2ijSetIntensityGamma(i, gamma);
            if (settings.getColorMode() == DisplaySettings.ColorMode.COMPOSITE) {
               ijBridge_.mm2ijSetVisibleChannels(i, channelSettings.isVisible());
            }
         }
         if (autostretch) {
            applyAutostretch(displayedImages_, settings);
         }
      }
      else {
         for (int chNr = 0; chNr < nChannels; chNr++) {
            /**
             * Note: Since the UI currently manipulates all components
             * identically, and setting a component results in many calculations
             * and redrawing the image, for performance reasons we only set one
             * component. Setting components differently in a performant way
             * will need a bit of re-architecting
             */
            // int nComponents = settings.getChannelSettings(0).getNumberOfComponents();
            // for (int i = 0; i < nComponents; ++i) {
            int i = 0;
            ComponentDisplaySettings componentSettings
                    = settings.getChannelSettings(0).getComponentSettings(i);
            int max = Math.min(Integer.MAX_VALUE,
                    (int) componentSettings.getScalingMaximum());
            int min = Math.max(1, (Math.min(max - 1,
                    (int) componentSettings.getScalingMinimum())));
            max = Math.max(min + 1, max);
            ijBridge_.mm2ijSetIntensityScaling(i, min, max);
            //}
         }
      }
      
      ijBridge_.mm2ijSetZoom(settings.getZoomRatio());
      
      displayController_.setPlaybackSpeedFps(settings.getPlaybackFPS());
      
   }

   @MustCallOnEDT
   private void applyAutostretch(ImagesAndStats images, DisplaySettings settings) {
      if (images == null || !settings.isAutostretchEnabled()) {
         return;
      }

      // TODO RGB
      // TODO "uniform" scaling

      int nChannels = ijBridge_.getIJNumberOfChannels();
      double q = settings.getAutoscaleIgnoredQuantile();
      for (int i = 0; i < nChannels; ++i) {
         int statsIndex = 0;
         for (int j = 0; j < images.getRequest().getNumberOfImages(); ++j) {
            Coords c = images.getRequest().getImage(j).getCoords();
            if (c.hasAxis(Coords.CHANNEL)) {
               if (c.getChannel() == i) {
                  statsIndex = j;
               }
            }
         }

         if (images.getResult().size() > statsIndex) {
         ImageStats stats = images.getResult().get(statsIndex);
         long min = stats.getComponentStats(0).getAutoscaleMinForQuantile(q);
         long max = Math.min(Integer.MAX_VALUE,
               stats.getComponentStats(0).getAutoscaleMaxForQuantile(q));
         // NS 2019-05-29: This should not be done here, but in IntegerComponentsStats
         // however, I do not understand that code enough to touch it....
         // This at least fixes the display somewhat (showing black for 
         // a saturated image is really, really bad!)
         if (min == max) {
            if (max == 0) {
               max++;
            } else {
               min--;
            }
         }
         // NS 2019-08-15: We really do need to write the min and max to 
         // the DisplaySettings (there already is a work-around in the 
         // IntensityInspectorPanelController handleAutostretch function, but 
         // the min and max value in the DisplaySettings should be these ones
         // at any point in time, and not only when the Autostretch checkbox
         // is checked.
         // I know that the correct way is to construct a complete new DisplaySettings
         // object with completely new ComnponentDisplaySettings, but it seems 
         // more than a little bit excessive to do that on every autostrech update
         // so we take the shortcut here
         DefaultComponentDisplaySettings dcds = (DefaultComponentDisplaySettings) 
                 settings.getChannelSettings(i).getComponentSettings(0);
         dcds.setScalingMinimum(min);
         dcds.setScalingMaximum(max);
         ijBridge_.mm2ijSetIntensityScaling(i, (int) min, (int) max);
         } else {
            ReportingUtils.logError("DisplayUICOntroller: Received request to " +
                    "autostretch image for which no statistics are available");
         }
      }
   }

   @MustCallOnEDT
   public void overlaysChanged() {
      if (ijBridge_ == null) {
         return;
      }
      ijBridge_.mm2ijRepaint();
   }

   void setPlaybackFpsIndicator(double fps) {
      playbackFpsButton_.setText(String.format("Playback: %.1f fps", fps));
   }

   void setNewImageIndicator(boolean show) {
      // NS: I am not sure what this means to the user in the snap/live window,
      // and it takes up space, so don't show in preview windows
      newImageIndicator_.setVisible(show && !isPreview_);
   }

   private void updateAxisPositionIndicator(final String axis, 
           final int position, final int length) {
      int checkedLength = length;
      if (checkedLength < 0) {
         int axisIndex = displayedAxes_.indexOf(axis);
         if (axisIndex < 0) {
            return;
         }
         checkedLength = displayedAxisLengths_.get(axisIndex);
      }
      if (checkedLength <= 1) {
         return; // Not displayed
      }
      int checkedPosition = position;
      if (checkedPosition < 0) {
         checkedPosition = scrollBarPanel_.getAxisPosition(axis);
      }
      for (Map.Entry<String, PopupButton> e : axisPositionButtons_) {
         if (axis.equals(e.getKey())) {
            e.getValue().setText(String.format("% 5d/% 5d",
                    checkedPosition + 1, checkedLength));
            if (!animationController_.isAnimating()) {
               JComponent popup = e.getValue().getPopupComponent();
               if (popup instanceof JSpinner) {
                  JSpinner js = (JSpinner) popup;
                  js.setModel(new SpinnerNumberModel(checkedPosition + 1, 1, checkedLength, 1));
               }
            }
            break;
         }

      }
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
   

   public void updateTitle() {
      if (frame_ != null) {
         
            runnablePool_.invokeAsLateAsPossibleWithCoalescence(new CoalescentRunnable() {
               @Override
               public Class<?> getCoalescenceClass() {
                  return getClass();
               }

               @Override
               public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
                  return later;
               }

               @Override
               public void run() {
                  setTitle(frame_);
               }
            });
      }
   }

   /**
    * Actually sets the title to this Display
    */
   @MustCallOnEDT
   private void setTitle(JFrame frame) {
      StringBuilder sb = new StringBuilder();
      sb.append(displayController_.getName());
      isPreview_ = isPreview(displayController_.getName());
      if (ijBridge_ != null) {
              sb.append(" (").
              append(NumberUtils.doubleToDisplayString(ijBridge_.getIJZoom() * 100)).
              append("%)");
      } else {
         sb.append(" (100%)");
      }
      // TODO: add save status, and listen for changes
      frame.setTitle(sb.toString());
   }
   
   private boolean isPreview(String title) {
      return  title.startsWith("Preview");
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

   /**
    * Callback for the ImageJ code.  Do not call directly.
    * Used to update the Micro-Manager code of the new zoom factor.
    * 
    * @param factor Newly set Zoom factor.
    */
   public void uiDidSetZoom(double factor) {
      updateZoomUIState();
      displayController_.setDisplaySettings(displayController_.getDisplaySettings().
              copyBuilder().zoomRatio(factor).build());
      updateTitle();
      canvasDidChangeSize();
   }

   public void canvasDidChangeSize() {
      // TODO XXX The following should also execute when we detect that the
      // frame has been moved from one monitor to another

      // We can rely on our sanitized canvas to report the max image size as
      // its max size
      Dimension canvasMaxSize = ijBridge_.getIJImageCanvas().getMaximumSize();
      if (isFullScreenMode()) {
         Insets frameInsets = fullScreenFrame_.getInsets();
         final int MARGIN = 16;
         int newCanvasWidth = Math.min(canvasMaxSize.width,
               fullScreenFrame_.getWidth() -
                     frameInsets.left - frameInsets.right - MARGIN);
         int newCanvasHeight = Math.min(canvasMaxSize.height,
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
         Rectangle screenBounds = Geometry.insettedRectangle(
               gConfig.getBounds(),
               Toolkit.getDefaultToolkit().getScreenInsets(gConfig));

         Insets frameInsets = frame_.getInsets();
         int newCanvasWidth = Math.min(canvasMaxSize.width,
               screenBounds.width - frameInsets.left - frameInsets.right -
                     2 * BORDER_THICKNESS);
         int newCanvasHeight = Math.min(canvasMaxSize.height,
               screenBounds.height - frameInsets.top - frameInsets.bottom -
                     2 * BORDER_THICKNESS -
                     topControlPanel_.getSize().height -
                     bottomControlPanel_.getSize().height);
         ijBridge_.getIJImageCanvas().setPreferredSize(
               new Dimension(newCanvasWidth, newCanvasHeight));
         ijBridge_.getIJImageCanvas().invalidate();

         frame_.pack(); // Includes validation

         // NS: I find the autonomous movement of the window highly annoying
         // Uncomment if you disagree and want the window to move all by itself
         // If we extended beyond bottom or right of the screen, move up/left
         /*
         int newFrameX = Math.min(frame_.getX(),
               screenBounds.x + screenBounds.width - frame_.getWidth());
         newFrameX = Math.max(1, newFrameX);
         int newFrameY = Math.min(frame_.getY(),
               screenBounds.y + screenBounds.height - frame_.getHeight());
         newFrameY = Math.max(1, newFrameY);
         frame_.setLocation(newFrameX, newFrameY);
         */
      }

      ijBridge_.mm2ijRepaint();
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

   public void selectionMayHaveChanged(final BoundsRectAndMask selection) {
      if (selection != lastSeenSelection_) {
         if (selection == null || !selection.equals(lastSeenSelection_)) {
            runnablePool_.invokeAsLateAsPossibleWithCoalescence(new CoalescentRunnable() {
               @Override
               public Class<?> getCoalescenceClass() {
                  return getClass();
               }

               @Override
               public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
                  return later;
               }

               @Override
               public void run() {
                  displayController_.selectionDidChange(selection);
               }
            });
         }
      }
      lastSeenSelection_ = selection;
   }

   /**
    * a key was pressed on the display Canvas.  Post as an event.
    * Let the caller know if any of the event handlers consumed the keypress
    *
    * @param e KeyEvent generated by the key press on our canvas
    * @return true if action was taken by one of the handlers, false otherwise
    */
   public boolean keyPressOnImageConsumed(KeyEvent e) {
      DisplayKeyPressEvent displayKeyPressEvent = new DisplayKeyPressEvent(e);
      displayController_.postDisplayEvent(displayKeyPressEvent);
      return displayKeyPressEvent.wasConsumed();
   }


   /**
    * Notify the UI controller that a mouse event occurred on the image canvas.
    *
    * If {@code imageLocation} is null or empty, the indicator is hidden. The
    * {@code imageLocation} parameter can be a rectangle containing more than
    * one pixel, for example if the point comes from a zoomed-out canvas.
    *
    * @param e MouseEvent that occurred on the Canvas. Use its getId() function
    * to discover what kind of Mouse Event happened.
    * @param imageLocation the image coordinates of the pixel for which
    * information should be displayed (in image coordinates)
    * @param ijToolId ID of tool selected in ImageJ tool-bar
    */
   public void mouseEventOnImage(final MouseEvent e, final Rectangle imageLocation, 
           final int ijToolId) {
      displayController_.postDisplayEvent( new DisplayMouseEvent(
              e, imageLocation, ijToolId));
      switch (e.getID()) {
         case MouseEvent.MOUSE_MOVED:
         case MouseEvent.MOUSE_ENTERED:
         case MouseEvent.MOUSE_EXITED:
            if (imageLocation == null
                    || imageLocation.width == 0 || imageLocation.height == 0) {
               if (mouseLocationOnImage_ == null) {
                  return;
               }
               mouseLocationOnImage_ = null;
               updatePixelInformation();
            } else {
               if (imageLocation.equals(mouseLocationOnImage_)) {
                  return;
               }
               mouseLocationOnImage_ = new Rectangle(imageLocation);
               updatePixelInformation();
            }
      }
   }
   
   public void mouseWheelMoved(MouseWheelEvent e) {
      displayController_.postDisplayEvent( new DisplayMouseWheelEvent(e));
   }

   private void updatePixelInformation() {
      if (displayedImages_ == null || mouseLocationOnImage_ == null) {
         displayController_.postDisplayEvent(
               DataViewerMousePixelInfoChangedEvent.createUnavailable());
         return;
      }

      List<Image> images = new ArrayList<>(
              displayedImages_.getRequest().getImages());
      if (images.isEmpty()) {
         displayController_.postDisplayEvent(
               DataViewerMousePixelInfoChangedEvent.createUnavailable());
         return;
      }

      // Perhaps we could compute the mean or median pixel info for the rect.
      // But for now we just use the center point.
      final Point center = new Point(
            mouseLocationOnImage_.x + mouseLocationOnImage_.width / 2,
            mouseLocationOnImage_.y + mouseLocationOnImage_.height / 2);

      if (center.y >= images.get(0).getHeight() || 
              center.x >= images.get(0).getWidth()) {
         displayController_.postDisplayEvent(
               DataViewerMousePixelInfoChangedEvent.createUnavailable());
         return;
      }
      
      if (images.get(0).getCoords().hasAxis(Coords.CHANNEL)) {
         try {
            displayController_.postDisplayEvent(
               DataViewerMousePixelInfoChangedEvent.
               fromAxesAndImages(center.x, center.y,
                     new String[] { Coords.CHANNEL }, images));
         } catch (IllegalArgumentException iea) {
            ReportingUtils.logError(iea);
            displayController_.postDisplayEvent(
               DataViewerMousePixelInfoChangedEvent.createUnavailable());
         }
      }
      else {
         displayController_.postDisplayEvent(
               DataViewerMousePixelInfoChangedEvent.fromImage(
                     center.x, center.y, images.get(0)));
      }
   }

   public void paintOverlays(Graphics2D g, Rectangle destRect,
         Rectangle2D.Float viewPort)
   {
      Preconditions.checkState(displayedImages_ != null);

      DisplaySettings displaySettings = displayController_.getDisplaySettings();
      List<Image> images = displayedImages_.getRequest().getImages();
      Coords nominalCoords = displayedImages_.getRequest().getNominalCoords();
      // The eternal problem of missing axes rises its ugly head again
      // This really  should be solved in a systematic way rather than patching
      // all the stuff that breaks!!!
      if (!nominalCoords.hasC()) {
         nominalCoords = nominalCoords.copyBuilder().c(0).build();
      }
      Image primaryImage = null;
      for (Image image : images) {
         if (image.getCoords().getChannel() == nominalCoords.getChannel()) {
            primaryImage = image;
         }
      }
      List<Overlay> overlays = displayController_.getOverlays();
      for (Overlay overlay : overlays) {
         if (overlay.isVisible()) {
            overlay.paintOverlay(g, destRect, displaySettings,
                  images, primaryImage, viewPort);
         }
      }
   }

   public void paintDidFinish() {
      perfMon_.sampleTimeInterval("Repaint completed");

      // Paints occur both by our requesting a new image to be displayed and
      // for other reasons. To compute the display rate, we want to count only
      // the former case.
      boolean countAsNewDisplayedImage =
            repaintScheduledForNewImages_.compareAndSet(true, false);
      if (countAsNewDisplayedImage) {
         perfMon_.sampleTimeInterval("Repaint counted as new display");

         displayIntervalEstimator_.get().sample();
         if (displayController_.isAnimating()) {
            adjustAnimationTickIntervalMs(
                  displayIntervalEstimator_.get().getQuantile(0.5));
         }
         perfMon_.sample("Display interval 25th percentile",
                 displayIntervalEstimator_.get().getQuantile(0.25));
         perfMon_.sample("Display interval 50th percentile",
                 displayIntervalEstimator_.get().getQuantile(0.5));
         perfMon_.sample("Display interval 75th percentile",
                 displayIntervalEstimator_.get().getQuantile(0.75));
         showFPS();
      }
   }

   private void adjustAnimationTickIntervalMs(double achievedMedianMs) {
      // Avoid adjusting too frequently
      if (System.nanoTime() - lastAnimationIntervalAdjustmentNs_ < 500000000L) {
         return;
      }

      // The ideal interval normally matches the animation fps, but at low fps
      // we get less jitter by using a shorter tick interval. In that case,
      // the animation controller will skip ticks that don't advance the
      // animation by at least 1 frame.
      double animationFPS = animationController_.getAnimationRateFPS();
      double lowJitterFPS = animationFPS *
            Math.max(1.0, Math.floor(60.0 / animationFPS));
      int idealIntervalMs = Math.max(17, (int) Math.round(1000.0 / lowJitterFPS));

      int targetMs;
      if (achievedMedianMs < 1.1 * 1000.0 / animationFPS) {
         targetMs = idealIntervalMs;
      }
      else {
         // Aim for a little faster, to allow for adaptive speed-up
         targetMs= (int) Math.max(idealIntervalMs, 0.9 * achievedMedianMs);
      }

      // Limit slew rate (25% per 500 ms)
      int currentSettingMs = animationController_.getTickIntervalMs();
      targetMs = (int) Math.max(0.75 * currentSettingMs,
            Math.min(1.25 * currentSettingMs, targetMs));

      animationController_.setTickIntervalMs(targetMs);
      lastAnimationIntervalAdjustmentNs_ = System.nanoTime();
   }

   /**
    * NS, 20190515: 
    * Display fps is confusing to the user.  It is unclear what it means.
    * It also can easily be confused with Playback fps and camera fps, 
    * which are more useful messages.
    * I leave the old code below for future use, but no longer call it
    * Showing camera fps instead.  Display fps is set by the user, and the code
    * should make sure that it is correct.
    * It is surprisingly difficult to estimate camera fps correctly with the
    * information we get from the core.  We are using the imageNumber, inserted
    * by the Circular Buffer to count images produced by the camera.  However,
    * when the circular buffer is full, it will reset, so we need to deduce
    * if that event took place, and calculate how many images we missed when that
    * happened.  When the camera runs very fast, and the circular buffer is
    * small, it may happen that the buffer overflows before we ever see an
    * image (or possibly, the first image we see has a higher image number
    * than the one we remembered from the last iteration, which is how we check
    * for overflows), resulting in poor fps estimates.
    * The Core is really much better suited to keep track of camera fps.
    * I'll open a ticket, but until then, this is teh best I can come up with.
    */
   private void showFPS() {
      // Show camera FPS, only in the preview window (otherwise, what does the number mean?)
      if (isPreview_) {
         List<Image> displayedImages = getDisplayedImages();
         if (displayedImages != null && displayedImages.size() > 0) {
            Metadata metadata = displayedImages.get(0).getMetadata();
            Long nr = metadata.getImageNumber();
            if (nr == null) {
               cameraFpsLabel_.setText(" ");
               return;
            }
            // Circular buffer overflow causes the imageNumber to reset to 0.
            // but not the elapasedTimeMs, we need to keep track of the elapsedTime
            // of every image with imageNumber 0:
            if (nrLiveFramesReceived_ == 0) {
               startTime_ = metadata.getElapsedTimeMs(-1.0);
            }
            double ms = metadata.getElapsedTimeMs(-1.0) - startTime_;
            if (nr != null) {
               if (nr < lastImageNumber_) {
                  // circular buffer must have overflown and was reset
                  // calculate missing images from buffer size and numbers we have
                  nrLiveFramesReceived_ += studio_.core().getBufferTotalCapacity() -
                          lastImageNumber_  + nr;
               } else {
                  nrLiveFramesReceived_ += nr - lastImageNumber_;
               }
               lastImageNumber_ = nr;
               if (ms > 0.0) {
                  double fps = (nrLiveFramesReceived_ * 1000.0) / ms;
                  if (fps < 2.0) {
                     cameraFpsLabel_.setText(String.format(
                             "Camera: %.3g fps", fps));
                  }
                  else {
                     cameraFpsLabel_.setText(String.format(
                             "Camera: %d fps", (int) fps));
                  }
               }
            } else {
               cameraFpsLabel_.setText(" ");
            }
         }
      }
      /*
      long nowNs = System.nanoTime();
      if (nowNs - fpsDisplayedTimeNs_ < 1000000 * FPS_DISPLAY_UPDATE_INTERVAL_MS) {
         if (perfMon_ != null) {
            perfMon_.sampleTimeInterval("FPS indicator update skipped");
         }
         return;
      }
      fpsDisplayedTimeNs_ = nowNs;

      double medianIntervalMs =
            displayIntervalEstimator_.get().getQuantile(0.5);
      if (medianIntervalMs <= 1e-3) {
         return; // Not computed yet.
      }
      if (medianIntervalMs > 0.99 * FPS_DISPLAY_DURATION_MS) {
         return;
      }

      double displayFPS = 1000.0 / medianIntervalMs;
      // We call it "Hz", rather than "fps", since this rate is a refresh rate
      // rather than the rate of different images appearing.
      fpsLabel_.setText(String.format("Display: %.2g Hz", displayFPS));
      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("FPS indicator updated");
      }
      
      scheduledExecutor_.schedule(new Runnable() {
         @Override
         public void run() {
            SwingUtilities.invokeLater(new Runnable() {
               @Override
               public void run() {
                  long nowNs = System.nanoTime();
                  if (nowNs - fpsDisplayedTimeNs_ < 1000000 * FPS_DISPLAY_DURATION_MS) {
                     return;
                  }
                  fpsLabel_.setText(" ");
                  if (perfMon_ != null) {
                     perfMon_.sampleTimeInterval("FPS indicator dismissed");
                  }
               }
            });
         }
      }, FPS_DISPLAY_DURATION_MS, TimeUnit.MILLISECONDS);
      */
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

      List<Coords> ret = new ArrayList<>();
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
      if (displayedImages_ == null) {
         return null;
      }
      return displayedImages_.getRequest().getNominalCoords();
   }

   public int getImageWidth() {
      try {
         return displayController_.getDataProvider().getAnyImage().getWidth();
      }
      catch (IOException e) {
         return 0;
      }
   }

   public int getImageHeight() {
      try {
         return displayController_.getDataProvider().getAnyImage().getHeight();
      }
      catch (IOException e) {
         return 0;
      }
   }

   /**
    * 
    * @return Human readable String explaining the pixel Type
    * TODO: Formalize these Strings
    */
   
   public String getPixelType() {
      try {
         int bytesPerPixel = displayController_.getDataProvider().getAnyImage().getBytesPerPixel();
         int numComponents = displayController_.getDataProvider().getAnyImage().getNumComponents();
         if (numComponents == 1) {
            switch (bytesPerPixel) {
               case 1:
                  return "8-bit";
               case 2:
                  return "16-bit";
               case 4:
                  return "32-bit";
               default:
                  break;
            }
         } else if (numComponents == 3) {
            switch (bytesPerPixel) {
               case 4:
                  return "RGB32";
               case 8:
                  return "RGB64;";
            }
         }
      } catch (IOException e) {
      }
      return "Unknown pixelType";
   }
  
   /**
    * Generates the info string in the top right of the viewer
    * showing:
    * - x-y size in microns
    * - x-y size in pixels
    * - pixelType
    * - Size in k/MB
    * 
    * This function is only called when the viewer is created or when the pixel
    * size changes
    * 
    * 
    * @param images
    * @return 
    */
   public String getInfoString(ImagesAndStats images) {
      StringBuilder infoStringB = new StringBuilder();
      Double pixelSize;
      long nrBytes;
      try {
         if (displayController_.getDataProvider().getAnyImage() == null) {
            return "No image yet";
         }
         // TODO: is 0 always the right choice?  
         images.getRequest().getImage(0);
         pixelSize = images.getRequest().getImage(0).getMetadata().getPixelSizeUm();
         nrBytes = getImageWidth() * getImageHeight()
                 * images.getRequest().getImage(0).getBytesPerPixel()
                 * images.getRequest().getImage(0).getNumComponents();

      } catch (IOException io) {
         return "Failed to find image";
      }

      double widthUm = getImageWidth() * pixelSize;
      double heightUm = getImageHeight() * pixelSize;
      infoStringB.append(NumberUtils.doubleToDisplayString(widthUm)).append("x").
              append(NumberUtils.doubleToDisplayString(heightUm)).
              append("\u00B5").append("m  ");

      infoStringB.append(getImageWidth()).append("x").append(getImageHeight());
      infoStringB.append("px  ");
      
      infoStringB.append(this.getPixelType()).append(" ");

      if (nrBytes / 1000 < 1000) {
         infoStringB.append((int) nrBytes / 1024).append("KB");
      } else {
         infoStringB.append((int) nrBytes / 1048576).append("MB");
      }
      
      return infoStringB.toString();
   }

   public List<Image> getDisplayedImages() {
      if (displayedImages_ == null) {
         return Collections.emptyList();
      }
      return displayedImages_.getRequest().getImages();
   }


   //
   // User input handlers
   //

   private void handleAxisAnimateButton(ActionEvent event) {
      List<String> animatedAxes = new ArrayList<>();
      for (Map.Entry<String, JToggleButton> e : axisAnimationButtons_) {
         if (e.getValue().isSelected()) {
            animatedAxes.add(e.getKey());
         }
      }
      displayController_.setPlaybackAnimationAxes(
            animatedAxes.toArray(new String[] {}));
   }
   
   private void handleAxisSpinner(String axis, JSpinner spinner, ChangeEvent event) {
      int newPosition = (Integer) spinner.getValue() - 1;
      displayController_.setDisplayPosition(
              displayController_.getDisplayPosition().copyBuilder().
                      index(axis, newPosition).build() 
      );
   }

   private void handlePlaybackFpsSpinner(ChangeEvent event) {
      double fps = (Double) playbackFpsSpinner_.getValue();
      displayController_.setDisplaySettings(displayController_.
              getDisplaySettings().copyBuilder().playbackFPS(fps).build());
   }

   private void handleLockButton (String axis, ActionEvent event) {
            JButton button = (JButton) event.getSource();
      // TODO This is a string-based prototype; also we need to correctly init
      // the button icon
      if (button.getIcon() == UNLOCKED_ICON) {
         displayController_.setAxisAnimationLock(axis, "F");
         button.setIcon(BLACK_LOCKED_ICON);
      }
      else if (button.getIcon() == BLACK_LOCKED_ICON) {
         displayController_.setAxisAnimationLock(axis, "S");
         button.setIcon(RED_LOCKED_ICON);
      }
      else {
         displayController_.setAxisAnimationLock(axis, "U");
         button.setIcon(UNLOCKED_ICON);
      }
   }
   
   //
   // WindowListener for the standard and full-screen frames
   //

   @Override
   public void windowActivated(WindowEvent e) {
      displayController_.frameDidBecomeActive();
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
   public void scrollBarPanelHeightWillChange(MDScrollBarPanel panel,
         int currentHeight)
   {
      panel.setVisible(false);
   }

   @Override
   public void scrollBarPanelHeightDidChange(MDScrollBarPanel panel,
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
   public void scrollBarPanelDidChangePositionInUI(MDScrollBarPanel panel) {
      // TODO We might want to leave out animated axes when setting the display
      // position based on user input. Also consider disabling scroll bars that
      // are being animated.
      Coords.CoordsBuilder builder = new DefaultCoords.Builder();
      // work around general bug needing all 4 basic stages
      // TODO: remove this dependency!!!
      builder.channel(0).time(0).stagePosition(0).z(0); 
      for (String axis : panel.getAxes()) {
         builder.index(axis, panel.getAxisPosition(axis));
      }
      Coords position = builder.build();
      animationController_.forceDataPosition(position);
   }

   @Subscribe
   public void onEvent(DataViewerMousePixelInfoChangedEvent e) {
      if (pixelInfoLabel_ == null) {
         return;
      }
      if (!e.isInfoAvailable()) {
         pixelInfoLabel_.setText(" ");
         return;
      }

      List<String> chStrings = new ArrayList<>();
      List<Coords> coords = e.getAllCoordsSorted();
      if (coords.isEmpty()) {
         chStrings.add("NA"); // Shouldn't normally happen
      }
      else if (coords.size() == 1) {
         chStrings.add(e.getComponentValuesStringForCoords(coords.get(0)));
      }
      else {
         int lastChannel = 0;
         for (Coords c : coords) {
            if (c.getChannel() > lastChannel) {
            while (c.getChannel() > lastChannel) {
               chStrings.add("-");
               lastChannel++;
            }
            } else {
               lastChannel++;
            }
            chStrings.add(e.getComponentValuesStringForCoords(c));
         }
      }

      String valuesString;
      if (chStrings.size() == 1) {
         valuesString = chStrings.get(0);
      }
      else {
         valuesString = "[" + Joiner.on(", ").join(chStrings) + "]";
      }

      pixelInfoLabel_.setText(String.format("%s = %s",
            e.getXYString(), valuesString));
      if (pixelInfoLabel_.getSize().width > pixelInfoLabel_.getMinimumSize().width) {
         pixelInfoLabel_.setMinimumSize(new Dimension(
                 pixelInfoLabel_.getSize().width, 10));
      }
   }

   @Subscribe
   public void onLiveModeEvent(LiveModeEvent liveModeEvent) {
      // Used to reset counters for camera fps measurements
      if (liveModeEvent.getIsOn()) {
         nrLiveFramesReceived_ = 0;
         lastImageNumber_ = 0;
      }
   }
}