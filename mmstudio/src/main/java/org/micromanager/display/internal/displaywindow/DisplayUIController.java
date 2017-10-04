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

import org.micromanager.display.internal.event.DataViewerMousePixelInfoChangedEvent;
import org.micromanager.internal.utils.ColorMaps;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.Rectangle2D;
import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.animate.AnimationController;
import org.micromanager.display.internal.displaywindow.imagej.ImageJBridge;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImageStats;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
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
import org.micromanager.display.DisplayWindowControlsFactory;
import org.micromanager.display.internal.gearmenu.GearButton;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.NumberUtils;

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
   private JLabel newImageIndicator_;
   private JLabel fpsLabel_;
   private JLabel infoLabel_;
   private PopupButton playbackFpsButton_;
   private JSpinner playbackFpsSpinner_;
   private MDScrollBarPanel scrollBarPanel_;
   private JButton axisLockButton_;

   // Subcomponents of the N-dimensional scroll bar panel
   // We need to look up in both directions, and don't need the efficiency of
   // a map for the small number of elements, so use lists of pairs.
   private final List<Map.Entry<String, JToggleButton>> axisAnimationButtons_ =
         new ArrayList<Map.Entry<String, JToggleButton>>();
   private final List<Map.Entry<String, PopupButton>> axisPositionButtons_ =
         new ArrayList<Map.Entry<String, PopupButton>>();
   private final List<Map.Entry<String, PopupButton>> axisLinkButtons_ =
         new ArrayList<Map.Entry<String, PopupButton>>();

   private static final Icon PLAY_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/play.png");
   private static final Icon PAUSE_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/pause.png");
   private static final Icon UNLOCKED_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/lock_open.png");
   private static final Icon BLACK_LOCKED_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/lock_locked.png");
   private static final Icon RED_LOCKED_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/lock_super.png");


   private ImageJBridge ijBridge_;

   // Data display state of the UI (which may lag behind the display
   // controller's notion of what's current)
   private final List<String> displayedAxes_ = new ArrayList<String>();
   private final List<Integer> displayedAxisLengths_ = new ArrayList<Integer>();
   private ImagesAndStats displayedImages_;

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
         new AtomicReference<TimeIntervalRunningQuantile>(
               TimeIntervalRunningQuantile.create(
                     DISPLAY_INTERVAL_SMOOTH_N_SAMPLES));
   private long fpsDisplayedTimeNs_;

   private final CoalescentEDTRunnablePool runnablePool_ =
         CoalescentEDTRunnablePool.create();

   private PerformanceMonitor perfMon_;

   private static final int MIN_CANVAS_HEIGHT = 100;
   private static final int BORDER_THICKNESS = 2;

   private static final class UpdatePixelInfoTag {}


   @MustCallOnEDT
   static DisplayUIController create(DisplayController parent,
         DisplayWindowControlsFactory controlsFactory,
         AnimationController animationController)
   {
      DisplayUIController instance = new DisplayUIController(parent,
            controlsFactory, animationController);
      parent.registerForEvents(instance);
      instance.frame_.addWindowListener(instance);
      return instance;
   }

   @MustCallOnEDT
   private DisplayUIController(DisplayController parent,
         DisplayWindowControlsFactory controlsFactory,
         AnimationController animationController)
   {
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
      String title = displayController_.getName();
      frame.setTitle(title); // TODO Listen for changes to name, zoom, and disk/memory/save state
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
      panel.add(pixelInfoLabel_, new CC().split(5));
      panel.add(new JPanel(), new CC().growX());
      newImageIndicator_ = new JLabel("NEW IMAGE");
      newImageIndicator_.setFont(newImageIndicator_.getFont().
            deriveFont(10.0f).deriveFont(Font.BOLD));
      newImageIndicator_.setVisible(false);
      panel.add(newImageIndicator_, new CC().hideMode(2));
      fpsLabel_ = new JLabel(" ");
      fpsLabel_.setFont(fpsLabel_.getFont().deriveFont(10.0f));
      panel.add(fpsLabel_, new CC());
      SpinnerModel fpsModel = new SpinnerNumberModel(10.0, 0.1, 1000.0, 5.0);
      playbackFpsSpinner_ = new JSpinner(fpsModel);
      playbackFpsSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            handlePlaybackFpsSpinner(e);
         }
      });
      playbackFpsButton_ = PopupButton.create("", playbackFpsSpinner_);
      playbackFpsButton_.setFont(playbackFpsButton_.getFont().deriveFont(10.0f));
      int width = 16 + playbackFpsButton_.getFontMetrics(
            playbackFpsButton_.getFont()).stringWidth("Playback: 9999.0 fps");
      Dimension fpsButtonSize = new Dimension(width,
            fpsLabel_.getPreferredSize().height);
      playbackFpsButton_.setMinimumSize(fpsButtonSize);
      playbackFpsButton_.setMaximumSize(fpsButtonSize);
      playbackFpsButton_.setPreferredSize(fpsButtonSize);
      playbackFpsButton_.addPopupButtonListener(new PopupButton.Listener() {
         @Override
         public void popupButtonWillShowPopup(PopupButton button) {
            playbackFpsSpinner_.setValue(displayController_.getPlaybackSpeedFps());
         }
      });
      setPlaybackFpsIndicator(displayController_.getPlaybackSpeedFps());
      playbackFpsButton_.setVisible(false);
      panel.add(playbackFpsButton_, new CC().hideMode(2).wrap());

      MDScrollBarPanel.ControlsFactory leftFactory =
            new MDScrollBarPanel.ControlsFactory() {
               @Override
               public JComponent getControlsForAxis(String axis, int height) {
                  return makeScrollBarLeftControls(axis, height);
               }
            };
      MDScrollBarPanel.ControlsFactory rightFactory =
            new MDScrollBarPanel.ControlsFactory() {
               @Override
               public JComponent getControlsForAxis(String axis, int height) {
                  return makeScrollBarRightControls(axis, height);
               }
            };
      scrollBarPanel_ = MDScrollBarPanel.create(leftFactory, rightFactory);
      scrollBarPanel_.addListener(this);
      panel.add(scrollBarPanel_, new CC().growX().pushX().split(2));

      axisLockButton_ = new JButton();
      Dimension size = new Dimension(MDScrollBarPanel.ROW_HEIGHT, MDScrollBarPanel.ROW_HEIGHT);
      axisLockButton_.setMinimumSize(size);
      axisLockButton_.setPreferredSize(size);
      axisLockButton_.setIcon(UNLOCKED_ICON);
      axisLockButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleLockButton(e);
         }
      });
      // TODO: Right-click menu
      axisLockButton_.setComponentPopupMenu(new JPopupMenu());
      axisLockButton_.setVisible(false);
      panel.add(axisLockButton_, new CC().gapBefore("0").growY().hideMode(2).wrap());

      JPanel customControlsPanel =
            new JPanel(new MigLayout(new LC().insets("0").gridGap("1", "0")));
      if (controlsFactory_ != null) {
         for (Component c : controlsFactory_.makeControls(displayController_)) {
            customControlsPanel.add(c);
         }
      }

      panel.add(customControlsPanel, new CC().split());
      panel.add(new JPanel(), new CC().growX());
      panel.add(new JLabel("[Save As...]"));
      // TODO Avoid static studio
      panel.add(new GearButton(displayController_, MMStudio.getInstance()));

      return panel;
   }

   @MustCallOnEDT
   private JComponent makeScrollBarLeftControls(String axis, int height) {
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
         Dimension size = new Dimension(2 * height, height);
         animateButton.setMinimumSize(size);
         animateButton.setMaximumSize(size);
         animateButton.setPreferredSize(size);
         animateButton.setHorizontalAlignment(SwingConstants.LEFT);
         animateButton.setHorizontalTextPosition(SwingConstants.RIGHT);
         animateButton.setIcon(PLAY_ICON);
         animateButton.setSelectedIcon(PAUSE_ICON);
         animateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               handleAxisAnimateButton(e);
            }
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
         positionButton = PopupButton.create("", new JPanel());
         positionButton.setFont(positionButton.getFont().deriveFont(10.0f));
         int width = 8 + positionButton .getFontMetrics(positionButton .getFont()).
               stringWidth("99999/99999");
         positionButton.setHorizontalAlignment(SwingConstants.RIGHT);
         Dimension size = new Dimension(width, height);
         positionButton.setMinimumSize(size);
         positionButton.setMaximumSize(size);
         positionButton.setPreferredSize(size);
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
         linkButton = PopupButton.create(IconLoader.getIcon("/org/micromanager/icons/linkflat.png"), linkPopup);
         linkButton.addPopupButtonListener(new PopupButton.Listener() {
            @Override
            public void popupButtonWillShowPopup(PopupButton button) {
               linker.updatePopupMenu(linkPopup);
            }
         });
         linkButton.setDisabledIcon(IconLoader.getDisabledIcon(linkButton.getIcon()));
         Dimension size = new Dimension(3 * height / 2, height);
         linkButton.setMinimumSize(size);
         linkButton.setMaximumSize(size);
         linkButton.setPreferredSize(size);
         axisLinkButtons_.add(new AbstractMap.SimpleEntry<String, PopupButton>(
               axis, linkButton));
      }
      ret.add(linkButton, new CC());

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
         updateAxisPositionIndicator(scrollableAxes.get(i),
               -1, scrollableLengths.get(i));
      }

      playbackFpsButton_.setVisible(!scrollableAxes.isEmpty());
      axisLockButton_.setVisible(!scrollableAxes.isEmpty());

      if (ijBridge_ != null) {
         ijBridge_.mm2ijEnsureDisplayAxisExtents();
      }
   }

   @MustCallOnEDT
   void displayImages(ImagesAndStats images) {
      setupDisplayUI();

      displayedImages_ = images;
      Coords nominalCoords = images.getRequest().getNominalCoords();

      // A display request may come in ahead of an expand-range request, so
      // make sure to update our range first
      expandDisplayedRangeToInclude(nominalCoords);
      expandDisplayedRangeToInclude(getAllDisplayedCoords());

      for (String axis : scrollBarPanel_.getAxes()) {
         if (nominalCoords.hasAxis(axis)) {
            scrollBarPanel_.setAxisPosition(axis, nominalCoords.getIndex(axis));
            updateAxisPositionIndicator(axis, nominalCoords.getIndex(axis), -1);
         }
      }

      ijBridge_.mm2ijSetDisplayPosition(nominalCoords);
      applyAutostretch(images, displayController_.getDisplaySettings());

      if (mouseLocationOnImage_ != null) {
         updatePixelInformation(); // TODO Can skip if identical images
      }
      
      infoLabel_.setText(this.getInfoString()); // TODO, this should be invariant, so only need to do this for first image

      repaintScheduledForNewImages_.set(true);
   }

   @MustCallOnEDT
   public void applyDisplaySettings(DisplaySettings settings) {
      // Note: This method only looks at changes in color settings. Others
      // (e.g. zoom) are ignored.

      if (ijBridge_ == null) {
         return;
      }

      int nChannels = ijBridge_.getIJNumberOfChannels();
      boolean autostretch = settings.isAutostretchEnabled()
            && displayedImages_ != null;
      boolean isRGB = ijBridge_.isIJRGB();

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

      if (!isRGB) {
         for (int i = 0; i < nChannels; ++i) {
            ChannelDisplaySettings channelSettings =
                  settings.getChannelSettings(i);
            ComponentDisplaySettings componentSettings =
                  channelSettings.getComponentSettings(0);
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
         int nComponents = settings.getChannelSettings(0).getNumberOfComponents();
         for (int i = 0; i < nComponents; ++i) {
            ComponentDisplaySettings componentSettings =
                  settings.getChannelSettings(0).getComponentSettings(i);
            int max = Math.min(Integer.MAX_VALUE,
                  (int) componentSettings.getScalingMaximum());
            int min = Math.max(max - 1,
                  (int) componentSettings.getScalingMinimum());
            ijBridge_.mm2ijSetIntensityScaling(i, min, max);
         }
      }
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

         ImageStats stats = images.getResult().get(statsIndex);
         long min = stats.getComponentStats(0).getAutoscaleMinForQuantile(q);
         long max = Math.min(Integer.MAX_VALUE,
               stats.getComponentStats(0).getAutoscaleMaxForQuantile(q));
         ijBridge_.mm2ijSetIntensityScaling(i, (int) min, (int) max);
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
      newImageIndicator_.setVisible(show);
   }

   private void updateAxisPositionIndicator(String axis, int position, int length) {
      if (length < 0) {
         int axisIndex = displayedAxes_.indexOf(axis);
         if (axisIndex < 0) {
            return;
         }
         length = displayedAxisLengths_.get(axisIndex);
      }
      if (length <= 1) {
         return; // Not displayed
      }
      if (position < 0) {
         position = scrollBarPanel_.getAxisPosition(axis);
      }
      for (Map.Entry<String, PopupButton> e : axisPositionButtons_) {
         if (axis.equals(e.getKey())) {
            e.getValue().setText(String.format("% 5d/% 5d",
                  position + 1, length));
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
         Rectangle screenBounds = Geometry.insettedRectangle(
               gConfig.getBounds(),
               Toolkit.getDefaultToolkit().getScreenInsets(gConfig));

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
    * Notify the UI controller that the mouse has moved on the image canvas.
    *
    * If {@code imageLocation} is null or empty, the indicator is hidden. The
    * {@code imageLocation} parameter can be a rectangle containing more than
    * one pixel, for example if the point comes from a zoomed-out canvas.
    *
    * @param imageLocation the image coordinates of the pixel for which
    * information should be displayed (in image coordinates)
    */
   public void mouseLocationOnImageChanged(Rectangle imageLocation) {
      if (imageLocation == null ||
            imageLocation.width == 0 || imageLocation.height == 0)
      {
         if (mouseLocationOnImage_ == null) {
            return;
         }
         mouseLocationOnImage_ = null;
         updatePixelInformation();
      }
      else {
         if (imageLocation.equals(mouseLocationOnImage_)) {
            return;
         }
         mouseLocationOnImage_ = new Rectangle(imageLocation);
         updatePixelInformation();
      }
   }

   private void updatePixelInformation() {
      if (displayedImages_ == null || mouseLocationOnImage_ == null) {
         displayController_.postDisplayEvent(
               DataViewerMousePixelInfoChangedEvent.createUnavailable());
         return;
      }

      List<Image> images = new ArrayList<Image>(
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

      if (images.get(0).getCoords().hasAxis(Coords.CHANNEL)) {
         displayController_.postDisplayEvent(
               DataViewerMousePixelInfoChangedEvent.
               fromAxesAndImages(center.x, center.y,
                     new String[] { Coords.CHANNEL }, images));
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

   private void showFPS() {
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
         } 
         // TODO: RGB 
      }
      catch (IOException e) {
      }
      return "Unknown pixelType";
   }
  
   public String getInfoString() {
      StringBuilder infoStringB = new StringBuilder();
      Double pixelSize;
      long nrBytes;
      try {
         if (displayController_.getDataProvider().getAnyImage() == null) {
            return "No image yet";
         }
         pixelSize = displayController_.getDataProvider().getAnyImage().getMetadata().getPixelSizeUm();
         nrBytes = getImageWidth() * getImageHeight()
                 * displayController_.getDataProvider().getAnyImage().getBytesPerPixel()
                 * displayController_.getDataProvider().getAnyImage().getNumComponents();

      } catch (IOException io) {
         return "Failed to find image";
      }

      double widthUm = getImageWidth() * pixelSize;
      double heightUm = getImageHeight() * pixelSize;
      infoStringB.append(NumberUtils.doubleToDisplayString(widthUm)).append("x").
              append(NumberUtils.doubleToDisplayString(heightUm)).
              append("\u00B5").append("m  ");

      infoStringB.append(getImageWidth()).append("x").append(getImageHeight());
      infoStringB.append("px  ").append(this.getPixelType()).append(" ");

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
      List<String> animatedAxes = new ArrayList<String>();
      for (Map.Entry<String, JToggleButton> e : axisAnimationButtons_) {
         if (e.getValue().isSelected()) {
            animatedAxes.add(e.getKey());
         }
      }
      displayController_.setPlaybackAnimationAxes(
            animatedAxes.toArray(new String[] {}));
   }

   private void handlePlaybackFpsSpinner(ChangeEvent event) {
      double fps = (Double) playbackFpsSpinner_.getValue();
      displayController_.setPlaybackSpeedFps(fps);
   }

   private void handleLockButton(ActionEvent event) {
      JButton button = (JButton) event.getSource();
      // TODO This is a string-based prototype; also we need to correctly init
      // the button icon
      if (button.getIcon() == UNLOCKED_ICON) {
         displayController_.setAxisAnimationLock("F");
         button.setIcon(BLACK_LOCKED_ICON);
      }
      else if (button.getIcon() == BLACK_LOCKED_ICON) {
         displayController_.setAxisAnimationLock("S");
         button.setIcon(RED_LOCKED_ICON);
      }
      else {
         displayController_.setAxisAnimationLock("U");
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

      List<String> chStrings = new ArrayList<String>();
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
            while (c.getChannel() > lastChannel++) {
               chStrings.add("_");
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
   }
}