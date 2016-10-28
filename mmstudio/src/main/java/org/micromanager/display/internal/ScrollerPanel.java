///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.event.MouseInputAdapter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.NewImageEvent;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.PixelsSetEvent;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;
import org.micromanager.display.internal.events.LayoutChangedEvent;
import org.micromanager.display.internal.link.ImageCoordsEvent;
import org.micromanager.display.internal.link.ImageCoordsLinker;
import org.micromanager.display.internal.link.LinkButton;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This class displays a grid of scrollbars for selecting which images in a
 * Datastore to show.
 */
public final class ScrollerPanel extends JPanel {
   /**
    * This class tracks relevant state for a single axis' set of controls.
    */
   private class AxisState {
      boolean isAnimated_;
      JButton posButton_;
      JLabel maxLabel_;
      JScrollBar scrollbar_;
      ScrollbarLockIcon.LockedState lockState_;
      // The cached index is the index we last recorded for the scrollbar.
      // Caching it allows us to avoid redundant GUI updates.
      int cachedIndex_;
      // The animated index is the index we last recorded when animating.
      int animatedIndex_;

      public AxisState(JButton posButton, JScrollBar scrollbar, JLabel maxLabel) {
         isAnimated_ = false;
         posButton_ = posButton;
         scrollbar_ = scrollbar;
         maxLabel_ = maxLabel;
         lockState_ = ScrollbarLockIcon.LockedState.UNLOCKED;
         cachedIndex_ = 0;
         animatedIndex_ = 0;
      }
   }

   /**
    * This class shows a popup menu to set the exact location of an axis.
    */
   public final class PositionPopup extends JPopupMenu {
      public PositionPopup(final String axis, JButton button) {
         super.add(new JLabel("Set index: "));
         final JTextField field = new JTextField(button.getText());
         field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent event) {
               try {
                  // Subtract one because displayed indices are 1-indexed
                  // while internal are 0-indexed.
                  int newPos = Integer.parseInt(field.getText()) - 1;
                  setPosition(axis, newPos);
               }
               catch (NumberFormatException e) {
                  // Ignore it
               }
            }
         });
         super.add(field);
      }
   }

   private final Datastore store_;
   private final DisplayWindow display_;
   private final Thread updateThread_;
   private final LinkedBlockingQueue<Coords> updateQueue_;
   private final AtomicBoolean shouldStopUpdates_;

   private final HashMap<String, AxisState> axisToState_;
   private final HashMap<String, Integer> axisToSavedPosition_;
   private JButton fpsButton_;
   private final FPSPopupMenu fpsMenu_;

   private Timer snapbackTimer_;
   private Timer animationTimer_;
   private double animationFPS_ = 0.0;
   private int animationStepSize_ = 0;
   private long lastAnimationTimeMs_ = 0;

   // We turn this off when we want to update the position of several
   // scrollbars in rapid succession, so that we don't post multiple spurious
   // draw requests.
   private boolean shouldPostEvents_ = true;

   public ScrollerPanel(Datastore store, DisplayWindow display) {
      store_ = store;
      display_ = display;

      updateQueue_ = new LinkedBlockingQueue<Coords>();
      shouldStopUpdates_ = new AtomicBoolean(false);

      axisToState_ = new HashMap<String, AxisState>();
      axisToSavedPosition_ = new HashMap<String, Integer>();

      // Only the scrollbar column is allowed to grow in width
      // Columns are animate, current position, max position, scrollbar,
      // lock, link
      super.setLayout(new MigLayout("insets 0", 
               "[][][][grow, shrink][][]"));
      // Don't prevent other components from shrinking
      super.setMinimumSize(new Dimension(1, 1));

      // Set up the FPS rate prior to calling addScroller(), below, as
      // that method creates the FPS button which needs animationFPS_ to be set
      Double fps = display.getDisplaySettings().getAnimationFPS();
      // Default to 10 if it's not set.
      if (fps == null) {
         fps = 10.0;
         display.setDisplaySettings(display.getDisplaySettings().copy()
               .animationFPS(fps).build());
      }
      animationFPS_ = fps;
      fpsMenu_ = new FPSPopupMenu(display_, animationFPS_);

      ArrayList<String> axes;
      String[] axisOrder = store_.getSummaryMetadata().getAxisOrder();
      if (axisOrder != null) {
         axes = new ArrayList<String>(Arrays.asList(axisOrder));
      }
      else {
         axes = new ArrayList<String>(store_.getAxes());
      }
      for (String axis : axes) {
         // Don't bother creating scrollers for axes with a length of 1.
         if (store_.getAxisLength(axis) > 1) {
            addScroller(axis);
         }
      }

      // Spin up a new thread to handle changes to the scrollbar positions.
      // See runUpdateThread() for more information.
      updateThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            runUpdateThread();
         }
      }, "Scrollbar panel update thread");

   }
   
   public void startUpdateThread() {
      updateThread_.start();
   }

   /**
    * Add a scroller for the specified axis. Scrollers are a row in our
    * grid-based layout, and consist of:
    * - a start/stop button for animating display of the axis
    * - a label indicating the current displayed index
    * - a scrollbar for moving through the axis
    * - a lock icon for preventing the display from changing
    */
   private void addScroller(final String axis) {
      final ScrollbarAnimateIcon animateIcon = new ScrollbarAnimateIcon(
            axis, this);
      add(animateIcon, "grow 0");

      // This button displays the current position along the axis, and when
      // clicked, pops up a text field for the user to edit in a new position.
      // Normally we would just use the text field directly; however, then we
      // run into the problem that the setPosition() method calls the non-
      // thread-safe JTextField.setText() method, potentially leading to EDT
      // hangs (because setText() acquires a document lock that the drawing
      // thread needs). And we can't just run setPosition() in the EDT because
      // that causes a gigantic pileup of events on the EDT and a horribly
      // buggy display (which continues making seemingly-random display updates
      // long after an acquisition is finished, for example).
      // The JButton approach dodges this issue because the text field we
      // create on click is not modified by setPosition(), and there are no
      // locks associated with changing the label on the JButton.
      final JButton positionButton = new JButton("1");
      positionButton.setFont(GUIUtils.buttonFont);
      positionButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
      positionButton.setMinimumSize(new Dimension(30, 18));
      positionButton.setPreferredSize(new Dimension(30, 18));
      positionButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new PositionPopup(axis, positionButton).show(
               positionButton, 0, positionButton.getHeight());
         }
      });
      add(positionButton, "grow 0");

      JLabel maxLabel = new JLabel("/ " + store_.getAxisLength(axis));
      add(maxLabel, "grow 0");

      final JScrollBar scrollbar = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1,
            0, store_.getAxisLength(axis));

      scrollbar.setMinimumSize(new Dimension(1, 1));
      scrollbar.addAdjustmentListener(new AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(AdjustmentEvent e) {
            // HACK: if the axis is currently locked and we believe this
            // action is a direct result of user input (instead of from
            // automatically setting the scrollbar to reflect the current
            // displayed image), then we should update the saved position.
            if (axisToSavedPosition_.containsKey(axis) &&
               e.getValueIsAdjusting()) {
               axisToSavedPosition_.put(axis, scrollbar.getValue());
               }
            setPosition(axis, scrollbar.getValue());
         }
      });
      add(scrollbar, "shrinkx, growx");

      ScrollbarLockIcon lock = new ScrollbarLockIcon(axis, display_);
      add(lock, "grow 0");

      LinkButton linker = new LinkButton(
            new ImageCoordsLinker(axis, display_, this),
            display_);
      add(linker, "grow 0, wrap");

      axisToState_.put(axis, new AxisState(positionButton, scrollbar, maxLabel));

      if (fpsButton_ == null) {
         // We have at least one scroller, so add our FPS control button.
         fpsButton_ = new JButton("FPS: " + animationFPS_);
         fpsButton_.setFont(GUIUtils.buttonFont);
         fpsButton_.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               fpsMenu_.show(fpsButton_, e.getX(), e.getY());
            }
         });
         add(fpsButton_, "dock east, growy");
      }
   }

   /**
    * One of our animation icons has changed state; adjust animation for that
    * icon. Called from the ScrollbarAnimateIcon class.
    * @param axis Axis to be animated
    */
   public void toggleAnimation(String axis) {
      // set the index to be animated to the currently displayed index
      AxisState axisState = axisToState_.get(axis);
      axisState.animatedIndex_ = axisState.scrollbar_.getValue();
      axisState.isAnimated_ = !axisState.isAnimated_;
      updateAnimation();
   }

   /**
    * Change the displayed image coordinate along the given axis to the
    * specified position. Even if we don't actually change the displayed
    * image, we still need to update our GUI components (scrollbar and
    * button).
    */
   private void setPosition(String axis, int pos) {
      if (pos == axisToState_.get(axis).cachedIndex_) {
         // We're already where we were told to go.
         return;
      }
      boolean didChange = axisToState_.get(axis).cachedIndex_ != pos;
      axisToState_.get(axis).cachedIndex_ = pos;
      // Update controls: the scrollbar and text field. Avoid redundant set
      // commands to avoid posting redundant events (which could hang the EDT).
      JScrollBar scrollbar = axisToState_.get(axis).scrollbar_;
      if (scrollbar.getValue() != pos) {
         scrollbar.setValue(pos);
      }
      // Add one so displayed values are 1-indexed.
      String newText = String.valueOf(
            Math.min(store_.getAxisLength(axis), Math.max(0, pos + 1)));
      JButton button = axisToState_.get(axis).posButton_;
      if (!button.getText().contentEquals(newText)) {
         button.setText(newText);
      }
      if (didChange) {
         postDrawEvent();
      }
   }

   /**
    * Post a draw event, if shouldPostEvents_ is set.
    * TODO: this seems to randomly interfere with animations, when the
    * animated axis is linked.
    */
   private void postDrawEvent() {
      if (!shouldPostEvents_) {
         return;
      }

      DefaultCoords.Builder builder = new DefaultCoords.Builder();
      // Fill in default positions for all axes, including those we don't have
      // scrollbars for.
      for (String axis : store_.getAxes()) {
         builder.index(axis, 0);
      }
      for (String axis : axisToState_.keySet()) {
         int pos = axisToState_.get(axis).scrollbar_.getValue();
         builder.index(axis, pos);
      }
      Coords target = builder.build();
      // Coordinate our linkers.
      display_.postEvent(new ImageCoordsEvent(target));
      display_.setDisplayedImageTo(target);
   }

   /**
    * One of our lock icons changed state; update lock statuses.
    * @param event event from the lock icon
    */
   @Subscribe
   public void onLockChanged(ScrollbarLockIcon.LockEvent event) {
      String axis = event.getAxis();
      ScrollbarLockIcon.LockedState lockState = event.getLockedState();
      axisToState_.get(axis).lockState_ = lockState;
      updateAnimation();
   }

   /**
    * Display settings have changed; update FPS and activity of the channel
    * scrollbar.
    * @param event - NewDisplaySettingsEvent
    */
   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      DisplaySettings settings = event.getDisplaySettings();
      // Check for change in FPS.
      if (settings.getAnimationFPS() != null &&
            settings.getAnimationFPS() != animationFPS_) {
         animationFPS_ = settings.getAnimationFPS();
         fpsButton_.setText("FPS: " + animationFPS_);
         updateAnimation();
      }
      // Check if the channel scrollbar should be enabled/disabled.
      if (settings.getChannelColorMode() != null &&
            axisToState_.containsKey(Coords.CHANNEL)) {
         boolean isEnabled = settings.getChannelColorMode() != DisplaySettings.ColorMode.COMPOSITE;
         AxisState state = axisToState_.get(Coords.CHANNEL);
         state.maxLabel_.setEnabled(isEnabled);
      }
   }

   /**
    * The drawn image has (potentially) changed; start the process of updating
    * our scrollbars to match.
    * @param event - PixelsSetEvent
    */
   @Subscribe
   public void onPixelsSet(PixelsSetEvent event) {
      Coords coords = event.getImage().getCoords();
      try {
         updateQueue_.put(coords);
      }
      catch (InterruptedException e) {} // Ignore it.
   }

   /**
    * We need to keep our scrollbars up-to-date with the currently-displayed
    * images. However, this takes time, and generates Swing events for each
    * scrollbar (which we don't do anything with, but nonetheless they can
    * clog up the EDT). Since images can change rapidly, we don't want to
    * change the scrollbars for each and every new image. Hence this
    * thread-and-queue system, which ignores image updates that we can't
    * respond to quickly enough.
    */
   private void runUpdateThread() {
      while(!shouldStopUpdates_.get()) {
         try {
            // Limit ourselves to about 30FPS.
            Thread.sleep(1000/30);
         }
         catch (InterruptedException e) {} // Ignore it.
         Coords coords = null;
         // Chew through the queue and take only the last item.
         while (!updateQueue_.isEmpty()) {
            coords = updateQueue_.poll();
         }
         if (coords == null) {
            continue;
         }
         synchronized(this) {
            shouldPostEvents_ = false;
            for (String axis : axisToState_.keySet()) {
               JScrollBar scroller = axisToState_.get(axis).scrollbar_;
               if (!scroller.isEnabled()) {
                  // The channel scrollbar can be disabled when in composite
                  // view mode; don't change it if so.
                  continue;
               }
               if (scroller.getValue() != coords.getIndex(axis)) {
                  scroller.setValue(coords.getIndex(axis));
               }
            }
            shouldPostEvents_ = true;
         }
      }
   }

   /**
    * A new image has arrived; update our scrollbar maxima and positions. Add
    * new scrollbars as needed.
    * @param event - NewImageEvent
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      try {
         Coords coords = event.getImage().getCoords();
         Coords.CoordsBuilder displayedBuilder = coords.copy();
         boolean didAddScrollers = false;
         for (String axis : store_.getAxes()) {
            int newPos = coords.getIndex(axis);
            didAddScrollers = updateScrollbar(axis, newPos) || didAddScrollers;
            if (axisToState_.containsKey(axis)) {
               displayedBuilder.index(axis,
                     axisToState_.get(axis).scrollbar_.getValue());
            }
         }
         if (didAddScrollers) {
            // Ensure new scrollers get displayed properly.
            display_.postEvent(new LayoutChangedEvent());
         }
         display_.setDisplayedImageTo(displayedBuilder.build());

         // Set up snapping back to our current positions. 
         if (snapbackTimer_ != null) {
            snapbackTimer_.cancel();
         }
         // Snap back either if we have a locked axis, or if an axis is
         // animated.
         boolean shouldSnap = false;
         for (String axis : axisToState_.keySet()) {
            AxisState state = axisToState_.get(axis);
            if (state.isAnimated_ ||
                  state.lockState_ != ScrollbarLockIcon.LockedState.UNLOCKED) {
               shouldSnap = true;
               break;
            }
         }
         if (shouldSnap) {
            snapbackTimer_ = new Timer("Scroller panel snapback");
            TimerTask task = new TimerTask() {
               @Override
               public void run() {
                  snapBack();
               }
            };
            snapbackTimer_.schedule(task, 500);
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error in onNewImage for ScrollerPanel");
      }
   }

   // This method is only called by the snapback timer.
   private void snapBack() {
      shouldPostEvents_ = false;
      for (String axis : axisToState_.keySet()) {
         AxisState state = axisToState_.get(axis);
         // This position is used for animated axes.
         int pos = state.animatedIndex_;
         if (axisToSavedPosition_.containsKey(axis)) {
            // This position is used for locked axes.
            pos = axisToSavedPosition_.get(axis);
         }
         state.scrollbar_.setValue(pos);
      }
      shouldPostEvents_ = true;
      postDrawEvent();
   }

   /**
    * Silently (i.e. without sending a draw event) update the specified
    * scrollbar to the desired position. Lengthen the scrollbar if necessary,
    * and if it doesn't exist then create it. Returns true if a new scrollbar
    * was created.
    */
   private boolean updateScrollbar(String axis, int newPos) {
      boolean didAddScroller = false;
      if (!axisToState_.containsKey(axis)) {
         if (newPos != 0) {
            // Now have at least two positions along this axis; add a
            // scroller.
            addScroller(axis);
            didAddScroller = true;
         }
         else {
            // Don't care about this axis as we have no scrollbar to
            // manipulate.
            return false;
         }
      }
      JScrollBar scrollbar = axisToState_.get(axis).scrollbar_;
      int axisLen = newPos + 1;
      if (scrollbar.getMaximum() < axisLen) {
         // Expand the range on the scrollbar.
         scrollbar.setMaximum(axisLen);
         axisToState_.get(axis).maxLabel_.setText(
               "/ " + (String.valueOf(axisLen)));
      }
      int pos = scrollbar.getValue();
      ScrollbarLockIcon.LockedState lockState = axisToState_.get(axis).lockState_;
      synchronized(this) {
         shouldPostEvents_ = false;
         if (null != lockState) switch (lockState) {
         // This axis is not allowed to move.
            case SUPERLOCKED:
               break;
            case LOCKED:
               // This axis can change, but must be snapped back later. Only if
               // we don't already have a saved index, though.
               if (!axisToSavedPosition_.containsKey(axis)) {
                  axisToSavedPosition_.put(axis, pos);
               }  scrollbar.setValue(newPos);
               break;
            default:
               // This axis is allowed to move and we don't need to snap it
               // back later.
               if (axisToSavedPosition_.containsKey(axis)) {
                  axisToSavedPosition_.remove(axis);
               }  scrollbar.setValue(newPos);
               break;
         }
         shouldPostEvents_ = true;
      }
      return didAddScroller;
   }

   /**
    * The canvas has finished drawing an image; move to the next one in our
    * animation.
    * @param event - CanvasDrawCompleteEvent
    */
   @Subscribe
   public void onCanvasDrawComplete(CanvasDrawCompleteEvent event) {
      updateAnimation();
   }

   /**
    * Move another step forward in our animation (assuming we are animated).
    */
   private synchronized void updateAnimation() {
      if (animationTimer_ != null) {
         animationTimer_.cancel();
      }

      boolean amAnimated = false;
      for (String axis : axisToState_.keySet()) {
         if (axisToState_.get(axis).isAnimated_) {
            amAnimated = true;
            break;
         }
      }
      if (!amAnimated) {
         // No animation is currently running.
         lastAnimationTimeMs_ = 0;
         return;
      }

      // Calculate the update rate and the number of images to step forward
      // by for each animation tick. We cap at an update rate of 30FPS;
      // past that, we step forward by more than one image per step to
      // compensate.
      double updateFPS = Math.min(30.0, animationFPS_);
      animationStepSize_ = (int) Math.max(1,
            Math.round(updateFPS * animationFPS_ / 1000.0));

      animationTimer_ = new Timer("Scroller panel animation");
      // This task simply pushes the scrollbars forward and then requests
      // drawing at the new coordinates.
      TimerTask task = new TimerTask() {
         @Override
         public void run() {
            lastAnimationTimeMs_ = System.currentTimeMillis();
            shouldPostEvents_ = false;
            for (String axis : axisToState_.keySet()) {
               if (axisToState_.get(axis).isAnimated_ &&
                     axisToState_.get(axis).lockState_ == ScrollbarLockIcon.LockedState.UNLOCKED) {
                  advancePosition(axis, animationStepSize_);
               }
            }
            shouldPostEvents_ = true;
            postDrawEvent();
         }
      };

      // Schedule the update to happen either immediately (for the first time
      // we run) or a set time after the last time we ran (for continuous
      // running).
      if (lastAnimationTimeMs_ == 0) {
         animationTimer_.schedule(task, 0);
      }
      else {
         // Target delay between updates
         int delayMs = (int) (1000 / updateFPS);
         long sleepTime = Math.max(0,
               delayMs - (System.currentTimeMillis() - lastAnimationTimeMs_));
         animationTimer_.schedule(task, sleepTime);
      }
   }

   /**
    * Push the relevant scrollbar forward, wrapping around when it hits the
    * end. Only used for animating.
    */
   private void advancePosition(String axis, int offset) {
      AxisState state = axisToState_.get(axis);
      JScrollBar scrollbar = state.scrollbar_;
      int target = (state.animatedIndex_ + offset) % scrollbar.getMaximum();
      scrollbar.setValue(target);
      state.animatedIndex_ = target;
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      store_.unregisterForEvents(this);
      display_.unregisterForEvents(this);
      if (animationTimer_ != null) {
         animationTimer_.cancel();
      }
      if (snapbackTimer_ != null) {
         snapbackTimer_.cancel();
      }
      shouldStopUpdates_.set(true);
      updateThread_.interrupt();
   }

   // Used by the ImageCoordsLinker to query position.
   public int getIndex(String axis) {
      return axisToState_.get(axis).scrollbar_.getValue();
   }
}
