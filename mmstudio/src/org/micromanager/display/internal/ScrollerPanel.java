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
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import net.miginfocom.swing.MigLayout;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JTextField;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.NewImageEvent;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewDisplaySettingsEvent;

import org.micromanager.data.internal.DefaultCoords;

import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
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
class ScrollerPanel extends JPanel {
   /**
    * This class tracks relevant state for a single axis' set of controls.
    */
   private class AxisState {
      boolean isAnimated_;
      JButton posButton_;
      JLabel maxLabel_;
      JScrollBar scrollbar_;
      ScrollbarLockIcon.LockedState lockState_;
      // The saved index is the index we need to snap back to later.
      int savedIndex_;
      // The cached index is the index we last recorded for the
      // scrollbar.
      int cachedIndex_;
      
      public AxisState(JButton posButton, JScrollBar scrollbar, JLabel maxLabel) {
         isAnimated_ = false;
         posButton_ = posButton;
         scrollbar_ = scrollbar;
         maxLabel_ = maxLabel;
         lockState_ = ScrollbarLockIcon.LockedState.UNLOCKED;
         savedIndex_ = 0;
         cachedIndex_ = 0;
      }
   }

   /**
    * This class shows a popup menu to set the exact location of an axis.
    */
   public class PositionPopup extends JPopupMenu {
      public PositionPopup(final String axis, JButton button) {
         add(new JLabel("Set index: "));
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
         add(field);
      }
   }

   private final Datastore store_;
   private final DisplayWindow parent_;
   private DisplaySettings settingsCache_;

   private final HashMap<String, AxisState> axisToState_;
   private final HashMap<String, Integer> axisToSavedPosition_;
   private JButton fpsButton_;
   private final FPSPopupMenu fpsMenu_;
   private int fps_;

   private Timer snapbackTimer_;
   private Timer animationTimer_;
   // We turn this off when we want to update the position of several
   // scrollbars in rapid succession, so that we don't post multiple spurious
   // draw requests.
   private boolean shouldPostEvents_ = true;

   public ScrollerPanel(Datastore store, DisplayWindow parent) {
      store_ = store;
      parent_ = parent;

      axisToState_ = new HashMap<String, AxisState>();
      axisToSavedPosition_ = new HashMap<String, Integer>();

      // Only the scrollbar column is allowed to grow in width
      // Columns are animate, current position, max position, scrollbar,
      // lock, link
      setLayout(new MigLayout("insets 0", 
               "[][][][grow, shrink][][]"));
      // Don't prevent other components from shrinking
      setMinimumSize(new Dimension(1, 1));

      ArrayList<String> axes = new ArrayList<String>(store_.getAxes());
      Collections.sort(axes);
      for (String axis : axes) {
         // Don't bother creating scrollers for axes with a length of 1.
         if (store_.getAxisLength(axis) > 1) {
            addScroller(axis);
         }
      }

      Integer fps = parent.getDisplaySettings().getAnimationFPS();
      // Default to 10 if it's not set.
      fps_ = (fps == null) ? 10 : fps;
      fpsMenu_ = new FPSPopupMenu(parent_, fps_);
      if (fps == null) {
         // Update the DisplaySettings to reflect our default.
         parent.setDisplaySettings(parent.getDisplaySettings().copy()
               .animationFPS(fps_).build());
      }

      store_.registerForEvents(this);
      parent_.registerForEvents(this);
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
      final ScrollbarAnimateIcon animateIcon = new ScrollbarAnimateIcon(axis);
      Dimension size = new Dimension(24, 14);
      animateIcon.setPreferredSize(size);
      animateIcon.setMaximumSize(size);
      animateIcon.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            toggleAnimation(axis, animateIcon);
            animateIcon.setIsAnimated(axisToState_.get(axis).isAnimated_);
         }
      });
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
      final JButton positionButton = new JButton("0");
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
            setPosition(axis, scrollbar.getValue());
         }
      });
      add(scrollbar, "shrinkx, growx");

      ScrollbarLockIcon lock = new ScrollbarLockIcon(axis,
            parent_.getDisplayBus());
      add(lock, "grow 0");

      LinkButton linker = new LinkButton(new ImageCoordsLinker(axis, parent_),
            parent_);
      add(linker, "grow 0, wrap");

      axisToState_.put(axis, new AxisState(positionButton, scrollbar, maxLabel));

      if (fpsButton_ == null) {
         // We have at least one scroller, so add our FPS control button.
         fpsButton_ = new JButton("FPS: " + fps_);
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
    * icon.
    */
   private void toggleAnimation(String axis, ScrollbarAnimateIcon icon) {
      axisToState_.get(axis).isAnimated_ = !axisToState_.get(axis).isAnimated_;
      resetAnimation();
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
      DisplaySettings settings = parent_.getDisplaySettings();
      settings = settings.copy().imageCoords(target).build();
      settingsCache_ = settings;
      parent_.setDisplaySettings(settings);
      parent_.postEvent(new ImageCoordsEvent(target));
      parent_.postEvent(new DefaultRequestToDrawEvent(builder.build()));
   }

   /**
    * One of our lock icons changed state; update lock statuses.
    */
   @Subscribe
   public void onLockChanged(ScrollbarLockIcon.LockEvent event) {
      String axis = event.getAxis();
      ScrollbarLockIcon.LockedState lockState = event.getLockedState();
      axisToState_.get(axis).lockState_ = lockState;
      resetAnimation();
   }

   /**
    * Display settings have changed; check for new drawing coordinates and FPS.
    */
   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      DisplaySettings settings = event.getDisplaySettings();
      if (settings == settingsCache_) {
         // Display is telling us about settings that we set on it, so we can
         // safely ignore these as we're already up-to-date.
         return;
      }
      if (settings.getAnimationFPS() != null &&
            settings.getAnimationFPS() != fps_) {
         fps_ = settings.getAnimationFPS();
         fpsButton_.setText("FPS: " + fps_);
         resetAnimation();
      }
      Coords coords = settings.getImageCoords();
      if (coords == null) {
         return;
      }
      shouldPostEvents_ = false;
      for (String axis : axisToState_.keySet()) {
         axisToState_.get(axis).scrollbar_.setValue(coords.getIndex(axis));
      }
      shouldPostEvents_ = true;
   }

   /**
    * Animation state or lock state has changed; reset our animation timers.
    */
   private void resetAnimation() {
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
         return;
      }

      // Calculate the update rate and the number of images to step forward
      // by for each animation tick. We cap at an update rate of 30FPS;
      // past that, we step forward by more than one image per step to
      // compensate.
      int updateRate = Math.min(30, fps_);
      final int updateStep = (int) Math.max(1,
            Math.round(updateRate * fps_ / 1000.0));

      animationTimer_ = new Timer();
      TimerTask task = new TimerTask() {
         @Override
         public void run() {
            shouldPostEvents_ = false;
            for (String axis : axisToState_.keySet()) {
               if (axisToState_.get(axis).isAnimated_ &&
                     axisToState_.get(axis).lockState_ == ScrollbarLockIcon.LockedState.UNLOCKED) {
                  advancePosition(axis, updateStep);
               }
            }
            shouldPostEvents_ = true;
            postDrawEvent();
         }
      };

      animationTimer_.schedule(task, 0, (int) (1000.0 / updateRate));
   }

   /**
    * A new image has arrived; update our scrollbar maxima and positions. Add
    * new scrollbars as needed.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      try {
         Coords coords = event.getImage().getCoords();
         Coords.CoordsBuilder displayedBuilder = coords.copy();
         boolean didAddScrollers = false;
         for (String axis : store_.getAxes()) {
            int newPos = coords.getIndex(axis);
            if (!axisToState_.containsKey(axis)) {
               if (newPos != 0) {
                  // Now have at least two positions along this axis; add a
                  // scroller.
                  addScroller(axis);
                  didAddScrollers = true;
               }
               else {
                  // Don't care about this axis as we have no scrollbar to
                  // manipulate.
                  continue;
               }
            }
            JScrollBar scrollbar = axisToState_.get(axis).scrollbar_;
            int axisLen = coords.getIndex(axis) + 1;
            if (scrollbar.getMaximum() < axisLen) {
               // Expand the range on the scrollbar.
               scrollbar.setMaximum(axisLen);
               axisToState_.get(axis).maxLabel_.setText(
                     "/ " + (String.valueOf(axisLen)));
            }
            int pos = scrollbar.getValue();
            ScrollbarLockIcon.LockedState lockState = axisToState_.get(axis).lockState_;
            if (lockState == ScrollbarLockIcon.LockedState.SUPERLOCKED) {
               // This axis is not allowed to move.
               displayedBuilder.index(axis, pos);
            }
            else if (lockState == ScrollbarLockIcon.LockedState.LOCKED) {
               // This axis can change, but must be snapped back later. Only if
               // we don't already have a saved index, though.
               if (!axisToSavedPosition_.containsKey(axis)) {
                  axisToSavedPosition_.put(axis, pos);
               }
               scrollbar.setValue(newPos);
            }
            else {
               // This axis is allowed to move and we don't need to snap it
               // back later.
               scrollbar.setValue(newPos);
            }
         }
         if (didAddScrollers) {
            // Ensure new scrollers get displayed properly.
            parent_.postEvent(new LayoutChangedEvent());
         }
         parent_.postEvent(new DefaultRequestToDrawEvent(displayedBuilder.build()));

         // Set up snapping back to our current positions. 
         if (axisToSavedPosition_.size() > 0) {
            if (snapbackTimer_ != null) {
               snapbackTimer_.cancel();
            }
            snapbackTimer_ = new Timer();
            TimerTask task = new TimerTask() {
               @Override
               public void run() {
                  shouldPostEvents_ = false;
                  for (String axis : axisToSavedPosition_.keySet()) {
                     int pos = axisToSavedPosition_.get(axis);
                     axisToState_.get(axis).scrollbar_.setValue(pos);
                  }
                  shouldPostEvents_ = true;
                  postDrawEvent();
               }
            };
            snapbackTimer_.schedule(task, 500);
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error in onNewImage for ScrollerPanel");
      }
   }

   /**
    * Push the relevant scrollbar forward, wrapping around when it hits the
    * end.
    */
   private void advancePosition(String axis, int offset) {
      JScrollBar scrollbar = axisToState_.get(axis).scrollbar_;
      int target = (scrollbar.getValue() + offset) % scrollbar.getMaximum();
      scrollbar.setValue(target);
   }

   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      store_.unregisterForEvents(this);
      parent_.unregisterForEvents(this);
      if (animationTimer_ != null) {
         animationTimer_.cancel();
      }
      if (snapbackTimer_ != null) {
         snapbackTimer_.cancel();
      }
   }
}
